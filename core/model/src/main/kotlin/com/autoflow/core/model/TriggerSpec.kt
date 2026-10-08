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
    ;

    /** True when the family can only be observed while a monitoring service is running. */
    val needsMonitoringService: Boolean get() = this != TIME
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
}

private fun ssidCapabilities(ssid: String?): Set<Capability> =
    if (ssid.isNullOrBlank()) emptySet() else setOf(Capability.LOCATION, Capability.BACKGROUND_LOCATION)
