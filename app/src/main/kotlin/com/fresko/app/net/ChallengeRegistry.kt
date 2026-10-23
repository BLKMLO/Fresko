package com.fresko.app.net

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.URI

/**
 * Vérifications anti-robot en attente, par site (hôte → URL à ouvrir). Persistées pour
 * que l'écran d'accueil les signale même après un check fait en arrière-plan.
 */
class ChallengeRegistry(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("challenges", Context.MODE_PRIVATE)

    private val _pending = MutableStateFlow(load())
    val pending: StateFlow<Map<String, String>> = _pending

    fun markPending(url: String) {
        val host = hostOf(url) ?: return
        if (_pending.value[host] != null) return
        prefs.edit().putString(PENDING + host, rootOf(url)).apply()
        _pending.value = _pending.value + (host to rootOf(url))
    }

    fun clear(url: String) {
        val host = hostOf(url) ?: return
        if (_pending.value[host] == null) return
        prefs.edit().remove(PENDING + host).remove(NOTIFIED + host).apply()
        _pending.value = _pending.value - host
    }

    /** Limite les tentatives invisibles (une WebView coûte de la batterie) : 1 par 30 min et par site. */
    @Synchronized
    fun tryStartHeadless(url: String, now: Long = System.currentTimeMillis()): Boolean {
        val host = hostOf(url) ?: return false
        val last = prefs.getLong(HEADLESS + host, 0)
        if (now - last < HEADLESS_COOLDOWN) return false
        prefs.edit().putLong(HEADLESS + host, now).apply()
        return true
    }

    /** Une notification par site toutes les 6 h au plus. */
    @Synchronized
    fun shouldNotify(url: String, now: Long = System.currentTimeMillis()): Boolean {
        val host = hostOf(url) ?: return false
        val last = prefs.getLong(NOTIFIED + host, 0)
        if (now - last < NOTIFY_COOLDOWN) return false
        prefs.edit().putLong(NOTIFIED + host, now).apply()
        return true
    }

    private fun load(): Map<String, String> = prefs.all
        .filterKeys { it.startsWith(PENDING) }
        .mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix(PENDING) to it } }
        .toMap()

    companion object {
        private const val PENDING = "pending_"
        private const val HEADLESS = "headless_"
        private const val NOTIFIED = "notified_"
        private const val HEADLESS_COOLDOWN = 30 * 60 * 1000L
        private const val NOTIFY_COOLDOWN = 6 * 3600 * 1000L

        fun hostOf(url: String): String? = runCatching { URI(url).host }.getOrNull()

        /** Page d'accueil du site : c'est elle qu'on ouvre pour la vérification. */
        fun rootOf(url: String): String = runCatching {
            val u = URI(url); "${u.scheme}://${u.host}/"
        }.getOrDefault(url)
    }
}
