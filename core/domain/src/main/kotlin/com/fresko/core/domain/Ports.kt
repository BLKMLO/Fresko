package com.fresko.core.domain

import com.fresko.core.model.ChapterInfo
import com.fresko.core.model.SourceId
import com.fresko.core.model.TrackedWork
import com.fresko.core.model.WorkKey
import com.fresko.core.source.UpdateCursor

/** Accès aux œuvres suivies (implémenté par core:data avec Room). */
interface TrackedWorksStore {
    suspend fun trackedWorks(sourceId: SourceId): List<TrackedWork>
    suspend fun saveLatestChapter(key: WorkKey, chapter: ChapterInfo, checkedAtMillis: Long)
}

/** État persistant du check (implémenté par core:data avec DataStore). */
interface CheckStateStore {
    suspend fun cursor(sourceId: SourceId): UpdateCursor?
    suspend fun saveCursor(sourceId: SourceId, cursor: UpdateCursor)
}

/** Réglages utiles au domaine. */
interface EnabledSourcesProvider {
    suspend fun enabledSourceIds(): Set<SourceId>
}
