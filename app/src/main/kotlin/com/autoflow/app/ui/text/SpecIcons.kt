package com.autoflow.app.ui.text

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.PowerOff
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.ui.graphics.vector.ImageVector

object SpecIcons {
    fun trigger(typeKey: String): ImageVector = when (typeKey) {
        "TIME" -> Icons.Outlined.Schedule
        "INTERVAL" -> Icons.Outlined.Timer
        "WIFI_CONNECTED" -> Icons.Outlined.Wifi
        "WIFI_DISCONNECTED" -> Icons.Outlined.WifiOff
        "BLUETOOTH_CONNECTED", "BLUETOOTH_DISCONNECTED" -> Icons.Outlined.Bluetooth
        "BATTERY_LEVEL", "BATTERY_CHANGED" -> Icons.Outlined.BatteryAlert
        "CHARGER_CONNECTED" -> Icons.Outlined.Power
        "CHARGER_DISCONNECTED" -> Icons.Outlined.PowerOff
        "APP_OPENED" -> Icons.Outlined.Apps
        "HEADPHONES_CONNECTED", "HEADPHONES_DISCONNECTED" -> Icons.Outlined.Headphones
        "LOCATION_ENTER" -> Icons.Outlined.LocationOn
        "LOCATION_EXIT" -> Icons.Outlined.LocationOff
        else -> Icons.AutoMirrored.Outlined.Rule
    }

    fun condition(typeKey: String): ImageVector = when (typeKey) {
        "TIME_RANGE" -> Icons.Outlined.Schedule
        "DAYS_OF_WEEK" -> Icons.Outlined.CalendarMonth
        "BATTERY_LEVEL" -> Icons.Outlined.BatteryAlert
        "CHARGING" -> Icons.Outlined.BatteryChargingFull
        "WIFI_STATE" -> Icons.Outlined.Wifi
        "BLUETOOTH_STATE" -> Icons.Outlined.Bluetooth
        "HEADPHONES_STATE" -> Icons.Outlined.Headphones
        "VARIABLE" -> Icons.Outlined.DataObject
        else -> Icons.AutoMirrored.Outlined.Rule
    }

    fun action(typeKey: String): ImageVector = when (typeKey) {
        "NOTIFICATION" -> Icons.Outlined.Notifications
        "LAUNCH_APP" -> Icons.Outlined.Apps
        "OPEN_URL" -> Icons.Outlined.Link
        "OPEN_SETTINGS" -> Icons.Outlined.Settings
        "SET_BRIGHTNESS" -> Icons.Outlined.BrightnessMedium
        "SET_VOLUME" -> Icons.AutoMirrored.Outlined.VolumeUp
        "DO_NOT_DISTURB" -> Icons.Outlined.DoNotDisturbOn
        "SPEAK" -> Icons.Outlined.RecordVoiceOver
        "PLAY_SOUND" -> Icons.Outlined.MusicNote
        "VIBRATE" -> Icons.Outlined.Vibration
        "HTTP_REQUEST" -> Icons.Outlined.Cloud
        "DELAY" -> Icons.Outlined.HourglassEmpty
        "SET_VARIABLE" -> Icons.Outlined.DataObject
        "IF_ELSE" -> Icons.Outlined.AccountTree
        "REPEAT" -> Icons.Outlined.Repeat
        else -> Icons.AutoMirrored.Outlined.OpenInNew
    }
}
