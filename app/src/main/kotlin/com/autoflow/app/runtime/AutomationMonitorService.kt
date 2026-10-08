package com.autoflow.app.runtime

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.autoflow.app.R
import com.autoflow.app.di.ApplicationScope
import com.autoflow.core.engine.AutomationEngine
import com.autoflow.core.engine.trigger.TriggerSource
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.TriggerFamily
import com.autoflow.platform.triggers.TriggerSourceFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps runtime-registered trigger sources alive. Android only
 * delivers battery, charger, headphone and network callbacks to a running process, so a
 * visible, user-stoppable service is the supported way to react to them in the background.
 */
@AndroidEntryPoint
class AutomationMonitorService : Service() {
    @Inject lateinit var coordinator: TriggerCoordinator

    @Inject lateinit var engine: AutomationEngine

    @Inject lateinit var sourceFactory: TriggerSourceFactory

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sources = mutableMapOf<TriggerFamily, TriggerSource>()

    override fun onCreate() {
        super.onCreate()
        startInForeground(emptySet())
        serviceScope.launch {
            coordinator.monitoredFamilies.collect { families ->
                updateSources(families)
                startInForeground(families)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(sources.keys)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sources.values.forEach { it.unregister() }
        sources.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun updateSources(wanted: Set<TriggerFamily>) {
        (sources.keys - wanted).forEach { family -> sources.remove(family)?.unregister() }
        (wanted - sources.keys).forEach { family ->
            val source = sourceFactory.create(family, serviceScope) ?: return@forEach
            try {
                source.register(::onEvent)
                sources[family] = source
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot observe $family: permission missing", e)
            }
        }
    }

    private fun onEvent(event: TriggerEvent) {
        Log.d(TAG, "Trigger event ${event.key}")
        appScope.launch { engine.handleEvent(event) }
    }

    private fun startInForeground(families: Set<TriggerFamily>) {
        val watching = families.sortedBy { it.ordinal }.joinToString { getString(familyLabel(it)) }
        val notification = AppNotifications.monitorNotification(this, watching)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                AppNotifications.MONITOR_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(AppNotifications.MONITOR_NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val TAG = "AutoFlow"

        fun familyLabel(family: TriggerFamily): Int = when (family) {
            TriggerFamily.TIME -> R.string.family_time
            TriggerFamily.WIFI -> R.string.family_wifi
            TriggerFamily.BLUETOOTH -> R.string.family_bluetooth
            TriggerFamily.BATTERY -> R.string.family_battery
            TriggerFamily.POWER -> R.string.family_power
            TriggerFamily.APP -> R.string.family_app
            TriggerFamily.HEADPHONES -> R.string.family_headphones
        }
    }
}
