package com.autoflow.core.engine

import com.autoflow.core.engine.condition.ConditionEnvironment
import com.autoflow.core.engine.repository.VariableRepository
import com.autoflow.core.engine.variable.BuiltInVariables
import com.autoflow.core.engine.variable.JsonPath
import com.autoflow.core.engine.variable.TemplateResolver
import com.autoflow.core.model.Automation
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.VariableScope
import java.time.Clock
import java.time.ZonedDateTime

/**
 * Per-execution state handed to conditions and actions: the triggering event,
 * a device-state snapshot and the variable scopes (local overlay on top of globals).
 */
class AutomationContext(
    val automation: Automation,
    val event: TriggerEvent,
    deviceState: DeviceState,
    globalVariables: Map<String, String>,
    private val variableRepository: VariableRepository,
    private val deviceStateProvider: DeviceStateProvider,
    private val clock: Clock,
) : ConditionEnvironment {
    override var deviceState: DeviceState = deviceState
        private set

    private val globals = globalVariables.toMutableMap()
    private val locals = mutableMapOf<String, String>()

    override val now: ZonedDateTime get() = ZonedDateTime.now(clock)

    /** Substitutes `%built-in%` and `$user` placeholders in [text]. */
    fun resolve(text: String): String = TemplateResolver.resolve(text, ::builtIn, ::userVariable)

    /**
     * Looks a variable up by name, as used in conditions: user/local variables first
     * (`counter`, `http.data.id`), then built-ins (`battery`).
     */
    override fun variable(name: String): String? {
        val trimmed = name.trim().removePrefix("$").removePrefix("%").removeSuffix("%")
        return userVariable(trimmed) ?: builtIn(trimmed.lowercase())
    }

    fun localVariables(): Map<String, String> = locals.toMap()

    suspend fun setVariable(name: String, value: String, scope: VariableScope = VariableScope.GLOBAL) {
        when (scope) {
            VariableScope.LOCAL -> locals[name] = value
            VariableScope.GLOBAL -> {
                locals.remove(name)
                globals[name] = value
                variableRepository.set(name, value)
            }
        }
    }

    /** Re-reads device state, e.g. before evaluating an If/Else after a delay. */
    suspend fun refreshDeviceState() {
        deviceState = deviceStateProvider.snapshot()
    }

    private fun builtIn(name: String): String? =
        BuiltInVariables.resolve(name, deviceState, now, automation.name, event)

    private fun userVariable(reference: String): String? {
        val segments = reference.split('.')
        val base = segments.first()
        val value = locals[base] ?: globals[base] ?: return null
        return if (segments.size == 1) value else JsonPath.extract(value, segments.drop(1))
    }
}
