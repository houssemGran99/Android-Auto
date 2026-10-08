package com.autoflow.data.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Triggers, condition tree and actions are stored as versioned JSON (same codec as export),
 * because conditions and control-flow actions are recursive trees.
 */
@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val enabled: Boolean,
    @ColumnInfo(name = "triggers_json") val triggersJson: String,
    @ColumnInfo(name = "condition_json") val conditionJson: String?,
    @ColumnInfo(name = "actions_json") val actionsJson: String,
    @ColumnInfo(name = "stop_on_error") val stopOnError: Boolean,
    @ColumnInfo(name = "quick_action") val quickAction: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "variables")
data class VariableEntity(
    @PrimaryKey val name: String,
    /** Plain text, or ciphertext produced by SecretCipher when [secret] is true. */
    val value: String,
    val secret: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "executions",
    indices = [Index("automation_id"), Index("started_at")],
)
data class ExecutionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "automation_id") val automationId: String,
    @ColumnInfo(name = "automation_name") val automationName: String,
    @ColumnInfo(name = "trigger_key") val triggerKey: String,
    @ColumnInfo(name = "trigger_detail") val triggerDetail: String,
    val status: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long,
)

@Entity(
    tableName = "execution_steps",
    foreignKeys = [
        ForeignKey(
            entity = ExecutionEntity::class,
            parentColumns = ["id"],
            childColumns = ["execution_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("execution_id")],
)
data class ExecutionStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "execution_id") val executionId: String,
    val position: Int,
    val kind: String,
    val key: String,
    val status: String,
    val message: String,
    @ColumnInfo(name = "failure_kind") val failureKind: String?,
    val timestamp: Long,
)

data class ExecutionWithSteps(
    @Embedded val execution: ExecutionEntity,
    @Relation(parentColumn = "id", entityColumn = "execution_id")
    val steps: List<ExecutionStepEntity>,
)
