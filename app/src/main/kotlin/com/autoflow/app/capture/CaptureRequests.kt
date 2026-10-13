package com.autoflow.app.capture

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

/** How a visible capture screen ended. */
sealed interface CaptureOutcome {
    data class Done(val message: String) : CaptureOutcome
    data class Failed(val message: String) : CaptureOutcome
    data object Cancelled : CaptureOutcome
}

/**
 * Hands the outcome of a capture screen back to the action that opened it (same process).
 * Screens opened later from a tap-to-open notification have no waiter; their outcome is dropped.
 */
object CaptureRequests {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<CaptureOutcome>>()

    fun create(): Pair<String, CompletableDeferred<CaptureOutcome>> {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<CaptureOutcome>()
        pending[id] = deferred
        return id to deferred
    }

    fun complete(id: String?, outcome: CaptureOutcome) {
        id?.let { pending.remove(it)?.complete(outcome) }
    }

    fun forget(id: String) {
        pending.remove(id)
    }
}
