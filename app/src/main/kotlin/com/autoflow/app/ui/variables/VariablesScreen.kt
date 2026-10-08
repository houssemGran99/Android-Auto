package com.autoflow.app.ui.variables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.autoflow.app.R
import com.autoflow.app.ui.builder.LabeledSwitch
import com.autoflow.app.ui.builder.TextInput
import com.autoflow.app.ui.components.ConfirmDialog
import com.autoflow.app.ui.components.SectionHeader
import com.autoflow.core.engine.DeviceStateProvider
import com.autoflow.core.engine.repository.VariableRepository
import com.autoflow.core.engine.variable.BuiltInVariables
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.Variable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class VariablesViewModel @Inject constructor(
    private val variables: VariableRepository,
    private val deviceState: DeviceStateProvider,
) : ViewModel() {
    val userVariables: StateFlow<List<Variable>> = variables.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _builtIns = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val builtIns: StateFlow<List<Pair<String, String>>> = _builtIns.asStateFlow()

    fun refreshBuiltIns() {
        viewModelScope.launch {
            val state = deviceState.snapshot()
            val now = ZonedDateTime.now()
            val event = TriggerEvent.Manual("app")
            _builtIns.value = BuiltInVariables.NAMES.map { name ->
                name to (BuiltInVariables.resolve(name, state, now, "", event) ?: "—")
            }
        }
    }

    fun save(name: String, value: String, secret: Boolean) {
        viewModelScope.launch { variables.set(name, value, secret) }
    }

    fun delete(name: String) {
        viewModelScope.launch { variables.delete(name) }
    }
}

@Composable
fun VariablesScreen(viewModel: VariablesViewModel = hiltViewModel()) {
    val userVariables by viewModel.userVariables.collectAsStateWithLifecycle()
    val builtIns by viewModel.builtIns.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Variable?>(null) }
    var deleting by remember { mutableStateOf<Variable?>(null) }

    LaunchedEffect(Unit) { viewModel.refreshBuiltIns() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_variables)) },
                actions = {
                    IconButton(onClick = viewModel::refreshBuiltIns) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.refresh))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Variable("", "") }) {
                Icon(Icons.Outlined.Add, stringResource(R.string.add_variable))
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            item {
                SectionHeader(stringResource(R.string.variables_user))
                Text(
                    stringResource(R.string.variables_user_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (userVariables.isEmpty()) {
                item { Text(stringResource(R.string.variables_none), modifier = Modifier.padding(vertical = 8.dp)) }
            }
            items(userVariables, key = { "user-" + it.name }) { variable ->
                ListItem(
                    headlineContent = { Text("\$${variable.name}", fontFamily = FontFamily.Monospace) },
                    supportingContent = { Text(if (variable.secret) "••••••" else variable.value, maxLines = 2) },
                    leadingContent = if (variable.secret) {
                        { Icon(Icons.Outlined.Lock, stringResource(R.string.variable_secret)) }
                    } else {
                        null
                    },
                    trailingContent = {
                        IconButton(onClick = { deleting = variable }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                    },
                    modifier = Modifier.clickable { editing = variable },
                )
            }
            item {
                SectionHeader(stringResource(R.string.variables_builtin))
                Text(
                    stringResource(R.string.variables_builtin_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(builtIns, key = { "builtin-" + it.first }) { (name, value) ->
                ListItem(
                    headlineContent = { Text("%$name%", fontFamily = FontFamily.Monospace) },
                    supportingContent = { Text(value) },
                )
            }
        }
    }

    editing?.let { variable ->
        VariableDialog(
            initial = variable,
            isNew = variable.name.isEmpty(),
            existingNames = userVariables.map { it.name }.toSet(),
            onSave = { name, value, secret -> viewModel.save(name, value, secret); editing = null },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { variable ->
        ConfirmDialog(
            title = stringResource(R.string.delete_variable_title),
            message = stringResource(R.string.delete_variable_message, variable.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(variable.name) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun VariableDialog(
    initial: Variable,
    isNew: Boolean,
    existingNames: Set<String>,
    onSave: (String, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var value by remember { mutableStateOf(initial.value) }
    var secret by remember { mutableStateOf(initial.secret) }
    val nameError = when {
        name.isEmpty() -> null
        !Variable.isValidName(name) -> stringResource(R.string.error_variable_name)
        isNew && name in existingNames -> stringResource(R.string.error_variable_exists)
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.add_variable else R.string.edit_variable)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isNew) {
                    TextInput(
                        stringResource(R.string.field_name),
                        name,
                        { name = it.trim().removePrefix("$") },
                        isError = nameError != null,
                        supporting = nameError ?: stringResource(R.string.hint_variable_name),
                    )
                }
                TextInput(stringResource(R.string.field_value), value, { value = it })
                LabeledSwitch(stringResource(R.string.variable_secret), secret, { secret = it })
                Text(
                    stringResource(R.string.variable_secret_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, value, secret) }, enabled = name.isNotEmpty() && nameError == null) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
