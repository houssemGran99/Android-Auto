package com.autoflow.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.autoflow.app.R
import com.autoflow.app.ui.Routes
import com.autoflow.app.ui.components.BackButton
import com.autoflow.app.ui.components.ConfirmDialog
import com.autoflow.app.ui.components.EmptyState
import com.autoflow.app.ui.components.ExecutionStatusIcon
import com.autoflow.app.ui.components.StepStatusIcon
import com.autoflow.app.ui.components.relativeTime
import com.autoflow.app.ui.text.rememberSpecFormatter
import com.autoflow.core.engine.repository.ExecutionRepository
import com.autoflow.core.model.ExecutionRecord
import com.autoflow.core.model.ExecutionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(private val executions: ExecutionRepository) : ViewModel() {
    val records: StateFlow<List<ExecutionRecord>?> = executions.observeRecent(LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun clear() {
        viewModelScope.launch { executions.clear() }
    }

    private companion object {
        const val LIMIT = 500
    }
}

@HiltViewModel
class ExecutionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    executions: ExecutionRepository,
) : ViewModel() {
    val record: StateFlow<ExecutionRecord?> = executions.observe(checkNotNull(savedStateHandle.get<String>(Routes.ARG_EXECUTION_ID)))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Audit log of every automation run, filterable by outcome. */
@Composable
fun HistoryScreen(onOpenExecution: (String) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val f = rememberSpecFormatter()
    var filter by remember { mutableStateOf<ExecutionStatus?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_history)) },
                actions = {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Outlined.DeleteSweep, stringResource(R.string.clear_history))
                    }
                },
            )
        },
    ) { padding ->
        val list = records ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(
                Icons.Outlined.History,
                stringResource(R.string.empty_history_title),
                stringResource(R.string.empty_history_message),
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(R.string.filter_all)) })
                    listOf(ExecutionStatus.SUCCESS, ExecutionStatus.PARTIAL, ExecutionStatus.FAILED, ExecutionStatus.SKIPPED).forEach {
                        FilterChip(selected = filter == it, onClick = { filter = it }, label = { Text(f.status(it)) })
                    }
                }
            }
            items(list.filter { filter == null || it.status == filter }, key = { it.id }) { record ->
                ListItem(
                    leadingContent = { ExecutionStatusIcon(record.status) },
                    headlineContent = { Text(record.automationName) },
                    supportingContent = {
                        Text(
                            listOf(f.triggerEvent(record.triggerKey), f.status(record.status), relativeTime(record.startedAt))
                                .joinToString(" · "),
                        )
                    },
                    modifier = Modifier.clickable { onOpenExecution(record.id) },
                )
                HorizontalDivider()
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.clear_history),
            message = stringResource(R.string.clear_history_message),
            confirmLabel = stringResource(R.string.action_clear),
            onConfirm = viewModel::clear,
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
fun ExecutionDetailScreen(
    onBack: () -> Unit,
    onOpenAutomation: (String) -> Unit,
    viewModel: ExecutionDetailViewModel = hiltViewModel(),
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    val f = rememberSpecFormatter()
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.MEDIUM) }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(record?.automationName.orEmpty()) },
            )
        },
    ) { padding ->
        val current = record ?: return@Scaffold
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ExecutionStatusIcon(current.status)
                            Text(f.status(current.status), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                        Text(stringResource(R.string.detail_started, dateFormat.format(Date(current.startedAt))))
                        Text(stringResource(R.string.detail_duration, current.durationMs))
                        Text(
                            stringResource(R.string.detail_trigger, f.triggerEvent(current.triggerKey)) +
                                if (current.triggerDetail.isNotBlank()) " (${current.triggerDetail})" else "",
                        )
                        TextButton(onClick = { onOpenAutomation(current.automationId) }) {
                            Text(stringResource(R.string.open_automation))
                        }
                    }
                }
            }
            items(current.steps) { step ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                    StepStatusIcon(step.status, Modifier.size(20.dp))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(f.stepLabel(step), style = MaterialTheme.typography.bodyLarge)
                        step.failureKind?.let {
                            Text(f.failureHint(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (step.message.isNotBlank()) {
                            Text(
                                step.message,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(timeFormat.format(Date(step.timestamp)), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
