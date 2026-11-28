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

    init {
        // Domaines abandonnés par les sources : leurs vérifications en attente ne seraient
        // jamais effacées (plus aucune requête vers eux).
        val stale = prefs.all.keys.filter { k -> OBSOLETE_HOSTS.any { k.endsWith("_$it") } }
        if (stale.isNotEmpty()) prefs.edit().apply { stale.forEach { remove(it) } }.apply()
    }

    private val _pending = MutableStateFlow(load())
    val pending: StateFlow<Map<String, String>> = _pending

    fun markPending(url: String) {
        val host = hostOf(url) ?: return
        if (_pending.value[host] != null) return
        // On garde la page qui a été bloquée (pas l'accueil) : le défi peut ne porter que sur
        // certaines adresses (API, recherche), et c'est là que le cookie doit être obtenu.
        prefs.edit().putString(PENDING + host, url).apply()
        _pending.value = _pending.value + (host to url)
    }

    fun clear(url: String) {
        val host = hostOf(url) ?: return
        if (_pending.value[host] == null) return
        prefs.edit().remove(PENDING + host).remove(NOTIFIED + host).remove(WEB_FAILED + host).apply()
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

    /**
     * Repli par la WebView (voir [ChallengeAwareFetcher]) : après un échec (défi interactif),
     * on ne réessaie pas avant 10 min, pour ne pas attendre le délai à chaque requête.
     */
    @Synchronized
    fun webFallbackAllowed(url: String, now: Long = System.currentTimeMillis()): Boolean {
        val host = hostOf(url) ?: return false
        return now - prefs.getLong(WEB_FAILED + host, 0) >= WEB_COOLDOWN
    }

    fun webFallbackFailed(url: String, now: Long = System.currentTimeMillis()) {
        val host = hostOf(url) ?: return
        prefs.edit().putLong(WEB_FAILED + host, now).apply()
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
        private const val WEB_FAILED = "webfailed_"
        private const val WEB_COOLDOWN = 10 * 60 * 1000L
        private const val HEADLESS_COOLDOWN = 30 * 60 * 1000L
        private const val NOTIFY_COOLDOWN = 6 * 3600 * 1000L
        /**
         * Anciens domaines de NatoManga : natomanga.com → manganami.com (2026-11-06) →
         * natumanga.com → saiyanmanga.com (2026-11-21).
         */
        private val OBSOLETE_HOSTS = setOf(
            "www.natomanga.com", "natomanga.com", "www.manganami.com", "www.natumanga.com",
        )

        fun hostOf(url: String): String? = runCatching { URI(url).host }.getOrNull()

        /** Page d'accueil du site : c'est elle qu'on ouvre pour la vérification. */
        fun rootOf(url: String): String = runCatching {
            val u = URI(url); "${u.scheme}://${u.host}/"
        }.getOrDefault(url)
    }
}
