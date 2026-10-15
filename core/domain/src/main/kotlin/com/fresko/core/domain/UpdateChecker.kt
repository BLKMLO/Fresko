package com.fresko.core.domain

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
            val latest = updates.latestByKey[work.key]
                ?: if (!fullyCovered || work.lastKnownChapter == null) {
                    try {
                        source.fetchLatestChapter(work.key)
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        if (firstError == null) firstError = e
                        null
                    }
                } else {
                    null
                }
                ?: continue

            val known = work.lastKnownChapter
            when {
                known == null -> works.saveLatestChapter(work.key, latest, startedAt)
                latest.isNewerThan(known) -> {
                    works.saveLatestChapter(work.key, latest, startedAt)
                    found += NewChapter(work, latest)
                }
            }
        }
        // On ne valide la période que si tout a pu être vérifié, sinon le prochain
        // check refera la vérification complète.
        firstError?.let { throw PartialCheckException(found, it) }
        state.saveCursor(source.id, UpdateCursor(startedAt, updates.anchors))
        return found
    }
}

data class CheckReport(
    val newChapters: List<NewChapter>,
    val errors: Map<SourceId, Throwable>,
)

/** Certaines œuvres n'ont pas pu être vérifiées ; [found] contient celles qui l'ont été. */
class PartialCheckException(val found: List<NewChapter>, cause: Throwable) :
    Exception("Vérification partielle", cause)
