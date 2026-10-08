package com.autoflow.app.ui.permissions

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.autoflow.app.R
import com.autoflow.core.model.Capability
import com.autoflow.platform.permissions.CapabilityStatus
import com.autoflow.platform.permissions.GrantRequest
import com.autoflow.platform.permissions.PermissionManager

val LocalPermissionManager = staticCompositionLocalOf<PermissionManager> {
    error("PermissionManager not provided")
}

/** Increments every time the screen resumes, so permission statuses are re-read after visiting Settings. */
@Composable
fun rememberResumeKey(): Int {
    var key by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) key++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return key
}

/**
 * Returns a function that asks for a capability: runtime permission dialog or the matching
 * system settings screen. [attempted] records capabilities the user was already asked for.
 */
@Composable
fun rememberCapabilityGranter(attempted: SnapshotStateList<Capability>): (Capability) -> Unit {
    val manager = LocalPermissionManager.current
    var pending by remember { mutableStateOf<Capability?>(null) }
    val finish = {
        pending?.let { if (it !in attempted) attempted.add(it) }
        pending = null
    }
    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { finish() }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { finish() }
    return { capability ->
        pending = capability
        when (val request = manager.grantRequest(capability)) {
            is GrantRequest.Runtime -> {
                // A permanently denied permission returns immediately: send the user to app settings.
                if (capability in attempted) {
                    settingsLauncher.launch(manager.appSettingsIntent())
                } else {
                    runtimeLauncher.launch(request.permissions.toTypedArray())
                }
            }
            is GrantRequest.SettingsScreen -> try {
                settingsLauncher.launch(request.intent)
            } catch (e: ActivityNotFoundException) {
                settingsLauncher.launch(manager.appSettingsIntent())
            }
            null -> finish()
        }
    }
}

/**
 * Explains and requests the capabilities an automation needs. Only shows what is missing
 * (or unavailable on this Android version), with the reason and the consequence of refusing.
 */
@Composable
fun PermissionPanel(capabilities: Set<Capability>, modifier: Modifier = Modifier) {
    val manager = LocalPermissionManager.current
    val resumeKey = rememberResumeKey()
    val attempted = remember { emptyList<Capability>().toMutableStateList() }
    val grant = rememberCapabilityGranter(attempted)
    val statuses = remember(capabilities, resumeKey, attempted.size) {
        capabilities.sortedBy { it.ordinal }.associateWith { manager.status(it) }
    }
    val relevant = statuses.filterValues { it == CapabilityStatus.MISSING || it == CapabilityStatus.UNAVAILABLE }
    if (relevant.isEmpty()) return

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        relevant.forEach { (capability, status) ->
            CapabilityCard(
                capability = capability,
                status = status,
                attempted = capability in attempted,
                onGrant = { grant(capability) },
            )
        }
    }
}

@Composable
fun CapabilityCard(
    capability: Capability,
    status: CapabilityStatus,
    attempted: Boolean,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val manager = LocalPermissionManager.current
    val info = manager.info(capability)
    val missing = status == CapabilityStatus.MISSING
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (missing && !capability.optional) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (missing) Icons.Outlined.Lock else Icons.Outlined.Info, contentDescription = null)
                Text(
                    stringResource(info.title) + if (capability.optional) " · " + stringResource(R.string.permission_recommended) else "",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(stringResource(info.rationale), style = MaterialTheme.typography.bodyMedium)
            if (missing && attempted) {
                Text(
                    stringResource(info.deniedMessage),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (missing) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (attempted) {
                        TextButton(onClick = onGrant) { Text(stringResource(R.string.permission_open_settings)) }
                    } else {
                        FilledTonalButton(onClick = onGrant) { Text(stringResource(R.string.permission_grant)) }
                    }
                }
            }
        }
    }
}
