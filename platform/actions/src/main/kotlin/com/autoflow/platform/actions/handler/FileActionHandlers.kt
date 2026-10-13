package com.autoflow.platform.actions.handler

import android.content.Context
import com.autoflow.core.engine.AutomationContext
import com.autoflow.core.engine.action.ActionHandler
import com.autoflow.core.model.ActionResult
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.FailureKind
import com.autoflow.core.model.Variable
import com.autoflow.platform.files.SafFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opens a picked folder or explains why it cannot be used. */
private fun openFolder(context: Context, folderUri: String, write: Boolean): Pair<SafFolder?, ActionResult.Failure?> {
    val folder = SafFolder.parse(context, folderUri)
        ?: return null to ActionResult.Failure(FailureKind.INVALID_CONFIGURATION, "No folder selected")
    if (!folder.hasAccess(write)) {
        val what = if (write) "write access to" else "access to"
        return null to ActionResult.Failure(FailureKind.PERMISSION_DENIED, "AutoFlow lost $what the folder; pick the folder again")
    }
    return folder to null
}

private fun fileName(context: AutomationContext, raw: String): String? = SafFolder.validFileName(context.resolve(raw))

private fun invalidName(raw: String) =
    ActionResult.Failure(FailureKind.INVALID_CONFIGURATION, "'$raw' is not a valid file name (no / allowed)")

class WriteFileActionHandler(private val context: Context) : ActionHandler<ActionSpec.WriteFile> {
    override suspend fun execute(action: ActionSpec.WriteFile, context: AutomationContext): ActionResult {
        val (folder, error) = openFolder(this.context, action.folderUri, write = true)
        if (folder == null) return error!!
        val name = fileName(context, action.fileName) ?: return invalidName(action.fileName)
        val content = context.resolve(action.content)
        withContext(Dispatchers.IO) { folder.writeText(name, content, action.append) }
        return ActionResult.Success("${if (action.append) "Appended" else "Wrote"} ${content.length} chars to $name")
    }
}

class ReadFileActionHandler(private val context: Context) : ActionHandler<ActionSpec.ReadFile> {
    override suspend fun execute(action: ActionSpec.ReadFile, context: AutomationContext): ActionResult {
        if (!Variable.isValidName(action.target)) {
            return ActionResult.Failure(FailureKind.INVALID_CONFIGURATION, "Invalid variable name '${action.target}'")
        }
        val (folder, error) = openFolder(this.context, action.folderUri, write = false)
        if (folder == null) return error!!
        val name = fileName(context, action.fileName) ?: return invalidName(action.fileName)
        val text = withContext(Dispatchers.IO) { folder.readText(name) }
            ?: return ActionResult.Failure(FailureKind.ERROR, "File $name not found")
        context.setVariable(action.target, text, action.scope)
        return ActionResult.Success("${action.target} = ${text.length} chars")
    }
}

class ListFilesActionHandler(private val context: Context) : ActionHandler<ActionSpec.ListFiles> {
    override suspend fun execute(action: ActionSpec.ListFiles, context: AutomationContext): ActionResult {
        if (!Variable.isValidName(action.target)) {
            return ActionResult.Failure(FailureKind.INVALID_CONFIGURATION, "Invalid variable name '${action.target}'")
        }
        val (folder, error) = openFolder(this.context, action.folderUri, write = false)
        if (folder == null) return error!!
        val filter = action.nameContains?.let(context::resolve)?.trim().orEmpty()
        val names = withContext(Dispatchers.IO) { folder.list() }
            .filter { !it.isDirectory && (filter.isEmpty() || it.name.contains(filter, ignoreCase = true)) }
            .map { it.name }
            .sortedBy { it.lowercase() }
        context.setVariable(action.target, names.joinToString("\n"), action.scope)
        context.setVariable("${action.target}_count", names.size.toString(), action.scope)
        return ActionResult.Success("${names.size} files")
    }
}

class CopyFileActionHandler(private val context: Context) : ActionHandler<ActionSpec.CopyFile> {
    override suspend fun execute(action: ActionSpec.CopyFile, context: AutomationContext): ActionResult {
        val (source, sourceError) = openFolder(this.context, action.folderUri, write = action.move)
        if (source == null) return sourceError!!
        val (destination, destinationError) = openFolder(this.context, action.destinationUri, write = true)
        if (destination == null) return destinationError!!
        val name = fileName(context, action.fileName) ?: return invalidName(action.fileName)
        if (source.treeUri == destination.treeUri) {
            return ActionResult.Failure(FailureKind.INVALID_CONFIGURATION, "Source and destination are the same folder")
        }
        return withContext(Dispatchers.IO) {
            val entry = source.find(name) ?: return@withContext ActionResult.Failure(FailureKind.ERROR, "File $name not found")
            destination.copyFrom(entry.uri, name)
            // The source is only deleted after the copy fully succeeded.
            if (action.move && !source.delete(name)) {
                ActionResult.Failure(FailureKind.ERROR, "Copied $name but could not delete the original")
            } else {
                ActionResult.Success("${if (action.move) "Moved" else "Copied"} $name")
            }
        }
    }
}

class DeleteFileActionHandler(private val context: Context) : ActionHandler<ActionSpec.DeleteFile> {
    override suspend fun execute(action: ActionSpec.DeleteFile, context: AutomationContext): ActionResult {
        val (folder, error) = openFolder(this.context, action.folderUri, write = true)
        if (folder == null) return error!!
        val name = fileName(context, action.fileName) ?: return invalidName(action.fileName)
        val deleted = withContext(Dispatchers.IO) { folder.delete(name) }
        return if (deleted) ActionResult.Success("Deleted $name") else ActionResult.Skipped("$name does not exist")
    }
}
