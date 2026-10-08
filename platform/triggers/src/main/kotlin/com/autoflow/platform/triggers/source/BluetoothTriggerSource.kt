package com.autoflow.platform.triggers.source

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.IntentCompat
import com.autoflow.core.model.Capability
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.TriggerFamily
import com.autoflow.platform.permissions.PermissionManager

/**
 * Bluetooth device connections (ACL level). Android 12+ only delivers these broadcasts
 * and device names when "Nearby devices" (BLUETOOTH_CONNECT) is granted.
 */
class BluetoothTriggerSource(
    context: Context,
    private val permissions: PermissionManager,
) : BroadcastTriggerSource(context) {
    override val family = TriggerFamily.BLUETOOTH

    override fun filter() = IntentFilter().apply {
        addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
    }

    // Device details may require BLUETOOTH_CONNECT; failures degrade to "unknown device".
    @SuppressLint("MissingPermission")
    override fun toEvent(intent: Intent): TriggerEvent? {
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        val name = device?.let(::safeName)
        val address = try {
            device?.address
        } catch (e: SecurityException) {
            null
        }
        return when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> TriggerEvent.BluetoothConnected(name, address)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> TriggerEvent.BluetoothDisconnected(name, address)
            else -> null
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeName(device: BluetoothDevice): String? {
        if (!permissions.isSatisfied(Capability.BLUETOOTH_CONNECT)) return null
        return try {
            device.name
        } catch (e: SecurityException) {
            null
        }
    }
}
