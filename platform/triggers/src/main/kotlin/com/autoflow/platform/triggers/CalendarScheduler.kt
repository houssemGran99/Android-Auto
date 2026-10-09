package com.autoflow.platform.triggers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import com.autoflow.core.model.Automation
import com.autoflow.core.model.Capability
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.TriggerSpec
import com.autoflow.platform.permissions.PermissionManager

/** One occurrence of a calendar event (recurring events are expanded by the provider). */
data class CalendarInstance(val title: String, val location: String, val begin: Long, val end: Long, val allDay: Boolean)

/**
 * Schedules an exact alarm at the next start (or end) of a calendar event matching each
 * calendar trigger. When an alarm fires, the app runs the trigger and calls [refresh] again,
 * which schedules the following occurrence. New or edited events are picked up by the
 * periodic refresh and whenever the app opens.
 */
class CalendarScheduler(
    context: Context,
    private val receiver: Class<out BroadcastReceiver>,
    private val permissions: PermissionManager,
    private val clock: () -> Long = System::currentTimeMillis,
    private val instances: (from: Long, to: Long) -> List<CalendarInstance> = { from, to -> queryInstances(context, from, to) },
) {
    private val context = context.applicationContext
    private val alarmManager = this.context.getSystemService(AlarmManager::class.java)
    private val prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Scheduled(val key: String, val fireAt: Long, val event: TriggerEvent.CalendarEvent)

    /** Returns what was scheduled; an empty result also covers "no calendar triggers" and "permission missing". */
    fun refresh(automations: List<Automation>): List<Scheduled> {
        val now = clock()
        val triggers = automations.filter { it.enabled }.flatMap { automation ->
            automation.triggers.mapIndexedNotNull { index, trigger ->
                when (trigger) {
                    is TriggerSpec.CalendarEventStart -> Triple(automation.id, index, trigger)
                    is TriggerSpec.CalendarEventEnd -> Triple(automation.id, index, trigger)
                    else -> null
                }
            }
        }
        val upcoming = if (triggers.isNotEmpty() && permissions.isSatisfied(Capability.CALENDAR)) {
            runCatching { instances(now - LOOKBACK_MS, now + LOOKAHEAD_MS) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val scheduled = triggers.mapNotNull { (automationId, index, trigger) ->
            nextOccurrence(trigger, upcoming, now)?.let { (fireAt, instance) ->
                val started = trigger is TriggerSpec.CalendarEventStart
                Scheduled(
                    key = key(automationId, index),
                    fireAt = fireAt,
                    event = TriggerEvent.CalendarEvent(automationId, index, started, instance.title, instance.location),
                )
            }
        }

        val editor = prefs.edit()
        (prefs.all.keys - scheduled.map { it.key }.toSet()).forEach { stale ->
            cancel(stale)
            editor.remove(stale)
        }
        scheduled.forEach { item ->
            setAlarm(item)
            editor.putLong(item.key, item.fireAt)
        }
        editor.apply()
        return scheduled
    }

    private fun setAlarm(item: Scheduled) {
        val intent = intent(item.key)
            .putExtra(EXTRA_AUTOMATION_ID, item.event.automationId)
            .putExtra(EXTRA_TRIGGER_INDEX, item.event.triggerIndex)
            .putExtra(EXTRA_STARTED, item.event.started)
            .putExtra(EXTRA_TITLE, item.event.title)
            .putExtra(EXTRA_LOCATION, item.event.location)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exact) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.fireAt, pendingIntent)
                return
            } catch (e: SecurityException) {
                // Permission revoked between the check and the call: fall back to an inexact alarm.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.fireAt, pendingIntent)
    }

    private fun cancel(key: String) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent(key),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun intent(key: String) = Intent(context, receiver)
        .setAction(ACTION_CALENDAR_TRIGGER)
        .setData(Uri.parse("autoflow://calendar-trigger/$key"))

    companion object {
        const val ACTION_CALENDAR_TRIGGER = "com.autoflow.action.CALENDAR_TRIGGER"
        private const val EXTRA_AUTOMATION_ID = "automation_id"
        private const val EXTRA_TRIGGER_INDEX = "trigger_index"
        private const val EXTRA_STARTED = "started"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_LOCATION = "location"
        private const val PREFS = "calendar_trigger_alarms"
        private const val LOOKBACK_MS = 24 * 60 * 60 * 1000L
        private const val LOOKAHEAD_MS = 8 * 24 * 60 * 60 * 1000L

        fun key(automationId: String, triggerIndex: Int) = "$automationId/$triggerIndex"

        /** Next start (or end) time strictly after [now] among events whose title matches the trigger filter. */
        fun nextOccurrence(trigger: TriggerSpec, instances: List<CalendarInstance>, now: Long): Pair<Long, CalendarInstance>? {
            val (filter, useEnd) = when (trigger) {
                is TriggerSpec.CalendarEventStart -> trigger.titleContains to false
                is TriggerSpec.CalendarEventEnd -> trigger.titleContains to true
                else -> return null
            }
            val needle = filter?.trim().orEmpty()
            return instances
                .filter { needle.isEmpty() || it.title.contains(needle, ignoreCase = true) }
                .map { (if (useEnd) it.end else it.begin) to it }
                .filter { (time, _) -> time > now }
                .minByOrNull { (time, _) -> time }
        }

        /** Rebuilds the event from the alarm intent, or null if it is not a calendar trigger intent. */
        fun parse(intent: Intent): TriggerEvent.CalendarEvent? {
            if (intent.action != ACTION_CALENDAR_TRIGGER) return null
            val automationId = intent.getStringExtra(EXTRA_AUTOMATION_ID) ?: return null
            return TriggerEvent.CalendarEvent(
                automationId = automationId,
                triggerIndex = intent.getIntExtra(EXTRA_TRIGGER_INDEX, -1),
                started = intent.getBooleanExtra(EXTRA_STARTED, true),
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                location = intent.getStringExtra(EXTRA_LOCATION).orEmpty(),
            )
        }

        private fun queryInstances(context: Context, from: Long, to: Long): List<CalendarInstance> {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .also { ContentUris.appendId(it, from) }
                .also { ContentUris.appendId(it, to) }
                .build()
            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
            )
            val result = mutableListOf<CalendarInstance>()
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    result += CalendarInstance(
                        title = cursor.getString(0).orEmpty(),
                        location = cursor.getString(1).orEmpty(),
                        begin = cursor.getLong(2),
                        end = cursor.getLong(3),
                        allDay = cursor.getInt(4) == 1,
                    )
                }
            }
            return result
        }
    }
}
