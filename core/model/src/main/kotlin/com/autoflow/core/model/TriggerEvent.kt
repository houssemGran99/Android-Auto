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
