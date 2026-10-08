package com.autoflow.platform.triggers.source

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.autoflow.core.engine.trigger.TriggerSource
import com.autoflow.core.model.HeadphoneKind
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.TriggerFamily

/**
 * Wired, USB and Bluetooth headphones via [AudioDeviceCallback]; needs no permission.
 * Devices already connected when monitoring starts do not fire.
 */
class HeadphonesTriggerSource(context: Context) : TriggerSource {
    override val family = TriggerFamily.HEADPHONES

    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val knownDeviceIds = mutableSetOf<Int>()
    private var callback: AudioDeviceCallback? = null

    override fun register(onEvent: (TriggerEvent) -> Unit) {
        unregister()
        knownDeviceIds += audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.id }
        val newCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                for (device in addedDevices) {
                    if (!knownDeviceIds.add(device.id) || !device.isSink) continue
                    val kind = headphoneKind(device.type) ?: continue
                    onEvent(TriggerEvent.HeadphonesConnected(kind, device.productName?.toString()))
                }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                for (device in removedDevices) {
                    if (!knownDeviceIds.remove(device.id) || !device.isSink) continue
                    val kind = headphoneKind(device.type) ?: continue
                    onEvent(TriggerEvent.HeadphonesDisconnected(kind, device.productName?.toString()))
                }
            }
        }
        callback = newCallback
        audioManager.registerAudioDeviceCallback(newCallback, Handler(Looper.getMainLooper()))
    }

    override fun unregister() {
        callback?.let { audioManager.unregisterAudioDeviceCallback(it) }
        callback = null
        knownDeviceIds.clear()
    }

    companion object {
        /** Maps output device types to headphone kinds; SCO (call audio) is ignored to avoid duplicates. */
        fun headphoneKind(type: Int): HeadphoneKind? = when {
            type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                type == AudioDeviceInfo.TYPE_USB_HEADSET -> HeadphoneKind.WIRED
            type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> HeadphoneKind.BLUETOOTH
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET -> HeadphoneKind.BLUETOOTH
            else -> null
        }

        fun isHeadphoneConnected(audioManager: AudioManager): Boolean =
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { headphoneKind(it.type) != null }
    }
}
