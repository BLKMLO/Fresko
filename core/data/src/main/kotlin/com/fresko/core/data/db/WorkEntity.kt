package com.fresko.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.fresko.core.model.ChapterInfo
import com.fresko.core.model.SourceId
import com.fresko.core.model.TrackedWork
import com.fresko.core.model.WorkKey

/**
 * Une œuvre du catalogue local. Pour une œuvre suivie, les colonnes `chapter_*`
 * contiennent le dernier chapitre déjà notifié (elles ne sont plus écrasées par
 * le rafraîchissement du catalogue).
 */
@Entity(tableName = "works", primaryKeys = ["source_id", "work_key"])
data class WorkEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "work_key") val workKey: String,
    val title: String,
    val url: String,
    @ColumnInfo(name = "cover_url") val coverUrl: String?,
    val tracked: Boolean = false,
    @ColumnInfo(name = "tracked_at") val trackedAt: Long? = null,
    @ColumnInfo(name = "chapter_number") val chapterNumber: Double? = null,
    @ColumnInfo(name = "chapter_label") val chapterLabel: String? = null,
    @ColumnInfo(name = "chapter_url") val chapterUrl: String? = null,
    @ColumnInfo(name = "chapter_published_at") val chapterPublishedAt: Long? = null,
    @ColumnInfo(name = "chapter_early_access") val chapterEarlyAccess: Boolean = false,
    @ColumnInfo(name = "last_checked_at") val lastCheckedAt: Long? = null,
) {
    val key: WorkKey get() = WorkKey(SourceId(sourceId), workKey)

    val chapter: ChapterInfo?
        get() = if (chapterUrl == null || chapterLabel == null) null else ChapterInfo(
            number = chapterNumber,
            label = chapterLabel,
            url = chapterUrl,
            publishedAtMillis = chapterPublishedAt,
            isEarlyAccess = chapterEarlyAccess,
        )

    fun toTrackedWork() = TrackedWork(key, title, url, chapter)
}
