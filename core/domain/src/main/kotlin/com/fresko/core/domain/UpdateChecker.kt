package com.fresko.core.domain

import com.fresko.core.model.ChapterInfo
import com.fresko.core.model.NewChapter
import com.fresko.core.model.SourceId
import com.fresko.core.model.TrackedWork
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
 * Groupes ([TrackedWork.groupId], même œuvre sur plusieurs sources) : chaque source garde
 * son propre dernier chapitre, mais un chapitre dont le numéro est déjà connu d'une autre
 * source du groupe n'est pas notifié une seconde fois. La première source qui publie gagne.
 *
 * Erreurs : l'échec de la requête dédiée à une œuvre (site en erreur, œuvre introuvable,
 * aucun chapitre renvoyé) est mémorisé sur l'œuvre ([TrackedWorksStore.setCheckError]) pour
 * être affiché, et l'œuvre est réinterrogée aux checks suivants jusqu'à réussite ; l'échec
 * global d'une source est mémorisé par [CheckStateStore.saveSourceError].
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
                // Les œuvres en échec portent leur propre erreur ; la source, elle, a répondu.
                newChapters += e.found
                errors[source.id] = e.cause ?: e
                state.saveSourceError(source.id, null, clock())
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                errors[source.id] = e
                state.saveSourceError(source.id, CheckErrors.describe(e), clock())
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
            // Une œuvre en erreur est réinterrogée à chaque check jusqu'à ce que ça passe.
            val needFetch = latest == null &&
                (!fullyCovered || known == null || (!allowEarly && work.earlyPending) || work.checkError != null)
            var workError: String? = null
            if (needFetch) {
                latest = try {
                    val fetched = source.fetchLatestChapter(work.key)
                    if (fetched == null) workError = CheckErrors.NO_CHAPTER
                    fetched?.takeIf { allowEarly || !it.isEarlyAccess }
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    if (firstError == null) firstError = e
                    workError = CheckErrors.describe(e)
                    null
                }
            }
            // L'œuvre a été vue (page de mises à jour ou requête dédiée) : on met son état à jour.
            if ((needFetch || latest != null) && workError != work.checkError) {
                works.setCheckError(work.key, workError, startedAt)
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
                        if (!alreadyKnownInGroup(work, latest)) found += NewChapter(work, latest)
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

    /**
     * Même œuvre suivie sur plusieurs sources : le chapitre a déjà été vu (et notifié) via
     * une autre source du groupe si son numéro n'est pas supérieur au meilleur connu.
     * Sans numéro, impossible de comparer entre sources : on notifie.
     */
    private suspend fun alreadyKnownInGroup(work: TrackedWork, chapter: ChapterInfo): Boolean {
        val group = work.groupId ?: return false
        val number = chapter.number ?: return false
        val best = works.groupBestNumber(group, work.key) ?: return false
        return number <= best
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
