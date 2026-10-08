package com.autoflow.app.data

import com.autoflow.app.di.ApplicationScope
import com.autoflow.core.engine.AutomationEngine
import com.autoflow.core.model.Automation
import com.autoflow.core.model.ExecutionRecord
import com.autoflow.core.model.TriggerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import javax.inject.Inject
import javax.inject.Singleton

/** Runs automations on request from the UI in the application scope, so leaving the screen does not cancel them. */
@Singleton
class AutomationRunner @Inject constructor(
    private val engine: AutomationEngine,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun runSaved(id: String): Deferred<ExecutionRecord?> =
        scope.async { engine.runById(id, TriggerEvent.Manual(SOURCE_APP)) }

    /** Test run from the builder: ignores conditions so every action can be verified. */
    fun testRun(automation: Automation): Deferred<ExecutionRecord?> =
        scope.async { engine.execute(automation, TriggerEvent.Manual(SOURCE_TEST), evaluateConditions = false) }

    private companion object {
        const val SOURCE_APP = "app"
        const val SOURCE_TEST = "test"
    }
}
