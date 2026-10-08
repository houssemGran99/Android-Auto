package com.autoflow.platform.triggers.source

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.autoflow.core.engine.trigger.TriggerSource
import com.autoflow.core.model.Capability
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.TriggerFamily
import com.autoflow.platform.permissions.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Detects the app in the foreground using UsageStatsManager (requires usage access).
 * Android has no broadcast for "app opened", so recent usage events are polled while the
 * screen is on. Polling stops when no automation uses this trigger.
 */
class AppUsageTriggerSource(
    context: Context,
    private val scope: CoroutineScope,
    private val permissions: PermissionManager,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
) : TriggerSource {
    override val family = TriggerFamily.APP

    private val appContext = context.applicationContext
    private val usageStats = appContext.getSystemService(UsageStatsManager::class.java)
    private val power = appContext.getSystemService(PowerManager::class.java)
    private var job: Job? = null

    override fun register(onEvent: (TriggerEvent) -> Unit) {
        unregister()
        job = scope.launch {
            var lastQuery = System.currentTimeMillis()
            var foreground: String? = null
            while (isActive) {
                delay(pollIntervalMs)
                if (!power.isInteractive || !permissions.isSatisfied(Capability.USAGE_ACCESS)) {
                    lastQuery = System.currentTimeMillis()
                    continue
                }
                val now = System.currentTimeMillis()
                val opened = latestResumedPackage(lastQuery, now)
                lastQuery = now
                if (opened != null && opened != foreground) {
                    foreground = opened
                    if (opened != appContext.packageName) onEvent(TriggerEvent.AppOpened(opened))
                }
            }
        }
    }

    override fun unregister() {
        job?.cancel()
        job = null
    }

    private fun latestResumedPackage(from: Long, to: Long): String? {
        val events = usageStats.queryEvents(from, to) ?: return null
        val event = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == resumedEventType()) latest = event.packageName
        }
        return latest
    }

    @Suppress("DEPRECATION")
    private fun resumedEventType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        UsageEvents.Event.ACTIVITY_RESUMED
    } else {
        UsageEvents.Event.MOVE_TO_FOREGROUND
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 2_000L
    }
}
