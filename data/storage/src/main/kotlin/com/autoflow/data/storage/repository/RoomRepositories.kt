package com.autoflow.data.storage.repository

import com.autoflow.core.engine.repository.AutomationRepository
import com.autoflow.core.engine.repository.ExecutionRepository
import com.autoflow.core.engine.repository.VariableRepository
import com.autoflow.core.model.Automation
import com.autoflow.core.model.AutomationJson
import com.autoflow.core.model.ExecutionRecord
import com.autoflow.core.model.ExecutionStatus
import com.autoflow.core.model.ExecutionStep
import com.autoflow.core.model.FailureKind
import com.autoflow.core.model.StepKind
import com.autoflow.core.model.StepStatus
import com.autoflow.core.model.Variable
import com.autoflow.data.storage.dao.AutomationDao
import com.autoflow.data.storage.dao.ExecutionDao
import com.autoflow.data.storage.dao.VariableDao
import com.autoflow.data.storage.entity.AutomationEntity
import com.autoflow.data.storage.entity.ExecutionEntity
import com.autoflow.data.storage.entity.ExecutionStepEntity
import com.autoflow.data.storage.entity.ExecutionWithSteps
import com.autoflow.data.storage.entity.VariableEntity
import com.autoflow.data.storage.security.SecretCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAutomationRepository(
    private val dao: AutomationDao,
    private val now: () -> Long = System::currentTimeMillis,
) : AutomationRepository {
    override fun observeAll(): Flow<List<Automation>> = dao.observeAll().map { list -> list.mapNotNull { it.toDomainOrNull() } }
    override fun observe(id: String): Flow<Automation?> = dao.observe(id).map { it?.toDomainOrNull() }
    override suspend fun getAll(): List<Automation> = dao.getAll().mapNotNull { it.toDomainOrNull() }
    override suspend fun getEnabled(): List<Automation> = dao.getEnabled().mapNotNull { it.toDomainOrNull() }
    override suspend fun get(id: String): Automation? = dao.get(id)?.toDomainOrNull()

    override suspend fun upsert(automation: Automation) {
        val timestamp = now()
        val existing = dao.get(automation.id)
        dao.upsert(
            automation.copy(
                createdAt = existing?.createdAt ?: automation.createdAt.takeIf { it > 0 } ?: timestamp,
                updatedAt = timestamp,
            ).toEntity(),
        )
    }

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun setEnabled(id: String, enabled: Boolean) = dao.setEnabled(id, enabled, now())
}

internal fun Automation.toEntity() = AutomationEntity(
    id = id,
    name = name,
    description = description,
    enabled = enabled,
    triggersJson = AutomationJson.encodeTriggers(triggers),
    conditionJson = AutomationJson.encodeCondition(condition),
    actionsJson = AutomationJson.encodeActions(actions),
    stopOnError = stopOnError,
    quickAction = quickAction,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Rows that cannot be decoded (e.g. written by a newer app version) are skipped instead of crashing. */
internal fun AutomationEntity.toDomainOrNull(): Automation? = try {
    Automation(
        id = id,
        name = name,
        description = description,
        enabled = enabled,
        triggers = AutomationJson.decodeTriggers(triggersJson),
        condition = AutomationJson.decodeCondition(conditionJson),
        actions = AutomationJson.decodeActions(actionsJson),
        stopOnError = stopOnError,
        quickAction = quickAction,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
} catch (e: IllegalArgumentException) {
    null
}

class RoomExecutionRepository(
    private val dao: ExecutionDao,
    private val keepRecords: Int = DEFAULT_KEEP,
) : ExecutionRepository {
    override fun observeRecent(limit: Int): Flow<List<ExecutionRecord>> = dao.observeRecent(limit).map { it.map(::toDomain) }

    override fun observeForAutomation(automationId: String, limit: Int): Flow<List<ExecutionRecord>> =
        dao.observeForAutomation(automationId, limit).map { it.map(::toDomain) }

    override fun observe(id: String): Flow<ExecutionRecord?> = dao.observe(id).map { it?.let(::toDomain) }

    override suspend fun record(record: ExecutionRecord) {
        val execution = ExecutionEntity(
            id = record.id,
            automationId = record.automationId,
            automationName = record.automationName,
            triggerKey = record.triggerKey,
            triggerDetail = record.triggerDetail,
            status = record.status.name,
            startedAt = record.startedAt,
            finishedAt = record.finishedAt,
        )
        val steps = record.steps.mapIndexed { index, step ->
            ExecutionStepEntity(
                executionId = record.id,
                position = index,
                kind = step.kind.name,
                key = step.key,
                status = step.status.name,
                message = step.message.take(MAX_MESSAGE_LENGTH),
                failureKind = step.failureKind?.name,
                timestamp = step.timestamp,
            )
        }
        dao.insert(execution, steps, keepRecords)
    }

    override suspend fun clear() = dao.clear()

    private fun toDomain(row: ExecutionWithSteps) = ExecutionRecord(
        id = row.execution.id,
        automationId = row.execution.automationId,
        automationName = row.execution.automationName,
        triggerKey = row.execution.triggerKey,
        triggerDetail = row.execution.triggerDetail,
        status = enumOrDefault(row.execution.status, ExecutionStatus.FAILED),
        startedAt = row.execution.startedAt,
        finishedAt = row.execution.finishedAt,
        steps = row.steps.sortedBy { it.position }.map { step ->
            ExecutionStep(
                kind = enumOrDefault(step.kind, StepKind.INFO),
                key = step.key,
                status = enumOrDefault(step.status, StepStatus.INFO),
                message = step.message,
                failureKind = step.failureKind?.let { enumOrDefault(it, FailureKind.ERROR) },
                timestamp = step.timestamp,
            )
        },
    )

    companion object {
        const val DEFAULT_KEEP = 1000
        private const val MAX_MESSAGE_LENGTH = 2000
    }
}

class RoomVariableRepository(
    private val dao: VariableDao,
    private val cipher: SecretCipher,
    private val now: () -> Long = System::currentTimeMillis,
) : VariableRepository {
    override fun observeAll(): Flow<List<Variable>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun getAll(): List<Variable> = dao.getAll().map { it.toDomain() }
    override suspend fun get(name: String): Variable? = dao.get(name)?.toDomain()

    override suspend fun set(name: String, value: String, secret: Boolean?) {
        require(Variable.isValidName(name)) { "Invalid variable name '$name'" }
        val isSecret = secret ?: dao.get(name)?.secret ?: false
        val stored = if (isSecret) cipher.encrypt(value) else value
        dao.upsert(VariableEntity(name, stored, isSecret, now()))
    }

    override suspend fun delete(name: String) = dao.delete(name)

    private fun VariableEntity.toDomain(): Variable {
        val plain = if (secret) runCatching { cipher.decrypt(value) }.getOrDefault("") else value
        return Variable(name, plain, secret, updatedAt)
    }
}

private inline fun <reified E : Enum<E>> enumOrDefault(name: String, default: E): E =
    runCatching { enumValueOf<E>(name) }.getOrDefault(default)
