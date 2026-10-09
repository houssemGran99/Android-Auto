package com.autoflow.core.model

/**
 * Something that happened on the device. The engine matches events against
 * the [TriggerSpec]s of enabled automations.
 */
sealed interface TriggerEvent {
    /** Stable identifier of the event type, used in logs and as `%trigger%`. */
    val key: String

    /** Event details exposed as `%trigger_<name>%` variables. */
    val details: Map<String, String> get() = emptyMap()

    /** Details safe to persist in the execution history (private content excluded). */
    val loggedDetails: Map<String, String> get() = details

    /** Run requested explicitly by the user (button, widget, quick action). */
    data class Manual(val source: String) : TriggerEvent {
        override val key get() = "MANUAL"
        override val details get() = mapOf("source" to source)
    }

    /** A scheduled alarm for a specific automation fired. */
    data class TimeAlarm(val automationId: String, val scheduledAt: Long) : TriggerEvent {
        override val key get() = "TIME"
    }

    data class WifiConnected(val ssid: String?) : TriggerEvent {
        override val key get() = "WIFI_CONNECTED"
        override val details get() = mapOf("ssid" to ssid.orEmpty())
    }

    data class WifiDisconnected(val ssid: String?) : TriggerEvent {
        override val key get() = "WIFI_DISCONNECTED"
        override val details get() = mapOf("ssid" to ssid.orEmpty())
    }

    data class BluetoothConnected(val deviceName: String?, val address: String?) : TriggerEvent {
        override val key get() = "BLUETOOTH_CONNECTED"
        override val details get() = mapOf("device" to deviceName.orEmpty(), "address" to address.orEmpty())
    }

    data class BluetoothDisconnected(val deviceName: String?, val address: String?) : TriggerEvent {
        override val key get() = "BLUETOOTH_DISCONNECTED"
        override val details get() = mapOf("device" to deviceName.orEmpty(), "address" to address.orEmpty())
    }

    /** Battery level changed; [previousLevel] is null for the first reading after monitoring starts. */
    data class BatteryChanged(val level: Int, val previousLevel: Int?) : TriggerEvent {
        override val key get() = "BATTERY_CHANGED"
        override val details get() = mapOf("level" to level.toString())
    }

    data object ChargerConnected : TriggerEvent {
        override val key get() = "CHARGER_CONNECTED"
    }

    data object ChargerDisconnected : TriggerEvent {
        override val key get() = "CHARGER_DISCONNECTED"
    }

    data class AppOpened(val packageName: String) : TriggerEvent {
        override val key get() = "APP_OPENED"
        override val details get() = mapOf("package" to packageName)
    }

    data class HeadphonesConnected(val kind: HeadphoneKind, val name: String?) : TriggerEvent {
        override val key get() = "HEADPHONES_CONNECTED"
        override val details get() = mapOf("kind" to kind.name, "device" to name.orEmpty())
    }

    /** Another app posted a notification. Its content is available to actions but never written to history. */
    data class NotificationPosted(
        val packageName: String,
        val appLabel: String,
        val title: String,
        val text: String,
    ) : TriggerEvent {
        override val key get() = "NOTIFICATION_RECEIVED"
        override val details get() = mapOf("package" to packageName, "app" to appLabel, "title" to title, "text" to text)
        override val loggedDetails get() = mapOf("package" to packageName)
    }

    /** A calendar event started (or ended) for the trigger at [triggerIndex] of one automation. */
    data class CalendarEvent(
        val automationId: String,
        val triggerIndex: Int,
        val started: Boolean,
        val title: String,
        val location: String,
    ) : TriggerEvent {
        override val key get() = if (started) "CALENDAR_EVENT_START" else "CALENDAR_EVENT_END"
        override val details get() = mapOf("title" to title, "location" to location)
        override val loggedDetails get() = emptyMap<String, String>()
    }

    /**
     * A geofence transition for the location trigger at [triggerIndex] of one automation.
     * [entered] is true for "arrive", false for "leave".
     */
    data class LocationTransition(
        val automationId: String,
        val triggerIndex: Int,
        val entered: Boolean,
        val placeName: String,
    ) : TriggerEvent {
        override val key get() = if (entered) "LOCATION_ENTER" else "LOCATION_EXIT"
        override val details get() = mapOf("place" to placeName)
    }

    data class HeadphonesDisconnected(val kind: HeadphoneKind, val name: String?) : TriggerEvent {
        override val key get() = "HEADPHONES_DISCONNECTED"
        override val details get() = mapOf("kind" to kind.name, "device" to name.orEmpty())
    }
}
