package com.autoflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoflow.app.runtime.TriggerCoordinator
import com.autoflow.app.ui.AutoFlowApp
import com.autoflow.app.ui.permissions.LocalPermissionManager
import com.autoflow.app.ui.theme.AutoFlowTheme
import com.autoflow.data.storage.settings.AppSettings
import com.autoflow.data.storage.settings.SettingsRepository
import com.autoflow.platform.permissions.PermissionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var permissionManager: PermissionManager

    @Inject lateinit var coordinator: TriggerCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            AutoFlowTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                CompositionLocalProvider(LocalPermissionManager provides permissionManager) {
                    AutoFlowApp()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Starting a foreground service is always allowed while visible; recover if a
        // background start was refused earlier.
        coordinator.ensureMonitoring()
        // Re-register geofences, e.g. after location permission was granted in Settings.
        coordinator.refresh()
    }
}
