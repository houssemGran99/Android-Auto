package com.autoflow.core.engine

import com.autoflow.core.engine.repository.AutomationRepository
import com.autoflow.core.engine.repository.EngineSettings
import com.autoflow.core.engine.repository.ExecutionRepository
import com.autoflow.core.engine.repository.VariableRepository
import com.autoflow.core.model.Automation
import com.autoflow.core.model.ExecutionRecord
import com.autoflow.core.model.Variable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeAutomationRepository(vararg initial: Automation) : AutomationRepository {
    val items = MutableStateFlow(initial.associateBy { it.id })

    override fun observeAll(): Flow<List<Automation>> = items.map { it.values.toList() }
    override fun observe(id: String): Flow<Automation?> = items.map { it[id] }
    override suspend fun getAll() = items.value.values.toList()
    override suspend fun getEnabled() = items.value.values.filter { it.enabled }
    override suspend fun get(id: String) = items.value[id]
    override suspend fun upsert(automation: Automation) {
        items.value = items.value + (automation.id to automation)
    }

    override suspend fun delete(id: String) {
        items.value = items.value - id
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        items.value[id]?.let { upsert(it.copy(enabled = enabled)) }
    }
}

class FakeExecutionRepository : ExecutionRepository {
    val records = MutableStateFlow<List<ExecutionRecord>>(emptyList())

    override fun observeRecent(limit: Int) = records.map { it.takeLast(limit) }
    override fun observeForAutomation(automationId: String, limit: Int) =
        records.map { list -> list.filter { it.automationId == automationId } }

    override fun observe(id: String) = records.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun record(record: ExecutionRecord) {
        records.value = records.value + record
    }

    override suspend fun clear() {
        records.value = emptyList()
    }
}

class FakeVariableRepository(vararg initial: Variable) : VariableRepository {
    val items = MutableStateFlow(initial.associateBy { it.name })

    override fun observeAll() = items.map { it.values.toList() }
    override suspend fun getAll() = items.value.values.toList()
    override suspend fun get(name: String) = items.value[name]
    override suspend fun set(name: String, value: String, secret: Boolean?) {
        val existing = items.value[name]
        items.value = items.value + (name to Variable(name, value, secret ?: existing?.secret ?: false))
    }

    override suspend fun delete(name: String) {
        items.value = items.value - name
    }
}

class FakeSettings(var master: Boolean = true, var logSkipped: Boolean = true) : EngineSettings {
    override suspend fun isMasterEnabled() = master
    override suspend fun logSkippedRuns() = logSkipped
}
