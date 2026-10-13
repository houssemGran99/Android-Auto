package com.autoflow.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autoflow.app.capture.CaptureOutcome
import com.autoflow.app.capture.CaptureRequests
import com.autoflow.app.capture.RecordAudioActionHandler
import com.autoflow.app.capture.TakePhotoActionHandler
import com.autoflow.core.engine.AutomationContext
import com.autoflow.core.engine.DeviceState
import com.autoflow.core.engine.repository.VariableRepository
import com.autoflow.core.model.ActionResult
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.Automation
import com.autoflow.core.model.FailureKind
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.Variable
import com.autoflow.platform.actions.ActivityLauncher
import com.autoflow.platform.permissions.PermissionManager
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class CaptureActionsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val permissions = PermissionManager(context)
    private val launcher = ActivityLauncher(context, permissions) { true }

    private val automationContext = AutomationContext(
        automation = Automation(id = "c", name = "Capture"),
        event = TriggerEvent.Manual("test"),
        deviceState = DeviceState(),
        globalVariables = emptyMap(),
        variableRepository = object : VariableRepository {
            override fun observeAll() = emptyFlow<List<Variable>>()
            override suspend fun getAll() = emptyList<Variable>()
            override suspend fun get(name: String): Variable? = null
            override suspend fun set(name: String, value: String, secret: Boolean?) = Unit
            override suspend fun delete(name: String) = Unit
        },
        deviceStateProvider = { DeviceState() },
        clock = Clock.systemUTC(),
    )

    @Test
    fun captureNeedsPermissionBeforeAnythingOpens() = runTest {
        val photo = TakePhotoActionHandler(context, permissions, launcher)
            .execute(ActionSpec.TakePhoto("content://tree/x"), automationContext)
        assertEquals(FailureKind.PERMISSION_DENIED, (photo as ActionResult.Failure).kind)
        val audio = RecordAudioActionHandler(context, permissions, launcher)
            .execute(ActionSpec.RecordAudio("content://tree/x"), automationContext)
        assertEquals(FailureKind.PERMISSION_DENIED, (audio as ActionResult.Failure).kind)
        assertTrue("no screen was opened", shadowOf(context as Application).nextStartedActivity == null)
    }

    @Test
    fun missingFolderIsReportedWhenPermissionGranted() = runTest {
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.CAMERA)
        val photo = TakePhotoActionHandler(context, permissions, launcher)
            .execute(ActionSpec.TakePhoto(folderUri = ""), automationContext)
        assertEquals(FailureKind.INVALID_CONFIGURATION, (photo as ActionResult.Failure).kind)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun outcomesReachOnlyTheirWaiter() {
        val (id, deferred) = CaptureRequests.create()
        CaptureRequests.complete("other", CaptureOutcome.Cancelled)
        assertTrue(!deferred.isCompleted)
        CaptureRequests.complete(id, CaptureOutcome.Done("Saved a.jpg"))
        assertEquals(CaptureOutcome.Done("Saved a.jpg"), deferred.getCompleted())
        CaptureRequests.complete(id, CaptureOutcome.Cancelled) // already completed: ignored
        assertEquals(CaptureOutcome.Done("Saved a.jpg"), deferred.getCompleted())
    }
}
