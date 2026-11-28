package com.fresko.app.net

import androidx.test.core.app.ApplicationProvider
import com.fresko.core.network.CloudflareChallengeException
import com.fresko.core.network.HttpFetcher
import com.fresko.core.network.HttpResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChallengeAwareFetcherTest {

    private val url = "https://www.saiyanmanga.com/search/story/solo"
    private lateinit var registry: ChallengeRegistry

    /** Renvoie un défi tant que [blocked] est vrai. */
    private class FakeSite(var blocked: Boolean) : HttpFetcher {
        var calls = 0
        override suspend fun get(url: String, headers: Map<String, String>): String {
            calls++
            if (blocked) throw CloudflareChallengeException(403, url)
            return "ok"
        }
    }

    private class FakeSolver(val site: FakeSite, val succeeds: Boolean) : ChallengeSolver {
        val solved = mutableListOf<String>()
        override suspend fun solveHeadless(url: String): Boolean {
            solved += url
            if (succeeds) site.blocked = false
            return succeeds
        }
    }

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences("challenges", 0).edit().clear().commit()
        registry = ChallengeRegistry(ctx)
    }

    @Test
    fun `defi resolu en arriere-plan - la requete aboutit`() = runBlocking {
        val site = FakeSite(blocked = true)
        val solver = FakeSolver(site, succeeds = true)
        val body = ChallengeAwareFetcher(site, solver, registry).get(url)
        assertEquals("ok", body)
        assertEquals(listOf(url), solver.solved) // la page bloquée elle-même (pas l'accueil)
        assertTrue(registry.pending.value.isEmpty())
    }

    @Test
    fun `defi interactif - verification en attente puis erreur`() = runBlocking {
        val site = FakeSite(blocked = true)
        val fetcher = ChallengeAwareFetcher(site, FakeSolver(site, succeeds = false), registry)
        try {
            fetcher.get(url); fail("exception attendue")
        } catch (_: CloudflareChallengeException) {
        }
        assertEquals(mapOf("www.saiyanmanga.com" to url), registry.pending.value)
    }

    @Test
    fun `pas plus d'une tentative invisible par demi-heure`() = runBlocking {
        val site = FakeSite(blocked = true)
        val solver = FakeSolver(site, succeeds = false)
        val fetcher = ChallengeAwareFetcher(site, solver, registry)
        repeat(3) { runCatching { fetcher.get(url) } }
        assertEquals(1, solver.solved.size)
    }

    @Test
    fun `une requete qui passe efface la verification en attente`() = runBlocking {
        registry.markPending(url)
        val site = FakeSite(blocked = false)
        ChallengeAwareFetcher(site, FakeSolver(site, succeeds = false), registry).get(url)
        assertTrue(registry.pending.value.isEmpty())
    }

    @Test
    fun `notification limitee a une toutes les 6 heures`() {
        val t = 1_000_000_000L
        assertTrue(registry.shouldNotify(url, t))
        assertTrue(!registry.shouldNotify(url, t + 3600_000))
        assertTrue(registry.shouldNotify(url, t + 6 * 3600_000 + 1))
    }

    @Test
    fun `etat persiste entre deux demarrages`() {
        registry.markPending(url)
        val again = ChallengeRegistry(ApplicationProvider.getApplicationContext())
        assertEquals(url, again.pending.value["www.saiyanmanga.com"])
    }

    @Test
    fun `verification en attente sur l'ancien domaine NatoManga purgee au demarrage`() {
        registry.markPending("https://www.natomanga.com/search/story/solo")
        registry.markPending(url)
        val again = ChallengeRegistry(ApplicationProvider.getApplicationContext())
        assertEquals(mapOf("www.saiyanmanga.com" to url), again.pending.value)
    }

    // --- Repli WebView (v1.1.0) ---

    private class FakePages(val result: HttpResult?) : PageFetcher {
        val loaded = mutableListOf<String>()
        override suspend fun fetch(url: String): HttpResult? { loaded += url; return result }
    }

    @Test
    fun `cookie obtenu mais client toujours bloque - la WebView recupere la page`() = runBlocking {
        val site = FakeSite(blocked = true)
        val solver = object : ChallengeSolver { override suspend fun solveHeadless(url: String) = true } // site reste bloqué
        val pages = FakePages(HttpResult("[]", url))
        val result = ChallengeAwareFetcher(site, solver, registry, pages).fetch(url)
        assertEquals("[]", result.body)
        assertEquals(listOf(url), pages.loaded)
        assertTrue(registry.pending.value.isEmpty())
    }

    @Test
    fun `defi interactif - WebView en echec, pas de nouvel essai avant 10 min`() = runBlocking {
        val site = FakeSite(blocked = true)
        val pages = FakePages(null)
        registry.tryStartHeadless(url) // résolution invisible déjà tentée il y a moins de 30 min
        val fetcher = ChallengeAwareFetcher(site, FakeSolver(site, succeeds = false), registry, pages)
        repeat(3) { runCatching { fetcher.fetch(url) } }
        assertEquals(1, pages.loaded.size) // les requêtes suivantes échouent tout de suite
        assertEquals(mapOf("www.saiyanmanga.com" to url), registry.pending.value)
        assertTrue(registry.webFallbackAllowed(url, System.currentTimeMillis() + 10 * 60_000 + 1))
    }

    @Test
    fun `lecture de la page WebView - JSON brut, HTML, defi`() {
        fun raw(o: org.json.JSONObject) = org.json.JSONObject.quote(o.toString())
        val json = PageSnapshot.parse(raw(org.json.JSONObject(mapOf(
            "url" to url, "ct" to "application/json", "rs" to "complete", "title" to "", "body" to "{\"success\":true}",
        ))))!!
        assertEquals("{\"success\":true}", json.body)
        assertTrue(!json.isChallenge && !json.isLoading)
        val challenge = PageSnapshot.parse(raw(org.json.JSONObject(mapOf(
            "url" to url, "ct" to "text/html", "rs" to "complete", "title" to "Just a moment...",
            "body" to "<html><script>window._cf_chl_opt={}</script></html>",
        ))))!!
        assertTrue(challenge.isChallenge)
        assertEquals(null, PageSnapshot.parse(null))
        assertEquals(null, PageSnapshot.parse("null"))
    }

    @Test
    fun `resolution invisible en echec - pas de seconde attente par la WebView`() = runBlocking {
        val site = FakeSite(blocked = true)
        val pages = FakePages(HttpResult("x", url))
        runCatching { ChallengeAwareFetcher(site, FakeSolver(site, succeeds = false), registry, pages).fetch(url) }
        assertTrue(pages.loaded.isEmpty())
    }
}
