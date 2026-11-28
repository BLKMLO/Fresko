package com.fresko.app.net

import com.fresko.core.network.CloudflareChallengeException
import com.fresko.core.network.HttpFetcher
import com.fresko.core.network.HttpResult

/**
 * Enrobe le client HTTP. Sur un défi Cloudflare :
 * 1. résolution invisible (1 fois / 30 min / site) puis nouvel essai du client HTTP ;
 * 2. toujours bloqué : **repli par la WebView** ([pages], 1 essai / 10 min / site après un
 *    échec) — le site est chargé comme dans un navigateur ;
 * 3. sinon, vérification notée « en attente » (l'utilisateur la fera dans
 *    [com.fresko.app.challenge.ChallengeActivity], sur la page bloquée) et l'erreur remonte.
 */
class ChallengeAwareFetcher(
    private val delegate: HttpFetcher,
    private val solver: ChallengeSolver,
    private val registry: ChallengeRegistry,
    private val pages: PageFetcher? = null,
) : HttpFetcher {

    override suspend fun get(url: String, headers: Map<String, String>): String = fetch(url, headers).body

    override suspend fun fetch(url: String, headers: Map<String, String>): HttpResult {
        try {
            return delegate.fetch(url, headers).also { registry.clear(url) }
        } catch (e: CloudflareChallengeException) {
            if (registry.tryStartHeadless(url)) {
                if (solver.solveHeadless(url)) {
                    try {
                        return delegate.fetch(url, headers).also { registry.clear(url) }
                    } catch (_: CloudflareChallengeException) {
                        // cookie obtenu mais client HTTP toujours bloqué : repli WebView ci-dessous
                    }
                } else {
                    // Défi interactif : la WebView de repli échouerait de même. On n'attend pas
                    // une seconde fois (la recherche restait bloquée près d'une minute).
                    registry.webFallbackFailed(url)
                }
            }
            if (pages != null && registry.webFallbackAllowed(url)) {
                val page = pages.fetch(url)
                if (page != null) {
                    registry.clear(url)
                    return page
                }
                registry.webFallbackFailed(url)
            }
            registry.markPending(url)
            throw e
        }
    }
}
