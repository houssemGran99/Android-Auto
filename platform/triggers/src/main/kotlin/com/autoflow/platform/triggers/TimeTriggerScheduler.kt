package com.autoflow.platform.triggers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.autoflow.core.engine.trigger.TimeScheduleCalculator
import com.autoflow.core.model.Automation
import com.autoflow.core.model.TriggerFamily
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Schedules time triggers with AlarmManager. Exact alarms are used when the user allowed
 * them (Android 12+); otherwise inexact "allow while idle" alarms that Android may defer.
 *
 * Scheduled fire times are persisted so that editing other automations does not reset
 * interval timers, and so stale alarms can be cancelled after process death.
 */
class TimeTriggerScheduler(
    context: Context,
    private val receiver: Class<out BroadcastReceiver>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val context = context.applicationContext
    private val alarmManager = this.context.getSystemService(AlarmManager::class.java)
    private val prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Result of a refresh, useful for diagnostics and tests. */
    data class Scheduled(val key: String, val automationId: String, val fireAt: Long, val exact: Boolean)

    /** Re-synchronizes alarms with [automations] (pass an empty list to cancel everything). */
    @Synchronized
    fun refresh(automations: List<Automation>): List<Scheduled> {
        val now = clock()
        val wanted = mutableMapOf<String, Pair<Automation, Int>>()
        automations.filter { it.enabled }.forEach { automation ->
            automation.triggers.forEachIndexed { index, trigger ->
                if (trigger.family == TriggerFamily.TIME) wanted[key(automation.id, index)] = automation to index
            }
        }

        val editor = prefs.edit()
        prefs.all.keys.filterNot { it in wanted }.forEach { stale ->
            cancel(stale)
            editor.remove(stale)
        }

        val result = wanted.mapNotNull { (key, value) ->
            val (automation, index) = value
            val trigger = automation.triggers[index]
            val signature = trigger.hashCode()
            val stored = prefs.getString(key, null)?.split(SEPARATOR)
            val storedTime = stored?.getOrNull(0)?.toLongOrNull()
            val storedSignature = stored?.getOrNull(1)?.toIntOrNull()
            val fireAt = if (storedTime != null && storedSignature == signature && storedTime > now) {
                storedTime
            } else {
                val after = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone())
                // Null only for sun events with no sunrise/sunset within a year (polar regions).
                TimeScheduleCalculator.nextFireTime(trigger, after)?.toInstant()?.toEpochMilli()
                    ?: return@mapNotNull null.also { cancel(key); editor.remove(key) }
            }
            editor.putString(key, "$fireAt$SEPARATOR$signature")
            val exact = setAlarm(key, automation.id, fireAt)
            Scheduled(key, automation.id, fireAt, exact)
        }
        editor.apply()
        return result
    }

    private fun setAlarm(key: String, automationId: String, fireAt: Long): Boolean {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent(key).putExtra(EXTRA_AUTOMATION_ID, automationId).putExtra(EXTRA_SCHEDULED_AT, fireAt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exact) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pendingIntent)
                return true
            } catch (e: SecurityException) {
                // Permission revoked between the check and the call: fall through to inexact.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pendingIntent)
        return false
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

    /** The data URI makes each alarm's PendingIntent distinct. */
    private fun intent(key: String) = Intent(context, receiver)
        .setAction(ACTION_TIME_TRIGGER)
        .setData(Uri.parse("autoflow://time-trigger/$key"))

    companion object {
        const val ACTION_TIME_TRIGGER = "com.autoflow.action.TIME_TRIGGER"
        const val EXTRA_AUTOMATION_ID = "automation_id"
        const val EXTRA_SCHEDULED_AT = "scheduled_at"
        private const val PREFS = "time_trigger_alarms"
        private const val SEPARATOR = "|"

        fun key(automationId: String, triggerIndex: Int) = "$automationId/$triggerIndex"
    }
}
