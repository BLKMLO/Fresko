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
import com.fresko.core.model.SourceId
import com.fresko.core.network.HttpFetcher
import com.fresko.core.network.OkHttpFetcher
import com.fresko.feature.checker.ChapterNotifier
import com.fresko.feature.checker.CheckScheduler
import com.fresko.feature.checker.CheckerDependencies
import okhttp3.OkHttpClient

/** Injection de dépendances manuelle : tout le câblage de l'app est ici. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    // Réseau : cookies partagés avec la WebView + même User-Agent (anti-robot Cloudflare)
    val cookies = WebViewCookieJar()
    val challenges = ChallengeRegistry(appContext)
    private val solver = CloudflareSolver(appContext, cookies)
    private val userAgent = WebUserAgent.get(appContext)
    private val httpClient = OkHttpFetcher.defaultClient().newBuilder().cookieJar(cookies).build()
    val http: HttpFetcher = ChallengeAwareFetcher(
        delegate = OkHttpFetcher(client = httpClient, userAgent = userAgent),
        solver = solver,
        registry = challenges,
    )
    val sources = SourceRegistry(http)

    /**
     * Client des couvertures (Coil) : mêmes cookies et même User-Agent que les requêtes de
     * l'app et la WebView. Avant la v0.7.0, Coil utilisait son propre client (User-Agent
     * « okhttp », sans cookie anti-robot) : les images d'un site protégé pouvaient échouer.
     */
    val imageClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
            }
            .build()
    }

    /** Referer à envoyer pour les couvertures, par source (voir [com.fresko.core.source.Source.imageReferer]). */
    val coverReferers: Map<SourceId, String> =
        sources.all.mapNotNull { s -> s.imageReferer?.let { s.id to it } }.toMap()
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
