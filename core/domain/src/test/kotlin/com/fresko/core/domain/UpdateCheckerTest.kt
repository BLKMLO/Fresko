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
    private fun ch(n: Double) = ChapterInfo(n, "Chapitre $n", "https://x/$n")

    private class FakeSource(
        override val id: SourceId,
        var recent: RecentUpdates,
        val perWork: Map<WorkKey, ChapterInfo> = emptyMap(),
        val failPerWork: Boolean = false,
    ) : Source {
        override val displayName = "Fake"
        val perWorkCalls = mutableListOf<WorkKey>()
        override suspend fun fetchCatalog(onProgress: (Int) -> Unit) = emptyList<WorkInfo>()
        override suspend fun fetchLatestChapter(key: WorkKey): ChapterInfo? {
            perWorkCalls += key
            if (failPerWork) throw IOException("réseau")
            return perWork[key]
        }
        override suspend fun fetchRecentUpdates(cursor: UpdateCursor?) = recent
    }

    private class Store(initial: List<TrackedWork>) : TrackedWorksStore, CheckStateStore, EnabledSourcesProvider {
        val works = initial.associateBy { it.key }.toMutableMap()
        val cursors = mutableMapOf<SourceId, UpdateCursor>()
        var enabledIds: Set<SourceId> = initial.map { it.key.sourceId }.toSet()
        override suspend fun trackedWorks(sourceId: SourceId) = works.values.filter { it.key.sourceId == sourceId }
        override suspend fun saveLatestChapter(key: WorkKey, chapter: ChapterInfo, checkedAtMillis: Long) {
            works[key] = works.getValue(key).copy(lastKnownChapter = chapter)
        }
        override suspend fun cursor(sourceId: SourceId) = cursors[sourceId]
        override suspend fun saveCursor(sourceId: SourceId, cursor: UpdateCursor) { cursors[sourceId] = cursor }
        override suspend fun enabledSourceIds() = enabledIds
    }

    private fun tracked(k: String, known: ChapterInfo?) = TrackedWork(key(k), k, "https://x/$k", known)

    @Test
    fun `nouveau chapitre detecte depuis les mises a jour recentes`() = runTest {
        val store = Store(listOf(tracked("a", ch(10.0)))).apply { cursors[src] = UpdateCursor(1_000, emptySet()) }
        val source = FakeSource(src, RecentUpdates(mapOf(key("a") to ch(11.0)), coversCursor = true, anchors = setOf("x")))
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
        val source = FakeSource(src, RecentUpdates(mapOf(key("a") to ch(11.0)), true))
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
            src, RecentUpdates(mapOf(key("a") to ch(2.0)), false), failPerWork = true,
        )
        val report = UpdateChecker(listOf(source), store, store, store).check()
        assertEquals(1, report.newChapters.size)
        assertTrue(report.errors.containsKey(src))
        assertTrue(store.cursors.isEmpty())
    }

    @Test
    fun `source desactivee ignoree`() = runTest {
        val store = Store(listOf(tracked("a", ch(1.0)))).apply { enabledIds = emptySet() }
        val source = FakeSource(src, RecentUpdates(mapOf(key("a") to ch(2.0)), false))
        val report = UpdateChecker(listOf(source), store, store, store).check()
        assertTrue(report.newChapters.isEmpty())
    }
}
