package com.autoflow.app.ui.builder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoflow.app.data.AppCatalog
import com.autoflow.app.data.AutomationRunner
import com.autoflow.app.data.InstalledApp
import com.autoflow.app.data.TemplateCatalog
import com.autoflow.app.ui.Routes
import com.autoflow.core.engine.repository.AutomationRepository
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.Automation
import com.autoflow.core.model.ConditionNode
import com.autoflow.core.model.TriggerSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class BuilderError { NAME_REQUIRED, ACTION_REQUIRED }

data class BuilderUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    /** Draft being edited; its condition is always an editable group (normalized on save). */
    val draft: Automation = Automation(id = "", name = ""),
    val original: Automation? = null,
    val error: BuilderError? = null,
    val testing: Boolean = false,
) {
    val condition: ConditionNode get() = ConditionTree.asEditableRoot(draft.condition)
    val hasChanges: Boolean get() = original == null || normalized(draft) != normalized(original)

    companion object {
        fun normalized(automation: Automation) =
            automation.copy(condition = automation.condition?.let(ConditionTree::normalize), updatedAt = 0)
    }
}

sealed interface BuilderEvent {
    data object Saved : BuilderEvent
    data object Deleted : BuilderEvent
    data class TestFinished(val executionId: String?) : BuilderEvent
}

@HiltViewModel
class BuilderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val automations: AutomationRepository,
    private val templates: TemplateCatalog,
    private val appCatalog: AppCatalog,
    private val runner: AutomationRunner,
) : ViewModel() {

    private val _state = MutableStateFlow(BuilderUiState())
    val state: StateFlow<BuilderUiState> = _state.asStateFlow()

    private val _events = Channel<BuilderEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _apps = MutableStateFlow<List<InstalledApp>?>(null)
    val apps: StateFlow<List<InstalledApp>?> = _apps.asStateFlow()

    init {
        val automationId: String? = savedStateHandle[Routes.ARG_AUTOMATION_ID]
        val templateId: String? = savedStateHandle[Routes.ARG_TEMPLATE_ID]
        viewModelScope.launch {
            val existing = automationId?.let { automations.get(it) }
            _state.value = when {
                existing != null -> BuilderUiState(loaded = true, isNew = false, draft = existing, original = existing)
                templateId != null -> BuilderUiState(
                    loaded = true,
                    draft = templates.instantiate(templateId) ?: blank(),
                )
                else -> BuilderUiState(loaded = true, draft = blank())
            }
        }
    }

    private fun blank() = Automation(id = UUID.randomUUID().toString(), name = "")

    private fun edit(transform: (Automation) -> Automation) =
        _state.update { it.copy(draft = transform(it.draft), error = null) }

    fun setName(name: String) = edit { it.copy(name = name) }
    fun setDescription(description: String) = edit { it.copy(description = description) }
    fun setEnabled(enabled: Boolean) = edit { it.copy(enabled = enabled) }
    fun setStopOnError(value: Boolean) = edit { it.copy(stopOnError = value) }
    fun setQuickAction(value: Boolean) = edit { it.copy(quickAction = value) }

    // Triggers
    fun addTrigger(trigger: TriggerSpec) = edit { it.copy(triggers = it.triggers + trigger) }
    fun updateTrigger(index: Int, trigger: TriggerSpec) =
        edit { a -> a.copy(triggers = a.triggers.toMutableList().also { it[index] = trigger }) }
    fun removeTrigger(index: Int) = edit { a -> a.copy(triggers = a.triggers.toMutableList().also { it.removeAt(index) }) }

    // Conditions (tree edits are done by ConditionTreeEditor on an immutable copy)
    fun setCondition(root: ConditionNode) = edit { it.copy(condition = root) }

    // Actions
    fun addAction(ref: ActionListRef, action: ActionSpec) = edit { it.copy(actions = ActionTree.add(it.actions, ref, action)) }
    fun replaceAction(ref: ActionListRef, index: Int, action: ActionSpec) =
        edit { it.copy(actions = ActionTree.replaceKeepingChildren(it.actions, ref, index, action)) }
    fun removeAction(ref: ActionListRef, index: Int) = edit { it.copy(actions = ActionTree.remove(it.actions, ref, index)) }
    fun moveAction(ref: ActionListRef, from: Int, to: Int) = edit { it.copy(actions = ActionTree.move(it.actions, ref, from, to)) }

    fun requestApps() {
        if (_apps.value != null) return
        viewModelScope.launch { _apps.value = appCatalog.launchableApps() }
    }

    private fun validated(): Automation? {
        val draft = _state.value.draft
        val error = when {
            draft.name.isBlank() -> BuilderError.NAME_REQUIRED
            draft.actions.isEmpty() -> BuilderError.ACTION_REQUIRED
            else -> null
        }
        _state.update { it.copy(error = error) }
        return if (error == null) draft.copy(name = draft.name.trim(), condition = draft.condition?.let(ConditionTree::normalize)) else null
    }

    fun save() {
        val automation = validated() ?: return
        viewModelScope.launch {
            automations.upsert(automation)
            _events.send(BuilderEvent.Saved)
        }
    }

    fun delete() {
        val id = _state.value.draft.id
        viewModelScope.launch {
            automations.delete(id)
            _events.send(BuilderEvent.Deleted)
        }
    }

    /** Runs the current draft once (conditions ignored) so the user can check every action. */
    fun testRun() {
        val automation = validated() ?: return
        _state.update { it.copy(testing = true) }
        viewModelScope.launch {
            val record = runner.testRun(automation).await()
            _state.update { it.copy(testing = false) }
            _events.send(BuilderEvent.TestFinished(record?.id))
        }
    }
}
