package com.fresko.sources.natomanga

import com.fresko.core.model.WorkKey
import com.fresko.core.network.HttpFetcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures = réponses réelles capturées le 2026-10-16 (recherche tronquée à 2 résultats). */
class NatoMangaSourceTest {

    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    private class FakeHttp(val routes: (String) -> String) : HttpFetcher {
        val calls = mutableListOf<String>()
        override suspend fun get(url: String, headers: Map<String, String>): String {
            calls += url
            return routes(url)
        }
    }

    @Test
    fun `alias de recherche identique a celui du site`() {
        assertEquals("solo_max_level_newbie", NatoMangaSource.searchAlias("Solo Max-Level Newbie"))
        assertEquals("the_max_level_hero_has_returned", NatoMangaSource.searchAlias("  The Max-Level Hero has Returned! "))
        assertEquals("elite_a_l_ecole", NatoMangaSource.searchAlias("Élite à l'école"))
    }

    @Test
    fun `recherche - titre, cle, couverture, dernier chapitre date`() = runTest {
        val http = FakeHttp { resource("search.html") }
        val results = NatoMangaSource(http).search("solo max level")
        assertEquals("https://www.natomanga.com/search/story/solo_max_level", http.calls.single())
        assertEquals(2, results.size)
        val solo = results[0]
        assertEquals(WorkKey(NatoMangaSource.ID, "solo-max-level-newbie"), solo.key)
        assertEquals("Solo Max-Level Newbie", solo.title)
        assertEquals("https://www.natomanga.com/manga/solo-max-level-newbie", solo.url)
        assertEquals("https://storage.waitst.com/thumb/solo-max-level-newbie.webp", solo.coverUrl)
        val ch = solo.latestChapter!!
        assertEquals(279.0, ch.number!!, 0.0)
        assertEquals("Chapitre 279", ch.label)
        assertEquals("https://www.natomanga.com/manga/solo-max-level-newbie/chapter-279", ch.url)
        assertEquals(NatoMangaSource.parseUpdated("Oct-14-2026 08:11"), ch.publishedAtMillis)
    }

    @Test
    fun `dernier chapitre via l'API`() = runTest {
        val http = FakeHttp { resource("chapters.json") }
        val ch = NatoMangaSource(http).fetchLatestChapter(WorkKey(NatoMangaSource.ID, "solo-max-level-newbie"))!!
        assertEquals("https://www.natomanga.com/api/manga/solo-max-level-newbie/chapters?limit=3", http.calls.single())
        assertEquals(279.0, ch.number!!, 0.0)
        assertEquals("https://www.natomanga.com/manga/solo-max-level-newbie/chapter-279", ch.url)
        assertEquals(java.time.Instant.parse("2026-10-14T08:11:20Z").toEpochMilli(), ch.publishedAtMillis)
        assertFalse(ch.isEarlyAccess)
    }

    @Test
    fun `heure de la recherche = UTC, coherente avec l'API`() {
        val api = java.time.Instant.parse("2026-10-14T08:11:20Z").toEpochMilli()
        val page = NatoMangaSource.parseUpdated("Oct-14-2026 08:11")!!
        assertTrue(api - page in 0 until 60_000)
    }

    @Test
    fun `pas de liste de mises a jour - verification par oeuvre`() = runTest {
        val http = FakeHttp { error("aucune requête attendue") }
        val updates = NatoMangaSource(http).fetchRecentUpdates(null)
        assertTrue(updates.recentByKey.isEmpty())
        assertFalse(updates.coversCursor)
        assertTrue(http.calls.isEmpty())
    }
}
