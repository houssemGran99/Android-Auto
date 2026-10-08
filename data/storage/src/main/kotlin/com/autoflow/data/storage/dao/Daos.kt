package com.autoflow.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.autoflow.data.storage.entity.AutomationEntity
import com.autoflow.data.storage.entity.ExecutionEntity
import com.autoflow.data.storage.entity.ExecutionStepEntity
import com.autoflow.data.storage.entity.ExecutionWithSteps
import com.autoflow.data.storage.entity.VariableEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automations ORDER BY created_at ASC")
    fun observeAll(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations WHERE id = :id")
    fun observe(id: String): Flow<AutomationEntity?>

    @Query("SELECT * FROM automations ORDER BY created_at ASC")
    suspend fun getAll(): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE enabled = 1 ORDER BY created_at ASC")
    suspend fun getEnabled(): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun get(id: String): AutomationEntity?

    @Upsert
    suspend fun upsert(entity: AutomationEntity)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE automations SET enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long)
}

@Dao
interface VariableDao {
    @Query("SELECT * FROM variables ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<VariableEntity>>

    @Query("SELECT * FROM variables ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<VariableEntity>

    @Query("SELECT * FROM variables WHERE name = :name")
    suspend fun get(name: String): VariableEntity?

    @Upsert
    suspend fun upsert(entity: VariableEntity)

    @Query("DELETE FROM variables WHERE name = :name")
    suspend fun delete(name: String)
}

@Dao
interface ExecutionDao {
    @Transaction
    @Query("SELECT * FROM executions ORDER BY started_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ExecutionWithSteps>>

    @Transaction
    @Query("SELECT * FROM executions WHERE automation_id = :automationId ORDER BY started_at DESC LIMIT :limit")
    fun observeForAutomation(automationId: String, limit: Int): Flow<List<ExecutionWithSteps>>

    @Transaction
    @Query("SELECT * FROM executions WHERE id = :id")
    fun observe(id: String): Flow<ExecutionWithSteps?>

    @Insert
    suspend fun insertExecution(execution: ExecutionEntity)

    @Insert
    suspend fun insertSteps(steps: List<ExecutionStepEntity>)

    @Query("DELETE FROM executions WHERE id NOT IN (SELECT id FROM executions ORDER BY started_at DESC LIMIT :keep)")
    suspend fun prune(keep: Int)

    @Query("DELETE FROM executions")
    suspend fun clear()

    @Transaction
    suspend fun insert(execution: ExecutionEntity, steps: List<ExecutionStepEntity>, keep: Int) {
        insertExecution(execution)
        insertSteps(steps)
        prune(keep)
    }
}
