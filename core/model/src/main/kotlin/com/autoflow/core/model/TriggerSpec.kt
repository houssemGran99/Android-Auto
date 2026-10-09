package com.autoflow.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Groups triggers by the OS mechanism that is used to observe them. */
enum class TriggerFamily {
    /** Scheduled with AlarmManager; no background service needed. */
    TIME,
    WIFI,
    BLUETOOTH,
    BATTERY,
    POWER,
    APP,
    HEADPHONES,

    /** Geofences registered with Google Play services; delivered by the system, no service needed. */
    LOCATION,

    /** Delivered to a NotificationListenerService that the system keeps bound. */
    NOTIFICATION,

    /** Event start/end times scheduled with AlarmManager. */
    CALENDAR,
    ;

    /** True when the family can only be observed while a monitoring service is running. */
    val needsMonitoringService: Boolean
        get() = this != TIME && this != LOCATION && this != NOTIFICATION && this != CALENDAR
}

/** A named circular area used by location triggers. */
@Serializable
data class GeoPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
        require(radiusMeters in MIN_RADIUS_METERS..MAX_RADIUS_METERS) {
            "Radius must be between $MIN_RADIUS_METERS and $MAX_RADIUS_METERS meters"
        }
    }

    companion object {
        /** Android recommends at least 100 m; smaller radii trigger unreliably. */
        const val MIN_RADIUS_METERS = 100
        const val MAX_RADIUS_METERS = 10_000
        const val DEFAULT_RADIUS_METERS = 150
    }
}

@Serializable
enum class ThresholdDirection { BELOW, ABOVE }

@Serializable
enum class HeadphoneKind { ANY, WIRED, BLUETOOTH }

/**
 * Configuration of something that can start an automation.
 * An automation runs when ANY of its triggers fires.
 */
@Serializable
sealed interface TriggerSpec {
    val family: TriggerFamily
    val capabilities: Set<Capability> get() = emptySet()

    /** Fires at a wall-clock time, optionally only on some days (empty = every day). */
    @Serializable
    @SerialName("TIME")
    data class Time(
        val at: TimeOfDay,
        val days: Set<Weekday> = emptySet(),
    ) : TriggerSpec {
        override val family get() = TriggerFamily.TIME
        override val capabilities get() = setOf(Capability.EXACT_ALARMS)
    }

    /** Fires repeatedly every [everyMinutes] minutes. */
    @Serializable
    @SerialName("INTERVAL")
    data class Interval(val everyMinutes: Int) : TriggerSpec {
        init {
            require(everyMinutes >= MIN_INTERVAL_MINUTES) { "Interval must be at least $MIN_INTERVAL_MINUTES minute(s)" }
        }

        override val family get() = TriggerFamily.TIME
        override val capabilities get() = setOf(Capability.EXACT_ALARMS)

        companion object {
            const val MIN_INTERVAL_MINUTES = 1
        }
    }

    /** Wi-Fi connected; [ssid] = null matches any network. */
    @Serializable
    @SerialName("WIFI_CONNECTED")
    data class WifiConnected(val ssid: String? = null) : TriggerSpec {
        override val family get() = TriggerFamily.WIFI
        override val capabilities get() = ssidCapabilities(ssid)
    }

    @Serializable
    @SerialName("WIFI_DISCONNECTED")
    data class WifiDisconnected(val ssid: String? = null) : TriggerSpec {
        override val family get() = TriggerFamily.WIFI
        override val capabilities get() = ssidCapabilities(ssid)
    }

    /** A Bluetooth device connected; [deviceName] = null matches any device. */
    @Serializable
    @SerialName("BLUETOOTH_CONNECTED")
    data class BluetoothConnected(val deviceName: String? = null) : TriggerSpec {
        override val family get() = TriggerFamily.BLUETOOTH
        override val capabilities get() = setOf(Capability.BLUETOOTH_CONNECT)
    }

    @Serializable
    @SerialName("BLUETOOTH_DISCONNECTED")
    data class BluetoothDisconnected(val deviceName: String? = null) : TriggerSpec {
        override val family get() = TriggerFamily.BLUETOOTH
        override val capabilities get() = setOf(Capability.BLUETOOTH_CONNECT)
    }

    /** Fires when the battery level crosses [threshold] in the given [direction]. */
    @Serializable
    @SerialName("BATTERY_LEVEL")
    data class BatteryLevel(
        val threshold: Int,
        val direction: ThresholdDirection = ThresholdDirection.BELOW,
    ) : TriggerSpec {
        init {
            require(threshold in 1..99) { "Battery threshold must be in 1..99" }
        }

        override val family get() = TriggerFamily.BATTERY
    }

    @Serializable
    @SerialName("CHARGER_CONNECTED")
    data object ChargerConnected : TriggerSpec {
        override val family get() = TriggerFamily.POWER
    }

    @Serializable
    @SerialName("CHARGER_DISCONNECTED")
    data object ChargerDisconnected : TriggerSpec {
        override val family get() = TriggerFamily.POWER
    }

    @Serializable
    @SerialName("APP_OPENED")
    data class AppOpened(
        val packageName: String,
        val appLabel: String = "",
    ) : TriggerSpec {
        override val family get() = TriggerFamily.APP
        override val capabilities get() = setOf(Capability.USAGE_ACCESS)
    }

    @Serializable
    @SerialName("HEADPHONES_CONNECTED")
    data class HeadphonesConnected(val kind: HeadphoneKind = HeadphoneKind.ANY) : TriggerSpec {
        override val family get() = TriggerFamily.HEADPHONES
    }

    @Serializable
    @SerialName("HEADPHONES_DISCONNECTED")
    data class HeadphonesDisconnected(val kind: HeadphoneKind = HeadphoneKind.ANY) : TriggerSpec {
        override val family get() = TriggerFamily.HEADPHONES
    }

    /**
     * A notification was posted. [packageName] = null matches any app; [textContains]
     * (case-insensitive) is searched in the title and the text.
     */
    @Serializable
    @SerialName("NOTIFICATION_RECEIVED")
    data class NotificationReceived(
        val packageName: String? = null,
        val appLabel: String = "",
        val textContains: String? = null,
    ) : TriggerSpec {
        override val family get() = TriggerFamily.NOTIFICATION
        override val capabilities get() = setOf(Capability.NOTIFICATION_LISTENER)
    }

    /** A calendar event starts; [titleContains] (case-insensitive) filters by event title. */
    @Serializable
    @SerialName("CALENDAR_EVENT_START")
    data class CalendarEventStart(val titleContains: String? = null) : TriggerSpec {
        override val family get() = TriggerFamily.CALENDAR
        override val capabilities get() = setOf(Capability.CALENDAR, Capability.EXACT_ALARMS)
    }

    /** A calendar event ends; [titleContains] (case-insensitive) filters by event title. */
    @Serializable
    @SerialName("CALENDAR_EVENT_END")
    data class CalendarEventEnd(val titleContains: String? = null) : TriggerSpec {
        override val family get() = TriggerFamily.CALENDAR
        override val capabilities get() = setOf(Capability.CALENDAR, Capability.EXACT_ALARMS)
    }

    /** Fires when the device enters (arrives at) [place]. */
    @Serializable
    @SerialName("LOCATION_ENTER")
    data class LocationEnter(val place: GeoPlace) : TriggerSpec {
        override val family get() = TriggerFamily.LOCATION
        override val capabilities get() = setOf(Capability.LOCATION, Capability.BACKGROUND_LOCATION)
    }

    /** Fires when the device exits (leaves) [place]. */
    @Serializable
    @SerialName("LOCATION_EXIT")
    data class LocationExit(val place: GeoPlace) : TriggerSpec {
        override val family get() = TriggerFamily.LOCATION
        override val capabilities get() = setOf(Capability.LOCATION, Capability.BACKGROUND_LOCATION)
    }
}

private fun ssidCapabilities(ssid: String?): Set<Capability> =
    if (ssid.isNullOrBlank()) emptySet() else setOf(Capability.LOCATION, Capability.BACKGROUND_LOCATION)
