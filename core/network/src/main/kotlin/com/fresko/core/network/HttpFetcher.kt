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

class HttpException(val code: Int, url: String) : IOException("HTTP $code pour $url")
