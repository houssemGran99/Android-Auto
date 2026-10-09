package com.autoflow.app.runtime

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CoordinatorEntryPoint {
    fun coordinator(): TriggerCoordinator
}

/** Re-reads the calendar periodically so new or moved events get their alarms. */
class CalendarRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, CoordinatorEntryPoint::class.java)
            .coordinator()
            .refresh()
            .join()
        return Result.success()
    }

    companion object {
        private const val NAME = "calendar-refresh"

        fun schedule(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (enabled) {
                val request = PeriodicWorkRequestBuilder<CalendarRefreshWorker>(1, TimeUnit.HOURS).build()
                workManager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            } else {
                workManager.cancelUniqueWork(NAME)
            }
        }
    }
}
