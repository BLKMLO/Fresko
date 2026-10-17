package com.fresko.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    /** Fichier de sauvegarde automatique (URI SAF), null si non configurée. */
    val backupFileUri: String? = null,
    /** Nom lisible du dossier de sauvegarde choisi. */
    val backupFolderLabel: String? = null,
    val lastBackupAt: Long? = null,
)

/**
 * Réglages utilisateur + état du check, stockés dans DataStore.
 *
 * Depuis la v0.5.0 on mémorise les sources **désactivées** (et non plus les activées) :
 * une source ajoutée dans une nouvelle version est donc active par défaut. Avant, une
 * liste explicite d'activées laissait NatoManga (v0.3.0) désactivée chez quiconque
 * avait déjà touché un interrupteur.
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
            val disabled = SourceSelection.disabled(p[LEGACY_ENABLED], p[DISABLED])
            p[DISABLED] = if (enabled) disabled - id.value else disabled + id.value
            p.remove(LEGACY_ENABLED)
        }
    }

    /** Remplace la liste des sources désactivées (restauration d'une sauvegarde). */
    suspend fun setDisabledSources(ids: Set<String>) {
        store.edit { p ->
            p[DISABLED] = ids
            p.remove(LEGACY_ENABLED)
        }
    }

    suspend fun setBackupTarget(fileUri: String?, folderLabel: String?) {
        store.edit { p ->
            if (fileUri == null) p.remove(BACKUP_URI) else p[BACKUP_URI] = fileUri
            if (folderLabel == null) p.remove(BACKUP_LABEL) else p[BACKUP_LABEL] = folderLabel
        }
    }

    suspend fun setLastBackupAt(millis: Long) {
        store.edit { it[LAST_BACKUP] = millis }
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

    private fun Preferences.toSettings(): Settings {
        val disabled = SourceSelection.disabled(this[LEGACY_ENABLED], this[DISABLED])
        return Settings(
            enabledSources = allSourceIds.filter { it.value !in disabled }.toSet(),
            intervalMinutes = this[INTERVAL] ?: DEFAULT_INTERVAL,
            lastSuccessBySource = allSourceIds.mapNotNull { id ->
                this[lastSuccessKey(id)]?.let { id to it }
            }.toMap(),
            backupFileUri = this[BACKUP_URI],
            backupFolderLabel = this[BACKUP_LABEL],
            lastBackupAt = this[LAST_BACKUP],
        )
    }

    companion object {
        const val DEFAULT_INTERVAL = 10
        const val MIN_INTERVAL = 5
        /** Ancien format (≤ v0.4.0) : sources activées. Lu pour la reprise, puis supprimé. */
        private val LEGACY_ENABLED = stringSetPreferencesKey("enabled_sources")
        private val DISABLED = stringSetPreferencesKey("disabled_sources")
        private val BACKUP_URI = stringPreferencesKey("backup_file_uri")
        private val BACKUP_LABEL = stringPreferencesKey("backup_folder_label")
        private val LAST_BACKUP = longPreferencesKey("last_backup_at")
        private val INTERVAL = intPreferencesKey("interval_minutes")
        private fun lastSuccessKey(id: SourceId) = longPreferencesKey("last_success_${id.value}")
        private fun anchorsKey(id: SourceId) = stringSetPreferencesKey("anchors_${id.value}")
    }
}
