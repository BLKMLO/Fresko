package com.fresko.core.model

/** Identifiant stable d'une source (ex. "asura", "demonic"). */
@JvmInline
value class SourceId(val value: String)

/**
 * Référence unique d'une œuvre : la source + la clé propre à cette source
 * (slug, chemin…). La clé ne doit jamais changer pour une même œuvre.
 */
data class WorkKey(val sourceId: SourceId, val key: String)

/** Œuvre telle que remontée par une source (catalogue ou recherche). */
data class WorkInfo(
    val key: WorkKey,
    val title: String,
    val url: String,
    val coverUrl: String?,
    /** Dernier chapitre connu si la source le fournit gratuitement avec la liste. */
    val latestChapter: ChapterInfo? = null,
)

/** Chapitre publié. */
data class ChapterInfo(
    /** Numéro du chapitre (peut être décimal, ex. 12.5). Null si non déterminable. */
    val number: Double?,
    val label: String,
    val url: String,
    /** Date de publication en epoch millis UTC, si connue. */
    val publishedAtMillis: Long? = null,
    /** Chapitre en accès anticipé / payant (ex. Asura premium). */
    val isEarlyAccess: Boolean = false,
)

/** Œuvre suivie par l'utilisateur, avec le dernier chapitre déjà notifié. */
data class TrackedWork(
    val key: WorkKey,
    val title: String,
    val url: String,
    val lastKnownChapter: ChapterInfo?,
)

/** Nouveau chapitre détecté pour une œuvre suivie. */
data class NewChapter(
    val work: TrackedWork,
    val chapter: ChapterInfo,
)

/** Compare deux chapitres : true si [candidate] est plus récent que [known]. */
fun ChapterInfo.isNewerThan(known: ChapterInfo?): Boolean {
    if (known == null) return false // pas de référence : on initialise, on ne notifie pas
    val a = number
    val b = known.number
    if (a != null && b != null) return a > b
    return url != known.url
}
