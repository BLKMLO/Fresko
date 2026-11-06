package com.fresko.core.domain

import com.fresko.core.model.ChapterInfo
import com.fresko.core.model.SourceId
import com.fresko.core.model.TrackedWork
import com.fresko.core.model.WorkInfo
import com.fresko.core.model.WorkKey
import com.fresko.core.source.RecentUpdates
import com.fresko.core.source.Source
import com.fresko.core.source.UpdateCursor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class UpdateCheckerTest {

    private val src = SourceId("fake")
    private fun key(k: String) = WorkKey(src, k)
    private fun ch(n: Double, early: Boolean = false) =
        ChapterInfo(n, "Chapitre $n", "https://x/$n", isEarlyAccess = early)

    private class FakeSource(
        override val id: SourceId,
        var recent: RecentUpdates,
        val perWork: Map<WorkKey, ChapterInfo> = emptyMap(),
        val failPerWork: Boolean = false,
        val failRecent: Boolean = false,
        val perWorkError: Throwable = IOException("réseau"),
    ) : Source {
        override val displayName = "Fake"
        val perWorkCalls = mutableListOf<WorkKey>()
        override suspend fun search(query: String) = emptyList<WorkInfo>()
        override suspend fun fetchLatestChapter(key: WorkKey): ChapterInfo? {
            perWorkCalls += key
            if (failPerWork) throw perWorkError
            return perWork[key]
        }
        override suspend fun fetchRecentUpdates(cursor: UpdateCursor?): RecentUpdates {
            if (failRecent) throw IOException("liste")
            return recent
        }
    }

    private class Store(initial: List<TrackedWork>) : TrackedWorksStore, CheckStateStore, EnabledSourcesProvider {
        val works = initial.associateBy { it.key }.toMutableMap()
        val cursors = mutableMapOf<SourceId, UpdateCursor>()
        val sourceErrors = mutableMapOf<SourceId, String>()
        var errorWrites = 0
        var enabledIds: Set<SourceId> = initial.map { it.key.sourceId }.toSet()
        override suspend fun trackedWorks(sourceId: SourceId) = works.values.filter { it.key.sourceId == sourceId }
        override suspend fun saveLatestChapter(key: WorkKey, chapter: ChapterInfo, checkedAtMillis: Long) {
            works[key] = works.getValue(key).copy(lastKnownChapter = chapter)
        }
        override suspend fun setEarlyPending(key: WorkKey, pending: Boolean) {
            works[key] = works.getValue(key).copy(earlyPending = pending)
        }
        override suspend fun groupBestNumber(groupId: String, exclude: WorkKey) =
            works.values.filter { it.groupId == groupId && it.key != exclude }
                .mapNotNull { it.lastKnownChapter?.number }.maxOrNull()
        override suspend fun setCheckError(key: WorkKey, message: String?, atMillis: Long) {
            errorWrites++
            works[key] = works.getValue(key).copy(checkError = message)
        }
        override suspend fun cursor(sourceId: SourceId) = cursors[sourceId]
        override suspend fun saveCursor(sourceId: SourceId, cursor: UpdateCursor) {
            cursors[sourceId] = cursor
            sourceErrors.remove(sourceId)
        }
        override suspend fun saveSourceError(sourceId: SourceId, message: String?, atMillis: Long) {
            if (message == null) sourceErrors.remove(sourceId) else sourceErrors[sourceId] = message
        }
        override suspend fun enabledSourceIds() = enabledIds
    }

    private fun tracked(
        k: String, known: ChapterInfo?, early: Boolean = true, pending: Boolean = false, error: String? = null,
    ) = TrackedWork(key(k), k, "https://x/$k", known, notifyEarlyAccess = early, earlyPending = pending, checkError = error)

    @Test
    fun `nouveau chapitre detecte depuis les mises a jour recentes`() = runTest {
        val store = Store(listOf(tracked("a", ch(10.0)))).apply { cursors[src] = UpdateCursor(1_000, emptySet()) }
        val source = FakeSource(src, RecentUpdates(mapOf(key("a") to listOf(ch(11.0))), coversCursor = true, anchors = setOf("x")))
        val report = UpdateChecker(listOf(source), store, store, store, clock = { 2_000 }).check()

        assertEquals(1, report.newChapters.size)
        assertEquals(11.0, report.newChapters[0].chapter.number!!, 0.0)
        assertEquals(11.0, store.works.getValue(key("a")).lastKnownChapter!!.number!!, 0.0)
        assertEquals(UpdateCursor(2_000, setOf("x")), store.cursors[src])
        assertTrue(source.perWorkCalls.isEmpty())
    }

    @Test
    fun `pas de notification si le chapitre est deja connu`() = runTest {
        val store = Store(listOf(tracked("a", ch(11.0)))).apply { cursors[src] = UpdateCursor(1_000, emptySet()) }
        val source = FakeSource(src, RecentUpdates(mapOf(key("a") to listOf(ch(11.0))), true))
        val report = UpdateChecker(listOf(source), store, store, store).check()
        assertTrue(report.newChapters.isEmpty())
    }

    @Test
    fun `premiere verification initialise sans notifier`() = runTest {
        val store = Store(listOf(tracked("a", null)))
        val source = FakeSource(src, RecentUpdates(emptyMap(), false), perWork = mapOf(key("a") to ch(5.0)))
        val report = UpdateChecker(listOf(source), store, store, store).check()
        assertTrue(report.newChapters.isEmpty())
        assertEquals(5.0, store.works.getValue(key("a")).lastKnownChapter!!.number!!, 0.0)
    }

    @Test
    fun `curseur couvert mais premier check - fallback quand meme`() = runTest {
        // Sans curseur, coversCursor n'a pas de sens : on interroge l'œuvre.
        val store = Store(listOf(tracked("a", ch(3.0))))
        val source = FakeSource(src, RecentUpdates(emptyMap(), coversCursor = true), perWork = mapOf(key("a") to ch(4.0)))
        val report = UpdateChecker(listOf(source), store, store, store).check()
        assertEquals(listOf(key("a")), source.perWorkCalls)
        assertEquals(1, report.newChapters.size)
    }

    @Test
    fun `periode couverte - pas de requete par oeuvre`() = runTest {
        val store = Store(listOf(tracked("a", ch(3.0)))).apply { cursors[src] = UpdateCursor(1_000, emptySet()) }
        val source = FakeSource(src, RecentUpdates(emptyMap(), coversCursor = true))
        UpdateChecker(listOf(source), store, store, store).check()
        assertTrue(source.perWorkCalls.isEmpty())
    }

    @Test
    fun `periode non couverte - fallback par oeuvre`() = runTest {
        val store = Store(listOf(tracked("a", ch(3.0)))).apply { cursors[src] = UpdateCursor(1_000, emptySet()) }
        val source = FakeSource(
            src, RecentUpdates(emptyMap(), coversCursor = false),
            perWork = mapOf(key("a") to ch(4.0)),
        )
        val report = UpdateChecker(listOf(source), store, store, store).check()
        assertEquals(listOf(key("a")), source.perWorkCalls)
        assertEquals(1, report.newChapters.size)
    }

    @Test
    fun `echec partiel - chapitres trouves notifies, periode non validee`() = runTest {
        val store = Store(listOf(tracked("a", ch(1.0)), tracked("b", ch(1.0))))
        val source = FakeSource(
            src, RecentUpdates(mapOf(key("a") to listOf(ch(2.0))), false), failPerWork = true,
        )
        val report = UpdateChecker(listOf(source), store, store, store).check()
        assertEquals(1, report.newChapters.size)
        assertTrue(report.errors.containsKey(src))
        assertTrue(store.cursors.isEmpty())
    }

    @Test
    fun `source desactivee ignoree`() = runTest {
        val store = Store(listOf(tracked("a", ch(1.0)))).apply { enabledIds = emptySet() }
        val source = FakeSource(src, RecentUpdates(mapOf(key("a") to listOf(ch(2.0))), false))
        val report = UpdateChecker(listOf(source), store, store, store).check()
        assertTrue(report.newChapters.isEmpty())
    }

    @Test
    fun `acces anticipe autorise - notifie le chapitre premium`() = runTest {
        val store = Store(listOf(tracked("a", ch(10.0)))).apply { cursors[src] = UpdateCursor(1_000, emptySet()) }
        val source = FakeSource(src, RecentUpdates(mapOf(key("a") to listOf(ch(11.0, early = true), ch(10.0))), true))
        val report = UpdateChecker(listOf(source), store, store, store).check()
        assertEquals(11.0, report.newChapters.single().chapter.number!!, 0.0)
    }

    @Test
    fun `acces anticipe ignore - pas d'alerte, oeuvre mise en attente`() = runTest {
        val store = Store(listOf(tracked("a", ch(10.0), early = false))).apply { cursors[src] = UpdateCursor(1_000, emptySet()) }
        val source = FakeSource(src, RecentUpdates(mapOf(key("a") to listOf(ch(11.0, early = true), ch(10.0))), true))
        val report = UpdateChecker(listOf(source), store, store, store).check()
        assertTrue(report.newChapters.isEmpty())
        assertEquals(10.0, store.works.getValue(key("a")).lastKnownChapter!!.number!!, 0.0)
        assertTrue(store.works.getValue(key("a")).earlyPending)
    }

    @Test
    fun `chapitre devenu gratuit - alerte via la requete dediee meme si la periode est couverte`() = runTest {
        val store = Store(listOf(tracked("a", ch(10.0), early = false, pending = true)))
            .apply { cursors[src] = UpdateCursor(1_000, emptySet()) }
        // L'œuvre n'est plus dans les mises à jour : seule la requête dédiée la voit.
        val source = FakeSource(src, RecentUpdates(emptyMap(), true), perWork = mapOf(key("a") to ch(11.0)))
        val report = UpdateChecker(listOf(source), store, store, store).check()
        assertEquals(listOf(key("a")), source.perWorkCalls)
        assertEquals(11.0, report.newChapters.single().chapter.number!!, 0.0)
        assertTrue(!store.works.getValue(key("a")).earlyPending)
    }

    @Test
    fun `toujours en acces anticipe - reste en attente sans alerte`() = runTest {
        val store = Store(listOf(tracked("a", ch(10.0), early = false, pending = true)))
            .apply { cursors[src] = UpdateCursor(1_000, emptySet()) }
        val source = FakeSource(src, RecentUpdates(emptyMap(), true), perWork = mapOf(key("a") to ch(10.0)))
        val report = UpdateChecker(listOf(source), store, store, store).check()
        assertTrue(report.newChapters.isEmpty())
        assertTrue(store.works.getValue(key("a")).earlyPending)
    }

    @Test
    fun `requete dediee en echec - erreur memorisee sur l'oeuvre`() = runTest {
        val store = Store(listOf(tracked("a", ch(1.0))))
        val source = FakeSource(
            src, RecentUpdates(emptyMap(), false), failPerWork = true,
            perWorkError = com.fresko.core.network.HttpException(400, "https://x/api/a"),
        )
        UpdateChecker(listOf(source), store, store, store).check()
        val error = store.works.getValue(key("a")).checkError!!
        assertTrue(error, error.contains("introuvable") && error.contains("400"))
        assertTrue(store.cursors.isEmpty())
    }

    @Test
    fun `aucun chapitre renvoye - erreur affichee, oeuvre reinterrogee meme si la periode est couverte`() = runTest {
        val store = Store(listOf(tracked("a", ch(1.0)))).apply { cursors[src] = UpdateCursor(1_000, emptySet()) }
        val source = FakeSource(src, RecentUpdates(emptyMap(), coversCursor = false))
        UpdateChecker(listOf(source), store, store, store).check()
        assertEquals(CheckErrors.NO_CHAPTER, store.works.getValue(key("a")).checkError)
        // Période couverte ensuite : l'œuvre en erreur est quand même réinterrogée.
        source.recent = RecentUpdates(emptyMap(), coversCursor = true)
        UpdateChecker(listOf(source), store, store, store).check()
        assertEquals(listOf(key("a"), key("a")), source.perWorkCalls)
    }

    @Test
    fun `oeuvre de nouveau verifiee - erreur effacee, sans ecriture inutile ensuite`() = runTest {
        val store = Store(listOf(tracked("a", ch(1.0), error = "ancienne erreur")))
            .apply { cursors[src] = UpdateCursor(1_000, emptySet()) }
        val source = FakeSource(src, RecentUpdates(emptyMap(), coversCursor = true), perWork = mapOf(key("a") to ch(1.0)))
        UpdateChecker(listOf(source), store, store, store).check()
        assertEquals(null, store.works.getValue(key("a")).checkError)
        assertEquals(1, store.errorWrites)
        UpdateChecker(listOf(source), store, store, store).check()
        assertEquals(1, store.errorWrites)
    }

    @Test
    fun `echec global d'une source - erreur de source memorisee puis effacee au check reussi`() = runTest {
        val store = Store(listOf(tracked("a", ch(1.0))))
        val failing = FakeSource(src, RecentUpdates(emptyMap(), false), failRecent = true)
        UpdateChecker(listOf(failing), store, store, store).check()
        assertTrue(store.sourceErrors.getValue(src).startsWith("Problème réseau"))
        val ok = FakeSource(src, RecentUpdates(emptyMap(), false), perWork = mapOf(key("a") to ch(1.0)))
        UpdateChecker(listOf(ok), store, store, store).check()
        assertTrue(store.sourceErrors.isEmpty())
    }

    // --- Même œuvre sur plusieurs sources ---

    private val src2 = SourceId("fake2")
    private fun grouped(source: SourceId, k: String, known: ChapterInfo?) =
        TrackedWork(WorkKey(source, k), k, "https://x/$k", known, groupId = "g1")

    @Test
    fun `groupe - premiere source qui publie notifie, la seconde non`() = runTest {
        val store = Store(listOf(grouped(src, "a", ch(282.0)), grouped(src2, "b", ch(282.0))))
        val s1 = FakeSource(src, RecentUpdates(emptyMap(), false), perWork = mapOf(key("a") to ch(283.0)))
        val s2 = FakeSource(src2, RecentUpdates(emptyMap(), false), perWork = mapOf(WorkKey(src2, "b") to ch(283.0)))
        val first = UpdateChecker(listOf(s1, s2), store, store, store).check()
        assertEquals(listOf(key("a")), first.newChapters.map { it.work.key })
        // Chaque source garde son propre dernier chapitre.
        assertEquals(283.0, store.works.getValue(WorkKey(src2, "b")).lastKnownChapter!!.number!!, 0.0)
    }

    @Test
    fun `groupe - la source en retard rattrape sans notifier, puis la suivante notifie`() = runTest {
        val store = Store(listOf(grouped(src, "a", ch(283.0)), grouped(src2, "b", ch(282.0))))
        val s1 = FakeSource(src, RecentUpdates(emptyMap(), false), perWork = mapOf(key("a") to ch(283.0)))
        val s2 = FakeSource(src2, RecentUpdates(emptyMap(), false), perWork = mapOf(WorkKey(src2, "b") to ch(283.0)))
        assertTrue(UpdateChecker(listOf(s1, s2), store, store, store).check().newChapters.isEmpty())
        val s2next = FakeSource(src2, RecentUpdates(emptyMap(), false), perWork = mapOf(WorkKey(src2, "b") to ch(284.0)))
        val report = UpdateChecker(listOf(s1, s2next), store, store, store).check()
        assertEquals(listOf(WorkKey(src2, "b")), report.newChapters.map { it.work.key })
    }

    @Test
    fun `groupe - chapitre sans numero toujours notifie`() = runTest {
        val store = Store(listOf(grouped(src, "a", ch(10.0)), grouped(src2, "b", ch(12.0))))
        val noNumber = ChapterInfo(null, "Épilogue", "https://x/epilogue")
        val s1 = FakeSource(src, RecentUpdates(emptyMap(), false), perWork = mapOf(key("a") to noNumber))
        val report = UpdateChecker(listOf(s1), store, store, store).check()
        assertEquals(1, report.newChapters.size)
    }
}
