package com.autoflow.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.autoflow.app.R
import com.autoflow.app.data.TemplateCatalog
import com.autoflow.app.ui.components.BackButton
import com.autoflow.app.ui.components.EmptyState
import com.autoflow.app.ui.components.ExecutionStatusIcon
import com.autoflow.app.ui.components.SectionHeader
import com.autoflow.app.ui.components.relativeTime
import com.autoflow.app.ui.text.SpecFormatter
import com.autoflow.app.ui.text.rememberSpecFormatter
import com.autoflow.core.engine.repository.AutomationRepository
import com.autoflow.core.engine.repository.ExecutionRepository
import com.autoflow.core.engine.repository.VariableRepository
import com.autoflow.core.model.Automation
import com.autoflow.core.model.flattenActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    automations: AutomationRepository,
    variables: VariableRepository,
    executions: ExecutionRepository,
    val templates: TemplateCatalog,
) : ViewModel() {
    val automations = automations.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val variables = variables.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history = executions.observeRecent(HISTORY_LIMIT).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private companion object {
        const val HISTORY_LIMIT = 300
    }
}

/** Searchable text of an automation: name, description and every trigger / condition / action label. */
private fun searchText(automation: Automation, f: SpecFormatter): String = buildString {
    append(automation.name).append(' ').append(automation.description).append(' ')
    automation.triggers.forEach { append(f.triggerTitle(it)).append(' ').append(f.triggerDetail(it)).append(' ') }
    automation.condition?.let { append(f.conditionSummary(it)).append(' ') }
    automation.actions.flattenActions().forEach { append(f.actionTitle(it)).append(' ').append(f.actionDetail(it)).append(' ') }
}

/** Global search across automations (incl. triggers and actions), templates, variables and history. */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenAutomation: (String) -> Unit,
    onOpenTemplate: (String) -> Unit,
    onOpenExecution: (String) -> Unit,
    onOpenVariables: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val automations by viewModel.automations.collectAsStateWithLifecycle()
    val variables by viewModel.variables.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val f = rememberSpecFormatter()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val q = query.trim()
    val matches: (String) -> Boolean = { it.contains(q, ignoreCase = true) }
    val automationHits = if (q.isEmpty()) emptyList() else automations.filter { matches(searchText(it, f)) }
    val templateHits = if (q.isEmpty()) {
        emptyList()
    } else {
        viewModel.templates.templates.filter { matches(context.getString(it.title) + " " + context.getString(it.description)) }
    }
    val variableHits = if (q.isEmpty()) emptyList() else variables.filter { matches(it.name) || (!it.secret && matches(it.value)) }
    val historyHits = if (q.isEmpty()) {
        emptyList()
    } else {
        history.filter { matches(it.automationName) || matches(f.triggerEvent(it.triggerKey)) || matches(f.status(it.status)) }.take(20)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier.focusRequester(focus),
                    )
                },
            )
        },
    ) { padding ->
        val nothing = automationHits.isEmpty() && templateHits.isEmpty() && variableHits.isEmpty() && historyHits.isEmpty()
        if (q.isNotEmpty() && nothing) {
            EmptyState(
                Icons.Outlined.SearchOff,
                stringResource(R.string.search_no_results),
                stringResource(R.string.search_no_results_hint),
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(horizontal = 16.dp)) {
            if (automationHits.isNotEmpty()) item { SectionHeader(stringResource(R.string.nav_automations)) }
            items(automationHits, key = { "a-" + it.id }) {
                ListItem(
                    headlineContent = { Text(it.name) },
                    supportingContent = { Text(f.automationSummary(it), maxLines = 2) },
                    modifier = Modifier.clickable { onOpenAutomation(it.id) },
                )
            }
            if (templateHits.isNotEmpty()) item { SectionHeader(stringResource(R.string.nav_templates)) }
            items(templateHits, key = { "t-" + it.id }) {
                ListItem(
                    headlineContent = { Text(stringResource(it.title)) },
                    supportingContent = { Text(stringResource(it.description), maxLines = 2) },
                    modifier = Modifier.clickable { onOpenTemplate(it.id) },
                )
            }
            if (variableHits.isNotEmpty()) item { SectionHeader(stringResource(R.string.nav_variables)) }
            items(variableHits, key = { "v-" + it.name }) {
                ListItem(
                    headlineContent = { Text("\$${it.name}") },
                    supportingContent = { Text(if (it.secret) "••••••" else it.value) },
                    modifier = Modifier.clickable(onClick = onOpenVariables),
                )
            }
            if (historyHits.isNotEmpty()) item { SectionHeader(stringResource(R.string.nav_history)) }
            items(historyHits, key = { "h-" + it.id }) {
                ListItem(
                    leadingContent = { ExecutionStatusIcon(it.status) },
                    headlineContent = { Text(it.automationName) },
                    supportingContent = { Text(f.triggerEvent(it.triggerKey) + " · " + relativeTime(it.startedAt)) },
                    modifier = Modifier.clickable { onOpenExecution(it.id) },
                )
            }
        }
    }
}
