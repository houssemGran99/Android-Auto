package com.autoflow.platform.triggers

import android.content.Context
import com.autoflow.core.engine.condition.ConditionEnvironment
import com.autoflow.core.engine.condition.LeafConditionEvaluator
import com.autoflow.core.model.ConditionNode
import com.autoflow.platform.files.SafFolder

/** Evaluates "File exists" conditions; an unreadable folder makes the condition false. */
class FileConditionEvaluator(context: Context) : LeafConditionEvaluator<ConditionNode.FileExists> {
    private val appContext = context.applicationContext

    override fun evaluate(condition: ConditionNode.FileExists, environment: ConditionEnvironment): Boolean {
        val folder = SafFolder.parse(appContext, condition.folderUri) ?: return false
        val name = SafFolder.validFileName(condition.fileName) ?: return false
        if (!folder.hasAccess()) return false
        val exists = runCatching { folder.find(name) != null }.getOrNull() ?: return false
        return exists == condition.exists
    }
}
