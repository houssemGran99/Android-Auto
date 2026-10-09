package com.autoflow.core.engine

import com.autoflow.core.engine.trigger.SolarCalculator
import com.autoflow.core.engine.trigger.TimeScheduleCalculator
import com.autoflow.core.engine.variable.VariableOperations
import com.autoflow.core.model.SunEventType
import com.autoflow.core.model.TriggerSpec
import com.autoflow.core.model.VariableOp
import com.autoflow.core.model.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SolarAndOperationsTest {
    private val paris = ZoneId.of("Europe/Paris")

    private fun assertNear(expected: ZonedDateTime, actual: ZonedDateTime?, toleranceMinutes: Long = 3) {
        requireNotNull(actual)
        val diff = Duration.between(expected, actual).abs().toMinutes()
        assertTrue("expected $expected but was $actual", diff <= toleranceMinutes)
    }

    @Test
    fun `sunrise and sunset match published times`() {
        // Paris, 21 June 2026: sunrise 05:47, sunset 21:58 (CEST).
        val date = LocalDate.of(2026, 6, 21)
        assertNear(
            ZonedDateTime.of(date.atTime(5, 47), paris),
            SolarCalculator.eventTime(date, 48.8566, 2.3522, SunEventType.SUNRISE)?.atZone(paris),
        )
        assertNear(
            ZonedDateTime.of(date.atTime(21, 58), paris),
            SolarCalculator.eventTime(date, 48.8566, 2.3522, SunEventType.SUNSET)?.atZone(paris),
        )
        // New York, 21 December 2026: sunrise 07:17, sunset 16:32 (EST).
        val ny = ZoneId.of("America/New_York")
        val winter = LocalDate.of(2026, 12, 21)
        assertNear(ZonedDateTime.of(winter.atTime(7, 17), ny), SolarCalculator.eventTime(winter, 40.7128, -74.006, SunEventType.SUNRISE)?.atZone(ny))
        assertNear(ZonedDateTime.of(winter.atTime(16, 32), ny), SolarCalculator.eventTime(winter, 40.7128, -74.006, SunEventType.SUNSET)?.atZone(ny))
    }

    @Test
    fun `polar day has no sunset`() {
        assertNull(SolarCalculator.eventTime(LocalDate.of(2026, 6, 21), 69.65, 18.96, SunEventType.SUNSET))
    }

    @Test
    fun `next sun event applies offset, days and skips polar days`() {
        val now = ZonedDateTime.of(2026, 6, 21, 12, 0, 0, 0, paris) // Sunday
        val sunset = TriggerSpec.SunEvent(SunEventType.SUNSET, 48.8566, 2.3522, offsetMinutes = -30)
        assertNear(ZonedDateTime.of(2026, 6, 21, 21, 28, 0, 0, paris), TimeScheduleCalculator.nextFireTime(sunset, now))

        val mondaySunrise = TriggerSpec.SunEvent(SunEventType.SUNRISE, 48.8566, 2.3522, days = setOf(Weekday.MONDAY))
        val next = TimeScheduleCalculator.nextFireTime(mondaySunrise, now)!!
        assertEquals(22, next.dayOfMonth)

        // Tromsø: no sunset around midsummer, the next one is in late July.
        val tromso = TriggerSpec.SunEvent(SunEventType.SUNSET, 69.65, 18.96)
        val oslo = ZoneId.of("Europe/Oslo")
        val polar = TimeScheduleCalculator.nextFireTime(tromso, ZonedDateTime.of(2026, 6, 21, 12, 0, 0, 0, oslo))!!
        assertEquals(7, polar.monthValue)
    }

    @Test
    fun `variable operations`() {
        fun c(op: VariableOp, value: String, a1: String = "", a2: String = "") = VariableOperations.compute(op, value, a1, a2)
        assertEquals("11", c(VariableOp.INCREMENT, "10"))
        assertEquals("1", c(VariableOp.INCREMENT, ""))
        assertEquals("7.5", c(VariableOp.DECREMENT, "10", "2.5"))
        assertEquals("ab", c(VariableOp.APPEND, "a", "b"))
        assertEquals("x-y-z", c(VariableOp.REPLACE, "x y z", " ", "-"))
        assertEquals("HI", c(VariableOp.UPPERCASE, "hi"))
        assertEquals("hi", c(VariableOp.TRIM, "  hi "))
        assertEquals("ell", c(VariableOp.SUBSTRING, "hello", "1", "4"))
        assertEquals("llo", c(VariableOp.SUBSTRING, "hello", "2"))
        assertEquals("b", c(VariableOp.SPLIT, "a,b,c", ",", "1"))
        assertEquals("", c(VariableOp.SPLIT, "a,b,c", ",", "9"))
        assertEquals("1234", c(VariableOp.REGEX_EXTRACT, "Code: 1234 ok", "(\\d+)"))
        assertEquals("Code", c(VariableOp.REGEX_EXTRACT, "Code: 1234", "[A-Z]\\w+"))
        assertEquals("5", c(VariableOp.LENGTH, "hello"))
        assertEquals("a+b%26c", c(VariableOp.URL_ENCODE, "a b&c"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `increment of text fails`() {
        VariableOperations.compute(VariableOp.INCREMENT, "abc", "", "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid regex fails`() {
        VariableOperations.compute(VariableOp.REGEX_EXTRACT, "abc", "([", "")
    }
}
