package com.autoflow.app.widget

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings

/**
 * Trampoline used by widget rows: Android does not let apps toggle Wi-Fi or Bluetooth,
 * so tapping the row opens the matching system panel instead.
 */
class SettingsPanelActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = when (intent.getStringExtra(EXTRA_PANEL)) {
            PANEL_BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS
        }
        try {
            startActivity(Intent(action))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
        finish()
    }

    companion object {
        private const val EXTRA_PANEL = "panel"
        const val PANEL_WIFI = "wifi"
        const val PANEL_BLUETOOTH = "bluetooth"

        fun intent(context: Context, panel: String): Intent =
            Intent(context, SettingsPanelActivity::class.java)
                // Distinct data keeps the widget's PendingIntents for each row separate.
                .setData(Uri.parse("autoflow://panel/$panel"))
                .putExtra(EXTRA_PANEL, panel)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
