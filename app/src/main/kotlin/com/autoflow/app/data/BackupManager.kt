package com.autoflow.app.data

import com.autoflow.core.engine.repository.AutomationRepository
import com.autoflow.core.engine.repository.VariableRepository
import com.autoflow.core.model.Automation
import com.autoflow.core.model.AutomationBundle
import com.autoflow.core.model.AutomationJson
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(val automations: Int, val variables: Int)

/** Export / import / duplicate. Secret variables are never exported. */
@Singleton
class BackupManager @Inject constructor(
    private val automations: AutomationRepository,
    private val variables: VariableRepository,
) {
    suspend fun exportAll(): String = AutomationJson.encodeBundle(
        AutomationBundle(
            exportedAt = System.currentTimeMillis(),
            automations = automations.getAll(),
            variables = variables.getAll().filterNot { it.secret },
        ),
    )

    suspend fun exportOne(id: String): String? = automations.get(id)?.let {
        AutomationJson.encodeBundle(AutomationBundle(exportedAt = System.currentTimeMillis(), automations = listOf(it)))
    }

    /**
     * Imports a bundle. Automations always get new ids so imports never overwrite existing ones;
     * imported automations start disabled so nothing runs before the user reviews it.
     * Existing variables are not overwritten.
     * @throws com.autoflow.core.model.InvalidAutomationFileException for invalid content
     */
    suspend fun import(json: String): ImportResult {
        val bundle = AutomationJson.decodeBundle(json)
        bundle.automations.forEach { automation ->
            automations.upsert(automation.copy(id = UUID.randomUUID().toString(), enabled = false, createdAt = 0, updatedAt = 0))
        }
        var importedVariables = 0
        bundle.variables.filterNot { it.secret }.forEach { variable ->
            if (variables.get(variable.name) == null) {
                variables.set(variable.name, variable.value, secret = false)
                importedVariables++
            }
        }
        return ImportResult(bundle.automations.size, importedVariables)
    }

    suspend fun duplicate(id: String, copySuffix: String): Automation? {
        val original = automations.get(id) ?: return null
        val copy = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} $copySuffix",
            enabled = false,
            createdAt = 0,
            updatedAt = 0,
        )
        automations.upsert(copy)
        return copy
    }
}
