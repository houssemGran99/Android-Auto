package com.autoflow.platform.triggers

import android.content.Context
import com.autoflow.core.engine.trigger.TriggerSource
import com.autoflow.core.model.TriggerFamily
import com.autoflow.platform.permissions.PermissionManager
import com.autoflow.platform.triggers.source.AppUsageTriggerSource
import com.autoflow.platform.triggers.source.BatteryTriggerSource
import com.autoflow.platform.triggers.source.BluetoothTriggerSource
import com.autoflow.platform.triggers.source.HeadphonesTriggerSource
import com.autoflow.platform.triggers.source.PowerTriggerSource
import com.autoflow.platform.triggers.source.WifiTriggerSource
import kotlinx.coroutines.CoroutineScope

/** Creates the [TriggerSource] for each family that needs a running monitor. */
class TriggerSourceFactory(
    private val context: Context,
    private val permissions: PermissionManager,
) {
    fun create(family: TriggerFamily, scope: CoroutineScope): TriggerSource? = when (family) {
        TriggerFamily.TIME -> null // AlarmManager, see TimeTriggerScheduler
        TriggerFamily.LOCATION -> null // Geofences, see GeofenceScheduler
        TriggerFamily.WIFI -> WifiTriggerSource(context)
        TriggerFamily.BLUETOOTH -> BluetoothTriggerSource(context, permissions)
        TriggerFamily.BATTERY -> BatteryTriggerSource(context)
        TriggerFamily.POWER -> PowerTriggerSource(context)
        TriggerFamily.APP -> AppUsageTriggerSource(context, scope, permissions)
        TriggerFamily.HEADPHONES -> HeadphonesTriggerSource(context)
    }
}
