package com.fresko.core.data.backup

import com.fresko.core.data.db.WorkEntity
import com.fresko.core.domain.backup.BackupChapter
import com.fresko.core.domain.backup.BackupWork

/** Conversion œuvre en base ↔ œuvre sauvegardée. */
object BackupMapper {

    fun toBackup(e: WorkEntity) = BackupWork(
        source = e.sourceId,
        key = e.workKey,
        title = e.title,
        url = e.url,
        cover = e.coverUrl,
        trackedAt = e.trackedAt,
        chapter = if (e.chapterUrl != null && e.chapterLabel != null) BackupChapter(
            number = e.chapterNumber,
            label = e.chapterLabel,
            url = e.chapterUrl,
            publishedAt = e.chapterPublishedAt,
            earlyAccess = e.chapterEarlyAccess,
        ) else null,
        readChapterUrl = e.readChapterUrl,
        notifyEarlyAccess = e.notifyEarlyAccess,
    )

    fun toEntity(w: BackupWork) = WorkEntity(
        sourceId = w.source,
        workKey = w.key,
        title = w.title,
        url = w.url,
        coverUrl = w.cover,
        tracked = true,
        trackedAt = w.trackedAt,
        chapterNumber = w.chapter?.number,
        chapterLabel = w.chapter?.label,
        chapterUrl = w.chapter?.url,
        chapterPublishedAt = w.chapter?.publishedAt,
        chapterEarlyAccess = w.chapter?.earlyAccess ?: false,
        notifyEarlyAccess = w.notifyEarlyAccess,
        readChapterUrl = w.readChapterUrl,
    )
}
