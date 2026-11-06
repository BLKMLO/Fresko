package com.fresko.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fresko.app.AppContainer
import com.fresko.core.data.db.WorkEntity
import com.fresko.core.domain.CheckErrors
import com.fresko.core.model.SourceId
import com.fresko.core.model.WorkInfo
import com.fresko.core.model.WorkKey
import com.fresko.core.model.isNewerThan
import com.fresko.app.net.ChallengeRegistry
import com.fresko.core.network.CloudflareChallengeException
import com.fresko.core.network.HttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SourceUi(val id: SourceId, val name: String, val hasEarlyAccess: Boolean)

/** Une œuvre suivie sur une source. */
data class Member(val entity: WorkEntity, val source: SourceUi)

/**
 * Une œuvre suivie, sur une ou plusieurs sources (groupe, v0.7.0). Les propriétés
 * [entity] et [source] désignent la source « en tête » : chapitre le plus avancé, et à
 * numéro égal celle qui l'a publié en premier.
 */
data class TrackedItem(val members: List<Member>) {
    constructor(entity: WorkEntity, source: SourceUi) : this(listOf(Member(entity, source)))

    val lead: Member = members.maxWith(
        compareBy<Member>(
            { it.entity.chapterNumber ?: Double.NEGATIVE_INFINITY },
            { -(it.entity.chapterPublishedAt ?: Long.MAX_VALUE) },
        )
    )
    val entity: WorkEntity get() = lead.entity
    val source: SourceUi get() = lead.source
    val key: WorkKey get() = lead.entity.key
    val isGroup: Boolean get() = members.size > 1

    /** Couverture : celle de la source en tête, sinon la première disponible. */
    val cover: Member get() = lead.takeIf { it.entity.coverUrl != null }
        ?: members.firstOrNull { it.entity.coverUrl != null } ?: lead

    fun contains(key: WorkKey?): Boolean = members.any { it.entity.key == key }

    /**
     * Un chapitre non lu est disponible. Dans un groupe, la comparaison se fait par numéro
     * (les URL diffèrent d'un site à l'autre) : chapitre le plus avancé > numéro lu le plus
     * avancé. Sans numéros partout, repli sur le statut de chaque source.
     */
    val isUnread: Boolean
        get() {
            if (!isGroup) return entity.isUnread
            val known = members.map { it.entity }.filter { it.chapterUrl != null }
            if (known.isEmpty()) return false
            if (known.all { it.chapterNumber != null }) {
                val read = members.mapNotNull { it.entity.readChapterNumber }.maxOrNull() ?: return true
                return known.maxOf { it.chapterNumber!! } > read
            }
            return known.any { it.isUnread }
        }

    /** Sources dont la dernière vérification a échoué. */
    val inError: List<Member> get() = members.filter { it.entity.checkError != null }
}

data class SourceCheckError(val source: SourceUi, val message: String, val atMillis: Long)

/**
 * @param inLinkedGroup ajout de source en cours : ce résultat fait déjà partie du groupe.
 */
data class SearchResultItem(val info: WorkInfo, val tracked: Boolean, val inLinkedGroup: Boolean = false)

data class SourceSearch(
    val source: SourceUi,
    val loading: Boolean,
    val failed: Boolean,
    val results: List<SearchResultItem>,
    /** Cause lisible de l'échec (ex. site qui bloque la requête). */
    val error: String? = null,
    /** Le site exige une vérification anti-robot : page à ouvrir pour la faire. */
    val challengeUrl: String? = null,
)

data class LibraryUiState(
    val tracked: List<TrackedItem> = emptyList(),
    val query: String = "",
    /** Œuvres suivies correspondant à la recherche en cours. */
    val matchingTracked: List<TrackedItem> = emptyList(),
    val search: List<SourceSearch> = emptyList(),
    val enabledSources: List<SourceUi> = emptyList(),
    val lastCheck: Long? = null,
    val checking: Boolean = false,
    /** Œuvres suivies sur des sources désactivées (conservées, mais masquées). */
    val hiddenCount: Int = 0,
    /** Vérifications anti-robot en attente : hôte → page à ouvrir. */
    val pendingChallenges: Map<String, String> = emptyMap(),
    /** Sources dont le dernier check a échoué entièrement (message lisible, instant). */
    val sourceErrors: List<SourceCheckError> = emptyList(),
    /** Ajout d'une source en cours : l'œuvre à compléter (la recherche sert à la trouver). */
    val linking: TrackedItem? = null,
) {
    /** Œuvres dont la dernière vérification a échoué (sur au moins une source). */
    val worksInError: Int get() = tracked.count { it.inError.isNotEmpty() }

    val isSearching: Boolean get() = query.trim().length >= MIN_QUERY
    val toRead: List<TrackedItem> get() = tracked.filter { it.isUnread }
    val upToDate: List<TrackedItem> get() = tracked.filterNot { it.isUnread }
}

private data class Slot(
    val loading: Boolean,
    val failed: Boolean,
    val results: List<WorkInfo>,
    val error: String? = null,
    val challengeUrl: String? = null,
)

private const val MIN_QUERY = 2

@OptIn(FlowPreview::class)
class LibraryViewModel(private val c: AppContainer) : ViewModel() {

    private val query = MutableStateFlow("")
    private val slots = MutableStateFlow<Map<SourceId, Slot>>(emptyMap())

    private val sourcesUi = c.sources.all.associate {
        it.id to SourceUi(it.id, it.displayName, it.hasEarlyAccess)
    }

    /** Œuvre à laquelle on ajoute une source (null = recherche normale). */
    private val linkTarget = MutableStateFlow<WorkKey?>(null)

    private data class SearchInput(
        val query: String,
        val slots: Map<SourceId, Slot>,
        val challenges: Map<String, String>,
        val linkTarget: WorkKey?,
    )

    private val searchInput = combine(query, slots, c.challenges.pending, linkTarget, ::SearchInput)

    val state: StateFlow<LibraryUiState> = combine(
        c.works.observeTracked(),
        c.settings.settings,
        searchInput,
        c.scheduler.observeManualCheck(),
    ) { works, settings, input, checking ->
        val (q, slotMap, challenges, target) = input
        val enabled = c.sources.all.filter { it.id in settings.enabledSources }.mapNotNull { sourcesUi[it.id] }
        val enabledIds = enabled.map { it.id }.toSet()
        // Une carte par œuvre : les sources d'un même groupe sont réunies (ordre de la base conservé).
        val tracked = works
            .filter { SourceId(it.sourceId) in enabledIds }
            .groupBy { it.groupId ?: "solo:${it.sourceId}:${it.workKey}" }
            .values
            .map { group -> TrackedItem(group.mapNotNull { e -> sourcesUi[SourceId(e.sourceId)]?.let { Member(e, it) } }) }
        val trackedKeys = works.map { it.key }.toSet()
        val linking = target?.let { t -> tracked.firstOrNull { it.contains(t) } }
        val linkedSources = linking?.members?.map { it.source.id }.orEmpty().toSet()
        val trimmed = q.trim()
        LibraryUiState(
            tracked = tracked,
            query = q,
            matchingTracked = if (trimmed.length >= MIN_QUERY && linking == null) {
                tracked.filter { item -> item.members.any { it.entity.title.contains(trimmed, ignoreCase = true) } }
            } else emptyList(),
            search = if (trimmed.length >= MIN_QUERY) enabled.filter { it.id !in linkedSources }.map { s ->
                val slot = slotMap[s.id]
                SourceSearch(
                    source = s,
                    loading = slot?.loading ?: true,
                    failed = slot?.failed ?: false,
                    error = slot?.error,
                    challengeUrl = slot?.challengeUrl,
                    results = slot?.results.orEmpty().map {
                        SearchResultItem(it, it.key in trackedKeys, inLinkedGroup = linking?.contains(it.key) == true)
                    },
                )
            } else emptyList(),
            linking = linking,
            enabledSources = enabled,
            lastCheck = settings.lastSuccessBySource.filterKeys { it in enabledIds }.values.maxOrNull(),
            checking = checking,
            hiddenCount = works.count { SourceId(it.sourceId) !in enabledIds },
            pendingChallenges = challenges,
            sourceErrors = enabled.mapNotNull { s ->
                settings.sourceErrors[s.id]?.let { SourceCheckError(s, it.message, it.atMillis) }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        viewModelScope.launch {
            query.map { it.trim() }
                .distinctUntilChanged()
                .debounce(350)
                .collectLatest { q -> if (q.length >= MIN_QUERY) runSearch(q) else slots.value = emptyMap() }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
        if (value.trim().length >= MIN_QUERY) {
            // Affiche immédiatement l'état « chargement » pendant l'anti-rebond.
            slots.update { map -> map.mapValues { it.value.copy(loading = true) } }
        }
    }

    fun clearQuery() = onQueryChange("")

    fun checkNow() = c.scheduler.checkNow()

    fun track(info: WorkInfo) {
        viewModelScope.launch { trackNow(info, groupWith = null) }
    }

    /** Ajouter une source à une œuvre suivie : la recherche s'ouvre sur son titre. */
    fun startLinking(key: WorkKey) {
        val item = state.value.tracked.firstOrNull { it.contains(key) } ?: return
        linkTarget.value = item.key
        onQueryChange(item.entity.title)
    }

    fun cancelLinking() {
        linkTarget.value = null
        clearQuery()
    }

    /** Rattache le résultat choisi à l'œuvre en cours de complément, puis quitte ce mode. */
    fun link(info: WorkInfo) {
        val target = linkTarget.value ?: return
        viewModelScope.launch {
            if (c.works.get(info.key)?.tracked == true) {
                c.works.joinGroup(info.key, target)
            } else {
                trackNow(info, groupWith = target)
            }
        }
        cancelLinking()
    }

    private suspend fun trackNow(info: WorkInfo, groupWith: WorkKey?) {
        c.works.track(info, groupWith = groupWith)
        // Référence fraîche pour ne pas notifier un chapitre déjà sorti ; on garde
        // le plus récent (l'endpoint dédié d'Asura omet l'accès anticipé).
        val source = c.sources[info.key.sourceId] ?: return
        val now = System.currentTimeMillis()
        val fetched = try {
            source.fetchLatestChapter(info.key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Affiché sur la carte ; le check réessaiera cette œuvre jusqu'à réussite.
            c.works.setCheckError(info.key, CheckErrors.describe(e), now)
            return
        }
        if (fetched == null) {
            c.works.setCheckError(info.key, CheckErrors.NO_CHAPTER, now)
            return
        }
        val known = info.latestChapter
        if (known == null || fetched.isNewerThan(known)) {
            c.works.saveLatestChapter(info.key, fetched, now)
            // La référence n'est pas une nouveauté (sauf source en avance dans un groupe).
            c.works.markReferenceRead(info.key)
        }
    }

    fun markRead(key: WorkKey) {
        viewModelScope.launch { c.works.markRead(key) }
    }

    fun markUnread(key: WorkKey) {
        viewModelScope.launch { c.works.markUnread(key) }
    }

    /** Restauration depuis l'accueil vide (après réinstallation). */
    fun restore(uri: android.net.Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val message = try {
                val n = c.backup.importFrom(uri)
                "Sauvegarde restaurée : $n œuvre${if (n > 1) "s" else ""}."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: "Restauration impossible."
            }
            onDone(message)
        }
    }

    /** Vérification réussie : on relance la recherche en cours et un check. */
    fun onChallengeSolved() {
        val q = query.value.trim()
        if (q.length >= MIN_QUERY) viewModelScope.launch { runSearch(q) }
        c.scheduler.checkNow()
    }

    /** Ne plus suivre cette source (les autres sources du groupe restent suivies). */
    fun untrack(key: WorkKey) {
        viewModelScope.launch { c.works.untrack(key) }
    }

    /** Ne plus suivre l'œuvre sur aucune source. */
    fun untrackAll(key: WorkKey) {
        viewModelScope.launch { c.works.untrackAll(key) }
    }

    fun setNotifyEarlyAccess(key: WorkKey, notify: Boolean) {
        viewModelScope.launch { c.works.setNotifyEarlyAccess(key, notify) }
    }

    private suspend fun runSearch(q: String) = coroutineScope {
        val enabled = c.settings.current().enabledSources
        // Ajout de source : inutile d'interroger les sources déjà présentes dans le groupe.
        val excluded = linkTarget.value
            ?.let { t -> state.value.tracked.firstOrNull { it.contains(t) } }
            ?.members?.map { it.source.id }.orEmpty().toSet()
        val targets = c.sources.all.filter { it.id in enabled && it.id !in excluded }
        slots.value = targets.associate { it.id to Slot(loading = true, failed = false, results = emptyList()) }
        for (source in targets) {
            launch {
                val slot = try {
                    Slot(loading = false, failed = false, results = source.search(q))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: CloudflareChallengeException) {
                    Slot(
                        loading = false, failed = true, results = emptyList(),
                        error = "${source.displayName} demande une vérification anti-robot.",
                        challengeUrl = ChallengeRegistry.rootOf(e.url),
                    )
                } catch (e: Exception) {
                    Slot(loading = false, failed = true, results = emptyList(), error = describeError(e))
                }
                slots.update { it + (source.id to slot) }
            }
        }
    }
}

/** Message compréhensible pour un échec de requête vers un site. */
internal fun describeError(e: Throwable): String = when (e) {
    is HttpException -> when (e.code) {
        403, 429, 503 -> "Le site bloque la requête (protection anti-robot, code ${e.code})."
        else -> "Le site a répondu une erreur (code ${e.code})."
    }
    is java.net.UnknownHostException -> "Site injoignable (pas de réseau ou nom introuvable)."
    is java.net.SocketTimeoutException -> "Le site ne répond pas (délai dépassé)."
    is java.io.IOException -> "Problème réseau : ${e.message ?: e::class.simpleName}."
    else -> "Réponse inattendue du site : ${e.message ?: e::class.simpleName}."
}
