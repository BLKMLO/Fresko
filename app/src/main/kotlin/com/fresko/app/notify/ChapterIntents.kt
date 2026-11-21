package com.fresko.app.notify

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import com.fresko.app.FreskoApp
import com.fresko.app.SourceRegistry
import com.fresko.core.model.NewChapter
import com.fresko.core.model.SourceId
import com.fresko.core.model.WorkKey
import com.fresko.feature.checker.ChapterActions
import kotlinx.coroutines.launch

/** Intents des notifications de chapitre : ouvrir (et marquer lu), ou marquer lu seulement. */
class AppChapterActions(private val context: Context, private val sources: SourceRegistry) : ChapterActions {

    override fun sourceName(id: SourceId): String = sources[id]?.displayName ?: id.value

    override fun openIntent(item: NewChapter, notificationId: Int): Intent =
        Intent(context, OpenChapterActivity::class.java).withChapter(item, notificationId)

    override fun markReadIntent(item: NewChapter, notificationId: Int): Intent =
        Intent(context, MarkReadReceiver::class.java).withChapter(item, notificationId)

    private fun Intent.withChapter(item: NewChapter, notificationId: Int) = apply {
        // Données uniques par notification (sinon Android réutilise le PendingIntent d'une autre).
        data = Uri.parse("fresko://chapter/$notificationId")
        putExtra(EXTRA_SOURCE, item.work.key.sourceId.value)
        putExtra(EXTRA_KEY, item.work.key.key)
        putExtra(EXTRA_URL, item.chapter.url)
        item.chapter.number?.let { putExtra(EXTRA_NUMBER, it) }
        putExtra(EXTRA_NOTIFICATION, notificationId)
    }

    companion object {
        const val EXTRA_SOURCE = "source"
        const val EXTRA_KEY = "key"
        const val EXTRA_URL = "url"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_NOTIFICATION = "notification"
    }
}

private class ChapterExtras(val key: WorkKey, val url: String, val number: Double?, val notificationId: Int)

private fun Intent.chapterExtras(): ChapterExtras? {
    val source = getStringExtra(AppChapterActions.EXTRA_SOURCE) ?: return null
    val key = getStringExtra(AppChapterActions.EXTRA_KEY) ?: return null
    val url = getStringExtra(AppChapterActions.EXTRA_URL) ?: return null
    val number = if (hasExtra(AppChapterActions.EXTRA_NUMBER)) getDoubleExtra(AppChapterActions.EXTRA_NUMBER, 0.0) else null
    return ChapterExtras(WorkKey(SourceId(source), key), url, number, getIntExtra(AppChapterActions.EXTRA_NOTIFICATION, 0))
}

/**
 * Toucher une notification : marque le chapitre lu (comme « Lire » dans la fiche) puis
 * l'ouvre dans le navigateur. Activité invisible qui se ferme aussitôt.
 */
class OpenChapterActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent.chapterExtras()
        if (extras != null) {
            val app = application as FreskoApp
            app.appScope.launch { app.container.works.markReadIfCurrent(extras.key, extras.url, extras.number) }
            val url = app.container.sources[extras.key.sourceId]?.openableUrl(extras.url) ?: extras.url
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
        finish()
    }
}

/** Bouton « Marquer lu » d'une notification : marque lu sans ouvrir, et retire la notification. */
class MarkReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.chapterExtras() ?: return
        val app = context.applicationContext as FreskoApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.container.works.markReadIfCurrent(extras.key, extras.url, extras.number)
                NotificationManagerCompat.from(context).cancel(extras.notificationId)
            } finally {
                pending.finish()
            }
        }
    }
}
