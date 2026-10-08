package com.autoflow.core.engine

/** Snapshot of device state used by conditions and built-in variables. Null = unknown / not readable. */
data class DeviceState(
    val batteryLevel: Int? = null,
    val charging: Boolean? = null,
    val wifiConnected: Boolean? = null,
    val wifiSsid: String? = null,
    val bluetoothEnabled: Boolean? = null,
    val headphonesConnected: Boolean? = null,
    val mediaVolumePercent: Int? = null,
    val brightnessPercent: Int? = null,
    /** Last known location; only filled when location permission is granted. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    val deviceModel: String = "",
    val osVersion: String = "",
)

/** Platform abstraction that reads the current [DeviceState]. */
fun interface DeviceStateProvider {
    suspend fun snapshot(): DeviceState
}

/** Engine diagnostics sink (mapped to Logcat on Android). */
interface EngineLogger {
    fun debug(message: String)
    fun warn(message: String, throwable: Throwable? = null)

    companion object {
        val NONE: EngineLogger = object : EngineLogger {
            override fun debug(message: String) = Unit
            override fun warn(message: String, throwable: Throwable?) = Unit
        }
    }
}

/** Normalizes Android SSIDs which are reported quoted ("Office") or as "<unknown ssid>". */
fun normalizeSsid(raw: String?): String? {
    val trimmed = raw?.trim()?.removeSurrounding("\"") ?: return null
    return trimmed.takeUnless { it.isEmpty() || it == "<unknown ssid>" }
}
