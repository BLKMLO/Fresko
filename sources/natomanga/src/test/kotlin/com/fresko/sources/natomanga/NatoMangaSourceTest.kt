package com.fresko.sources.natomanga

import com.fresko.core.model.WorkKey
import com.fresko.core.network.HttpException
import com.fresko.core.network.HttpFetcher
import com.fresko.core.network.HttpResult
import com.fresko.core.source.SiteAddressStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures = réponses réelles :
 * - `search.html`, `chapters.json` : www.natomanga.com, 2026-10-16 (ancien format ; recherche
 *   tronquée à 2 résultats, liens absolus vers l'ancien domaine) ;
 * - `saiyan-search.json` (3 premiers résultats de « solo »), `saiyan-search.html` (scripts et
 *   pied de page retirés), `saiyan-chapters.json` : www.saiyanmanga.com, 2026-11-21.
 */
class NatoMangaSourceTest {

    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    private class FakeHttp(val routes: (String) -> String) : HttpFetcher {
        val calls = mutableListOf<String>()
        override suspend fun get(url: String, headers: Map<String, String>): String {
            calls += url
            return routes(url)
        }
    }

    private class MemoryStore : SiteAddressStore {
        val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun set(key: String, baseUrl: String) { values[key] = baseUrl }
    }

    private val base = NatoMangaSource.DEFAULT_BASE_URL

    @Test
    fun `alias de recherche identique a celui du site`() {
        assertEquals("solo_max_level_newbie", NatoMangaSource.searchAlias("Solo Max-Level Newbie"))
        assertEquals("the_max_level_hero_has_returned", NatoMangaSource.searchAlias("  The Max-Level Hero has Returned! "))
        assertEquals("elite_a_l_ecole", NatoMangaSource.searchAlias("Élite à l'école"))
    }

    @Test
    fun `recherche JSON du nouveau site`() = runTest {
        val http = FakeHttp { resource("saiyan-search.json") }
        val results = NatoMangaSource(http).search("solo")
        assertEquals(listOf("$base/home/search/json?searchword=solo"), http.calls)
        assertEquals(3, results.size)
        val solo = results[0]
        assertEquals(WorkKey(NatoMangaSource.ID, "solo-max-level-newbie"), solo.key)
        assertEquals("Solo Max-Level Newbie", solo.title)
        assertEquals("$base/manga/solo-max-level-newbie", solo.url)
        assertEquals("https://img-r1.2xstorage.com/thumb/solo-max-level-newbie.webp", solo.coverUrl)
        assertEquals(285.0, solo.latestChapter!!.number!!, 0.0)
        assertEquals("Chapitre 285", solo.latestChapter!!.label)
        assertEquals("$base/manga/solo-max-level-newbie/chapter-285", solo.latestChapter!!.url)
    }

    @Test
    fun `recherche - repli HTML ancien format si le JSON n'existe pas`() = runTest {
        val http = FakeHttp { url -> if ("/home/search/json" in url) throw HttpException(404, url) else resource("search.html") }
        val results = NatoMangaSource(http).search("solo max level")
        assertEquals("$base/search/story/solo_max_level", http.calls.last())
        assertEquals(2, results.size)
        val solo = results[0]
        assertEquals(WorkKey(NatoMangaSource.ID, "solo-max-level-newbie"), solo.key)
        assertEquals("Solo Max-Level Newbie", solo.title)
        assertEquals("https://storage.waitst.com/thumb/solo-max-level-newbie.webp", solo.coverUrl)
        val ch = solo.latestChapter!!
        assertEquals(279.0, ch.number!!, 0.0)
        assertEquals("Chapitre 279", ch.label)
        assertEquals(NatoMangaSource.parseUpdated("Oct-14-2026 08:11"), ch.publishedAtMillis)
    }

    @Test
    fun `recherche - repli HTML si le JSON renvoie une page HTML`() = runTest {
        val http = FakeHttp { url -> if ("/home/search/json" in url) "<html>ancien site</html>" else resource("search.html") }
        assertEquals(2, NatoMangaSource(http).search("solo").size)
    }

    @Test
    fun `recherche - nouveau format HTML (saiyanmanga)`() {
        val results = NatoMangaSource(FakeHttp { "" }).parseSearch(resource("saiyan-search.html"))
        assertEquals(1, results.size)
        val solo = results[0]
        assertEquals("solo-max-level-newbie", solo.key.key)
        assertEquals("Solo Max-Level Newbie", solo.title)
        assertEquals("https://img-r1.2xstorage.com/thumb/solo-max-level-newbie.webp", solo.coverUrl)
        assertEquals(285.0, solo.latestChapter!!.number!!, 0.0)
    }

    @Test
    fun `recherche - site en panne ou bloque, pas de repli`() = runTest {
        val http = FakeHttp { url -> throw HttpException(503, url) }
        val error = runCatching { NatoMangaSource(http).search("solo") }.exceptionOrNull()
        assertEquals(503, (error as HttpException).code)
        assertEquals(1, http.calls.size)
    }

    @Test
    fun `dernier chapitre via l'API - ancien format de date`() = runTest {
        val http = FakeHttp { resource("chapters.json") }
        val ch = NatoMangaSource(http).fetchLatestChapter(WorkKey(NatoMangaSource.ID, "solo-max-level-newbie"))!!
        assertEquals("$base/api/manga/solo-max-level-newbie/chapters?limit=3", http.calls.single())
        assertEquals(279.0, ch.number!!, 0.0)
        assertEquals("$base/manga/solo-max-level-newbie/chapter-279", ch.url)
        assertEquals(java.time.Instant.parse("2026-10-14T08:11:20Z").toEpochMilli(), ch.publishedAtMillis)
        assertFalse(ch.isEarlyAccess)
    }

    @Test
    fun `dernier chapitre via l'API - nouveau format de date`() = runTest {
        val http = FakeHttp { resource("saiyan-chapters.json") }
        val ch = NatoMangaSource(http).fetchLatestChapter(WorkKey(NatoMangaSource.ID, "solo-max-level-newbie"))!!
        assertEquals(285.0, ch.number!!, 0.0)
        assertEquals("Chapitre 285", ch.label)
        // Même chapitre sur natomanga.com : « 2026-11-14T11:47:21.000000Z ».
        assertEquals(java.time.Instant.parse("2026-11-14T11:47:21Z").toEpochMilli(), ch.publishedAtMillis)
    }

    @Test
    fun `changement de domaine suivi automatiquement`() = runTest {
        val store = MemoryStore()
        val calls = mutableListOf<String>()
        val http = object : HttpFetcher {
            override suspend fun get(url: String, headers: Map<String, String>) = error("fetch attendu")
            override suspend fun fetch(url: String, headers: Map<String, String>): HttpResult {
                calls += url
                return HttpResult(resource("saiyan-chapters.json"), url.replace("www.saiyanmanga.com", "www.nouveau.example"))
            }
        }
        val source = NatoMangaSource(http, addresses = store)
        source.fetchLatestChapter(WorkKey(NatoMangaSource.ID, "solo-max-level-newbie"))
        assertEquals("https://www.nouveau.example", source.baseUrl)
        assertEquals("www.nouveau.example", source.currentHost)
        // La requête suivante part directement vers le nouveau domaine.
        val ch = source.fetchLatestChapter(WorkKey(NatoMangaSource.ID, "solo-max-level-newbie"))!!
        assertTrue(calls[1].startsWith("https://www.nouveau.example/api/"))
        assertEquals("https://www.nouveau.example/manga/solo-max-level-newbie/chapter-285", ch.url)
    }

    @Test
    fun `redirection vers une autre page - pas un demenagement`() {
        assertNull(NatoMangaSource.movedTo("https://a.com/api/x", "https://parking.com/"))
        assertNull(NatoMangaSource.movedTo("https://a.com/api/x", "https://a.com/api/x"))
        assertNull(NatoMangaSource.movedTo("https://a.com/api/x", "http://b.com/api/x"))
        assertEquals("https://b.com", NatoMangaSource.movedTo("https://a.com/api/x?limit=3", "https://b.com/api/x?limit=3"))
    }

    @Test
    fun `adresse apprise ignoree si l'adresse par defaut change (mise a jour de l'app)`() {
        val store = MemoryStore().apply { values["natomanga|https://ancien.example"] = "https://appris.example" }
        assertEquals(base, NatoMangaSource(FakeHttp { "" }, addresses = store).baseUrl)
    }

    @Test
    fun `URL enregistrees sur un ancien domaine ouvertes sur l'actuel`() {
        val source = NatoMangaSource(FakeHttp { "" })
        assertEquals(
            "$base/manga/solo-max-level-newbie/chapter-279",
            source.openableUrl("https://www.manganami.com/manga/solo-max-level-newbie/chapter-279"),
        )
        assertEquals("$base/manga/x", source.openableUrl("https://www.natomanga.com/manga/x"))
        assertEquals("https://autre.example/page", source.openableUrl("https://autre.example/page"))
    }

    @Test
    fun `slug de chapitre decimal`() {
        assertEquals("chapter-285", NatoMangaSource.chapterSlugOf(285.0))
        assertEquals("chapter-195-1", NatoMangaSource.chapterSlugOf(195.1))
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
