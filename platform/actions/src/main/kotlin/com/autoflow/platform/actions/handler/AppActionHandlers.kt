package com.autoflow.platform.actions.handler

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.autoflow.core.engine.AutomationContext
import com.autoflow.core.engine.action.ActionHandler
import com.autoflow.core.model.ActionResult
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.FailureKind
import com.autoflow.core.model.SettingsPanel
import com.autoflow.platform.actions.ActivityLauncher
import com.autoflow.platform.actions.R

class LaunchAppActionHandler(
    private val context: Context,
    private val launcher: ActivityLauncher,
) : ActionHandler<ActionSpec.LaunchApp> {
    override suspend fun execute(action: ActionSpec.LaunchApp, context: AutomationContext): ActionResult {
        val intent = this.context.packageManager.getLaunchIntentForPackage(action.packageName)
            ?: return ActionResult.Failure(FailureKind.INVALID_CONFIGURATION, "${action.packageName} is not installed")
        return launcher.launch(intent, action.appLabel.ifBlank { action.packageName }, context.automation.name)
    }
}

class OpenUrlActionHandler(private val launcher: ActivityLauncher) : ActionHandler<ActionSpec.OpenUrl> {
    override suspend fun execute(action: ActionSpec.OpenUrl, context: AutomationContext): ActionResult {
        val url = context.resolve(action.url).trim()
        val uri = Uri.parse(url)
        if (uri.scheme.isNullOrBlank()) {
            return ActionResult.Failure(FailureKind.INVALID_CONFIGURATION, "Invalid URL '$url'")
        }
        return launcher.launch(Intent(Intent.ACTION_VIEW, uri), url, context.automation.name)
    }
}

/**
 * Android does not let regular apps toggle Wi-Fi (10+), Bluetooth (13+), airplane mode or
 * battery saver. The supported alternative is opening the matching settings panel.
 */
class OpenSettingsActionHandler(
    private val context: Context,
    private val launcher: ActivityLauncher,
) : ActionHandler<ActionSpec.OpenSettings> {
    override suspend fun execute(action: ActionSpec.OpenSettings, context: AutomationContext): ActionResult {
        val (intentAction, label) = panel(action.panel)
        return launcher.launch(Intent(intentAction), this.context.getString(label), context.automation.name)
    }

    private fun panel(panel: SettingsPanel): Pair<String, Int> {
        val q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        return when (panel) {
            SettingsPanel.WIFI ->
                (if (q) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS) to R.string.settings_panel_wifi
            SettingsPanel.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS to R.string.settings_panel_bluetooth
            SettingsPanel.INTERNET ->
                (if (q) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS) to
                    R.string.settings_panel_internet
            SettingsPanel.VOLUME ->
                (if (q) Settings.Panel.ACTION_VOLUME else Settings.ACTION_SOUND_SETTINGS) to R.string.settings_panel_volume
            SettingsPanel.DISPLAY -> Settings.ACTION_DISPLAY_SETTINGS to R.string.settings_panel_display
            SettingsPanel.BATTERY_SAVER -> Settings.ACTION_BATTERY_SAVER_SETTINGS to R.string.settings_panel_battery_saver
            SettingsPanel.AIRPLANE_MODE -> Settings.ACTION_AIRPLANE_MODE_SETTINGS to R.string.settings_panel_airplane
            SettingsPanel.LOCATION -> Settings.ACTION_LOCATION_SOURCE_SETTINGS to R.string.settings_panel_location
        }
    }
}
