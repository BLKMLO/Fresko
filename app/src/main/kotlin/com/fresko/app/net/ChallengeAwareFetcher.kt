package com.fresko.app.net

import com.fresko.core.network.CloudflareChallengeException
import com.fresko.core.network.HttpFetcher

/**
 * Enrobe le client HTTP : sur un défi Cloudflare, tente une résolution invisible puis
 * réessaie une fois ; sinon note la vérification « en attente » (l'utilisateur la fera
 * dans [com.fresko.app.challenge.ChallengeActivity]) et propage l'erreur.
 */
class ChallengeAwareFetcher(
    private val delegate: HttpFetcher,
    private val solver: ChallengeSolver,
    private val registry: ChallengeRegistry,
) : HttpFetcher {

    override suspend fun get(url: String, headers: Map<String, String>): String {
        try {
            return delegate.get(url, headers).also { registry.clear(url) }
        } catch (e: CloudflareChallengeException) {
            val root = ChallengeRegistry.rootOf(url)
            if (registry.tryStartHeadless(url) && solver.solveHeadless(root)) {
                try {
                    return delegate.get(url, headers).also { registry.clear(url) }
                } catch (_: CloudflareChallengeException) {
                    // défi interactif : il faudra l'utilisateur
                }
            }
            registry.markPending(url)
            throw e
        }
    }
}
