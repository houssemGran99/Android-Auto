package com.autoflow.app.ui.builder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoflow.app.R
import com.autoflow.app.ui.components.BackButton
import com.autoflow.app.ui.components.ConfirmDialog
import com.autoflow.app.ui.components.SpecCard
import com.autoflow.app.ui.permissions.PermissionPanel
import com.autoflow.app.ui.text.SpecIcons
import com.autoflow.app.ui.text.rememberSpecFormatter
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.TriggerSpec
import com.autoflow.core.model.typeKey

private sealed interface Editing {
    data class NewTrigger(val spec: TriggerSpec) : Editing
    data class Trigger(val index: Int, val spec: TriggerSpec) : Editing
    data class NewAction(val ref: ActionListRef, val spec: ActionSpec) : Editing
    data class Action(val ref: ActionListRef, val index: Int, val spec: ActionSpec) : Editing
}

private sealed interface Adding {
    data object Trigger : Adding
    data class Action(val ref: ActionListRef) : Adding
}

/** Visual WHEN → IF → THEN builder. */
@Composable
fun BuilderScreen(
    onBack: () -> Unit,
    onOpenExecution: (String) -> Unit,
    viewModel: BuilderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val f = rememberSpecFormatter()
    val snackbar = remember { SnackbarHostState() }
    var adding by remember { mutableStateOf<Adding?>(null) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val testDone = stringResource(R.string.test_run_done)
    val viewLog = stringResource(R.string.view_log)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                BuilderEvent.Saved, BuilderEvent.Deleted -> onBack()
                is BuilderEvent.TestFinished -> {
                    val result = snackbar.showSnackbar(testDone, actionLabel = event.executionId?.let { viewLog })
                    if (result == SnackbarResult.ActionPerformed) event.executionId?.let(onOpenExecution)
                }
            }
        }
    }

    val requestBack = { if (state.hasChanges && state.loaded) confirmDiscard = true else onBack() }
    BackHandler(onBack = requestBack)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = requestBack) { Icon(Icons.Outlined.Close, stringResource(R.string.action_close)) }
                },
                title = { Text(stringResource(if (state.isNew) R.string.new_automation else R.string.edit_automation)) },
                actions = {
                    if (state.testing) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp))
                    } else {
                        IconButton(onClick = viewModel::testRun) {
                            Icon(Icons.Outlined.PlayArrow, stringResource(R.string.test_run))
                        }
                    }
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete))
                        }
                    }
                    TextButton(onClick = viewModel::save) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val draft = state.draft
        CompositionLocalProvider(LocalAppsSource provides AppsSource(apps, viewModel::requestApps)) {
            Column(
                Modifier
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextInput(
                    label = stringResource(R.string.field_name),
                    value = draft.name,
                    onValueChange = viewModel::setName,
                    isError = state.error == BuilderError.NAME_REQUIRED,
                    supporting = if (state.error == BuilderError.NAME_REQUIRED) stringResource(R.string.error_name_required) else null,
                )
                TextInput(
                    label = stringResource(R.string.field_description),
                    value = draft.description,
                    onValueChange = viewModel::setDescription,
                )

                PermissionPanel(draft.requiredCapabilities)

                // WHEN
                StepHeader(stringResource(R.string.builder_when))
                if (draft.triggers.isEmpty()) {
                    Hint(stringResource(R.string.builder_no_triggers))
                }
                draft.triggers.forEachIndexed { index, trigger ->
                    if (index > 0) Connector(stringResource(R.string.logic_or))
                    SpecCard(
                        icon = SpecIcons.trigger(trigger.typeKey),
                        title = f.triggerTitle(trigger),
                        detail = f.triggerDetail(trigger),
                        onClick = { editing = Editing.Trigger(index, trigger) },
                        trailing = {
                            IconButton(onClick = { viewModel.removeTrigger(index) }) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.action_remove))
                            }
                        },
                    )
                }
                TextButton(onClick = { adding = Adding.Trigger }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(stringResource(R.string.add_trigger), modifier = Modifier.padding(start = 4.dp))
                }

                // IF
                StepHeader(stringResource(R.string.builder_if))
                ConditionTreeEditor(root = state.condition, onChange = viewModel::setCondition)

                // THEN
                StepHeader(stringResource(R.string.builder_then))
                if (state.error == BuilderError.ACTION_REQUIRED) {
                    Text(stringResource(R.string.error_action_required), color = MaterialTheme.colorScheme.error)
                }
                ActionListEditor(
                    actions = draft.actions,
                    ref = ActionListRef(),
                    callbacks = ActionListCallbacks(
                        onAdd = { adding = Adding.Action(it) },
                        onEdit = { ref, index, spec -> editing = Editing.Action(ref, index, spec) },
                        onRemove = viewModel::removeAction,
                        onMove = viewModel::moveAction,
                    ),
                    f = f,
                )

                StepHeader(stringResource(R.string.builder_options))
                LabeledSwitch(stringResource(R.string.option_enabled), draft.enabled, viewModel::setEnabled)
                LabeledSwitch(stringResource(R.string.option_quick_action), draft.quickAction, viewModel::setQuickAction)
                LabeledSwitch(stringResource(R.string.option_stop_on_error), draft.stopOnError, viewModel::setStopOnError)

                Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Text(stringResource(R.string.action_save))
                }
            }

            AddDialogs(adding, onDismiss = { adding = null }) { picked ->
                adding = null
                editing = picked
            }
            EditDialogs(
                editing = editing,
                onDismiss = { editing = null },
                viewModel = viewModel,
            )
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            message = stringResource(R.string.discard_message),
            confirmLabel = stringResource(R.string.action_discard),
            onConfirm = onBack,
            onDismiss = { confirmDiscard = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_automation_title),
            message = stringResource(R.string.delete_automation_message, state.draft.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = viewModel::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun AddDialogs(adding: Adding?, onDismiss: () -> Unit, onPicked: (Editing) -> Unit) {
    val f = rememberSpecFormatter()
    when (adding) {
        Adding.Trigger -> AddItemDialog(
            title = stringResource(R.string.add_trigger),
            items = BuilderCatalog.triggers,
            itemTitle = { f.triggerTypeTitle(it.typeKey) },
            itemIcon = { SpecIcons.trigger(it.typeKey) },
            onPick = { onPicked(Editing.NewTrigger(it.create())) },
            onDismiss = onDismiss,
        )
        is Adding.Action -> AddItemDialog(
            title = stringResource(R.string.add_action),
            items = BuilderCatalog.actions,
            itemTitle = { it.title?.let(f::res) ?: f.actionTypeTitle(it.typeKey) },
            itemIcon = { SpecIcons.action(it.typeKey) },
            onPick = { onPicked(Editing.NewAction(adding.ref, it.create())) },
            onDismiss = onDismiss,
        )
        null -> Unit
    }
}

@Composable
private fun EditDialogs(editing: Editing?, onDismiss: () -> Unit, viewModel: BuilderViewModel) {
    val f = rememberSpecFormatter()
    when (editing) {
        is Editing.NewTrigger -> if (needsTriggerForm(editing.spec)) {
            SpecEditorDialog(f.triggerTitle(editing.spec), editing.spec, ::isValidTrigger, {
                viewModel.addTrigger(it)
                onDismiss()
            }, onDismiss) { value, change -> TriggerForm(value, change) }
        } else {
            LaunchedEffect(editing) {
                viewModel.addTrigger(editing.spec)
                onDismiss()
            }
        }
        is Editing.Trigger -> SpecEditorDialog(f.triggerTitle(editing.spec), editing.spec, ::isValidTrigger, {
            viewModel.updateTrigger(editing.index, it)
            onDismiss()
        }, onDismiss) { value, change -> TriggerForm(value, change) }
        is Editing.NewAction -> SpecEditorDialog(f.actionTitle(editing.spec), editing.spec, ::isValidAction, {
            viewModel.addAction(editing.ref, it)
            onDismiss()
        }, onDismiss) { value, change -> ActionForm(value, change) }
        is Editing.Action -> SpecEditorDialog(f.actionTitle(editing.spec), editing.spec, ::isValidAction, {
            viewModel.replaceAction(editing.ref, editing.index, it)
            onDismiss()
        }, onDismiss) { value, change -> ActionForm(value, change) }
        null -> Unit
    }
}

private fun needsTriggerForm(spec: TriggerSpec) =
    spec != TriggerSpec.ChargerConnected && spec != TriggerSpec.ChargerDisconnected

@Composable
private fun StepHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
private fun Connector(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp),
    )
}
