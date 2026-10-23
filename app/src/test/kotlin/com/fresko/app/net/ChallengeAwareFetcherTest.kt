package com.fresko.app.net

import androidx.test.core.app.ApplicationProvider
import com.fresko.core.network.CloudflareChallengeException
import com.fresko.core.network.HttpFetcher
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

    private val url = "https://www.natomanga.com/search/story/solo"
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
        assertEquals(listOf("https://www.natomanga.com/"), solver.solved) // page d'accueil ouverte
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
        assertEquals(mapOf("www.natomanga.com" to "https://www.natomanga.com/"), registry.pending.value)
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
        assertEquals("https://www.natomanga.com/", again.pending.value["www.natomanga.com"])
    }
}
