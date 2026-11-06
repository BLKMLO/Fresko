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
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Point d'accès unique aux œuvres suivies pour l'UI et le check. */
class WorkRepository(private val dao: WorkDao) : TrackedWorksStore {

    fun observeTracked(): Flow<List<WorkEntity>> = dao.observeTracked()

    suspend fun get(key: WorkKey): WorkEntity? = dao.get(key.sourceId.value, key.key)

    /**
     * Suit une œuvre trouvée par la recherche (référence = son dernier chapitre connu).
     * @param groupWith œuvre déjà suivie dont c'est la même œuvre sur une autre source :
     *   les deux rejoignent le même groupe.
     */
    suspend fun track(info: WorkInfo, now: Long = System.currentTimeMillis(), groupWith: WorkKey? = null) {
        val groupId = groupWith?.let { ensureGroup(it) }
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
                readChapterUrl = c?.url, // ce qui est déjà sorti au moment du suivi est « lu »
                readChapterNumber = c?.number,
                groupId = groupId,
            )
        )
        if (groupId != null) markReferenceRead(info.key)
    }

    /**
     * La référence (chapitre déjà sorti au moment du suivi) est considérée comme lue… sauf,
     * dans un groupe, si cette source est en avance sur ce que l'utilisateur a lu ailleurs :
     * l'œuvre apparaît alors « à lire » (sans notification).
     */
    suspend fun markReferenceRead(key: WorkKey) {
        val e = get(key) ?: return
        val group = e.groupId ?: return dao.markRead(e.sourceId, e.workKey)
        val number = e.chapterNumber
        val readElsewhere = dao.groupReadNumber(group, e.sourceId, e.workKey)
        if (number == null || readElsewhere == null || number <= readElsewhere) {
            dao.markRead(e.sourceId, e.workKey)
        } else {
            dao.markUnread(e.sourceId, e.workKey)
        }
    }

    /** Rattache une œuvre déjà suivie (et son éventuel groupe) au groupe de [groupWith]. */
    suspend fun joinGroup(key: WorkKey, groupWith: WorkKey) {
        if (key == groupWith) return
        val target = ensureGroup(groupWith)
        val e = get(key) ?: return
        when (val current = e.groupId) {
            null -> dao.setGroup(e.sourceId, e.workKey, target)
            target -> Unit
            else -> dao.moveGroup(current, target)
        }
    }

    private suspend fun ensureGroup(key: WorkKey): String {
        val lead = get(key) ?: error("Œuvre inconnue : $key")
        lead.groupId?.let { return it }
        val id = UUID.randomUUID().toString()
        dao.setGroup(lead.sourceId, lead.workKey, id)
        return id
    }

    /** Marque lue l'œuvre (et toutes les sources de son groupe). */
    suspend fun markRead(key: WorkKey) {
        val group = get(key)?.groupId
        if (group != null) dao.markGroupRead(group) else dao.markRead(key.sourceId.value, key.key)
    }

    suspend fun markUnread(key: WorkKey) {
        val group = get(key)?.groupId
        if (group != null) dao.markGroupUnread(group) else dao.markUnread(key.sourceId.value, key.key)
    }

    /** Nombre d'œuvres suivies par source (y compris sources désactivées). */
    fun observeCountsBySource(): Flow<Map<SourceId, Int>> =
        dao.observeTracked().map { list -> list.groupingBy { SourceId(it.sourceId) }.eachCount() }

    /** Toutes les œuvres suivies (pour la sauvegarde). */
    suspend fun snapshot(): List<WorkEntity> = dao.allTracked()

    /** Restauration : ajoute ou remplace les œuvres (celles déjà présentes sont écrasées). */
    suspend fun restore(entities: List<WorkEntity>) = dao.insertAll(entities)

    /** Ne plus suivre cette source ; un groupe réduit à une seule source est dissous. */
    suspend fun untrack(key: WorkKey) {
        val group = get(key)?.groupId
        dao.delete(key.sourceId.value, key.key)
        if (group != null) {
            val rest = dao.groupMembers(group)
            if (rest.size == 1) dao.setGroup(rest[0].sourceId, rest[0].workKey, null)
        }
    }

    /** Ne plus suivre l'œuvre du tout (toutes les sources de son groupe). */
    suspend fun untrackAll(key: WorkKey) {
        val group = get(key)?.groupId
        if (group != null) dao.deleteGroup(group) else dao.delete(key.sourceId.value, key.key)
    }

    override suspend fun groupBestNumber(groupId: String, exclude: WorkKey): Double? =
        dao.groupBestNumber(groupId, exclude.sourceId.value, exclude.key)

    suspend fun setNotifyEarlyAccess(key: WorkKey, notify: Boolean) =
        dao.setNotifyEarlyAccess(key.sourceId.value, key.key, notify)

    override suspend fun trackedWorks(sourceId: SourceId): List<TrackedWork> =
        dao.tracked(sourceId.value).map { it.toTrackedWork() }

    override suspend fun saveLatestChapter(key: WorkKey, chapter: ChapterInfo, checkedAtMillis: Long) =
        dao.updateChapter(
            key.sourceId.value, key.key, chapter.number, chapter.label, chapter.url,
            chapter.publishedAtMillis, chapter.isEarlyAccess, checkedAtMillis,
        )

    override suspend fun setCheckError(key: WorkKey, message: String?, atMillis: Long) =
        dao.setCheckError(key.sourceId.value, key.key, message, if (message == null) null else atMillis)

    override suspend fun setEarlyPending(key: WorkKey, pending: Boolean) =
        dao.setEarlyPending(key.sourceId.value, key.key, pending)
}
