package com.autoflow.core.engine.variable

import com.autoflow.core.engine.DeviceState
import com.autoflow.core.model.TriggerEvent
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Names and resolution of `%built-in%` variables. */
object BuiltInVariables {
    /** Built-in names shown in the variables screen (trigger_* names depend on the event). */
    val NAMES: List<String> = listOf(
        "battery", "charging", "wifi", "ssid", "bluetooth", "headphones", "volume", "brightness",
        "latitude", "longitude", "location", "time", "date", "datetime", "day", "timestamp", "device", "android", "automation", "trigger",
        "notification_app", "notification_title", "notification_text", "event_title", "event_location",
    )

    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val DATE = DateTimeFormatter.ISO_LOCAL_DATE
    private val DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun resolve(
        name: String,
        state: DeviceState,
        now: ZonedDateTime,
        automationName: String,
        event: TriggerEvent,
    ): String? = when (name) {
        "battery" -> state.batteryLevel?.toString()
        "charging" -> state.charging?.onOff()
        "wifi" -> state.wifiConnected?.onOff()
        "ssid" -> state.wifiSsid
        "bluetooth" -> state.bluetoothEnabled?.onOff()
        "headphones" -> state.headphonesConnected?.onOff()
        "volume" -> state.mediaVolumePercent?.toString()
        "brightness" -> state.brightnessPercent?.toString()
        "latitude" -> state.latitude?.let(::coordinate)
        "longitude" -> state.longitude?.let(::coordinate)
        "location" -> if (state.latitude != null && state.longitude != null) {
            "${coordinate(state.latitude)},${coordinate(state.longitude)}"
        } else {
            null
        }
        "time" -> now.format(TIME)
        "date" -> now.format(DATE)
        "datetime" -> now.format(DATETIME)
        "day" -> now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        "timestamp" -> now.toEpochSecond().toString()
        "device" -> state.deviceModel
        "android" -> state.osVersion
        "automation" -> automationName
        "trigger" -> event.key
        "notification_app" -> (event as? TriggerEvent.NotificationPosted)?.let { it.appLabel.ifBlank { it.packageName } }
        "notification_title" -> (event as? TriggerEvent.NotificationPosted)?.title
        "notification_text" -> (event as? TriggerEvent.NotificationPosted)?.text
        "event_title" -> (event as? TriggerEvent.CalendarEvent)?.title
        "event_location" -> (event as? TriggerEvent.CalendarEvent)?.location
        else -> if (name.startsWith(TRIGGER_PREFIX)) event.details[name.removePrefix(TRIGGER_PREFIX)] else null
    }

    private const val TRIGGER_PREFIX = "trigger_"

    private fun coordinate(value: Double): String = String.format(Locale.ROOT, "%.6f", value)

    private fun Boolean.onOff() = if (this) "on" else "off"
}
