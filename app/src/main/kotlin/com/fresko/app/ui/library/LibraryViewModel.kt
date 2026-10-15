package com.fresko.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fresko.app.AppContainer
import com.fresko.core.data.db.WorkEntity
import com.fresko.core.model.SourceId
import com.fresko.core.model.isNewerThan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkItem(
    val entity: WorkEntity,
    val sourceName: String,
)

data class SourceChip(val id: SourceId, val name: String)

data class LibraryUiState(
    val tracked: List<WorkItem> = emptyList(),
    val others: List<WorkItem> = emptyList(),
    val query: String = "",
    val sourceFilter: SourceId? = null,
    val sources: List<SourceChip> = emptyList(),
    val refreshing: Boolean = false,
    val message: String? = null,
)

private data class Transient(
    val query: String = "",
    val sourceFilter: SourceId? = null,
    val refreshing: Boolean = false,
    val message: String? = null,
)

@OptIn(FlowPreview::class)
class LibraryViewModel(private val c: AppContainer) : ViewModel() {

    private val transient = MutableStateFlow(Transient())

    val state: StateFlow<LibraryUiState> = combine(
        c.works.observeAll(),
        c.settings.settings,
        transient,
    ) { all, settings, t ->
        val enabled = settings.enabledSources
        val names = c.sources.all.associate { it.id to it.displayName }
        val q = t.query.trim()
        val visible = all.asSequence()
            .filter { SourceId(it.sourceId) in enabled }
            .filter { t.sourceFilter == null || it.sourceId == t.sourceFilter.value }
            .filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) }
            .map { WorkItem(it, names[SourceId(it.sourceId)] ?: it.sourceId) }
            .toList()
        LibraryUiState(
            tracked = visible.filter { it.entity.tracked },
            others = visible.filterNot { it.entity.tracked },
            query = t.query,
            sourceFilter = t.sourceFilter,
            sources = c.sources.all.filter { it.id in enabled }.map { SourceChip(it.id, it.displayName) },
            refreshing = t.refreshing,
            message = t.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        // Premier lancement : charge le catalogue des sources encore vides.
        viewModelScope.launch {
            val enabled = c.settings.current().enabledSources
            val empty = c.sources.all.filter { it.id in enabled && c.works.catalogSize(it.id) == 0 }
            if (empty.isNotEmpty()) refreshCatalog(empty.map { it.id })
        }
        // Recherche distante (sources au catalogue partiel), avec anti-rebond.
        viewModelScope.launch {
            transient.map { it.query.trim() }
                .distinctUntilChanged()
                .debounce(600)
                .filter { it.length >= 3 }
                .collect { remoteSearch(it) }
        }
    }

    fun onQueryChange(query: String) = transient.update { it.copy(query = query) }

    fun onSourceFilter(id: SourceId?) = transient.update { it.copy(sourceFilter = id) }

    fun dismissMessage() = transient.update { it.copy(message = null) }

    fun refreshCatalog(only: List<SourceId>? = null) {
        if (transient.value.refreshing) return
        viewModelScope.launch {
            transient.update { it.copy(refreshing = true, message = null) }
            val enabled = c.settings.current().enabledSources
            val targets = c.sources.all.filter { it.id in enabled && (only == null || it.id in only) }
            val failures = mutableListOf<String>()
            for (source in targets) {
                try {
                    val items = source.fetchCatalog { n ->
                        transient.update { it.copy(message = "${source.displayName} : $n œuvres…") }
                    }
                    c.works.mergeCatalog(items)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures += "${source.displayName} (${e.message ?: e::class.simpleName})"
                }
            }
            transient.update {
                it.copy(
                    refreshing = false,
                    message = if (failures.isEmpty()) null else "Échec : " + failures.joinToString(),
                )
            }
        }
    }

    fun toggleTracked(item: WorkItem) {
        val entity = item.entity
        val nowTracked = !entity.tracked
        viewModelScope.launch {
            c.works.setTracked(entity.key, nowTracked)
            if (nowTracked) {
                // Référence fraîche pour ne pas notifier un chapitre déjà sorti. On garde
                // le plus récent des deux : l'endpoint dédié d'Asura omet les chapitres
                // en accès anticipé que la liste, elle, contient.
                val source = c.sources[entity.key.sourceId] ?: return@launch
                val fetched = runCatching { source.fetchLatestChapter(entity.key) }.getOrNull()
                    ?: return@launch
                val known = entity.chapter
                if (known == null || fetched.isNewerThan(known)) {
                    c.works.saveLatestChapter(entity.key, fetched, System.currentTimeMillis())
                }
            }
        }
    }

    private suspend fun remoteSearch(query: String) {
        val enabled = c.settings.current().enabledSources
        for (source in c.sources.all.filter { it.id in enabled && it.supportsRemoteSearch }) {
            try {
                val results = source.search(query)
                if (results.isNotEmpty()) c.works.mergeCatalog(results)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Recherche best-effort : on garde les résultats locaux.
            }
        }
    }
}
