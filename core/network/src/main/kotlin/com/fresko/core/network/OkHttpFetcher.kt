package com.fresko.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Implémentation OkHttp de [HttpFetcher]. Une seule instance partagée par l'app. */
class OkHttpFetcher(
    private val client: OkHttpClient = defaultClient(),
    private val userAgent: String = DEFAULT_USER_AGENT,
) : HttpFetcher {

    override suspend fun get(url: String, headers: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw HttpException(response.code, url)
                response.body?.string().orEmpty()
            }
        }

    companion object {
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
