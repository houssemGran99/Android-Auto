package com.autoflow.app.capture

import android.content.Context
import android.content.Intent
import com.autoflow.core.engine.AutomationContext
import com.autoflow.core.engine.action.ActionHandler
import com.autoflow.core.model.ActionResult
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.Capability
import com.autoflow.core.model.FailureKind
import com.autoflow.platform.actions.ActivityLauncher
import com.autoflow.platform.files.SafFolder
import com.autoflow.platform.permissions.PermissionManager
import kotlinx.coroutines.withTimeoutOrNull

/** Checks shared by both capture actions; returns the resolved file name or a failure. */
private fun prepare(
    context: Context,
    permissions: PermissionManager,
    capability: Capability,
    folderUri: String,
    rawName: String,
    automation: AutomationContext,
): Pair<String?, ActionResult.Failure?> {
    if (!permissions.isSatisfied(capability)) {
        return null to ActionResult.Failure(FailureKind.PERMISSION_DENIED, "${capability.name.lowercase()} permission not granted")
    }
    val folder = SafFolder.parse(context, folderUri)
        ?: return null to ActionResult.Failure(FailureKind.INVALID_CONFIGURATION, "No folder selected")
    if (!folder.hasAccess(write = true)) {
        return null to ActionResult.Failure(FailureKind.PERMISSION_DENIED, "AutoFlow lost write access to the folder; pick it again")
    }
    val name = SafFolder.validFileName(automation.resolve(rawName))
        ?: return null to ActionResult.Failure(FailureKind.INVALID_CONFIGURATION, "'$rawName' is not a valid file name")
    return name to null
}

/**
 * Opens the visible capture screen and waits for its outcome. If Android only allows a
 * tap-to-open notification, the run reports that instead of waiting.
 */
private suspend fun openAndAwait(
    launcher: ActivityLauncher,
    intent: Intent,
    requestId: String,
    deferred: kotlinx.coroutines.CompletableDeferred<CaptureOutcome>,
    label: String,
    automationName: String,
    timeoutMs: Long,
): ActionResult {
    val launched = launcher.launch(intent, label, automationName)
    if (launched !is ActionResult.Success) {
        CaptureRequests.forget(requestId)
        return launched
    }
    return when (val outcome = withTimeoutOrNull(timeoutMs) { deferred.await() }) {
        is CaptureOutcome.Done -> ActionResult.Success(outcome.message)
        is CaptureOutcome.Failed -> ActionResult.Failure(FailureKind.ERROR, outcome.message)
        CaptureOutcome.Cancelled -> ActionResult.Skipped("Cancelled on the capture screen")
        null -> {
            CaptureRequests.forget(requestId)
            ActionResult.Failure(FailureKind.TIMEOUT, "The $label screen was not completed in time")
        }
    }
}

class TakePhotoActionHandler(
    private val context: Context,
    private val permissions: PermissionManager,
    private val launcher: ActivityLauncher,
) : ActionHandler<ActionSpec.TakePhoto> {
    override suspend fun execute(action: ActionSpec.TakePhoto, context: AutomationContext): ActionResult {
        val (name, error) = prepare(this.context, permissions, Capability.CAMERA, action.folderUri, action.fileName, context)
        if (name == null) return error!!
        val (id, deferred) = CaptureRequests.create()
        val intent = PhotoCaptureActivity.intent(this.context, id, action, name, context.automation.name)
        return openAndAwait(launcher, intent, id, deferred, "camera", context.automation.name, PHOTO_TIMEOUT_MS)
    }

    private companion object {
        const val PHOTO_TIMEOUT_MS = 90_000L
    }
}

class RecordAudioActionHandler(
    private val context: Context,
    private val permissions: PermissionManager,
    private val launcher: ActivityLauncher,
) : ActionHandler<ActionSpec.RecordAudio> {
    override suspend fun execute(action: ActionSpec.RecordAudio, context: AutomationContext): ActionResult {
        val (name, error) = prepare(this.context, permissions, Capability.MICROPHONE, action.folderUri, action.fileName, context)
        if (name == null) return error!!
        if (AudioRecordService.state.value is RecordingState.Recording) {
            return ActionResult.Skipped("A recording is already running")
        }
        val (id, deferred) = CaptureRequests.create()
        val intent = AudioRecordActivity.intent(this.context, id, action, name, context.automation.name)
        // Only waits until recording has started; the recording itself continues in a visible service.
        return openAndAwait(launcher, intent, id, deferred, "audio recorder", context.automation.name, START_TIMEOUT_MS)
    }

    private companion object {
        const val START_TIMEOUT_MS = 60_000L
    }
}
