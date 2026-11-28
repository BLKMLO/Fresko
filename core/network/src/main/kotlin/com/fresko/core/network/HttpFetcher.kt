package com.fresko.core.network

import java.io.IOException

/**
 * Abstraction minimale d'un client HTTP pour que les sources restent
 * testables (on injecte un faux fetcher dans les tests).
 */
interface HttpFetcher {
    /** Retourne le corps de la réponse en texte. Lève [HttpException] si code != 2xx. */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String

    /**
     * Comme [get], mais indique aussi l'adresse finale après redirections : sert à
     * détecter un site qui a changé de domaine. Par défaut, pas de redirection connue.
     */
    suspend fun fetch(url: String, headers: Map<String, String> = emptyMap()): HttpResult =
        HttpResult(get(url, headers), url)
}

/** Réponse HTTP réussie : corps texte et URL finale (après redirections éventuelles). */
data class HttpResult(val body: String, val finalUrl: String)

open class HttpException(val code: Int, val url: String) : IOException("HTTP $code pour $url")

/**
 * Le site (derrière Cloudflare) exige une vérification anti-robot avant de répondre.
 * Elle se résout dans un navigateur (WebView) qui obtient le cookie `cf_clearance`.
 */
class CloudflareChallengeException(code: Int, url: String) : HttpException(code, url)

/**
 * Reconnaît une page anti-robot Cloudflare (logique pure, testée).
 *
 * Cas reconnus (v1.1.0 : les trois derniers passaient inaperçus) :
 * - code 403 / 429 / 503 + page Cloudflare avec un marqueur de défi ;
 * - en-tête `cf-mitigated: challenge`, **quel que soit le code** ;
 * - page de défi servie en **200** (marqueurs propres aux pages de défi seulement : le script
 *   `challenge-platform/…/jsd` est aussi injecté dans des pages normales) ;
 * - **HTML servi par Cloudflare là où l'app attend du JSON** ([expectsJson]) : les API du site
 *   répondent toujours en `application/json` (vérifié le 2026-11-28) ; une page à la place est
 *   une page intermédiaire (défi, blocage). Avant, l'erreur devenait « réponse inattendue ».
 */
object CloudflareDetector {
    private val CHALLENGE_CODES = setOf(403, 429, 503)
    private val BODY_MARKERS = listOf(
        "challenge-platform", "cf_chl_opt", "cf-chl", "Just a moment", "Attention Required",
    )

    /** Marqueurs présents uniquement sur les pages de défi (jamais sur une page normale du site). */
    private val STRONG_MARKERS = listOf("_cf_chl_opt", "<title>Just a moment", "cf-chl-widget", "orchestrate/chl_page")

    fun isChallenge(
        code: Int,
        cfMitigated: String?,
        server: String?,
        bodyStart: String,
        contentType: String? = null,
        expectsJson: Boolean = false,
    ): Boolean {
        if (cfMitigated.equals("challenge", ignoreCase = true)) return true
        val cloudflare = server?.contains("cloudflare", ignoreCase = true) == true
        if (!cloudflare) return false
        val html = contentType?.contains("html", ignoreCase = true) == true ||
            bodyStart.trimStart().startsWith("<")
        // HTML au lieu de JSON : réponse « réussie » ou code de blocage (un 404 HTML = route inconnue).
        if (expectsJson && html && (code in 200..299 || code in CHALLENGE_CODES)) return true
        if (code in CHALLENGE_CODES) return BODY_MARKERS.any { bodyStart.contains(it, ignoreCase = true) }
        return html && looksLikeChallengePage(bodyStart)
    }

    /** La page (HTML) est une page de défi Cloudflare. Sert aussi à la WebView de repli. */
    fun looksLikeChallengePage(html: String): Boolean =
        STRONG_MARKERS.any { html.contains(it, ignoreCase = true) }
}
