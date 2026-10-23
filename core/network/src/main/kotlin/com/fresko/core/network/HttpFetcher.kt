package com.fresko.core.network

import java.io.IOException

/**
 * Abstraction minimale d'un client HTTP pour que les sources restent
 * testables (on injecte un faux fetcher dans les tests).
 */
interface HttpFetcher {
    /** Retourne le corps de la réponse en texte. Lève [HttpException] si code != 2xx. */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String
}

open class HttpException(val code: Int, val url: String) : IOException("HTTP $code pour $url")

/**
 * Le site (derrière Cloudflare) exige une vérification anti-robot avant de répondre.
 * Elle se résout dans un navigateur (WebView) qui obtient le cookie `cf_clearance`.
 */
class CloudflareChallengeException(code: Int, url: String) : HttpException(code, url)

/** Reconnaît une page de défi Cloudflare (logique pure, testée). */
object CloudflareDetector {
    private val CHALLENGE_CODES = setOf(403, 429, 503)
    private val BODY_MARKERS = listOf(
        "challenge-platform", "cf_chl_opt", "cf-chl", "Just a moment", "Attention Required",
    )

    fun isChallenge(code: Int, cfMitigated: String?, server: String?, bodyStart: String): Boolean {
        if (code !in CHALLENGE_CODES) return false
        if (cfMitigated.equals("challenge", ignoreCase = true)) return true
        val cloudflare = server?.contains("cloudflare", ignoreCase = true) == true
        return cloudflare && BODY_MARKERS.any { bodyStart.contains(it, ignoreCase = true) }
    }
}
