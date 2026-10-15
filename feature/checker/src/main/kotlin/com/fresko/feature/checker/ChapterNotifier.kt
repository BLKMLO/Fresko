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

/** Affiche une notification par nouveau chapitre ; un appui ouvre le chapitre. */
class ChapterNotifier(private val context: Context) {

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
            val text = buildString {
                append(chapter.label)
                if (chapter.isEarlyAccess) append(" (accès anticipé)")
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_chapter)
                .setColor(ACCENT)
                .setContentTitle(item.work.title)
                .setContentText(text)
                .setSubText(item.work.key.sourceId.value)
                .setAutoCancel(true)
                .setGroup(GROUP)
                .setContentIntent(openUrl(chapter.url, item.work.key.hashCode()))
                .build()
            notifySafely(manager, item.work.key.hashCode(), notification)
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

    private fun openUrl(url: String, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CHANNEL_ID = "new_chapters"
        private const val ACCENT = 0xFFFF6B4A.toInt()
        private const val GROUP = "fresko_new_chapters"
        private const val SUMMARY_ID = 0x5EED
    }
}
