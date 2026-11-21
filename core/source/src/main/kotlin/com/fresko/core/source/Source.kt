package com.fresko.core.source

import com.fresko.core.model.ChapterInfo
import com.fresko.core.model.SourceId
import com.fresko.core.model.WorkInfo
import com.fresko.core.model.WorkKey

/**
 * Contrat qu'implémente chaque site supporté. Ajouter un site = créer un module
 * `sources/<nom>` qui implémente cette interface et l'enregistrer dans
 * `SourceRegistry` (module app). Rien d'autre à modifier.
 */
interface Source {
    val id: SourceId
    val displayName: String

    /** True si le site publie des chapitres en accès anticipé (payant / premium). */
    val hasEarlyAccess: Boolean get() = false

    /**
     * Referer à envoyer pour charger les couvertures (comme le ferait un navigateur sur le
     * site), ou null pour n'en envoyer aucun.
     */
    val imageReferer: String? get() = null

    /**
     * Adresse à ouvrir pour une URL enregistrée de cette source (chapitre, œuvre). Un site
     * qui a changé de domaine y remplace l'ancien domaine par l'actuel ; par défaut, inchangée.
     */
    fun openableUrl(url: String): String = url

    /** Domaine actuellement utilisé, s'il peut changer (affiché dans les réglages). */
    val currentHost: String? get() = null

    /** Recherche sur le site : c'est le seul moyen de trouver une œuvre à suivre. */
    suspend fun search(query: String): List<WorkInfo>

    /** Dernier chapitre d'une œuvre précise (requête dédiée, plus coûteuse). */
    suspend fun fetchLatestChapter(key: WorkKey): ChapterInfo?

    /**
     * Mises à jour récentes du site, en un minimum de requêtes. Sert au check
     * périodique : une seule page couvre généralement toutes les œuvres suivies.
     *
     * @param cursor état du dernier check réussi (null = premier check). La source
     *   peut charger quelques pages de plus pour le couvrir, dans une limite raisonnable.
     */
    suspend fun fetchRecentUpdates(cursor: UpdateCursor?): RecentUpdates
}

/**
 * Point de reprise laissé par le dernier check réussi.
 *
 * @param sinceMillis instant du dernier check réussi.
 * @param anchors identifiants opaques (URL de chapitre…) des entrées vues lors de ce check.
 */
data class UpdateCursor(
    val sinceMillis: Long,
    val anchors: Set<String>,
)

/**
 * Résultat de [Source.fetchRecentUpdates].
 *
 * @param recentByKey derniers chapitres (du plus récent au plus ancien) de chaque
 *   œuvre présente dans les mises à jour.
 * @param coversCursor true si toute œuvre mise à jour depuis le curseur figure
 *   forcément dans [recentByKey]. Sinon le vérificateur interroge chaque œuvre suivie.
 * @param anchors entrées vues, à mémoriser pour le prochain check.
 *
 * Principe des ancres : la liste du site est triée par date de mise à jour. Si une
 * entrée (œuvre + même chapitre) déjà vue au check précédent est encore présente,
 * elle n'a pas bougé depuis ; toute œuvre mise à jour depuis est donc au-dessus
 * d'elle, dans les pages chargées.
 */
data class RecentUpdates(
    val recentByKey: Map<WorkKey, List<ChapterInfo>>,
    val coversCursor: Boolean,
    val anchors: Set<String> = emptySet(),
)

/**
 * Mémorise l'adresse actuelle d'un site qui change de domaine (NatoManga en a changé trois
 * fois en un mois). Implémenté par l'app (stockage persistant) ; [NoAddressStore] en test.
 *
 * @param key identifiant de l'adresse (source + adresse par défaut de la version de l'app :
 *   une adresse apprise avec une ancienne valeur par défaut est ignorée après mise à jour).
 */
interface SiteAddressStore {
    fun get(key: String): String?
    fun set(key: String, baseUrl: String)
}

/** Aucune mémorisation (tests, sources à domaine fixe). */
object NoAddressStore : SiteAddressStore {
    override fun get(key: String): String? = null
    override fun set(key: String, baseUrl: String) = Unit
}
