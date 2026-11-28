package com.fresko.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Implémentation OkHttp de [HttpFetcher]. Une seule instance partagée par l'app.
 * Sur Android, le client reçoit un CookieJar partagé avec la WebView et [userAgent]
 * est celui de la WebView : le cookie anti-robot `cf_clearance` n'est valable qu'avec
 * le même navigateur annoncé.
 */
class OkHttpFetcher(
    private val client: OkHttpClient = defaultClient(),
    private val userAgent: String = DEFAULT_USER_AGENT,
) : HttpFetcher {

    override suspend fun get(url: String, headers: Map<String, String>): String = fetch(url, headers).body

    override suspend fun fetch(url: String, headers: Map<String, String>): HttpResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()
            client.newCall(request).execute().use { response ->
                val server = response.header("server")
                val contentType = response.header("content-type")
                val expectsJson = headers.entries.any {
                    it.key.equals("Accept", ignoreCase = true) && it.value.contains("json", ignoreCase = true)
                }
                // Le corps n'est lu (8 Ko au plus, sans le consommer) que pour une réponse Cloudflare
                // suspecte : échec, ou HTML (une page normale ne coûte qu'une recherche de marqueurs).
                val suspect = !response.isSuccessful ||
                    (server?.contains("cloudflare", ignoreCase = true) == true &&
                        contentType?.contains("html", ignoreCase = true) == true)
                if (suspect) {
                    val bodyStart = runCatching { response.peekBody(PEEK_BYTES).string() }.getOrDefault("")
                    if (CloudflareDetector.isChallenge(
                            response.code, response.header("cf-mitigated"), server, bodyStart, contentType, expectsJson,
                        )
                    ) {
                        throw CloudflareChallengeException(response.code, url)
                    }
                }
                if (!response.isSuccessful) throw HttpException(response.code, url)
                // response.request = dernière requête de la chaîne de redirections.
                HttpResult(response.body?.string().orEmpty(), response.request.url.toString())
            }
        }

    companion object {
        private const val PEEK_BYTES = 8_192L

        /** En-tête à envoyer aux API JSON : une page HTML en retour sera reconnue comme défi. */
        val JSON_HEADERS = mapOf("Accept" to "application/json")

        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/130.0.0.0 Mobile Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
