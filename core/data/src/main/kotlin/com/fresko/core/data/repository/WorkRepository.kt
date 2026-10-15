package com.fresko.core.data.repository

import com.fresko.core.data.db.WorkDao
import com.fresko.core.data.db.WorkEntity
import com.fresko.core.domain.TrackedWorksStore
import com.fresko.core.model.ChapterInfo
import com.fresko.core.model.SourceId
import com.fresko.core.model.TrackedWork
import com.fresko.core.model.WorkInfo
import com.fresko.core.model.WorkKey
import kotlinx.coroutines.flow.Flow

/** Point d'accès unique aux œuvres pour l'UI et le check. */
class WorkRepository(private val dao: WorkDao) : TrackedWorksStore {

    fun observeAll(): Flow<List<WorkEntity>> = dao.observeAll()

    suspend fun catalogSize(sourceId: SourceId): Int = dao.count(sourceId.value)

    suspend fun mergeCatalog(items: List<WorkInfo>) = dao.mergeCatalog(items.map { it.toEntity() })

    suspend fun get(key: WorkKey): WorkEntity? = dao.get(key.sourceId.value, key.key)

    suspend fun setTracked(key: WorkKey, tracked: Boolean, now: Long = System.currentTimeMillis()) =
        dao.setTracked(key.sourceId.value, key.key, tracked, if (tracked) now else null)

    override suspend fun trackedWorks(sourceId: SourceId): List<TrackedWork> =
        dao.tracked(sourceId.value).map { it.toTrackedWork() }

    override suspend fun saveLatestChapter(key: WorkKey, chapter: ChapterInfo, checkedAtMillis: Long) =
        dao.updateChapter(
            key.sourceId.value, key.key, chapter.number, chapter.label, chapter.url,
            chapter.publishedAtMillis, chapter.isEarlyAccess, checkedAtMillis,
        )

    private fun WorkInfo.toEntity() = WorkEntity(
        sourceId = key.sourceId.value,
        workKey = key.key,
        title = title,
        url = url,
        coverUrl = coverUrl,
        chapterNumber = latestChapter?.number,
        chapterLabel = latestChapter?.label,
        chapterUrl = latestChapter?.url,
        chapterPublishedAt = latestChapter?.publishedAtMillis,
        chapterEarlyAccess = latestChapter?.isEarlyAccess ?: false,
    )
}
