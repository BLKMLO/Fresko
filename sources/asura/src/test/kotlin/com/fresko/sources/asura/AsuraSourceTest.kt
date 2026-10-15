package com.fresko.sources.asura

import com.fresko.core.model.WorkKey
import com.fresko.core.network.HttpFetcher
import com.fresko.core.source.UpdateCursor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures = réponses réelles de l'API capturées le 2026-10-15 (tronquées). */
class AsuraSourceTest {

    private fun resource(name: String) =
        javaClass.classLoader!!.getResource(name)!!.readText()

    private class FakeHttp(val routes: (String) -> String) : HttpFetcher {
        val calls = mutableListOf<String>()
        override suspend fun get(url: String, headers: Map<String, String>): String {
            calls += url
            return routes(url)
        }
    }

    @Test
    fun `recent updates expose le chapitre le plus haut, premium inclus`() = runTest {
        val http = FakeHttp { resource("series_page.json") }
        val updates = AsuraSource(http).fetchRecentUpdates(cursor = null)

        val soloChapters = updates.recentByKey.getValue(WorkKey(AsuraSource.ID, "solo-max-level-newbie"))
        assertEquals(listOf(281.0, 280.0, 279.0), soloChapters.map { it.number })
        assertEquals(listOf(true, false, false), soloChapters.map { it.isEarlyAccess })
        val solo = soloChapters.first()
        assertEquals(281.0, solo.number!!, 0.0)
        assertTrue(solo.isEarlyAccess)
        assertEquals(
            "https://asurascans.com/comics/solo-max-level-newbie-1bb35e56/chapter/281",
            solo.url,
        )
        assertEquals(3, updates.recentByKey.size)
        assertEquals(1, http.calls.size) // sans date de référence : une seule page
    }

    @Test
    fun `couvert si la derniere serie de la page est anterieure au curseur`() = runTest {
        val http = FakeHttp { resource("series_page.json") }
        // last_chapter_at de "raising-villains-the-right-way" (dernière de la fixture)
        val oldest = AsuraSource.parseInstant("2026-10-15T19:09:09.618433Z")!!
        val updates = AsuraSource(http).fetchRecentUpdates(UpdateCursor(oldest, emptySet()))
        assertTrue(updates.coversCursor)
        assertEquals(1, http.calls.size)
    }

    @Test
    fun `charge des pages supplementaires si la periode n'est pas couverte`() = runTest {
        val http = FakeHttp { url ->
            if (url.contains("offset=0")) resource("series_page.json") else """{"data":null,"meta":{}}"""
        }
        val updates = AsuraSource(http).fetchRecentUpdates(UpdateCursor(0L, emptySet()))
        assertEquals(2, http.calls.size)
        assertTrue(updates.coversCursor) // fin de liste atteinte : tout couvert
    }

    @Test
    fun `dernier chapitre via l'endpoint chapitres`() = runTest {
        val http = FakeHttp { resource("chapters.json") }
        val ch = AsuraSource(http).fetchLatestChapter(WorkKey(AsuraSource.ID, "solo-max-level-newbie"))!!
        assertEquals(280.0, ch.number!!, 0.0)
        assertTrue(ch.label.startsWith("Chapitre 280"))
        assertEquals("https://api.asurascans.com/api/series/solo-max-level-newbie/chapters", http.calls.single())
    }

    @Test
    fun `recherche - meme format que la liste, dernier chapitre inclus`() = runTest {
        val http = FakeHttp { resource("series_page.json") }
        val results = AsuraSource(http).search("solo max")
        assertEquals("https://api.asurascans.com/api/series?search=solo+max&limit=20&offset=0", http.calls.single())
        val solo = results.first { it.key.key == "solo-max-level-newbie" }
        assertEquals("Solo Max-Level Newbie", solo.title)
        assertEquals("https://asurascans.com/comics/solo-max-level-newbie-1bb35e56", solo.url)
        assertEquals(281.0, solo.latestChapter!!.number!!, 0.0)
    }

    @Test
    fun `format des numeros de chapitre`() {
        assertEquals("12", AsuraSource.formatNumber(12.0))
        assertEquals("12.5", AsuraSource.formatNumber(12.5))
    }
}
