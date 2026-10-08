package com.autoflow.platform.triggers.source

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.TriggerFamily

/** Battery level changes (ACTION_BATTERY_CHANGED can only be received by registered receivers). */
class BatteryTriggerSource(context: Context) : BroadcastTriggerSource(context) {
    override val family = TriggerFamily.BATTERY
    private var lastLevel: Int? = null

    override fun filter() = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

    override fun onRegistered() {
        lastLevel = null
    }

    override fun toEvent(intent: Intent): TriggerEvent? {
        val level = batteryPercent(intent) ?: return null
        val previous = lastLevel
        if (previous == level) return null
        lastLevel = level
        return TriggerEvent.BatteryChanged(level, previous)
    }

    companion object {
        fun batteryPercent(intent: Intent): Int? {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return null
            return (level * 100 / scale).coerceIn(0, 100)
        }
    }
}

/** Charger connected / disconnected. */
class PowerTriggerSource(context: Context) : BroadcastTriggerSource(context) {
    override val family = TriggerFamily.POWER

    override fun filter() = IntentFilter().apply {
        addAction(Intent.ACTION_POWER_CONNECTED)
        addAction(Intent.ACTION_POWER_DISCONNECTED)
    }

    override fun toEvent(intent: Intent): TriggerEvent? = when (intent.action) {
        Intent.ACTION_POWER_CONNECTED -> TriggerEvent.ChargerConnected
        Intent.ACTION_POWER_DISCONNECTED -> TriggerEvent.ChargerDisconnected
        else -> null
    }
}
