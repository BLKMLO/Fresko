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

/** Point d'accès unique aux œuvres suivies pour l'UI et le check. */
class WorkRepository(private val dao: WorkDao) : TrackedWorksStore {

    fun observeTracked(): Flow<List<WorkEntity>> = dao.observeTracked()

    suspend fun get(key: WorkKey): WorkEntity? = dao.get(key.sourceId.value, key.key)

    /** Suit une œuvre trouvée par la recherche (référence = son dernier chapitre connu). */
    suspend fun track(info: WorkInfo, now: Long = System.currentTimeMillis()) {
        val c = info.latestChapter
        dao.insert(
            WorkEntity(
                sourceId = info.key.sourceId.value,
                workKey = info.key.key,
                title = info.title,
                url = info.url,
                coverUrl = info.coverUrl,
                tracked = true,
                trackedAt = now,
                chapterNumber = c?.number,
                chapterLabel = c?.label,
                chapterUrl = c?.url,
                chapterPublishedAt = c?.publishedAtMillis,
                chapterEarlyAccess = c?.isEarlyAccess ?: false,
            )
        )
    }

    suspend fun untrack(key: WorkKey) = dao.delete(key.sourceId.value, key.key)

    suspend fun setNotifyEarlyAccess(key: WorkKey, notify: Boolean) =
        dao.setNotifyEarlyAccess(key.sourceId.value, key.key, notify)

    override suspend fun trackedWorks(sourceId: SourceId): List<TrackedWork> =
        dao.tracked(sourceId.value).map { it.toTrackedWork() }

    override suspend fun saveLatestChapter(key: WorkKey, chapter: ChapterInfo, checkedAtMillis: Long) =
        dao.updateChapter(
            key.sourceId.value, key.key, chapter.number, chapter.label, chapter.url,
            chapter.publishedAtMillis, chapter.isEarlyAccess, checkedAtMillis,
        )

    override suspend fun setEarlyPending(key: WorkKey, pending: Boolean) =
        dao.setEarlyPending(key.sourceId.value, key.key, pending)
}
