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

    /**
     * Catalogue listé par l'application. Peut être partiel si le site est trop
     * gros (voir [supportsRemoteSearch]). Appelé rarement (rafraîchissement manuel).
     */
    suspend fun fetchCatalog(onProgress: (loaded: Int) -> Unit = {}): List<WorkInfo>

    /** True si [search] interroge le site (utile quand le catalogue est partiel). */
    val supportsRemoteSearch: Boolean get() = false

    suspend fun search(query: String): List<WorkInfo> = emptyList()

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
 * @param latestByKey dernier chapitre de chaque œuvre présente dans les mises à jour.
 * @param coversCursor true si toute œuvre mise à jour depuis le curseur figure
 *   forcément dans [latestByKey]. Sinon le vérificateur interroge chaque œuvre suivie.
 * @param anchors entrées vues, à mémoriser pour le prochain check.
 *
 * Principe des ancres : la liste du site est triée par date de mise à jour. Si une
 * entrée (œuvre + même chapitre) déjà vue au check précédent est encore présente,
 * elle n'a pas bougé depuis ; toute œuvre mise à jour depuis est donc au-dessus
 * d'elle, dans les pages chargées.
 */
data class RecentUpdates(
    val latestByKey: Map<WorkKey, ChapterInfo>,
    val coversCursor: Boolean,
    val anchors: Set<String> = emptySet(),
)
