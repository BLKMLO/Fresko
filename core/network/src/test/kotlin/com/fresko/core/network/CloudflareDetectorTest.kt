package com.fresko.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareDetectorTest {

    private val challengePage = """<!DOCTYPE html><html lang="en-US"><head><title>Just a moment...</title>
        <script>window._cf_chl_opt={cvId: '3'}</script><script src="/cdn-cgi/challenge-platform/h/b/orchestrate/chl_page/v1"></script>"""

    @Test
    fun `entete cf-mitigated suffit`() {
        assertTrue(CloudflareDetector.isChallenge(403, "challenge", "cloudflare", ""))
    }

    @Test
    fun `page Just a moment servie par Cloudflare`() {
        assertTrue(CloudflareDetector.isChallenge(403, null, "cloudflare", challengePage))
        assertTrue(CloudflareDetector.isChallenge(503, null, "cloudflare", challengePage))
    }

    @Test
    fun `simple 403 d'un autre serveur - pas un defi`() {
        assertFalse(CloudflareDetector.isChallenge(403, null, "nginx", challengePage))
        assertFalse(CloudflareDetector.isChallenge(403, null, "cloudflare", "<h1>Forbidden</h1>"))
    }

    @Test
    fun `entete cf-mitigated - quel que soit le code`() {
        assertTrue(CloudflareDetector.isChallenge(200, "challenge", "cloudflare", ""))
    }

    @Test
    fun `page de defi servie en 200`() {
        assertTrue(CloudflareDetector.isChallenge(200, null, "cloudflare", challengePage, "text/html"))
    }

    @Test
    fun `page normale avec le script jsd de Cloudflare - pas un defi`() {
        val normal = """<html><head><title>SaiyanManga</title></head><body>…
            <script src="/cdn-cgi/challenge-platform/h/b/scripts/jsd/a1b2/main.js"></script></body></html>"""
        assertFalse(CloudflareDetector.isChallenge(200, null, "cloudflare", normal, "text/html"))
    }

    @Test
    fun `HTML au lieu du JSON attendu - defi, sauf 404 (route inconnue)`() {
        val page = "<!DOCTYPE html><html><head><title>Un instant</title></head></html>"
        assertTrue(CloudflareDetector.isChallenge(200, null, "cloudflare", page, "text/html", expectsJson = true))
        assertTrue(CloudflareDetector.isChallenge(403, null, "cloudflare", page, "text/html", expectsJson = true))
        assertFalse(CloudflareDetector.isChallenge(404, null, "cloudflare", page, "text/html", expectsJson = true))
        assertFalse(CloudflareDetector.isChallenge(200, null, "cloudflare", "{\"success\":true}", "application/json", expectsJson = true))
        // Sans Cloudflare : pas un défi (autre problème du site).
        assertFalse(CloudflareDetector.isChallenge(200, null, "nginx", page, "text/html", expectsJson = true))
    }

    @Test
    fun `404 d'une page de defi sans en-tete - pas un defi`() {
        assertFalse(CloudflareDetector.isChallenge(404, null, "cloudflare", "<h1>Not found</h1>", "text/html"))
    }
}
