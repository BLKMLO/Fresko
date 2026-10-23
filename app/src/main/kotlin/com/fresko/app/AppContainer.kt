package com.fresko.app

import android.content.Context
import com.fresko.app.backup.BackupController
import com.fresko.core.data.DataModule
import com.fresko.core.data.settings.SettingsRepository
import com.fresko.core.domain.UpdateChecker
import com.fresko.app.challenge.ChallengeActivity
import com.fresko.app.net.ChallengeAwareFetcher
import com.fresko.app.net.ChallengeRegistry
import com.fresko.app.net.CloudflareSolver
import com.fresko.app.net.WebUserAgent
import com.fresko.app.net.WebViewCookieJar
import com.fresko.core.network.HttpFetcher
import com.fresko.core.network.OkHttpFetcher
import com.fresko.feature.checker.ChapterNotifier
import com.fresko.feature.checker.CheckScheduler
import com.fresko.feature.checker.CheckerDependencies

/** Injection de dépendances manuelle : tout le câblage de l'app est ici. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    // Réseau : cookies partagés avec la WebView + même User-Agent (anti-robot Cloudflare)
    val cookies = WebViewCookieJar()
    val challenges = ChallengeRegistry(appContext)
    private val solver = CloudflareSolver(appContext, cookies)
    val http: HttpFetcher = ChallengeAwareFetcher(
        delegate = OkHttpFetcher(
            client = OkHttpFetcher.defaultClient().newBuilder().cookieJar(cookies).build(),
            userAgent = WebUserAgent.get(appContext),
        ),
        solver = solver,
        registry = challenges,
    )
    val sources = SourceRegistry(http)
    private val data = DataModule(appContext)
    val works get() = data.works
    val settings = SettingsRepository(appContext, sources.all.map { it.id })
    val notifier = ChapterNotifier(appContext)
    val scheduler by lazy { CheckScheduler(appContext) }

    val checker by lazy {
        UpdateChecker(
            sources = sources.all,
            enabled = settings,
            works = works,
            state = settings,
        )
    }

    val backup by lazy {
        BackupController(appContext, works, settings, { scheduler }, sources.all.map { it.id })
    }

    fun checkerDependencies() = CheckerDependencies(
        checker = checker,
        notifier = notifier,
        scheduler = scheduler,
        intervalMinutes = { settings.current().intervalMinutes },
        onChallenge = { e ->
            if (challenges.shouldNotify(e.url)) {
                notifier.notifyChallenge(
                    ChallengeRegistry.hostOf(e.url) ?: e.url,
                    ChallengeActivity.intent(appContext, ChallengeRegistry.rootOf(e.url)),
                )
            }
        },
    )
}
