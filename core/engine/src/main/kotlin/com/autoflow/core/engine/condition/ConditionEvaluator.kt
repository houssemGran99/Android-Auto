package com.autoflow.core.engine.condition

import com.autoflow.core.engine.DeviceState
import com.autoflow.core.engine.normalizeSsid
import com.autoflow.core.model.Comparison
import com.autoflow.core.model.ConditionNode
import com.autoflow.core.model.TimeOfDay
import com.autoflow.core.model.Weekday
import com.autoflow.core.model.typeKey
import java.time.ZonedDateTime
import kotlin.reflect.KClass

/** What conditions can read. */
interface ConditionEnvironment {
    val deviceState: DeviceState
    val now: ZonedDateTime
    fun variable(name: String): String?
}

/** Result of one evaluated leaf, kept for the execution log. */
data class ConditionTrace(val key: String, val result: Boolean, val detail: String)

/** Extension point for leaf conditions that are not built into the engine. */
fun interface LeafConditionEvaluator<in C : ConditionNode> {
    fun evaluate(condition: C, environment: ConditionEnvironment): Boolean
}

/**
 * Evaluates condition trees with short-circuit AND / OR and NOT.
 * Empty groups evaluate to true (an empty group adds no constraint).
 * Unknown device state (e.g. permission missing) makes a leaf false.
 */
class ConditionEvaluator(
    private val customEvaluators: Map<KClass<out ConditionNode>, LeafConditionEvaluator<*>> = emptyMap(),
) {
    fun evaluate(
        node: ConditionNode,
        environment: ConditionEnvironment,
        trace: MutableList<ConditionTrace>? = null,
    ): Boolean = when (node) {
        is ConditionNode.And -> node.children.all { evaluate(it, environment, trace) }
        is ConditionNode.Or -> node.children.isEmpty() || node.children.any { evaluate(it, environment, trace) }
        is ConditionNode.Not -> !evaluate(node.child, environment, trace)
        else -> {
            val (result, detail) = evaluateLeaf(node, environment)
            trace?.add(ConditionTrace(node.typeKey, result, detail))
            result
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun evaluateLeaf(node: ConditionNode, env: ConditionEnvironment): Pair<Boolean, String> {
        (customEvaluators[node::class] as? LeafConditionEvaluator<ConditionNode>)?.let {
            return it.evaluate(node, env) to "custom"
        }
        val state = env.deviceState
        return when (node) {
            is ConditionNode.TimeRange -> {
                val now = TimeOfDay.of(env.now.toLocalTime())
                isInRange(now, node.start, node.end) to "now=$now range=${node.start}-${node.end}"
            }
            is ConditionNode.DaysOfWeek -> {
                val today = Weekday.of(env.now.dayOfWeek)
                (node.days.isEmpty() || today in node.days) to "today=$today"
            }
            is ConditionNode.BatteryLevel -> {
                val level = state.batteryLevel
                (level != null && compareNumbers(level.toDouble(), node.value.toDouble(), node.comparison)) to
                    "battery=${level ?: "unknown"}"
            }
            is ConditionNode.Charging ->
                (state.charging != null && state.charging == node.charging) to "charging=${state.charging ?: "unknown"}"
            is ConditionNode.WifiState -> {
                val connected = state.wifiConnected
                val ssid = normalizeSsid(state.wifiSsid)
                val wanted = normalizeSsid(node.ssid)
                val result = when {
                    connected == null -> false
                    !node.connected -> if (wanted == null) !connected else !(connected && ssid.equals(wanted, ignoreCase = true))
                    wanted == null -> connected
                    else -> connected && ssid.equals(wanted, ignoreCase = true)
                }
                result to "wifi=${connected ?: "unknown"} ssid=${ssid ?: "unknown"}"
            }
            is ConditionNode.BluetoothState ->
                (state.bluetoothEnabled != null && state.bluetoothEnabled == node.enabled) to
                    "bluetooth=${state.bluetoothEnabled ?: "unknown"}"
            is ConditionNode.HeadphonesState ->
                (state.headphonesConnected != null && state.headphonesConnected == node.connected) to
                    "headphones=${state.headphonesConnected ?: "unknown"}"
            is ConditionNode.VariableCompare -> {
                val actual = env.variable(node.variable)
                (actual != null && compareValues(actual, node.value, node.comparison)) to
                    "${node.variable}=${actual ?: "unset"}"
            }
            is ConditionNode.And, is ConditionNode.Or, is ConditionNode.Not -> error("Groups are not leaves")
        }
    }

    companion object {
        /** [start] inclusive, [end] exclusive; wraps around midnight when end < start; start == end means all day. */
        fun isInRange(now: TimeOfDay, start: TimeOfDay, end: TimeOfDay): Boolean = when {
            start == end -> true
            start < end -> now >= start && now < end
            else -> now >= start || now < end
        }

        fun compareValues(actual: String, expected: String, comparison: Comparison): Boolean {
            val a = actual.trim().toDoubleOrNull()
            val b = expected.trim().toDoubleOrNull()
            return when (comparison) {
                Comparison.CONTAINS -> actual.contains(expected, ignoreCase = true)
                Comparison.MATCHES_REGEX -> runCatching { Regex(expected).containsMatchIn(actual) }.getOrDefault(false)
                else -> if (a != null && b != null) {
                    compareNumbers(a, b, comparison)
                } else {
                    val order = actual.compareTo(expected, ignoreCase = true)
                    when (comparison) {
                        Comparison.EQUALS -> order == 0
                        Comparison.NOT_EQUALS -> order != 0
                        Comparison.LESS_THAN -> order < 0
                        Comparison.LESS_OR_EQUAL -> order <= 0
                        Comparison.GREATER_THAN -> order > 0
                        Comparison.GREATER_OR_EQUAL -> order >= 0
                        Comparison.CONTAINS, Comparison.MATCHES_REGEX -> error("handled above")
                    }
                }
            }
        }

        fun compareNumbers(a: Double, b: Double, comparison: Comparison): Boolean = when (comparison) {
            Comparison.EQUALS -> a == b
            Comparison.NOT_EQUALS -> a != b
            Comparison.LESS_THAN -> a < b
            Comparison.LESS_OR_EQUAL -> a <= b
            Comparison.GREATER_THAN -> a > b
            Comparison.GREATER_OR_EQUAL -> a >= b
            Comparison.CONTAINS -> a.toString().contains(b.toString())
            Comparison.MATCHES_REGEX -> false
        }
    }
}
