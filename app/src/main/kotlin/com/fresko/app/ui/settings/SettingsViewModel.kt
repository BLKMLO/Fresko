package com.fresko.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fresko.app.AppContainer
import com.fresko.core.model.SourceId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SourceSetting(val id: SourceId, val name: String, val enabled: Boolean, val lastSuccess: Long?)

data class SettingsUiState(
    val sources: List<SourceSetting> = emptyList(),
    val intervalMinutes: Int = 10,
)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {

    val state: StateFlow<SettingsUiState> = c.settings.settings.map { s ->
        SettingsUiState(
            sources = c.sources.all.map {
                SourceSetting(it.id, it.displayName, it.id in s.enabledSources, s.lastSuccessBySource[it.id])
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

    companion object {
        val INTERVALS = listOf(10, 15, 30, 60, 120, 360, 720)
    }
}
