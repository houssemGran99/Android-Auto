package com.autoflow.app.ui.automations

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoflow.app.R
import com.autoflow.app.ui.components.ConfirmDialog
import com.autoflow.app.ui.components.EmptyState
import com.autoflow.app.ui.text.rememberSpecFormatter
import com.autoflow.core.model.Automation

@Composable
fun AutomationListScreen(
    onOpenAutomation: (String) -> Unit,
    onCreate: () -> Unit,
    onOpenTemplates: () -> Unit,
    viewModel: AutomationListViewModel = hiltViewModel(),
) {
    val automations by viewModel.automationList.collectAsStateWithLifecycle()
    val formatter = rememberSpecFormatter()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Automation?>(null) }
    val ranMessage = stringResource(R.string.run_started)
    val shareTitle = stringResource(R.string.share_automation)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ListEvent.Share -> {
                    val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT, event.json)
                    context.startActivity(Intent.createChooser(send, shareTitle))
                }
                is ListEvent.Ran -> snackbar.showSnackbar(String.format(ranMessage, event.name))
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_automations)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_automation)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = automations
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(
                icon = Icons.Outlined.SmartToy,
                title = stringResource(R.string.empty_automations_title),
                message = stringResource(R.string.empty_automations_message),
                modifier = Modifier.padding(padding),
                action = {
                    FilledTonalButton(onClick = onOpenTemplates) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                        Text(stringResource(R.string.browse_templates), modifier = Modifier.padding(start = 8.dp))
                    }
                },
            )
            else -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(list, key = { it.id }) { automation ->
                    AutomationRow(
                        automation = automation,
                        summary = formatter.automationSummary(automation),
                        onClick = { onOpenAutomation(automation.id) },
                        onToggle = { viewModel.setEnabled(automation.id, it) },
                        onRun = { viewModel.run(automation) },
                        onDuplicate = { viewModel.duplicate(automation.id, context.getString(R.string.copy_suffix)) },
                        onShare = { viewModel.share(automation.id) },
                        onDelete = { pendingDelete = automation },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    pendingDelete?.let { automation ->
        ConfirmDialog(
            title = stringResource(R.string.delete_automation_title),
            message = stringResource(R.string.delete_automation_message, automation.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(automation.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun AutomationRow(
    automation: Automation,
    summary: String,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(automation.name) },
        supportingContent = { Text(summary, maxLines = 2) },
        trailingContent = {
            androidx.compose.foundation.layout.Row {
                Switch(checked = automation.enabled, onCheckedChange = onToggle)
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_run_now)) },
                            leadingIcon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                            onClick = { menu = false; onRun() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_duplicate)) },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = { menu = false; onDuplicate() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                            onClick = { menu = false; onShare() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
