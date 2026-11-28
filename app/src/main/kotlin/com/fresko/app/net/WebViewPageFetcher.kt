package com.fresko.app.net

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import com.fresko.core.network.CloudflareDetector
import com.fresko.core.network.HttpResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

/** Charge une page comme le ferait un navigateur (repli quand le client HTTP est bloqué). */
interface PageFetcher {
    /** @return la page une fois le défi éventuel passé, ou null (défi interactif, délai dépassé…). */
    suspend fun fetch(url: String): HttpResult?
}

/**
 * Repli anti-robot : charge l'adresse dans une **WebView invisible** (vrai moteur Chrome, mêmes
 * cookies et même User-Agent que l'app) et lit le résultat.
 *
 * Pourquoi : Cloudflare peut continuer de bloquer le client HTTP de l'app même avec un cookie
 * `cf_clearance` valide (l'autorisation peut dépendre de l'« empreinte » du navigateur, pas
 * seulement du cookie), alors que la WebView passe. Les défis non interactifs se résolvent
 * d'eux-mêmes dans la WebView ; on attend la vraie page jusqu'à [timeoutMs].
 * Images non chargées (économie de données).
 */
class WebViewPageFetcher(
    context: Context,
    private val timeoutMs: Long = 25_000,
) : PageFetcher {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    override suspend fun fetch(url: String): HttpResult? = mutex.withLock {
        val loaded = Channel<Unit>(Channel.CONFLATED)
        var webView: WebView? = null
        try {
            withContext(Dispatchers.Main) {
                webView = createWebView { loaded.trySend(Unit) }.apply { loadUrl(url) }
            }
            withTimeoutOrNull(timeoutMs) {
                var page: PageSnapshot? = null
                while (page == null) {
                    // Fin de chargement, ou au plus 1 s (un défi résolu recharge la page).
                    withTimeoutOrNull(1_000) { loaded.receive() }
                    val raw = withContext(Dispatchers.Main) { webView?.evaluate(PageSnapshot.SCRIPT) }
                    page = PageSnapshot.parse(raw)?.takeUnless { it.isChallenge || it.isLoading }
                }
                HttpResult(page.body, page.url.ifEmpty { url })
            }
        } catch (_: Exception) {
            null // WebView indisponible (mise à jour en cours…)
        } finally {
            withContext(Dispatchers.Main) { webView?.run { stopLoading(); destroy() } }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(onLoaded: () -> Unit) = WebView(appContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.blockNetworkImage = true
        settings.userAgentString = WebUserAgent.get(appContext)
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, u: String?) = onLoaded()
        }
    }

    private suspend fun WebView.evaluate(script: String): String? = suspendCancellableCoroutine { cont ->
        evaluateJavascript(script) { cont.resume(it) }
    }
}

/**
 * État d'une page lu dans la WebView. [body] : le JSON brut pour une réponse JSON (affichée par
 * Chrome dans un `<pre>`), sinon le HTML de la page.
 */
data class PageSnapshot(
    val url: String,
    val contentType: String,
    val readyState: String,
    val title: String,
    val body: String,
) {
    val isLoading: Boolean get() = readyState == "loading"

    /** Page de défi encore affichée (le JSON attendu n'en est jamais une). */
    val isChallenge: Boolean
        get() = !contentType.contains("json", ignoreCase = true) &&
            (title.startsWith("Just a moment", ignoreCase = true) || CloudflareDetector.looksLikeChallengePage(body))

    companion object {
        /** Script exécuté dans la page : renvoie un objet JSON (sérialisé) décrivant la page. */
        const val SCRIPT = """(function(){
            var d=document, json=(d.contentType||'').indexOf('json')>=0, pre=d.querySelector('pre');
            return JSON.stringify({url:location.href, ct:d.contentType||'', rs:d.readyState||'', title:d.title||'',
              body: json ? (pre ? pre.innerText : (d.body ? d.body.innerText : '')) : d.documentElement.outerHTML});
        })()"""

        /**
         * Résultat de `evaluateJavascript` : la valeur renvoyée, encodée en JSON (ici une chaîne
         * qui contient elle-même un objet JSON). null si illisible.
         */
        fun parse(raw: String?): PageSnapshot? = runCatching {
            val inner = JSONTokener(raw ?: return null).nextValue() as? String ?: return null
            val o = JSONObject(inner)
            PageSnapshot(
                url = o.optString("url"),
                contentType = o.optString("ct"),
                readyState = o.optString("rs"),
                title = o.optString("title"),
                body = o.optString("body"),
            )
        }.getOrNull()
    }
}
