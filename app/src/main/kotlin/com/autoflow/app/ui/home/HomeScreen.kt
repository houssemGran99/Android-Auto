package com.autoflow.app.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoflow.app.R
import com.autoflow.app.ui.components.ExecutionStatusIcon
import com.autoflow.app.ui.components.SectionHeader
import com.autoflow.app.ui.components.relativeTime
import com.autoflow.app.ui.text.rememberSpecFormatter
import java.time.LocalTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onOpenAutomation: (String) -> Unit,
    onOpenExecution: (String) -> Unit,
    onCreate: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formatter = rememberSpecFormatter()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(greeting())) },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.search))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_automation)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        ) {
            item {
                MasterSwitchCard(
                    enabled = state.masterEnabled,
                    // map is inline, so the composable familyName may be called here; joinToString's lambda is not.
                    monitoring = state.monitoring.sortedBy { it.ordinal }.map { familyName(it) }.joinToString(),
                    onToggle = viewModel::setMasterEnabled,
                )
            }

            item { SectionHeader(stringResource(R.string.home_active_automations)) }
            if (state.active.isEmpty() && !state.loading) {
                item {
                    Text(
                        stringResource(R.string.home_no_active),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.active, key = { "active-" + it.id }) { automation ->
                ListItem(
                    headlineContent = { Text(automation.name) },
                    supportingContent = { Text(formatter.automationSummary(automation), maxLines = 1) },
                    trailingContent = {
                        Switch(checked = automation.enabled, onCheckedChange = { viewModel.setEnabled(automation.id, it) })
                    },
                    modifier = Modifier.clickable { onOpenAutomation(automation.id) },
                )
            }

            item { SectionHeader(stringResource(R.string.home_quick_actions)) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.quickActions.forEach { automation ->
                        AssistChip(
                            onClick = { viewModel.run(automation.id) },
                            label = { Text(automation.name) },
                            leadingIcon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                        )
                    }
                    AssistChip(
                        onClick = {
                            val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                Settings.Panel.ACTION_WIFI
                            } else {
                                Settings.ACTION_WIFI_SETTINGS
                            }
                            safeStart(context, Intent(action))
                        },
                        label = { Text(stringResource(R.string.quick_wifi)) },
                        leadingIcon = { Icon(Icons.Outlined.Wifi, contentDescription = null) },
                    )
                    AssistChip(
                        onClick = { safeStart(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                        label = { Text(stringResource(R.string.quick_bluetooth)) },
                        leadingIcon = { Icon(Icons.Outlined.Bluetooth, contentDescription = null) },
                    )
                }
                if (state.quickActions.isEmpty()) {
                    Text(
                        stringResource(R.string.home_quick_actions_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SectionHeader(stringResource(R.string.home_recent_activity)) {
                    TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.see_all)) }
                }
            }
            if (state.recent.isEmpty() && !state.loading) {
                item {
                    Text(
                        stringResource(R.string.home_no_activity),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.recent, key = { "recent-" + it.id }) { record ->
                ListItem(
                    leadingContent = { ExecutionStatusIcon(record.status) },
                    headlineContent = { Text(stringResource(R.string.executed_format, record.automationName, formatter.status(record.status))) },
                    supportingContent = { Text(relativeTime(record.startedAt)) },
                    modifier = Modifier.clickable { onOpenExecution(record.id) },
                )
            }
        }
    }
}

@Composable
private fun MasterSwitchCard(enabled: Boolean, monitoring: String, onToggle: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (enabled) R.string.master_on else R.string.master_off),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    when {
                        !enabled -> stringResource(R.string.master_off_detail)
                        monitoring.isEmpty() -> stringResource(R.string.master_on_idle)
                        else -> stringResource(R.string.master_on_detail, monitoring)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun familyName(family: com.autoflow.core.model.TriggerFamily): String = stringResource(
    when (family) {
        com.autoflow.core.model.TriggerFamily.TIME -> R.string.family_time
        com.autoflow.core.model.TriggerFamily.WIFI -> R.string.family_wifi
        com.autoflow.core.model.TriggerFamily.BLUETOOTH -> R.string.family_bluetooth
        com.autoflow.core.model.TriggerFamily.BATTERY -> R.string.family_battery
        com.autoflow.core.model.TriggerFamily.POWER -> R.string.family_power
        com.autoflow.core.model.TriggerFamily.APP -> R.string.family_app
        com.autoflow.core.model.TriggerFamily.HEADPHONES -> R.string.family_headphones
    },
)

private fun greeting(): Int = when (LocalTime.now().hour) {
    in 5..11 -> R.string.greeting_morning
    in 12..17 -> R.string.greeting_afternoon
    else -> R.string.greeting_evening
}

private fun safeStart(context: android.content.Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Settings screen not available on this device.
    }
}
