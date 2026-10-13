package com.autoflow.platform.actions.handler

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.autoflow.core.engine.AutomationContext
import com.autoflow.core.engine.action.ActionHandler
import com.autoflow.core.model.ActionResult
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.FailureKind
import com.autoflow.core.model.FlashlightMode
import com.autoflow.platform.permissions.PermissionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Sets the screen timeout; needs "Modify system settings". */
class SetScreenTimeoutActionHandler(private val context: Context) : ActionHandler<ActionSpec.SetScreenTimeout> {
    override suspend fun execute(action: ActionSpec.SetScreenTimeout, context: AutomationContext): ActionResult {
        if (!Settings.System.canWrite(this.context)) {
            return ActionResult.Failure(FailureKind.PERMISSION_DENIED, "\"Modify system settings\" not granted")
        }
        Settings.System.putInt(this.context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, action.seconds * 1000)
        return ActionResult.Success("${action.seconds} s")
    }
}

/** Locks the screen with DevicePolicyManager.lockNow (device admin with only the force-lock policy). */
class LockScreenActionHandler(
    private val context: Context,
    private val permissions: PermissionManager,
) : ActionHandler<ActionSpec.LockScreen> {
    override suspend fun execute(action: ActionSpec.LockScreen, context: AutomationContext): ActionResult {
        if (!permissions.isDeviceAdminActive()) {
            return ActionResult.Failure(FailureKind.PERMISSION_DENIED, "Lock screen access is not activated")
        }
        val policy = this.context.getSystemService(DevicePolicyManager::class.java)
            ?: return ActionResult.Failure(FailureKind.NOT_SUPPORTED, "Device policy service unavailable")
        policy.lockNow()
        return ActionResult.Success("Locked")
    }
}

/** Turns the torch on / off with CameraManager.setTorchMode (no camera permission needed). */
class FlashlightActionHandler(private val context: Context) : ActionHandler<ActionSpec.Flashlight> {
    override suspend fun execute(action: ActionSpec.Flashlight, context: AutomationContext): ActionResult {
        val camera = this.context.getSystemService(CameraManager::class.java)
            ?: return ActionResult.Failure(FailureKind.NOT_SUPPORTED, "No camera service")
        val id = try {
            camera.cameraIdList.firstOrNull { id ->
                camera.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: CameraAccessException) {
            null
        } ?: return ActionResult.Failure(FailureKind.NOT_SUPPORTED, "This device has no flashlight")
        val on = when (action.mode) {
            FlashlightMode.ON -> true
            FlashlightMode.OFF -> false
            FlashlightMode.TOGGLE -> !(currentTorchState(camera, id) ?: false)
        }
        return try {
            camera.setTorchMode(id, on)
            ActionResult.Success(if (on) "On" else "Off")
        } catch (e: CameraAccessException) {
            // E.g. the camera is in use by another app.
            ActionResult.Failure(FailureKind.ERROR, "Flashlight unavailable: ${e.message}")
        }
    }

    /** Android has no getter for the torch state; the callback reports it right after registering. */
    private suspend fun currentTorchState(camera: CameraManager, cameraId: String): Boolean? {
        val state = CompletableDeferred<Boolean>()
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, enabled: Boolean) {
                if (id == cameraId) state.complete(enabled)
            }

            override fun onTorchModeUnavailable(id: String) {
                if (id == cameraId) state.complete(false)
            }
        }
        camera.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        return try {
            withTimeoutOrNull(STATE_TIMEOUT_MS) { state.await() }
        } finally {
            camera.unregisterTorchCallback(callback)
        }
    }

    private companion object {
        const val STATE_TIMEOUT_MS = 1_000L
    }
}
