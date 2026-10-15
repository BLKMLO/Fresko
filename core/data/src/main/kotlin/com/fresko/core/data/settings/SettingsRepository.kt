package com.fresko.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fresko.core.domain.CheckStateStore
import com.fresko.core.domain.EnabledSourcesProvider
import com.fresko.core.model.SourceId
import com.fresko.core.source.UpdateCursor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    val enabledSources: Set<SourceId>,
    val intervalMinutes: Int,
    val lastSuccessBySource: Map<SourceId, Long>,
)

/**
 * Réglages utilisateur + état du check, stockés dans DataStore.
 * @param allSourceIds toutes les sources connues (activées par défaut).
 */
class SettingsRepository(
    context: Context,
    private val allSourceIds: List<SourceId>,
) : EnabledSourcesProvider, CheckStateStore {

    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data.map { p -> p.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun setSourceEnabled(id: SourceId, enabled: Boolean) {
        store.edit { p ->
            val currentSet = p[ENABLED]?.toSet() ?: allSourceIds.map { it.value }.toSet()
            p[ENABLED] = if (enabled) currentSet + id.value else currentSet - id.value
        }
    }

    suspend fun setIntervalMinutes(minutes: Int) {
        store.edit { it[INTERVAL] = minutes.coerceAtLeast(MIN_INTERVAL) }
    }

    override suspend fun enabledSourceIds(): Set<SourceId> = current().enabledSources

    override suspend fun cursor(sourceId: SourceId): UpdateCursor? {
        val p = store.data.first()
        val since = p[lastSuccessKey(sourceId)] ?: return null
        return UpdateCursor(since, p[anchorsKey(sourceId)].orEmpty())
    }

    override suspend fun saveCursor(sourceId: SourceId, cursor: UpdateCursor) {
        store.edit {
            it[lastSuccessKey(sourceId)] = cursor.sinceMillis
            it[anchorsKey(sourceId)] = cursor.anchors
        }
    }

    private fun Preferences.toSettings() = Settings(
        enabledSources = (this[ENABLED] ?: allSourceIds.map { it.value }.toSet())
            .map(::SourceId).toSet(),
        intervalMinutes = this[INTERVAL] ?: DEFAULT_INTERVAL,
        lastSuccessBySource = allSourceIds.mapNotNull { id ->
            this[lastSuccessKey(id)]?.let { id to it }
        }.toMap(),
    )

    companion object {
        const val DEFAULT_INTERVAL = 10
        const val MIN_INTERVAL = 5
        private val ENABLED = stringSetPreferencesKey("enabled_sources")
        private val INTERVAL = intPreferencesKey("interval_minutes")
        private fun lastSuccessKey(id: SourceId) = longPreferencesKey("last_success_${id.value}")
        private fun anchorsKey(id: SourceId) = stringSetPreferencesKey("anchors_${id.value}")
    }
}
