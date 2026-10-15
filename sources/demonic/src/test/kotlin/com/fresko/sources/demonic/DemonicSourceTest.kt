package com.fresko.sources.demonic

import com.fresko.core.model.WorkKey
import com.fresko.core.network.HttpFetcher
import com.fresko.core.source.UpdateCursor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures = extraits de pages réelles capturées le 2026-10-15. */
class DemonicSourceTest {

    private fun resource(name: String) =
        javaClass.classLoader!!.getResource(name)!!.readText()

    private class FakeHttp(val routes: (String) -> String) : HttpFetcher {
        val calls = mutableListOf<String>()
        override suspend fun get(url: String, headers: Map<String, String>): String {
            calls += url
            return routes(url)
        }
    }

    private val parser = DemonicParser("https://demonicscans.org")

    @Test
    fun `liste des mises a jour`() {
        val works = parser.parseUpdatesList(resource("lastupdates.html"))
        assertEquals(3, works.size)
        val first = works.first()
        assertEquals(WorkKey(DemonicSource.ID, "The-Demon-King-Overrun-by-Heroes"), first.key)
        assertEquals("The Demon King Overrun by Heroes", first.title)
        assertEquals("https://demonicscans.org/manga/The-Demon-King-Overrun-by-Heroes", first.url)
        val ch = first.latestChapter!!
        assertEquals(52.0, ch.number!!, 0.0)
        assertEquals("Chapitre 52", ch.label)
        assertEquals("https://demonicscans.org/chaptered.php?manga=14174&chapter=52", ch.url)
        assertEquals(DemonicParser.parseDate("2026-10-15"), ch.publishedAtMillis)
    }

    @Test
    fun `cle conservee telle que fournie par le site (encodage inclus)`() {
        val works = parser.parseUpdatesList(resource("lastupdates.html"))
        assertTrue(works.any { it.key.key == "Devely%2527s-Healing-Time" })
    }

    @Test
    fun `recherche`() {
        val results = parser.parseSearch(resource("search.html"))
        assertEquals(1, results.size)
        assertEquals("Solo Max-Level Newbie", results[0].title)
        assertEquals("Solo-Max%25252DLevel-Newbie", results[0].key.key)
        assertNull(results[0].latestChapter)
    }

    @Test
    fun `dernier chapitre depuis la page de l'oeuvre`() {
        val ch = parser.parseWorkPageLatest(resource("work_page.html"))!!
        assertEquals(158.0, ch.number!!, 0.0)
        assertEquals("https://demonicscans.org/chaptered.php?manga=11751&chapter=158", ch.url)
    }

    @Test
    fun `premier check - une seule page, ancres renvoyees`() = runTest {
        val http = FakeHttp { resource("lastupdates.html") }
        val updates = DemonicSource(http).fetchRecentUpdates(cursor = null)
        assertEquals(1, http.calls.size)
        assertFalse(updates.coversCursor)
        assertTrue("https://demonicscans.org/chaptered.php?manga=14174&chapter=52" in updates.anchors)
    }

    @Test
    fun `ancre retrouvee - couvert des la premiere page`() = runTest {
        val http = FakeHttp { resource("lastupdates.html") }
        val cursor = UpdateCursor(
            sinceMillis = 0L, // trop ancien pour la règle des dates : seule l'ancre joue
            anchors = setOf("https://demonicscans.org/chaptered.php?manga=14174&chapter=52"),
        )
        val updates = DemonicSource(http).fetchRecentUpdates(cursor)
        assertTrue(updates.coversCursor)
        assertEquals(1, http.calls.size)
    }

    @Test
    fun `sans ancre ni date suffisante - pages bornees puis non couvert`() = runTest {
        val http = FakeHttp { resource("lastupdates.html") }
        val updates = DemonicSource(http).fetchRecentUpdates(UpdateCursor(0L, setOf("autre")))
        assertEquals(3, http.calls.size)
        assertFalse(updates.coversCursor)
    }

    @Test
    fun `repli sur les dates - minuit UTC + 24 h + 14 h`() {
        val day = DemonicParser.parseDate("2026-10-15")!!
        assertEquals(day + 38 * 3600 * 1000L, DemonicSource.coverageStart(day))
    }
}
