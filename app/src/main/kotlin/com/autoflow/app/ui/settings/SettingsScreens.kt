package com.autoflow.app.ui.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ListItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoflow.app.BuildConfig
import com.autoflow.app.R
import com.autoflow.app.ui.builder.LabeledSwitch
import com.autoflow.app.ui.builder.SegmentedChoice
import com.autoflow.app.ui.components.BackButton
import com.autoflow.app.ui.components.ConfirmDialog
import com.autoflow.app.ui.components.SectionHeader
import com.autoflow.app.ui.permissions.CapabilityCard
import com.autoflow.app.ui.permissions.LocalPermissionManager
import com.autoflow.app.ui.permissions.rememberCapabilityGranter
import com.autoflow.app.ui.permissions.rememberResumeKey
import com.autoflow.core.model.Capability
import com.autoflow.data.storage.settings.ThemeMode
import com.autoflow.platform.permissions.CapabilityStatus

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenPermissions: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    val manager = LocalPermissionManager.current
    val resumeKey = rememberResumeKey()
    val batteryOptimized = remember(resumeKey) { !manager.isIgnoringBatteryOptimizations() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportTo)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFrom)
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                SettingsMessage.Exported -> context.getString(R.string.export_done)
                is SettingsMessage.Imported -> context.getString(R.string.import_done, message.automations, message.variables)
                is SettingsMessage.ImportFailed -> context.getString(R.string.import_failed, message.reason)
                SettingsMessage.IoFailed -> context.getString(R.string.io_failed)
                SettingsMessage.HistoryCleared -> context.getString(R.string.history_cleared)
            }
            snackbar.showSnackbar(text)
        }
    }

    Scaffold(
        topBar = { TopAppBar(navigationIcon = { BackButton(onBack) }, title = { Text(stringResource(R.string.settings)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            SectionHeader(stringResource(R.string.settings_automation))
            LabeledSwitch(stringResource(R.string.settings_master), settings.masterEnabled, viewModel::setMaster)
            Text(
                stringResource(R.string.settings_master_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LabeledSwitch(stringResource(R.string.settings_log_skipped), settings.logSkippedRuns, viewModel::setLogSkipped)
            SettingsLink(stringResource(R.string.settings_permissions), stringResource(R.string.settings_permissions_hint), onOpenPermissions)
            SettingsLink(
                stringResource(R.string.settings_battery),
                stringResource(if (batteryOptimized) R.string.settings_battery_optimized else R.string.settings_battery_unrestricted),
            ) {
                try {
                    context.startActivity(manager.batteryOptimizationSettingsIntent())
                } catch (e: ActivityNotFoundException) {
                    context.startActivity(manager.appSettingsIntent())
                }
            }

            SectionHeader(stringResource(R.string.settings_appearance))
            val themeLabels = mapOf(
                ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                ThemeMode.LIGHT to stringResource(R.string.theme_light),
                ThemeMode.DARK to stringResource(R.string.theme_dark),
            )
            SegmentedChoice(ThemeMode.entries, settings.themeMode, { themeLabels.getValue(it) }, viewModel::setTheme)
            LabeledSwitch(stringResource(R.string.settings_dynamic_color), settings.dynamicColor, viewModel::setDynamicColor)

            SectionHeader(stringResource(R.string.settings_data))
            SettingsLink(stringResource(R.string.settings_export), stringResource(R.string.settings_export_hint)) {
                exportLauncher.launch("autoflow-backup.json")
            }
            SettingsLink(stringResource(R.string.settings_import), stringResource(R.string.settings_import_hint)) {
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
            SettingsLink(stringResource(R.string.clear_history), stringResource(R.string.clear_history_message)) { confirmClear = true }

            SectionHeader(stringResource(R.string.settings_privacy))
            Text(stringResource(R.string.privacy_statement), style = MaterialTheme.typography.bodyMedium)

            SectionHeader(stringResource(R.string.settings_about))
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.about_logcat),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.clear_history),
            message = stringResource(R.string.clear_history_message),
            confirmLabel = stringResource(R.string.action_clear),
            onConfirm = viewModel::clearHistory,
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun SettingsLink(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Central overview of every capability the app can use, with its current status. */
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val manager = LocalPermissionManager.current
    val resumeKey = rememberResumeKey()
    val attempted = remember { emptyList<Capability>().toMutableStateList() }
    val grant = rememberCapabilityGranter(attempted)
    val statuses = remember(resumeKey, attempted.size) { Capability.entries.associateWith { manager.status(it) } }

    Scaffold(
        topBar = { TopAppBar(navigationIcon = { BackButton(onBack) }, title = { Text(stringResource(R.string.settings_permissions)) }) },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(stringResource(R.string.permissions_intro), style = MaterialTheme.typography.bodyMedium)
            }
            items(Capability.entries.toList(), key = { it.name }) { capability ->
                when (val status = statuses.getValue(capability)) {
                    CapabilityStatus.MISSING, CapabilityStatus.UNAVAILABLE -> CapabilityCard(
                        capability = capability,
                        status = status,
                        attempted = capability in attempted,
                        onGrant = { grant(capability) },
                    )
                    CapabilityStatus.GRANTED, CapabilityStatus.NOT_NEEDED -> ListItem(
                        leadingContent = {
                            Icon(
                                if (status == CapabilityStatus.GRANTED) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                                contentDescription = null,
                            )
                        },
                        headlineContent = { Text(stringResource(manager.info(capability).title)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (status == CapabilityStatus.GRANTED) R.string.permission_granted else R.string.permission_not_needed,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}
