package com.fresko.app.net

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Résolution **invisible** d'un défi Cloudflare : une WebView hors écran charge le site ;
 * les défis non interactifs (vérification JavaScript, Turnstile « managed ») passent seuls
 * et déposent un nouveau `cf_clearance`. Si un clic est exigé, ça échoue (→ écran manuel).
 */
interface ChallengeSolver {
    /** @return true si un nouveau cookie `cf_clearance` a été obtenu pour [url]. */
    suspend fun solveHeadless(url: String): Boolean
}

class CloudflareSolver(
    context: Context,
    private val cookies: WebViewCookieJar,
) : ChallengeSolver {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    override suspend fun solveHeadless(url: String): Boolean = solveHeadless(url, 30_000)

    suspend fun solveHeadless(url: String, timeoutMs: Long): Boolean = mutex.withLock {
        val before = cookies.value(url, WebViewCookieJar.CLEARANCE)
        var webView: WebView? = null
        try {
            withContext(Dispatchers.Main) { webView = createWebView().apply { loadUrl(url) } }
            val solved = withTimeoutOrNull(timeoutMs) {
                var now: String? = before
                while (now == null || now == before) {
                    delay(500)
                    now = cookies.value(url, WebViewCookieJar.CLEARANCE)
                }
                true
            } ?: false
            if (solved) cookies.flush()
            solved
        } catch (_: Exception) {
            false // WebView indisponible (mise à jour en cours…)
        } finally {
            withContext(Dispatchers.Main) {
                webView?.run { stopLoading(); destroy() }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() = WebView(appContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString = WebUserAgent.get(appContext)
        webViewClient = WebViewClient()
    }
}
