package com.autoflow.app.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.CallSuper
import com.autoflow.platform.triggers.GeofenceScheduler
import com.autoflow.platform.triggers.TimeTriggerScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Hilt injects @AndroidEntryPoint receivers in super.onReceive(), which Kotlin cannot call on
 * the abstract BroadcastReceiver.onReceive; this concrete base makes the call possible.
 */
abstract class InjectingReceiver : BroadcastReceiver() {
    @CallSuper
    override fun onReceive(context: Context, intent: Intent) = Unit
}

/** Receives AlarmManager time triggers and hands them to WorkManager for execution. */
@AndroidEntryPoint
class TimeTriggerReceiver : InjectingReceiver() {
    @Inject lateinit var coordinator: TriggerCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != TimeTriggerScheduler.ACTION_TIME_TRIGGER) return
        val automationId = intent.getStringExtra(TimeTriggerScheduler.EXTRA_AUTOMATION_ID) ?: return
        val scheduledAt = intent.getLongExtra(TimeTriggerScheduler.EXTRA_SCHEDULED_AT, System.currentTimeMillis())
        AutomationRunWorker.enqueueTimeTrigger(context, automationId, scheduledAt)
        // Schedule the next occurrence; keep the receiver alive until the alarms are updated.
        val pending = goAsync()
        coordinator.refresh().invokeOnCompletion { pending.finish() }
    }
}

/** Re-creates alarms (cleared on reboot) and monitoring after boot, updates and clock changes. */
@AndroidEntryPoint
class BootReceiver : InjectingReceiver() {
    @Inject lateinit var coordinator: TriggerCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Exported receiver: ignore explicit intents carrying any other action.
        if (intent.action !in HANDLED_ACTIONS) return
        val pending = goAsync()
        coordinator.refresh().invokeOnCompletion { pending.finish() }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            android.location.LocationManager.PROVIDERS_CHANGED_ACTION,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            // AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (API 31)
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}

/** Receives geofence transitions from Google Play services and runs the matching location triggers. */
@AndroidEntryPoint
class GeofenceReceiver : InjectingReceiver() {
    @Inject lateinit var geofenceScheduler: GeofenceScheduler

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != GeofenceScheduler.ACTION_GEOFENCE) return
        GeofenceScheduler.parse(intent, geofenceScheduler::placeName).forEach { event ->
            AutomationRunWorker.enqueueLocationTransition(context, event)
        }
    }
}
