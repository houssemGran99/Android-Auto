package com.autoflow.core.model

import kotlinx.serialization.Serializable

/**
 * An automation profile: WHEN any trigger fires, IF the condition tree is true,
 * THEN run the actions in order.
 */
@Serializable
data class Automation(
    val id: String,
    val name: String,
    val description: String = "",
    val enabled: Boolean = true,
    val triggers: List<TriggerSpec> = emptyList(),
    val condition: ConditionNode? = null,
    val actions: List<ActionSpec> = emptyList(),
    /** Stop the action pipeline at the first failing action. */
    val stopOnError: Boolean = false,
    /** Show as a quick action on the home screen and in the widget. */
    val quickAction: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    /** All capabilities required by triggers, conditions and actions (recursively). */
    val requiredCapabilities: Set<Capability>
        get() = buildSet {
            triggers.forEach { addAll(it.capabilities) }
            condition?.let { addAll(it.capabilities) }
            actions.forEach { addAll(it.capabilities) }
        }

    val triggerFamilies: Set<TriggerFamily> get() = triggers.mapTo(mutableSetOf()) { it.family }
}

/** Flattens nested control-flow actions depth-first. */
fun List<ActionSpec>.flattenActions(): List<ActionSpec> = flatMap { listOf(it) + it.children.flattenActions() }
