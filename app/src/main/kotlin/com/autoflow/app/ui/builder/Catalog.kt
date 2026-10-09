package com.autoflow.app.ui.builder

import androidx.annotation.StringRes
import com.autoflow.app.R
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.Comparison
import com.autoflow.core.model.ConditionNode
import com.autoflow.core.model.GeoPlace
import com.autoflow.core.model.HeadphoneKind
import com.autoflow.core.model.SettingsPanel
import com.autoflow.core.model.SoundType
import com.autoflow.core.model.SunEventType
import com.autoflow.core.model.VariableOp
import com.autoflow.core.model.ThresholdDirection
import com.autoflow.core.model.TimeOfDay
import com.autoflow.core.model.TriggerSpec
import com.autoflow.core.model.Weekday

/**
 * One entry of the "add" sheet. [restriction] marks things Android does not allow;
 * picking them adds the closest supported alternative and explains why.
 */
data class CatalogItem<T>(
    val typeKey: String,
    @StringRes val category: Int,
    @StringRes val title: Int? = null,
    @StringRes val restriction: Int? = null,
    val needsConfiguration: Boolean = true,
    val create: () -> T,
)

object BuilderCatalog {
    /** Placeholder until the user picks a location; the editor refuses to save it unchanged. */
    val NEW_PLACE = GeoPlace(name = "", latitude = 0.0, longitude = 0.0)

    val triggers: List<CatalogItem<TriggerSpec>> = listOf(
        CatalogItem("TIME", R.string.category_time) { TriggerSpec.Time(TimeOfDay(8, 0)) },
        CatalogItem("INTERVAL", R.string.category_time) { TriggerSpec.Interval(30) },
        CatalogItem("SUN_EVENT", R.string.category_time) { TriggerSpec.SunEvent(SunEventType.SUNSET, 0.0, 0.0) },
        CatalogItem("WIFI_CONNECTED", R.string.category_connectivity) { TriggerSpec.WifiConnected() },
        CatalogItem("WIFI_DISCONNECTED", R.string.category_connectivity) { TriggerSpec.WifiDisconnected() },
        CatalogItem("BLUETOOTH_CONNECTED", R.string.category_connectivity) { TriggerSpec.BluetoothConnected() },
        CatalogItem("BLUETOOTH_DISCONNECTED", R.string.category_connectivity) { TriggerSpec.BluetoothDisconnected() },
        CatalogItem("BATTERY_LEVEL", R.string.category_battery) { TriggerSpec.BatteryLevel(20, ThresholdDirection.BELOW) },
        CatalogItem("CHARGER_CONNECTED", R.string.category_battery, needsConfiguration = false) { TriggerSpec.ChargerConnected },
        CatalogItem("CHARGER_DISCONNECTED", R.string.category_battery, needsConfiguration = false) { TriggerSpec.ChargerDisconnected },
        CatalogItem("HEADPHONES_CONNECTED", R.string.category_hardware) { TriggerSpec.HeadphonesConnected(HeadphoneKind.ANY) },
        CatalogItem("HEADPHONES_DISCONNECTED", R.string.category_hardware) { TriggerSpec.HeadphonesDisconnected(HeadphoneKind.ANY) },
        CatalogItem("APP_OPENED", R.string.category_apps) { TriggerSpec.AppOpened(packageName = "") },
        CatalogItem("NOTIFICATION_RECEIVED", R.string.category_notifications) { TriggerSpec.NotificationReceived() },
        CatalogItem("CALENDAR_EVENT_START", R.string.category_calendar) { TriggerSpec.CalendarEventStart() },
        CatalogItem("CALENDAR_EVENT_END", R.string.category_calendar) { TriggerSpec.CalendarEventEnd() },
        CatalogItem("LOCATION_ENTER", R.string.category_location) { TriggerSpec.LocationEnter(NEW_PLACE) },
        CatalogItem("LOCATION_EXIT", R.string.category_location) { TriggerSpec.LocationExit(NEW_PLACE) },
    )

    const val GROUP_AND = "GROUP_AND"
    const val GROUP_OR = "GROUP_OR"

    val conditions: List<CatalogItem<ConditionNode>> = listOf(
        CatalogItem("TIME_RANGE", R.string.category_time) { ConditionNode.TimeRange(TimeOfDay(8, 0), TimeOfDay(18, 0)) },
        CatalogItem("DAYS_OF_WEEK", R.string.category_time) { ConditionNode.DaysOfWeek(Weekday.WORKDAYS) },
        CatalogItem("BATTERY_LEVEL", R.string.category_battery) { ConditionNode.BatteryLevel(Comparison.GREATER_THAN, 30) },
        CatalogItem("CHARGING", R.string.category_battery) { ConditionNode.Charging(true) },
        CatalogItem("WIFI_STATE", R.string.category_connectivity) { ConditionNode.WifiState(connected = true) },
        CatalogItem("BLUETOOTH_STATE", R.string.category_connectivity) { ConditionNode.BluetoothState(enabled = true) },
        CatalogItem("HEADPHONES_STATE", R.string.category_hardware) { ConditionNode.HeadphonesState(connected = true) },
        CatalogItem("VARIABLE", R.string.category_logic) { ConditionNode.VariableCompare("", Comparison.EQUALS, "") },
        CatalogItem(GROUP_AND, R.string.category_logic, title = R.string.group_and, needsConfiguration = false) {
            ConditionNode.And(emptyList())
        },
        CatalogItem(GROUP_OR, R.string.category_logic, title = R.string.group_or, needsConfiguration = false) {
            ConditionNode.Or(emptyList())
        },
    )

    val actions: List<CatalogItem<ActionSpec>> = listOf(
        CatalogItem("NOTIFICATION", R.string.category_notifications) { ActionSpec.ShowNotification("", "") },
        CatalogItem("DISMISS_NOTIFICATIONS", R.string.category_notifications) { ActionSpec.DismissNotifications() },
        CatalogItem("LAUNCH_APP", R.string.category_apps) { ActionSpec.LaunchApp(packageName = "") },
        CatalogItem("OPEN_URL", R.string.category_apps) { ActionSpec.OpenUrl("https://") },
        CatalogItem("OPEN_SETTINGS", R.string.category_apps) { ActionSpec.OpenSettings(SettingsPanel.WIFI) },
        CatalogItem("SET_BRIGHTNESS", R.string.category_device) { ActionSpec.SetBrightness(50) },
        CatalogItem("SET_VOLUME", R.string.category_device) { ActionSpec.SetVolume(percent = 50) },
        CatalogItem("DO_NOT_DISTURB", R.string.category_device) { ActionSpec.SetDoNotDisturb(true) },
        CatalogItem("VIBRATE", R.string.category_device) { ActionSpec.Vibrate(500) },
        CatalogItem("SPEAK", R.string.category_audio) { ActionSpec.Speak("") },
        CatalogItem("PLAY_SOUND", R.string.category_audio) { ActionSpec.PlaySound(SoundType.NOTIFICATION) },
        CatalogItem("HTTP_REQUEST", R.string.category_web) { ActionSpec.HttpRequest(url = "https://") },
        CatalogItem("DELAY", R.string.category_logic) { ActionSpec.Delay(5_000) },
        CatalogItem("SET_VARIABLE", R.string.category_variables) { ActionSpec.SetVariable("", "") },
        CatalogItem("IF_ELSE", R.string.category_logic) { ActionSpec.IfElse(ConditionNode.And(emptyList()), emptyList()) },
        CatalogItem("REPEAT", R.string.category_logic) { ActionSpec.Repeat(3, emptyList()) },
        CatalogItem("WHILE", R.string.category_logic) { ActionSpec.While(ConditionNode.And(emptyList()), emptyList()) },
        CatalogItem("WAIT_UNTIL", R.string.category_logic) { ActionSpec.WaitUntil(ConditionNode.And(emptyList())) },
        CatalogItem("STOP", R.string.category_logic, needsConfiguration = false) { ActionSpec.Stop },
        CatalogItem("VARIABLE_OPERATION", R.string.category_variables) { ActionSpec.VariableOperation("", VariableOp.INCREMENT) },
        CatalogItem("PARSE_JSON", R.string.category_variables) { ActionSpec.ParseJson("\$http", "", "") },
        // Not allowed by Android for regular apps: offer the settings panel instead.
        CatalogItem("TOGGLE_WIFI", R.string.category_restricted, R.string.restricted_wifi_title, R.string.restricted_wifi) {
            ActionSpec.OpenSettings(SettingsPanel.WIFI)
        },
        CatalogItem("TOGGLE_BLUETOOTH", R.string.category_restricted, R.string.restricted_bluetooth_title, R.string.restricted_bluetooth) {
            ActionSpec.OpenSettings(SettingsPanel.BLUETOOTH)
        },
        CatalogItem("TOGGLE_AIRPLANE", R.string.category_restricted, R.string.restricted_airplane_title, R.string.restricted_airplane) {
            ActionSpec.OpenSettings(SettingsPanel.AIRPLANE_MODE)
        },
        CatalogItem("TOGGLE_BATTERY_SAVER", R.string.category_restricted, R.string.restricted_battery_saver_title, R.string.restricted_battery_saver) {
            ActionSpec.OpenSettings(SettingsPanel.BATTERY_SAVER)
        },
    )
}
