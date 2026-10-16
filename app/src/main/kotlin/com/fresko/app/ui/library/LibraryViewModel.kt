package com.fresko.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fresko.app.AppContainer
import com.fresko.core.data.db.WorkEntity
import com.fresko.core.model.SourceId
import com.fresko.core.model.WorkInfo
import com.fresko.core.model.WorkKey
import com.fresko.core.model.isNewerThan
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

data class TrackedItem(val entity: WorkEntity, val source: SourceUi)

data class SearchResultItem(val info: WorkInfo, val tracked: Boolean)

data class SourceSearch(
    val source: SourceUi,
    val loading: Boolean,
    val failed: Boolean,
    val results: List<SearchResultItem>,
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
) {
    val isSearching: Boolean get() = query.trim().length >= MIN_QUERY
    val toRead: List<TrackedItem> get() = tracked.filter { it.entity.isUnread }
    val upToDate: List<TrackedItem> get() = tracked.filterNot { it.entity.isUnread }
}

private data class Slot(val loading: Boolean, val failed: Boolean, val results: List<WorkInfo>)

private const val MIN_QUERY = 2

@OptIn(FlowPreview::class)
class LibraryViewModel(private val c: AppContainer) : ViewModel() {

    private val query = MutableStateFlow("")
    private val slots = MutableStateFlow<Map<SourceId, Slot>>(emptyMap())

    private val sourcesUi = c.sources.all.associate {
        it.id to SourceUi(it.id, it.displayName, it.hasEarlyAccess)
    }

    val state: StateFlow<LibraryUiState> = combine(
        c.works.observeTracked(),
        c.settings.settings,
        query,
        slots,
        c.scheduler.observeManualCheck(),
    ) { works, settings, q, slotMap, checking ->
        val enabled = c.sources.all.filter { it.id in settings.enabledSources }.mapNotNull { sourcesUi[it.id] }
        val enabledIds = enabled.map { it.id }.toSet()
        val tracked = works
            .filter { SourceId(it.sourceId) in enabledIds }
            .mapNotNull { e -> sourcesUi[SourceId(e.sourceId)]?.let { TrackedItem(e, it) } }
        val trackedKeys = works.map { it.key }.toSet()
        val trimmed = q.trim()
        LibraryUiState(
            tracked = tracked,
            query = q,
            matchingTracked = if (trimmed.length >= MIN_QUERY) {
                tracked.filter { it.entity.title.contains(trimmed, ignoreCase = true) }
            } else emptyList(),
            search = if (trimmed.length >= MIN_QUERY) enabled.map { s ->
                val slot = slotMap[s.id]
                SourceSearch(
                    source = s,
                    loading = slot?.loading ?: true,
                    failed = slot?.failed ?: false,
                    results = slot?.results.orEmpty().map { SearchResultItem(it, it.key in trackedKeys) },
                )
            } else emptyList(),
            enabledSources = enabled,
            lastCheck = settings.lastSuccessBySource.filterKeys { it in enabledIds }.values.maxOrNull(),
            checking = checking,
            hiddenCount = works.count { SourceId(it.sourceId) !in enabledIds },
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
        viewModelScope.launch {
            c.works.track(info)
            // Référence fraîche pour ne pas notifier un chapitre déjà sorti ; on garde
            // le plus récent (l'endpoint dédié d'Asura omet l'accès anticipé).
            val source = c.sources[info.key.sourceId] ?: return@launch
            val fetched = runCatching { source.fetchLatestChapter(info.key) }.getOrNull() ?: return@launch
            val known = info.latestChapter
            if (known == null || fetched.isNewerThan(known)) {
                c.works.saveLatestChapter(info.key, fetched, System.currentTimeMillis())
                c.works.markRead(info.key) // la référence n'est pas une nouveauté
            }
        }
    }

    fun markRead(key: WorkKey) {
        viewModelScope.launch { c.works.markRead(key) }
    }

    fun markUnread(key: WorkKey) {
        viewModelScope.launch { c.works.markUnread(key) }
    }

    fun untrack(key: WorkKey) {
        viewModelScope.launch { c.works.untrack(key) }
    }

    fun setNotifyEarlyAccess(key: WorkKey, notify: Boolean) {
        viewModelScope.launch { c.works.setNotifyEarlyAccess(key, notify) }
    }

    private suspend fun runSearch(q: String) = coroutineScope {
        val enabled = c.settings.current().enabledSources
        val targets = c.sources.all.filter { it.id in enabled }
        slots.value = targets.associate { it.id to Slot(loading = true, failed = false, results = emptyList()) }
        for (source in targets) {
            launch {
                val slot = try {
                    Slot(loading = false, failed = false, results = source.search(q))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Slot(loading = false, failed = true, results = emptyList())
                }
                slots.update { it + (source.id to slot) }
            }
        }
    }
}
