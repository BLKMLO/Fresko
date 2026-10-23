package com.fresko.app.net

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Cookies partagés entre OkHttp et la WebView (magasin de cookies d'Android, persistant).
 * Ainsi le cookie anti-robot `cf_clearance` obtenu dans la WebView sert aux requêtes de l'app.
 */
class WebViewCookieJar : CookieJar {

    private val manager: CookieManager? by lazy { runCatching { CookieManager.getInstance() }.getOrNull() }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val m = manager ?: return
        val u = url.toString()
        cookies.forEach { m.setCookie(u, it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = runCatching { manager?.getCookie(url.toString()) }.getOrNull() ?: return emptyList()
        return header.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }

    /** Valeur actuelle d'un cookie pour [url], ou null. */
    fun value(url: String, name: String): String? =
        runCatching { manager?.getCookie(url) }.getOrNull()
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')

    fun flush() {
        runCatching { manager?.flush() }
    }

    companion object {
        const val CLEARANCE = "cf_clearance"
    }
}
