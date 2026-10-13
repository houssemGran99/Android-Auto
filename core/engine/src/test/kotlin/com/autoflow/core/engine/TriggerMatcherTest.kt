package com.autoflow.core.engine

import com.autoflow.core.engine.trigger.TimeScheduleCalculator
import com.autoflow.core.engine.trigger.TriggerMatcher
import com.autoflow.core.model.HeadphoneKind
import com.autoflow.core.model.ThresholdDirection
import com.autoflow.core.model.TimeOfDay
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.TriggerSpec
import com.autoflow.core.model.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TriggerMatcherTest {
    private val matcher = TriggerMatcher()

    @Test
    fun `wifi ssid matching ignores quotes and case`() {
        assertTrue(matcher.matches(TriggerSpec.WifiConnected("Office-WiFi"), TriggerEvent.WifiConnected("\"office-wifi\"")))
        assertTrue(matcher.matches(TriggerSpec.WifiConnected(null), TriggerEvent.WifiConnected(null)))
        assertFalse(matcher.matches(TriggerSpec.WifiConnected("Office"), TriggerEvent.WifiConnected("<unknown ssid>")))
        assertFalse(matcher.matches(TriggerSpec.WifiConnected("Office"), TriggerEvent.WifiDisconnected("Office")))
    }

    @Test
    fun `battery fires only when crossing threshold`() {
        val below20 = TriggerSpec.BatteryLevel(20, ThresholdDirection.BELOW)
        assertTrue(matcher.matches(below20, TriggerEvent.BatteryChanged(19, 20)))
        assertFalse(matcher.matches(below20, TriggerEvent.BatteryChanged(18, 19)))
        assertFalse(matcher.matches(below20, TriggerEvent.BatteryChanged(15, null)))
        val above80 = TriggerSpec.BatteryLevel(80, ThresholdDirection.ABOVE)
        assertTrue(matcher.matches(above80, TriggerEvent.BatteryChanged(81, 80)))
        assertFalse(matcher.matches(above80, TriggerEvent.BatteryChanged(80, 79)))
    }

    @Test
    fun `headphones kind and bluetooth device`() {
        assertTrue(matcher.matches(TriggerSpec.HeadphonesConnected(), TriggerEvent.HeadphonesConnected(HeadphoneKind.BLUETOOTH, "Buds")))
        assertFalse(
            matcher.matches(
                TriggerSpec.HeadphonesConnected(HeadphoneKind.WIRED),
                TriggerEvent.HeadphonesConnected(HeadphoneKind.BLUETOOTH, "Buds"),
            ),
        )
        assertTrue(matcher.matches(TriggerSpec.BluetoothConnected("car kit"), TriggerEvent.BluetoothConnected("Car Kit", "AA:BB")))
        assertTrue(matcher.matches(TriggerSpec.BluetoothConnected("AA:BB"), TriggerEvent.BluetoothConnected(null, "aa:bb")))
        assertTrue(matcher.matches(TriggerSpec.AppOpened("com.spotify.music"), TriggerEvent.AppOpened("com.spotify.music")))
        assertTrue(matcher.matches(TriggerSpec.ChargerConnected, TriggerEvent.ChargerConnected))
    }

    @Test
    fun `next fire time respects days and rolls over`() {
        val zone = ZoneId.of("Europe/Paris")
        // Wednesday 2026-10-07 09:30
        val now = ZonedDateTime.of(2026, 10, 7, 9, 30, 0, 0, zone)
        val daily = TriggerSpec.Time(TimeOfDay(7, 30))
        assertEquals(ZonedDateTime.of(2026, 10, 8, 7, 30, 0, 0, zone), TimeScheduleCalculator.nextFireTime(daily, now))
        val laterToday = TriggerSpec.Time(TimeOfDay(23, 0))
        assertEquals(ZonedDateTime.of(2026, 10, 7, 23, 0, 0, 0, zone), TimeScheduleCalculator.nextFireTime(laterToday, now))
        val mondays = TriggerSpec.Time(TimeOfDay(8, 0), setOf(Weekday.MONDAY))
        assertEquals(ZonedDateTime.of(2026, 10, 12, 8, 0, 0, 0, zone), TimeScheduleCalculator.nextFireTime(mondays, now))
        val sameMinute = TriggerSpec.Time(TimeOfDay(9, 30))
        assertEquals(ZonedDateTime.of(2026, 10, 8, 9, 30, 0, 0, zone), TimeScheduleCalculator.nextFireTime(sameMinute, now))
        val interval = TriggerSpec.Interval(30)
        assertEquals(ZonedDateTime.of(2026, 10, 7, 10, 0, 0, 0, zone), TimeScheduleCalculator.nextFireTime(interval, now.plusSeconds(20)))
    }

    @Test
    fun `next fire time across DST gap`() {
        val zone = ZoneId.of("Europe/Paris")
        // DST starts 2026-03-29 02:00 -> 03:00
        val now = ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, zone)
        val next = TimeScheduleCalculator.nextFireTime(TriggerSpec.Time(TimeOfDay(2, 30)), now)!!
        assertEquals(29, next.dayOfMonth)
        assertEquals(3, next.hour)
    }

    @Test
    fun `media playback start and stop`() {
        val matcher = TriggerMatcher()
        assertTrue(matcher.matches(TriggerSpec.MediaStarted, TriggerEvent.MediaPlaybackChanged(playing = true)))
        assertFalse(matcher.matches(TriggerSpec.MediaStarted, TriggerEvent.MediaPlaybackChanged(playing = false)))
        assertTrue(matcher.matches(TriggerSpec.MediaStopped, TriggerEvent.MediaPlaybackChanged(playing = false)))
        assertFalse(matcher.matches(TriggerSpec.MediaStopped, TriggerEvent.ChargerConnected))
    }

    @Test
    fun `file added in watched folder with name filter`() {
        val matcher = TriggerMatcher()
        val spec = TriggerSpec.FileAdded("content://tree/a", nameContains = "invoice")
        assertTrue(matcher.matches(spec, TriggerEvent.FileAdded("content://tree/a", "Invoice-2026.pdf")))
        assertFalse(matcher.matches(spec, TriggerEvent.FileAdded("content://tree/a", "photo.jpg")))
        assertFalse(matcher.matches(spec, TriggerEvent.FileAdded("content://tree/b", "invoice.pdf")))
        assertTrue(matcher.matches(TriggerSpec.FileAdded("content://tree/b"), TriggerEvent.FileAdded("content://tree/b", "x")))
    }

    @Test
    fun `screen, shake and flip`() {
        val matcher = TriggerMatcher()
        assertTrue(matcher.matches(TriggerSpec.ScreenOn, TriggerEvent.ScreenChanged(on = true)))
        assertFalse(matcher.matches(TriggerSpec.ScreenOn, TriggerEvent.ScreenChanged(on = false)))
        assertTrue(matcher.matches(TriggerSpec.ScreenOff, TriggerEvent.ScreenChanged(on = false)))
        assertTrue(matcher.matches(TriggerSpec.DeviceUnlocked, TriggerEvent.DeviceUnlocked))
        val gentle = TriggerEvent.Shaken(peakG = 2.0f)
        assertTrue(matcher.matches(TriggerSpec.Shake(com.autoflow.core.model.ShakeSensitivity.HIGH), gentle))
        assertFalse(matcher.matches(TriggerSpec.Shake(com.autoflow.core.model.ShakeSensitivity.LOW), gentle))
        assertTrue(matcher.matches(TriggerSpec.Shake(com.autoflow.core.model.ShakeSensitivity.LOW), TriggerEvent.Shaken(3.5f)))
        val down = TriggerEvent.Flipped(com.autoflow.core.model.FlipPosition.FACE_DOWN)
        assertTrue(matcher.matches(TriggerSpec.Flip(com.autoflow.core.model.FlipPosition.FACE_DOWN), down))
        assertFalse(matcher.matches(TriggerSpec.Flip(com.autoflow.core.model.FlipPosition.FACE_UP), down))
    }
}
