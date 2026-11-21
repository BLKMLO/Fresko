package com.fresko.feature.checker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fresko.core.model.NewChapter
import com.fresko.core.model.SourceId

/**
 * Ce que l'application fournit aux notifications : nom affiché des sources, et intents
 * d'ouverture / « Marquer lu » (activité et récepteur déclarés dans le module app).
 */
interface ChapterActions {
    fun sourceName(id: SourceId): String = id.value

    /** Activité lancée au toucher de la notification (par défaut : le chapitre dans le navigateur). */
    fun openIntent(item: NewChapter, notificationId: Int): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(item.chapter.url))

    /** Broadcast du bouton « Marquer lu » ; null = pas de bouton. */
    fun markReadIntent(item: NewChapter, notificationId: Int): Intent? = null
}

/** Affiche une notification par nouveau chapitre ; un appui ouvre le chapitre. */
class ChapterNotifier(
    private val context: Context,
    private val actions: ChapterActions = object : ChapterActions {},
) {

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Nouveaux chapitres",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Prévient quand une œuvre suivie publie un chapitre" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notify(chapters: List<NewChapter>) {
        if (chapters.isEmpty() || !canNotify()) return
        ensureChannel()
        val manager = NotificationManagerCompat.from(context)
        for (item in chapters) {
            val chapter = item.chapter
            val id = notificationId(item)
            val text = buildString {
                append(chapter.label)
                if (chapter.isEarlyAccess) append(" (accès anticipé)")
            }
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_chapter)
                .setColor(ACCENT)
                .setContentTitle(item.work.title)
                .setContentText(text)
                .setSubText(actions.sourceName(item.work.key.sourceId))
                .setAutoCancel(true)
                .setGroup(GROUP)
                .setContentIntent(activityIntent(actions.openIntent(item, id), id))
            actions.markReadIntent(item, id)?.let { intent ->
                builder.addAction(
                    R.drawable.ic_stat_chapter, "Marquer lu",
                    PendingIntent.getBroadcast(
                        context, id, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
            notifySafely(manager, id, builder.build())
        }
        if (chapters.size > 1) {
            val summary = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_chapter)
                .setContentTitle("${chapters.size} nouveaux chapitres")
                .setStyle(
                    NotificationCompat.InboxStyle().also { style ->
                        chapters.forEach { style.addLine("${it.work.title} — ${it.chapter.label}") }
                    }
                )
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()
            notifySafely(manager, SUMMARY_ID, summary)
        }
    }

    /** Un site demande une vérification anti-robot : un appui ouvre l'écran de vérification. */
    fun notifyChallenge(host: String, intent: Intent) {
        if (!canNotify()) return
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_chapter)
            .setColor(ACCENT)
            .setContentTitle("Vérification requise : $host")
            .setContentText("Le site bloque les vérifications. Touchez pour la faire une fois.")
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, host.hashCode(), intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .build()
        notifySafely(NotificationManagerCompat.from(context), CHALLENGE_ID_BASE + host.hashCode(), notification)
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun notifySafely(
        manager: NotificationManagerCompat,
        id: Int,
        notification: android.app.Notification,
    ) {
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Permission retirée entre-temps : rien à faire.
        }
    }

    private fun activityIntent(intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        /** Une notification par œuvre (un nouveau chapitre remplace le précédent). */
        fun notificationId(item: NewChapter): Int = item.work.key.hashCode()

        /**
         * Les notifications peuvent-elles s'afficher ? Faux si la permission (Android 13+) est
         * refusée, si les notifications de l'app sont coupées, ou si le canal est désactivé.
         */
        fun notificationsAllowed(context: Context): Boolean {
            val permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!permission || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            val channel = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL_ID)
            return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        }

        const val CHANNEL_ID = "new_chapters"
        private const val ACCENT = 0xFFFF6B4A.toInt()
        private const val GROUP = "fresko_new_chapters"
        private const val SUMMARY_ID = 0x5EED
        private const val CHALLENGE_ID_BASE = 0x0C0F
    }
}
