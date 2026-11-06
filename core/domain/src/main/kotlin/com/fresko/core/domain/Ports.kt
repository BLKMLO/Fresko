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
    suspend fun setEarlyPending(key: WorkKey, pending: Boolean)

    /** Mémorise (ou efface, si [message] est null) l'échec de vérification d'une œuvre. */
    suspend fun setCheckError(key: WorkKey, message: String?, atMillis: Long)
}

/** État persistant du check (implémenté par core:data avec DataStore). */
interface CheckStateStore {
    suspend fun cursor(sourceId: SourceId): UpdateCursor?
    /** Check réussi : enregistre le curseur **et efface l'erreur de la source**. */
    suspend fun saveCursor(sourceId: SourceId, cursor: UpdateCursor)

    /** Mémorise (ou efface, si [message] est null) l'échec global du check d'une source. */
    suspend fun saveSourceError(sourceId: SourceId, message: String?, atMillis: Long)
}

/** Réglages utiles au domaine. */
interface EnabledSourcesProvider {
    suspend fun enabledSourceIds(): Set<SourceId>
}
