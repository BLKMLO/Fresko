package com.fresko.app.net

import android.content.Context
import android.webkit.WebSettings
import com.fresko.core.network.OkHttpFetcher

/**
 * Identité de navigateur commune à OkHttp et à la WebView : Cloudflare lie le cookie
 * `cf_clearance` au User-Agent. On part de celui de la WebView du téléphone, sans les
 * marqueurs « ; wv » et « Version/4.0 » qui la signalent comme navigateur intégré.
 */
object WebUserAgent {
    @Volatile private var cached: String? = null

    fun get(context: Context): String = cached ?: resolve(context).also { cached = it }

    private fun resolve(context: Context): String =
        runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
            ?.replace("; wv", "")
            ?.replace(Regex("""\s*Version/\d+(\.\d+)*"""), "")
            ?.takeIf { it.isNotBlank() }
            ?: OkHttpFetcher.DEFAULT_USER_AGENT
}
