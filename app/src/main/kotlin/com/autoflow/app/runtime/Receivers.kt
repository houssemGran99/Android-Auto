package com.autoflow.app.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.CallSuper
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
        // Schedule the next occurrence.
        coordinator.refresh()
    }
}

/** Re-creates alarms (cleared on reboot) and monitoring after boot, updates and clock changes. */
@AndroidEntryPoint
class BootReceiver : InjectingReceiver() {
    @Inject lateinit var coordinator: TriggerCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        coordinator.refresh()
    }
}
