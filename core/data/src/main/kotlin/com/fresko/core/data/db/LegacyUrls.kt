package com.fresko.core.data.db

/**
 * Anciens domaines de sources. NatoManga : www.natomanga.com répond 301 vers
 * www.manganami.com (constaté le 2026-11-06). Utilisé par la migration Room 3→4 et à la
 * restauration d'une sauvegarde plus ancienne, pour que les URL restent comparables.
 */
object LegacyUrls {
    const val OLD_NATO = "https://www.natomanga.com/"
    const val NEW_NATO = "https://www.manganami.com/"

    fun normalize(sourceId: String, url: String?): String? =
        if (sourceId == "natomanga" && url != null && url.startsWith(OLD_NATO)) {
            NEW_NATO + url.removePrefix(OLD_NATO)
        } else url
}
