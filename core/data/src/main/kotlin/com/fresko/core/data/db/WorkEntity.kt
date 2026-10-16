package com.fresko.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.fresko.core.model.ChapterInfo
import com.fresko.core.model.SourceId
import com.fresko.core.model.TrackedWork
import com.fresko.core.model.WorkKey

/**
 * Une œuvre suivie (depuis la v0.2.0, la table ne contient que les œuvres suivies :
 * les œuvres se trouvent par la recherche). Les colonnes `chapter_*` contiennent
 * le dernier chapitre déjà notifié.
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
    @ColumnInfo(name = "notify_early_access", defaultValue = "1") val notifyEarlyAccess: Boolean = true,
    @ColumnInfo(name = "early_pending", defaultValue = "0") val earlyPending: Boolean = false,
    /** URL du dernier chapitre marqué comme lu (null = rien de lu). */
    @ColumnInfo(name = "read_chapter_url") val readChapterUrl: String? = null,
) {
    /** Un chapitre plus récent que le dernier lu est disponible. */
    val isUnread: Boolean get() = chapterUrl != null && chapterUrl != readChapterUrl

    val key: WorkKey get() = WorkKey(SourceId(sourceId), workKey)

    val chapter: ChapterInfo?
        get() = if (chapterUrl == null || chapterLabel == null) null else ChapterInfo(
            number = chapterNumber,
            label = chapterLabel,
            url = chapterUrl,
            publishedAtMillis = chapterPublishedAt,
            isEarlyAccess = chapterEarlyAccess,
        )

    fun toTrackedWork() = TrackedWork(key, title, url, chapter, notifyEarlyAccess, earlyPending)
}
