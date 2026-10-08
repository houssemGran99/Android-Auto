package com.autoflow.core.model

enum class ExecutionStatus {
    /** All actions succeeded. */
    SUCCESS,

    /** Ran, but at least one action failed or used a fallback. */
    PARTIAL,

    /** Nothing useful happened: every action failed or the pipeline aborted. */
    FAILED,

    /** Conditions were not satisfied (or the automation was already running). */
    SKIPPED,
}

enum class StepKind { TRIGGER, CONDITION, ACTION, INFO }

enum class StepStatus { SUCCESS, WARNING, FAILURE, SKIPPED, INFO }

enum class FailureKind { PERMISSION_DENIED, NOT_SUPPORTED, INVALID_CONFIGURATION, TIMEOUT, ERROR }

/**
 * One line of the execution log.
 * [key] identifies what the step refers to (action/condition/trigger type, e.g. "SET_VOLUME"),
 * [message] is technical detail useful for debugging.
 */
data class ExecutionStep(
    val kind: StepKind,
    val key: String,
    val status: StepStatus,
    val message: String = "",
    val failureKind: FailureKind? = null,
    val timestamp: Long,
)

data class ExecutionRecord(
    val id: String,
    val automationId: String,
    val automationName: String,
    val triggerKey: String,
    val triggerDetail: String,
    val status: ExecutionStatus,
    val startedAt: Long,
    val finishedAt: Long,
    val steps: List<ExecutionStep>,
) {
    val durationMs: Long get() = finishedAt - startedAt
}

/** Outcome of a single action. */
sealed interface ActionResult {
    val message: String

    data class Success(override val message: String = "") : ActionResult

    /** The action did something useful but not exactly what was requested (e.g. Android restriction). */
    data class Fallback(override val message: String) : ActionResult

    data class Failure(val kind: FailureKind, override val message: String) : ActionResult

    data class Skipped(override val message: String) : ActionResult
}
