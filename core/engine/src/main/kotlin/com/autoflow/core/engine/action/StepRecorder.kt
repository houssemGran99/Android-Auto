package com.autoflow.core.engine.action

import com.autoflow.core.model.ActionResult
import com.autoflow.core.model.ExecutionStep
import com.autoflow.core.model.FailureKind
import com.autoflow.core.model.StepKind
import com.autoflow.core.model.StepStatus
import java.time.Clock

/** Collects the execution log of one run. */
class StepRecorder(private val clock: Clock) {
    private val steps = mutableListOf<ExecutionStep>()

    val all: List<ExecutionStep> get() = steps.toList()

    fun add(
        kind: StepKind,
        key: String,
        status: StepStatus,
        message: String = "",
        failureKind: FailureKind? = null,
    ) {
        steps += ExecutionStep(kind, key, status, message, failureKind, clock.millis())
    }

    fun addActionResult(key: String, result: ActionResult) = when (result) {
        is ActionResult.Success -> add(StepKind.ACTION, key, StepStatus.SUCCESS, result.message)
        is ActionResult.Fallback -> add(StepKind.ACTION, key, StepStatus.WARNING, result.message)
        is ActionResult.Failure -> add(StepKind.ACTION, key, StepStatus.FAILURE, result.message, result.kind)
        is ActionResult.Skipped -> add(StepKind.ACTION, key, StepStatus.SKIPPED, result.message)
    }
}
