package com.autoflow.app.runtime

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.autoflow.core.engine.AutomationEngine
import com.autoflow.core.model.TriggerEvent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface EngineEntryPoint {
    fun engine(): AutomationEngine
}

/**
 * Runs an automation outside of any UI or service (time triggers, widget buttons).
 * Expedited when quota allows so it starts immediately even in Doze.
 */
class AutomationRunWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val automationId = inputData.getString(KEY_AUTOMATION_ID) ?: return Result.failure()
        val engine = EntryPointAccessors.fromApplication(applicationContext, EngineEntryPoint::class.java).engine()
        when (val source = inputData.getString(KEY_SOURCE)) {
            SOURCE_TIME -> engine.handleEvent(
                TriggerEvent.TimeAlarm(automationId, inputData.getLong(KEY_SCHEDULED_AT, System.currentTimeMillis())),
            )
            else -> engine.runById(automationId, TriggerEvent.Manual(source ?: SOURCE_MANUAL))
        }
        return Result.success()
    }

    /** Only used on Android 11 and lower, where expedited work runs as a foreground service. */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(AppNotifications.RUNNING_NOTIFICATION_ID, AppNotifications.runningNotification(applicationContext))

    companion object {
        private const val KEY_AUTOMATION_ID = "automation_id"
        private const val KEY_SOURCE = "source"
        private const val KEY_SCHEDULED_AT = "scheduled_at"
        private const val SOURCE_TIME = "time"
        private const val SOURCE_MANUAL = "manual"
        const val SOURCE_WIDGET = "widget"

        fun enqueueTimeTrigger(context: Context, automationId: String, scheduledAt: Long) =
            enqueue(context, workDataOf(KEY_AUTOMATION_ID to automationId, KEY_SOURCE to SOURCE_TIME, KEY_SCHEDULED_AT to scheduledAt))

        fun enqueueManual(context: Context, automationId: String, source: String) =
            enqueue(context, workDataOf(KEY_AUTOMATION_ID to automationId, KEY_SOURCE to source))

        private fun enqueue(context: Context, data: androidx.work.Data) {
            val request = OneTimeWorkRequestBuilder<AutomationRunWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
