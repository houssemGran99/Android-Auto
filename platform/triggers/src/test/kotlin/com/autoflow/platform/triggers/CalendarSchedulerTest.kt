package com.autoflow.platform.triggers

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autoflow.core.model.Automation
import com.autoflow.core.model.TriggerSpec
import com.autoflow.platform.permissions.PermissionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CalendarSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    class DummyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = Unit
    }

    private val now = 1_000_000L
    private val standup = CalendarInstance("Daily standup meeting", "Room 1", now + 60_000, now + 120_000, false)
    private val lunch = CalendarInstance("Lunch", "", now - 10_000, now + 30_000, false)

    @Test
    fun nextOccurrencePicksTheEarliestFutureMatchingEdge() {
        val events = listOf(standup, lunch)
        assertEquals(standup.begin to standup, CalendarScheduler.nextOccurrence(TriggerSpec.CalendarEventStart("MEETING"), events, now))
        // Lunch already started; its end is the next "end" edge.
        assertEquals(lunch.end to lunch, CalendarScheduler.nextOccurrence(TriggerSpec.CalendarEventEnd(), events, now))
        assertEquals(standup.begin, CalendarScheduler.nextOccurrence(TriggerSpec.CalendarEventStart(), events, now)?.first)
        assertNull(CalendarScheduler.nextOccurrence(TriggerSpec.CalendarEventStart("dentist"), events, now))
    }

    @Test
    fun refreshSchedulesAlarmsOnlyWithPermissionAndRoundTripsTheEvent() {
        val automation = Automation(
            id = "meet",
            name = "Meetings",
            triggers = listOf(TriggerSpec.CalendarEventStart("meeting"), TriggerSpec.CalendarEventEnd("meeting")),
        )
        val scheduler = CalendarScheduler(
            context,
            DummyReceiver::class.java,
            PermissionManager(context),
            clock = { now },
            instances = { _, _ -> listOf(standup, lunch) },
        )
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))

        shadowOf(context as Application).denyPermissions(Manifest.permission.READ_CALENDAR)
        assertTrue(scheduler.refresh(listOf(automation)).isEmpty())

        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_CALENDAR)
        val scheduled = scheduler.refresh(listOf(automation))
        assertEquals(listOf(standup.begin, standup.end), scheduled.map { it.fireAt })
        assertEquals(2, alarms.scheduledAlarms.size)

        val fired = alarms.scheduledAlarms.first { it.triggerAtTime == standup.begin }
        val event = CalendarScheduler.parse(shadowOf(fired.operation).savedIntent)
        assertEquals(scheduled.first().event, event)

        scheduler.refresh(listOf(automation.copy(enabled = false)))
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }
}
