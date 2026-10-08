package com.autoflow.platform.triggers

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autoflow.core.model.Automation
import com.autoflow.core.model.TimeOfDay
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.TriggerSpec
import com.autoflow.platform.triggers.source.BatteryTriggerSource
import com.autoflow.platform.triggers.source.PowerTriggerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TriggerSourcesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    class DummyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = Unit
    }

    private fun batteryIntent(level: Int) = Intent(Intent.ACTION_BATTERY_CHANGED)
        .putExtra(BatteryManager.EXTRA_LEVEL, level)
        .putExtra(BatteryManager.EXTRA_SCALE, 100)

    @Test
    fun batterySourceReportsChangesWithPreviousLevel() {
        val events = mutableListOf<TriggerEvent>()
        val source = BatteryTriggerSource(context)
        source.register { events += it }

        context.sendBroadcast(batteryIntent(21))
        context.sendBroadcast(batteryIntent(21))
        context.sendBroadcast(batteryIntent(19))
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(
            listOf(TriggerEvent.BatteryChanged(21, null), TriggerEvent.BatteryChanged(19, 21)),
            events,
        )
        source.unregister()
        context.sendBroadcast(batteryIntent(10))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(2, events.size)
    }

    @Test
    fun powerSourceReportsChargerChanges() {
        val events = mutableListOf<TriggerEvent>()
        val source = PowerTriggerSource(context)
        source.register { events += it }
        context.sendBroadcast(Intent(Intent.ACTION_POWER_CONNECTED))
        context.sendBroadcast(Intent(Intent.ACTION_POWER_DISCONNECTED))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(listOf(TriggerEvent.ChargerConnected, TriggerEvent.ChargerDisconnected), events)
        source.unregister()
    }

    @Test
    fun timeSchedulerSetsAlarmsAndCancelsStaleOnes() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 10, 7, 9, 30, 0, 0, zone).toInstant().toEpochMilli()
        val scheduler = TimeTriggerScheduler(context, DummyReceiver::class.java, clock = { now }, zone = { zone })
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val automation = Automation(
            id = "night",
            name = "Night",
            triggers = listOf(TriggerSpec.Time(TimeOfDay(23, 0)), TriggerSpec.Interval(15), TriggerSpec.ChargerConnected),
        )

        val scheduled = scheduler.refresh(listOf(automation))
        assertEquals(2, scheduled.size)
        assertEquals(ZonedDateTime.of(2026, 10, 7, 23, 0, 0, 0, zone).toInstant().toEpochMilli(), scheduled[0].fireAt)
        assertEquals(now + 15 * 60_000, scheduled[1].fireAt)
        assertEquals(2, shadowOf(alarmManager).scheduledAlarms.size)

        // Refreshing again keeps the same interval fire time.
        assertEquals(scheduled.map { it.fireAt }, scheduler.refresh(listOf(automation)).map { it.fireAt })

        scheduler.refresh(listOf(automation.copy(enabled = false)))
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }
}
