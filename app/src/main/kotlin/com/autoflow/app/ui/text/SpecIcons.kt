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
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.DataArray
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.NotificationsActive
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
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.ui.graphics.vector.ImageVector

object SpecIcons {
    fun trigger(typeKey: String): ImageVector = when (typeKey) {
        "TIME" -> Icons.Outlined.Schedule
        "INTERVAL" -> Icons.Outlined.Timer
        "SUN_EVENT" -> Icons.Outlined.WbTwilight
        "WIFI_CONNECTED" -> Icons.Outlined.Wifi
        "WIFI_DISCONNECTED" -> Icons.Outlined.WifiOff
        "BLUETOOTH_CONNECTED", "BLUETOOTH_DISCONNECTED" -> Icons.Outlined.Bluetooth
        "BATTERY_LEVEL", "BATTERY_CHANGED" -> Icons.Outlined.BatteryAlert
        "CHARGER_CONNECTED" -> Icons.Outlined.Power
        "CHARGER_DISCONNECTED" -> Icons.Outlined.PowerOff
        "APP_OPENED" -> Icons.Outlined.Apps
        "HEADPHONES_CONNECTED", "HEADPHONES_DISCONNECTED" -> Icons.Outlined.Headphones
        "NOTIFICATION_RECEIVED" -> Icons.Outlined.NotificationsActive
        "CALENDAR_EVENT_START" -> Icons.Outlined.Event
        "CALENDAR_EVENT_END" -> Icons.Outlined.EventAvailable
        "LOCATION_ENTER" -> Icons.Outlined.LocationOn
        "LOCATION_EXIT" -> Icons.Outlined.LocationOff
        "MEDIA_STARTED" -> Icons.Outlined.PlayCircle
        "MEDIA_STOPPED" -> Icons.Outlined.MusicOff
        "FILE_ADDED" -> Icons.Outlined.CreateNewFolder
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
        "MUSIC_PLAYING" -> Icons.Outlined.GraphicEq
        "FILE_EXISTS" -> Icons.Outlined.FindInPage
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
        "DISMISS_NOTIFICATIONS" -> Icons.Outlined.ClearAll
        "WHILE" -> Icons.Outlined.Loop
        "WAIT_UNTIL" -> Icons.Outlined.PauseCircle
        "STOP" -> Icons.Outlined.StopCircle
        "VARIABLE_OPERATION" -> Icons.Outlined.Calculate
        "PARSE_JSON" -> Icons.Outlined.DataArray
        "MEDIA_CONTROL" -> Icons.Outlined.SkipNext
        "PLAY_MUSIC_FOLDER" -> Icons.Outlined.LibraryMusic
        "STOP_MUSIC" -> Icons.Outlined.MusicOff
        "WRITE_FILE" -> Icons.Outlined.EditNote
        "READ_FILE" -> Icons.Outlined.Description
        "LIST_FILES" -> Icons.Outlined.FolderOpen
        "COPY_FILE" -> Icons.Outlined.FileCopy
        "DELETE_FILE" -> Icons.Outlined.DeleteOutline
        else -> Icons.AutoMirrored.Outlined.OpenInNew
    }
}
