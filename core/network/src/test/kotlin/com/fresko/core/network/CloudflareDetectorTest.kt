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
    fun `404 ou 200 jamais un defi`() {
        assertFalse(CloudflareDetector.isChallenge(404, "challenge", "cloudflare", challengePage))
        assertFalse(CloudflareDetector.isChallenge(200, null, "cloudflare", challengePage))
    }
}
