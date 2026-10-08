package com.autoflow.core.engine.repository

import com.autoflow.core.model.Automation
import com.autoflow.core.model.ExecutionRecord
import com.autoflow.core.model.Variable
import kotlinx.coroutines.flow.Flow

interface AutomationRepository {
    fun observeAll(): Flow<List<Automation>>
    fun observe(id: String): Flow<Automation?>
    suspend fun getAll(): List<Automation>
    suspend fun getEnabled(): List<Automation>
    suspend fun get(id: String): Automation?
    suspend fun upsert(automation: Automation)
    suspend fun delete(id: String)
    suspend fun setEnabled(id: String, enabled: Boolean)
}

interface ExecutionRepository {
    fun observeRecent(limit: Int): Flow<List<ExecutionRecord>>
    fun observeForAutomation(automationId: String, limit: Int): Flow<List<ExecutionRecord>>
    fun observe(id: String): Flow<ExecutionRecord?>
    suspend fun record(record: ExecutionRecord)
    suspend fun clear()
}

interface VariableRepository {
    fun observeAll(): Flow<List<Variable>>
    suspend fun getAll(): List<Variable>
    suspend fun get(name: String): Variable?

    /** Creates or updates a variable. When [secret] is null an existing variable keeps its flag. */
    suspend fun set(name: String, value: String, secret: Boolean? = null)
    suspend fun delete(name: String)
}

/** Settings the engine needs at execution time. */
interface EngineSettings {
    /** Master switch: when false, no automation reacts to triggers. */
    suspend fun isMasterEnabled(): Boolean

    /** Whether runs whose conditions were not satisfied are written to history. */
    suspend fun logSkippedRuns(): Boolean
}
