package com.fresko.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fresko.app.AppContainer
import com.fresko.core.model.SourceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SourceSetting(
    val id: SourceId,
    val name: String,
    val enabled: Boolean,
    val lastSuccess: Long?,
    val trackedCount: Int = 0,
)

data class BackupUi(
    /** Dossier de sauvegarde automatique, null si non configurée. */
    val folder: String? = null,
    val lastBackupAt: Long? = null,
    val error: String? = null,
    /** Résultat de la dernière action (export, restauration…). */
    val message: String? = null,
    val busy: Boolean = false,
)

data class SettingsUiState(
    val sources: List<SourceSetting> = emptyList(),
    val intervalMinutes: Int = 10,
    val backup: BackupUi = BackupUi(),
)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {

    private val backupAction = MutableStateFlow(BackupUi())

    val state: StateFlow<SettingsUiState> = combine(
        c.settings.settings,
        c.works.observeCountsBySource(),
        c.backup.lastError,
        backupAction,
    ) { s, counts, backupError, action ->
        SettingsUiState(
            backup = action.copy(
                folder = s.backupFolderLabel.takeIf { s.backupFileUri != null },
                lastBackupAt = s.lastBackupAt,
                error = backupError,
            ),
            sources = c.sources.all.map {
                SourceSetting(it.id, it.displayName, it.id in s.enabledSources, s.lastSuccessBySource[it.id], counts[it.id] ?: 0)
            },
            intervalMinutes = s.intervalMinutes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setSourceEnabled(id: SourceId, enabled: Boolean) = viewModelScope.launch {
        c.settings.setSourceEnabled(id, enabled)
    }

    fun setInterval(minutes: Int) = viewModelScope.launch {
        c.settings.setIntervalMinutes(minutes)
        c.scheduler.schedule(minutes, replace = true)
    }

    fun checkNow() = c.scheduler.checkNow()

    fun chooseBackupFolder(treeUri: Uri) = backupAction {
        val restored = c.backup.setFolder(treeUri)
        if (restored > 0) "Sauvegarde trouvée dans ce dossier : $restored œuvre${plural(restored)} restaurée${plural(restored)}."
        else "Sauvegarde automatique activée."
    }

    fun stopAutoBackup() = backupAction {
        c.backup.clearFolder()
        "Sauvegarde automatique arrêtée (le fichier déjà écrit est conservé)."
    }

    fun exportTo(uri: Uri) = backupAction {
        val n = c.backup.exportTo(uri)
        "Copie exportée : $n œuvre${plural(n)}."
    }

    fun importFrom(uri: Uri) = backupAction {
        val n = c.backup.importFrom(uri)
        "Sauvegarde restaurée : $n œuvre${plural(n)}."
    }

    private fun backupAction(block: suspend () -> String) =
        viewModelScope.launch {
            backupAction.update { it.copy(busy = true, message = null) }
            val message = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: "Opération impossible."
            }
            backupAction.update { it.copy(busy = false, message = message) }
        }

    private fun plural(n: Int) = if (n > 1) "s" else ""

    companion object {
        val INTERVALS = listOf(10, 15, 30, 60, 120, 360, 720)
    }
}
