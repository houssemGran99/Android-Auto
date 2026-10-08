package com.autoflow.core.engine.action

import com.autoflow.core.engine.AutomationContext
import com.autoflow.core.engine.EngineLogger
import com.autoflow.core.engine.condition.ConditionEvaluator
import com.autoflow.core.engine.condition.ConditionTrace
import com.autoflow.core.engine.variable.ExpressionEvaluator
import com.autoflow.core.engine.variable.ExpressionException
import com.autoflow.core.model.ActionResult
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.FailureKind
import com.autoflow.core.model.StepKind
import com.autoflow.core.model.StepStatus
import com.autoflow.core.model.Variable
import com.autoflow.core.model.typeKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext

/** Counts of action outcomes for one run. */
data class PipelineResult(
    val succeeded: Int = 0,
    val warnings: Int = 0,
    val failed: Int = 0,
    val aborted: Boolean = false,
) {
    operator fun plus(other: PipelineResult) = PipelineResult(
        succeeded + other.succeeded,
        warnings + other.warnings,
        failed + other.failed,
        aborted || other.aborted,
    )
}

/**
 * Runs actions sequentially. Control-flow actions (delay, variables, if/else, repeat)
 * are interpreted here; everything else is dispatched to the [ActionRegistry].
 */
class ActionPipeline(
    private val registry: ActionRegistry,
    private val conditionEvaluator: ConditionEvaluator,
    private val logger: EngineLogger = EngineLogger.NONE,
    private val actionTimeoutMs: Long = DEFAULT_ACTION_TIMEOUT_MS,
) {
    suspend fun run(
        actions: List<ActionSpec>,
        context: AutomationContext,
        recorder: StepRecorder,
        stopOnError: Boolean,
        depth: Int = 0,
    ): PipelineResult {
        var total = PipelineResult()
        for (action in actions) {
            coroutineContext.ensureActive()
            val result = runAction(action, context, recorder, stopOnError, depth)
            total += result
            if (result.aborted || (stopOnError && result.failed > 0)) {
                recorder.add(StepKind.INFO, "ABORTED", StepStatus.INFO, "Stopped after failing action")
                return total.copy(aborted = true)
            }
        }
        return total
    }

    private suspend fun runAction(
        action: ActionSpec,
        context: AutomationContext,
        recorder: StepRecorder,
        stopOnError: Boolean,
        depth: Int,
    ): PipelineResult {
        if (depth > MAX_DEPTH) {
            return record(recorder, action, ActionResult.Failure(FailureKind.INVALID_CONFIGURATION, "Nesting deeper than $MAX_DEPTH"))
        }
        return when (action) {
            is ActionSpec.Delay -> {
                delay(action.durationMs)
                record(recorder, action, ActionResult.Success("${action.durationMs} ms"))
            }
            is ActionSpec.SetVariable -> record(recorder, action, setVariable(action, context))
            is ActionSpec.IfElse -> {
                context.refreshDeviceStateSafely()
                val trace = mutableListOf<ConditionTrace>()
                val matched = conditionEvaluator.evaluate(action.condition, context, trace)
                recorder.add(
                    StepKind.CONDITION,
                    action.typeKey,
                    StepStatus.INFO,
                    (if (matched) "then" else "else") + trace.joinToString(prefix = " [", postfix = "]") { "${it.key}:${it.detail}" },
                )
                val branch = if (matched) action.thenActions else action.elseActions
                run(branch, context, recorder, stopOnError, depth + 1)
            }
            is ActionSpec.Repeat -> {
                var total = PipelineResult()
                for (index in 0 until action.times) {
                    context.setVariable(LOOP_VARIABLE, (index + 1).toString(), com.autoflow.core.model.VariableScope.LOCAL)
                    val iteration = run(action.actions, context, recorder, stopOnError, depth + 1)
                    total += iteration
                    if (iteration.aborted) break
                }
                total
            }
            else -> record(recorder, action, dispatch(action, context))
        }
    }

    private suspend fun dispatch(action: ActionSpec, context: AutomationContext): ActionResult {
        val handler = registry.handlerFor(action)
            ?: return ActionResult.Failure(FailureKind.NOT_SUPPORTED, "No handler for ${action.typeKey} on this device")
        return try {
            withTimeout(actionTimeoutMs) { handler.execute(action, context) }
        } catch (e: TimeoutCancellationException) {
            ActionResult.Failure(FailureKind.TIMEOUT, "Timed out after ${actionTimeoutMs / 1000} s")
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            logger.warn("Permission denied for ${action.typeKey}", e)
            ActionResult.Failure(FailureKind.PERMISSION_DENIED, e.message ?: "Permission denied")
        } catch (e: Exception) {
            logger.warn("Action ${action.typeKey} failed", e)
            ActionResult.Failure(FailureKind.ERROR, e.message ?: e::class.simpleName.orEmpty())
        }
    }

    private suspend fun setVariable(action: ActionSpec.SetVariable, context: AutomationContext): ActionResult {
        if (!Variable.isValidName(action.name)) {
            return ActionResult.Failure(FailureKind.INVALID_CONFIGURATION, "Invalid variable name '${action.name}'")
        }
        val resolved = context.resolve(action.value)
        val value = if (action.evaluateMath) {
            try {
                ExpressionEvaluator.format(ExpressionEvaluator.evaluate(resolved))
            } catch (e: ExpressionException) {
                return ActionResult.Failure(FailureKind.INVALID_CONFIGURATION, "Cannot evaluate '$resolved': ${e.message}")
            }
        } else {
            resolved
        }
        context.setVariable(action.name, value, action.scope)
        return ActionResult.Success("${action.name} = $value")
    }

    private suspend fun AutomationContext.refreshDeviceStateSafely() {
        try {
            refreshDeviceState()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Could not refresh device state", e)
        }
    }

    private fun record(recorder: StepRecorder, action: ActionSpec, result: ActionResult): PipelineResult {
        recorder.addActionResult(action.typeKey, result)
        return when (result) {
            is ActionResult.Success -> PipelineResult(succeeded = 1)
            is ActionResult.Fallback -> PipelineResult(warnings = 1)
            is ActionResult.Failure -> PipelineResult(failed = 1)
            is ActionResult.Skipped -> PipelineResult()
        }
    }

    companion object {
        const val DEFAULT_ACTION_TIMEOUT_MS = 120_000L
        const val MAX_DEPTH = 16
        const val LOOP_VARIABLE = "loop"
    }
}
