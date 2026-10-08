package com.autoflow.app.runtime

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.autoflow.app.di.ApplicationScope
import com.autoflow.app.widget.QuickActionsWidget
import com.autoflow.core.engine.repository.AutomationRepository
import com.autoflow.core.model.Automation
import com.autoflow.core.model.TriggerFamily
import com.autoflow.data.storage.settings.SettingsRepository
import com.autoflow.platform.triggers.GeofenceScheduler
import com.autoflow.platform.triggers.TimeTriggerScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps OS registrations in sync with the stored automations:
 *  - time triggers → AlarmManager (no service needed)
 *  - location triggers → Play services geofences (no service needed)
 *  - Wi-Fi / Bluetooth / battery / charger / headphones / app triggers → foreground monitoring service,
 *    which runs only while at least one enabled automation needs it.
 */
@Singleton
class TriggerCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val automations: AutomationRepository,
    private val settings: SettingsRepository,
    private val timeScheduler: TimeTriggerScheduler,
    private val geofenceScheduler: GeofenceScheduler,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)
    private val refreshLock = Mutex()
    private val _monitoredFamilies = MutableStateFlow<Set<TriggerFamily>>(emptySet())

    private val _geofenceStatus = MutableStateFlow(GeofenceScheduler.Status.NOTHING_TO_REGISTER)

    /** Whether location triggers are currently registered (shown in the UI when they are not). */
    val geofenceStatus: StateFlow<GeofenceScheduler.Status> = _geofenceStatus.asStateFlow()

    /** Trigger families the monitoring service must observe. */
    val monitoredFamilies: StateFlow<Set<TriggerFamily>> = _monitoredFamilies.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(
                automations.observeAll(),
                settings.settings.map { it.masterEnabled }.distinctUntilChanged(),
            ) { all, master -> if (master) all.filter { it.enabled } else emptyList() }
                .collect { active -> sync(active) }
        }
        scope.launch {
            // Widgets show automation names and the master switch.
            combine(automations.observeAll(), settings.settings.map { it.masterEnabled }) { _, _ -> Unit }
                .collect { runCatching { QuickActionsWidget.refresh(context) } }
        }
    }

    /** Recomputes alarms, e.g. after an alarm fired, a reboot or a time zone change. */
    fun refresh(): Job = scope.launch { sync(activeAutomations()) }

    /** Starts the monitoring service again if it should run (e.g. after Android refused a background start). */
    fun ensureMonitoring() {
        if (_monitoredFamilies.value.isNotEmpty()) startService()
    }

    private suspend fun activeAutomations(): List<Automation> =
        if (settings.settings.first().masterEnabled) automations.getEnabled() else emptyList()

    private suspend fun sync(active: List<Automation>) = refreshLock.withLock {
        try {
            timeScheduler.refresh(active)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not schedule alarms", e)
        }
        _geofenceStatus.value = geofenceScheduler.refresh(active)
        val families = active.flatMapTo(mutableSetOf()) { it.triggerFamilies }
            .filterTo(mutableSetOf()) { it.needsMonitoringService }
        val previous = _monitoredFamilies.getAndUpdate { families }
        // A running service follows monitoredFamilies itself; only start it when monitoring begins
        // (ensureMonitoring() recovers if a start was refused while in the background).
        when {
            families.isEmpty() -> stopService()
            previous.isEmpty() -> startService()
            else -> Unit
        }
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, AutomationMonitorService::class.java))
            AppNotifications.cancelResumeMonitoring(context)
        } catch (e: IllegalStateException) {
            // Android 12+ refuses foreground service starts from the background in most cases.
            Log.w(TAG, "Monitoring service could not be started from the background", e)
            AppNotifications.showResumeMonitoring(context)
        }
    }

    private fun stopService() {
        context.stopService(Intent(context, AutomationMonitorService::class.java))
    }

    private companion object {
        const val TAG = "AutoFlow"
    }
}
