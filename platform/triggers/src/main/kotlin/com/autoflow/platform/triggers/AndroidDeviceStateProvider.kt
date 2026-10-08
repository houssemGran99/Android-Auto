package com.autoflow.platform.triggers

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import com.autoflow.core.engine.DeviceState
import com.autoflow.core.engine.DeviceStateProvider
import com.autoflow.core.model.Capability
import com.autoflow.platform.permissions.PermissionManager
import com.autoflow.platform.triggers.source.BatteryTriggerSource
import com.autoflow.platform.triggers.source.HeadphonesTriggerSource
import com.autoflow.platform.triggers.source.WifiTriggerSource

/** Reads the current device state from Android system services. Unreadable values are null. */
class AndroidDeviceStateProvider(
    context: Context,
    private val permissions: PermissionManager,
) : DeviceStateProvider {
    private val context = context.applicationContext

    override suspend fun snapshot(): DeviceState {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        val wifiConnected = isWifiConnected()
        return DeviceState(
            batteryLevel = battery?.let(BatteryTriggerSource::batteryPercent),
            charging = batteryManager?.isCharging,
            wifiConnected = wifiConnected,
            wifiSsid = if (wifiConnected == true && permissions.isSatisfied(Capability.LOCATION)) {
                WifiTriggerSource.currentSsid(context)
            } else {
                null
            },
            bluetoothEnabled = runCatching {
                context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled
            }.getOrNull(),
            headphonesConnected = HeadphonesTriggerSource.isHeadphoneConnected(audio),
            mediaVolumePercent = volumePercent(audio, AudioManager.STREAM_MUSIC),
            brightnessPercent = runCatching {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) * 100 / MAX_BRIGHTNESS
            }.getOrNull(),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            osVersion = Build.VERSION.RELEASE,
        )
    }

    private fun isWifiConnected(): Boolean? = runCatching {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork
        val caps = network?.let(connectivity::getNetworkCapabilities)
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }.getOrNull()

    companion object {
        const val MAX_BRIGHTNESS = 255

        fun volumePercent(audio: AudioManager, stream: Int): Int? {
            val max = audio.getStreamMaxVolume(stream)
            val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audio.getStreamMinVolume(stream) else 0
            if (max <= min) return null
            return ((audio.getStreamVolume(stream) - min) * 100 / (max - min)).coerceIn(0, 100)
        }
    }
}
