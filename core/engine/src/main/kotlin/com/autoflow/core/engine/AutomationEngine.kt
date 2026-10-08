package com.autoflow.core.engine

import com.autoflow.core.engine.action.ActionPipeline
import com.autoflow.core.engine.action.PipelineResult
import com.autoflow.core.engine.action.StepRecorder
import com.autoflow.core.engine.condition.ConditionEvaluator
import com.autoflow.core.engine.condition.ConditionTrace
import com.autoflow.core.engine.repository.AutomationRepository
import com.autoflow.core.engine.repository.EngineSettings
import com.autoflow.core.engine.repository.ExecutionRepository
import com.autoflow.core.engine.repository.VariableRepository
import com.autoflow.core.engine.trigger.TriggerMatcher
import com.autoflow.core.model.Automation
import com.autoflow.core.model.ExecutionRecord
import com.autoflow.core.model.ExecutionStatus
import com.autoflow.core.model.StepKind
import com.autoflow.core.model.StepStatus
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.TriggerFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Orchestrates: trigger event → matching automations → conditions → action pipeline → history.
 *
 * The engine has no Android dependencies; platform modules feed it [TriggerEvent]s and
 * provide device state, action handlers and persistence.
 */
class AutomationEngine(
    private val automations: AutomationRepository,
    private val executions: ExecutionRepository,
    private val variables: VariableRepository,
    private val settings: EngineSettings,
    private val deviceStateProvider: DeviceStateProvider,
    private val pipeline: ActionPipeline,
    private val conditionEvaluator: ConditionEvaluator,
    private val triggerMatcher: TriggerMatcher = TriggerMatcher(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val logger: EngineLogger = EngineLogger.NONE,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** Runs every enabled automation that has a trigger matching [event]. Respects the master switch. */
    suspend fun handleEvent(event: TriggerEvent): List<ExecutionRecord> {
        if (!settings.isMasterEnabled()) {
            logger.debug("Master switch is off, ignoring ${event.key}")
            return emptyList()
        }
        val candidates = findMatching(event)
        if (candidates.isEmpty()) return emptyList()
        logger.debug("${event.key} matched ${candidates.size} automation(s)")
        return coroutineScope {
            candidates.map { automation -> async { execute(automation, event) } }.awaitAll()
        }.filterNotNull()
    }

    /** Explicit run (button, widget). Works even when the master switch is off because the user asked for it. */
    suspend fun runById(
        automationId: String,
        event: TriggerEvent = TriggerEvent.Manual("app"),
        evaluateConditions: Boolean = true,
    ): ExecutionRecord? {
        val automation = automations.get(automationId) ?: return null
        return execute(automation, event, evaluateConditions)
    }

    suspend fun findMatching(event: TriggerEvent): List<Automation> = when (event) {
        is TriggerEvent.TimeAlarm -> listOfNotNull(automations.get(event.automationId))
            .filter { it.enabled && TriggerFamily.TIME in it.triggerFamilies }
        else -> automations.getEnabled().filter { automation ->
            automation.triggers.any { triggerMatcher.matches(it, event) }
        }
    }

    /**
     * Executes [automation] for [event]. Returns null only when the run was skipped
     * and skipped runs are not logged.
     */
    suspend fun execute(
        automation: Automation,
        event: TriggerEvent,
        evaluateConditions: Boolean = true,
    ): ExecutionRecord? {
        val startedAt = clock.millis()
        val recorder = StepRecorder(clock)
        recorder.add(StepKind.TRIGGER, event.key, StepStatus.SUCCESS, describe(event))

        val lock = locks.computeIfAbsent(automation.id) { Mutex() }
        if (!lock.tryLock()) {
            recorder.add(StepKind.INFO, "ALREADY_RUNNING", StepStatus.SKIPPED, "Previous run still in progress")
            return finish(automation, event, startedAt, recorder, ExecutionStatus.SKIPPED, persist = true)
        }
        try {
            val context = createContext(automation, event)

            val condition = automation.condition
            if (evaluateConditions && condition != null) {
                val trace = mutableListOf<ConditionTrace>()
                val satisfied = conditionEvaluator.evaluate(condition, context, trace)
                trace.forEach {
                    recorder.add(StepKind.CONDITION, it.key, if (it.result) StepStatus.SUCCESS else StepStatus.FAILURE, it.detail)
                }
                if (!satisfied) {
                    recorder.add(StepKind.INFO, "CONDITIONS_NOT_MET", StepStatus.SKIPPED)
                    return finish(automation, event, startedAt, recorder, ExecutionStatus.SKIPPED, settings.logSkippedRuns())
                }
                recorder.add(StepKind.INFO, "CONDITIONS_MET", StepStatus.SUCCESS)
            }

            val result = try {
                pipeline.run(automation.actions, context, recorder, automation.stopOnError)
            } catch (e: CancellationException) {
                recorder.add(StepKind.INFO, "CANCELLED", StepStatus.FAILURE, "Execution was cancelled by the system")
                withContext(NonCancellable) {
                    finish(automation, event, startedAt, recorder, ExecutionStatus.FAILED, persist = true)
                }
                throw e
            }
            return finish(automation, event, startedAt, recorder, statusOf(result), persist = true)
        } finally {
            lock.unlock()
        }
    }

    private suspend fun createContext(automation: Automation, event: TriggerEvent): AutomationContext {
        val state = try {
            deviceStateProvider.snapshot()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Device state unavailable", e)
            DeviceState()
        }
        val globals = variables.getAll().associate { it.name to it.value }
        return AutomationContext(automation, event, state, globals, variables, deviceStateProvider, clock)
    }

    private suspend fun finish(
        automation: Automation,
        event: TriggerEvent,
        startedAt: Long,
        recorder: StepRecorder,
        status: ExecutionStatus,
        persist: Boolean,
    ): ExecutionRecord? {
        val record = ExecutionRecord(
            id = idGenerator(),
            automationId = automation.id,
            automationName = automation.name,
            triggerKey = event.key,
            triggerDetail = describe(event),
            status = status,
            startedAt = startedAt,
            finishedAt = clock.millis(),
            steps = recorder.all,
        )
        if (!persist) return null
        try {
            executions.record(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Could not store execution history", e)
        }
        logger.debug("${automation.name}: $status in ${record.durationMs} ms")
        return record
    }

    companion object {
        fun statusOf(result: PipelineResult): ExecutionStatus = when {
            result.failed == 0 && result.warnings == 0 -> ExecutionStatus.SUCCESS
            result.failed > 0 && result.succeeded == 0 && result.warnings == 0 -> ExecutionStatus.FAILED
            else -> ExecutionStatus.PARTIAL
        }

        fun describe(event: TriggerEvent): String =
            event.details.filterValues { it.isNotEmpty() }.entries.joinToString { "${it.key}=${it.value}" }
    }
}
