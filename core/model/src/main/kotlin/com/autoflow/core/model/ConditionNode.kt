package com.autoflow.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Comparison { EQUALS, NOT_EQUALS, LESS_THAN, LESS_OR_EQUAL, GREATER_THAN, GREATER_OR_EQUAL, CONTAINS, MATCHES_REGEX }

/**
 * A boolean condition tree. Groups ([And], [Or], [Not]) combine leaves.
 * Leaves read device state, time or variables at evaluation time.
 */
@Serializable
sealed interface ConditionNode {
    val capabilities: Set<Capability> get() = emptySet()

    @Serializable
    @SerialName("AND")
    data class And(val children: List<ConditionNode>) : ConditionNode {
        override val capabilities get() = children.flatMapTo(mutableSetOf()) { it.capabilities }
    }

    @Serializable
    @SerialName("OR")
    data class Or(val children: List<ConditionNode>) : ConditionNode {
        override val capabilities get() = children.flatMapTo(mutableSetOf()) { it.capabilities }
    }

    @Serializable
    @SerialName("NOT")
    data class Not(val child: ConditionNode) : ConditionNode {
        override val capabilities get() = child.capabilities
    }

    /** True between [start] (inclusive) and [end] (exclusive). Ranges may wrap midnight (22:00-06:00). */
    @Serializable
    @SerialName("TIME_RANGE")
    data class TimeRange(val start: TimeOfDay, val end: TimeOfDay) : ConditionNode

    @Serializable
    @SerialName("DAYS_OF_WEEK")
    data class DaysOfWeek(val days: Set<Weekday>) : ConditionNode

    @Serializable
    @SerialName("BATTERY_LEVEL")
    data class BatteryLevel(val comparison: Comparison, val value: Int) : ConditionNode

    @Serializable
    @SerialName("CHARGING")
    data class Charging(val charging: Boolean = true) : ConditionNode

    /** Wi-Fi connection state; with [ssid] it requires being connected to that network. */
    @Serializable
    @SerialName("WIFI_STATE")
    data class WifiState(val connected: Boolean = true, val ssid: String? = null) : ConditionNode {
        override val capabilities: Set<Capability>
            get() = if (ssid.isNullOrBlank()) emptySet() else setOf(Capability.LOCATION, Capability.BACKGROUND_LOCATION)
    }

    @Serializable
    @SerialName("BLUETOOTH_STATE")
    data class BluetoothState(val enabled: Boolean = true) : ConditionNode

    @Serializable
    @SerialName("HEADPHONES_STATE")
    data class HeadphonesState(val connected: Boolean = true) : ConditionNode

    /** Compares a variable (name without prefix, e.g. "counter" or "battery") with [value]. */
    @Serializable
    @SerialName("VARIABLE")
    data class VariableCompare(
        val variable: String,
        val comparison: Comparison,
        val value: String,
    ) : ConditionNode
}

val ConditionNode.isGroup: Boolean
    get() = this is ConditionNode.And || this is ConditionNode.Or || this is ConditionNode.Not
