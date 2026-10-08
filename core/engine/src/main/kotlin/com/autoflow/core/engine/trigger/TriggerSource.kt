package com.autoflow.core.engine.trigger

import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.TriggerFamily

/**
 * Observes one family of device events and reports them as [TriggerEvent]s.
 * Implementations are platform specific; new trigger families plug in by adding a source.
 */
interface TriggerSource {
    val family: TriggerFamily

    /** Starts observing. Must be idempotent-safe: callers call [unregister] before registering again. */
    fun register(onEvent: (TriggerEvent) -> Unit)

    fun unregister()
}
