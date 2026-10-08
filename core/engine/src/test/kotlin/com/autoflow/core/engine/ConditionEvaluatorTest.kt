package com.autoflow.core.engine

import com.autoflow.core.engine.condition.ConditionEnvironment
import com.autoflow.core.engine.condition.ConditionEvaluator
import com.autoflow.core.engine.condition.ConditionTrace
import com.autoflow.core.model.Comparison
import com.autoflow.core.model.ConditionNode
import com.autoflow.core.model.TimeOfDay
import com.autoflow.core.model.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ConditionEvaluatorTest {
    private val evaluator = ConditionEvaluator()

    // Wednesday 2026-10-07 09:30
    private fun env(
        state: DeviceState = DeviceState(),
        now: ZonedDateTime = ZonedDateTime.of(2026, 10, 7, 9, 30, 0, 0, ZoneId.of("UTC")),
        vars: Map<String, String> = emptyMap(),
    ) = object : ConditionEnvironment {
        override val deviceState = state
        override val now = now
        override fun variable(name: String) = vars[name]
    }

    @Test
    fun `time range inside, outside and across midnight`() {
        val office = ConditionNode.TimeRange(TimeOfDay(8, 0), TimeOfDay(18, 0))
        assertTrue(evaluator.evaluate(office, env()))
        val night = ConditionNode.TimeRange(TimeOfDay(22, 0), TimeOfDay(6, 0))
        assertFalse(evaluator.evaluate(night, env()))
        assertTrue(ConditionEvaluator.isInRange(TimeOfDay(23, 15), TimeOfDay(22, 0), TimeOfDay(6, 0)))
        assertTrue(ConditionEvaluator.isInRange(TimeOfDay(5, 59), TimeOfDay(22, 0), TimeOfDay(6, 0)))
        assertFalse(ConditionEvaluator.isInRange(TimeOfDay(6, 0), TimeOfDay(22, 0), TimeOfDay(6, 0)))
        assertFalse(ConditionEvaluator.isInRange(TimeOfDay(18, 0), TimeOfDay(8, 0), TimeOfDay(18, 0)))
    }

    @Test
    fun `and or not combine with short circuit`() {
        val state = DeviceState(batteryLevel = 50, wifiConnected = true, wifiSsid = "\"Office\"")
        val tree = ConditionNode.And(
            listOf(
                ConditionNode.WifiState(connected = true, ssid = "office"),
                ConditionNode.DaysOfWeek(Weekday.WORKDAYS),
                ConditionNode.Or(
                    listOf(
                        ConditionNode.BatteryLevel(Comparison.GREATER_THAN, 80),
                        ConditionNode.Not(ConditionNode.Charging(true)),
                    ),
                ),
            ),
        )
        // charging unknown -> Charging leaf false -> Not -> true
        assertTrue(evaluator.evaluate(tree, env(state)))
        val trace = mutableListOf<ConditionTrace>()
        assertFalse(evaluator.evaluate(tree, env(state.copy(wifiSsid = "Home")), trace))
        assertEquals("short-circuit stops after first false leaf", 1, trace.size)
    }

    @Test
    fun `empty groups add no constraint`() {
        assertTrue(evaluator.evaluate(ConditionNode.And(emptyList()), env()))
        assertTrue(evaluator.evaluate(ConditionNode.Or(emptyList()), env()))
    }

    @Test
    fun `unknown state is false`() {
        assertFalse(evaluator.evaluate(ConditionNode.BatteryLevel(Comparison.LESS_THAN, 100), env()))
        assertFalse(evaluator.evaluate(ConditionNode.BluetoothState(true), env()))
    }

    @Test
    fun `variable comparison numeric, text and regex`() {
        val e = env(vars = mapOf("counter" to "10", "name" to "John"))
        assertTrue(evaluator.evaluate(ConditionNode.VariableCompare("counter", Comparison.GREATER_OR_EQUAL, "9.5"), e))
        assertTrue(evaluator.evaluate(ConditionNode.VariableCompare("name", Comparison.EQUALS, "john"), e))
        assertTrue(evaluator.evaluate(ConditionNode.VariableCompare("name", Comparison.MATCHES_REGEX, "^J.h"), e))
        assertFalse(evaluator.evaluate(ConditionNode.VariableCompare("name", Comparison.MATCHES_REGEX, "(["), e))
        assertFalse(evaluator.evaluate(ConditionNode.VariableCompare("missing", Comparison.EQUALS, ""), e))
    }

    @Test
    fun `wifi not connected to specific network`() {
        val cond = ConditionNode.WifiState(connected = false, ssid = "Office")
        assertTrue(evaluator.evaluate(cond, env(DeviceState(wifiConnected = true, wifiSsid = "Home"))))
        assertFalse(evaluator.evaluate(cond, env(DeviceState(wifiConnected = true, wifiSsid = "Office"))))
        assertTrue(evaluator.evaluate(cond, env(DeviceState(wifiConnected = false))))
    }
}
