package com.fresko.core.domain

import com.fresko.core.model.ChapterInfo
import com.fresko.core.model.NewChapter
import com.fresko.core.model.SourceId
import com.fresko.core.model.isNewerThan
import com.fresko.core.source.RecentUpdates
import com.fresko.core.source.Source
import com.fresko.core.source.UpdateCursor
import kotlinx.coroutines.CancellationException

/**
 * Cœur du check périodique, indépendant d'Android.
 *
 * Pour chaque source activée ayant au moins une œuvre suivie :
 * 1. une requête « mises à jour récentes » (souvent une seule page) ;
 * 2. si ces mises à jour ne couvrent pas toute la période depuis le dernier check
 *    réussi (téléphone resté hors-ligne, premier lancement…), on interroge chaque
 *    œuvre suivie absente de la page ;
 * 3. on compare au dernier chapitre connu : plus récent → [NewChapter].
 *
 * Une œuvre sans chapitre connu est simplement initialisée (pas de notification).
 *
 * Accès anticipé désactivé pour une œuvre ([TrackedWork.notifyEarlyAccess] = false) :
 * ces chapitres sont ignorés ; tant qu'un chapitre ignoré n'est pas devenu gratuit
 * ([TrackedWork.earlyPending]), l'œuvre est vérifiée individuellement (le passage en
 * gratuit ne la fait pas remonter dans les mises à jour du site).
 */
class UpdateChecker(
    private val sources: List<Source>,
    private val enabled: EnabledSourcesProvider,
    private val works: TrackedWorksStore,
    private val state: CheckStateStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun check(): CheckReport {
        val enabledIds = enabled.enabledSourceIds()
        val newChapters = mutableListOf<NewChapter>()
        val errors = mutableMapOf<SourceId, Throwable>()
        for (source in sources.filter { it.id in enabledIds }) {
            try {
                newChapters += checkSource(source)
            } catch (e: PartialCheckException) {
                // Les chapitres trouvés sont déjà enregistrés : il faut les notifier.
                newChapters += e.found
                errors[source.id] = e.cause ?: e
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                errors[source.id] = e
            }
        }
        return CheckReport(newChapters, errors)
    }

    private suspend fun checkSource(source: Source): List<NewChapter> {
        val tracked = works.trackedWorks(source.id)
        if (tracked.isEmpty()) return emptyList()

        val startedAt = clock()
        val cursor = state.cursor(source.id)
        val updates: RecentUpdates = source.fetchRecentUpdates(cursor)
        val fullyCovered = cursor != null && updates.coversCursor

        val found = mutableListOf<NewChapter>()
        var firstError: Throwable? = null
        for (work in tracked) {
            val allowEarly = work.notifyEarlyAccess
            val known = work.lastKnownChapter
            val recent = updates.recentByKey[work.key]
            var latest = recent?.firstOrNull { allowEarly || !it.isEarlyAccess }
            val needFetch = latest == null &&
                (!fullyCovered || known == null || (!allowEarly && work.earlyPending))
            if (needFetch) {
                latest = try {
                    source.fetchLatestChapter(work.key)?.takeIf { allowEarly || !it.isEarlyAccess }
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    if (firstError == null) firstError = e
                    null
                }
            }

            var newKnown = known
            if (latest != null) {
                when {
                    known == null -> {
                        works.saveLatestChapter(work.key, latest, startedAt)
                        newKnown = latest
                    }
                    latest.isNewerThan(known) -> {
                        works.saveLatestChapter(work.key, latest, startedAt)
                        found += NewChapter(work, latest)
                        newKnown = latest
                    }
                }
            }

            val pending = when {
                allowEarly -> false
                // La page de mises à jour montre-t-elle un chapitre anticipé encore ignoré ?
                recent != null -> recent.any { it.isEarlyAccess && it.isNewerThanOrFirst(newKnown) }
                // Un chapitre gratuit vient d'être notifié : l'attente est résolue.
                newKnown !== known -> false
                else -> work.earlyPending
            }
            if (pending != work.earlyPending) works.setEarlyPending(work.key, pending)
        }
        // On ne valide la période que si tout a pu être vérifié, sinon le prochain
        // check refera la vérification complète.
        firstError?.let { throw PartialCheckException(found, it) }
        state.saveCursor(source.id, UpdateCursor(startedAt, updates.anchors))
        return found
    }
}

private fun ChapterInfo.isNewerThanOrFirst(known: ChapterInfo?) =
    known == null || isNewerThan(known)

data class CheckReport(
    val newChapters: List<NewChapter>,
    val errors: Map<SourceId, Throwable>,
)

/** Certaines œuvres n'ont pas pu être vérifiées ; [found] contient celles qui l'ont été. */
class PartialCheckException(val found: List<NewChapter>, cause: Throwable) :
    Exception("Vérification partielle", cause)
