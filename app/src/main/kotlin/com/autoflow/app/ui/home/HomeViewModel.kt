package com.autoflow.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoflow.app.data.AutomationRunner
import com.autoflow.app.runtime.TriggerCoordinator
import com.autoflow.core.engine.repository.AutomationRepository
import com.autoflow.core.engine.repository.ExecutionRepository
import com.autoflow.core.model.Automation
import com.autoflow.core.model.ExecutionRecord
import com.autoflow.core.model.TriggerFamily
import com.autoflow.data.storage.settings.SettingsRepository
import com.autoflow.platform.triggers.GeofenceScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val masterEnabled: Boolean = true,
    val automations: List<Automation> = emptyList(),
    val recent: List<ExecutionRecord> = emptyList(),
    val monitoring: Set<TriggerFamily> = emptySet(),
    val geofences: GeofenceScheduler.Status = GeofenceScheduler.Status.NOTHING_TO_REGISTER,
    val loading: Boolean = true,
) {
    val active: List<Automation> get() = automations.filter { it.enabled }
    val quickActions: List<Automation> get() = automations.filter { it.quickAction }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val automations: AutomationRepository,
    executions: ExecutionRepository,
    private val settings: SettingsRepository,
    private val runner: AutomationRunner,
    coordinator: TriggerCoordinator,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        settings.settings,
        automations.observeAll(),
        executions.observeRecent(RECENT_COUNT),
        coordinator.monitoredFamilies,
        coordinator.geofenceStatus,
    ) { settings, all, recent, monitoring, geofences ->
        HomeUiState(settings.masterEnabled, all, recent, monitoring, geofences, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    fun setMasterEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setMasterEnabled(enabled) }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { automations.setEnabled(id, enabled) }
    }

    fun run(id: String) {
        runner.runSaved(id)
    }

    private companion object {
        const val RECENT_COUNT = 5
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
