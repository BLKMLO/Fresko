package com.fresko.app.net

/**
 * Détecte l'apparition d'un **nouveau** cookie `cf_clearance` sur l'un des sites traversés
 * par une WebView. Un site peut rediriger vers un autre domaine (NatoManga :
 * www.natomanga.com → www.manganami.com) : le cookie est alors déposé sur le domaine
 * final, pas sur celui de l'URL ouverte. On mémorise donc la valeur initiale du cookie
 * pour chaque site vu, et on surveille chacun.
 *
 * @param clearanceOf valeur actuelle du cookie pour une URL (null si absent).
 */
class ClearanceWatch(private val clearanceOf: (String) -> String?) {

    private val initial = LinkedHashMap<String, String?>()

    /** Signale une page chargée (ouverture, redirection…). */
    @Synchronized
    fun observe(url: String?) {
        if (url == null || !url.startsWith("http")) return
        val root = ChallengeRegistry.rootOf(url)
        if (root !in initial) initial[root] = clearanceOf(root)
    }

    /** Site (racine) où un nouveau cookie est apparu, ou null. */
    @Synchronized
    fun solvedRoot(): String? = initial.entries.firstOrNull { (root, before) ->
        val now = clearanceOf(root)
        now != null && now != before
    }?.key
}
