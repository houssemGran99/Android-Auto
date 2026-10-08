package com.autoflow.app.ui.automations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoflow.app.data.AutomationRunner
import com.autoflow.app.data.BackupManager
import com.autoflow.core.engine.repository.AutomationRepository
import com.autoflow.core.model.Automation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ListEvent {
    data class Share(val json: String) : ListEvent
    data class Ran(val name: String) : ListEvent
}

@HiltViewModel
class AutomationListViewModel @Inject constructor(
    private val automations: AutomationRepository,
    private val backup: BackupManager,
    private val runner: AutomationRunner,
) : ViewModel() {

    val automationList: StateFlow<List<Automation>?> = automations.observeAll()
        .map<List<Automation>, List<Automation>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = Channel<ListEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { automations.setEnabled(id, enabled) }
    }

    fun delete(id: String) {
        viewModelScope.launch { automations.delete(id) }
    }

    fun duplicate(id: String, copySuffix: String) {
        viewModelScope.launch { backup.duplicate(id, copySuffix) }
    }

    fun share(id: String) {
        viewModelScope.launch { backup.exportOne(id)?.let { _events.send(ListEvent.Share(it)) } }
    }

    fun run(automation: Automation) {
        runner.runSaved(automation.id)
        viewModelScope.launch { _events.send(ListEvent.Ran(automation.name)) }
    }
}
