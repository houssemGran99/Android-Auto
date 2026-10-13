package com.autoflow.platform.actions

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autoflow.core.engine.AutomationContext
import com.autoflow.core.engine.DeviceState
import com.autoflow.core.engine.repository.VariableRepository
import com.autoflow.core.model.ActionResult
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.Automation
import com.autoflow.core.model.FailureKind
import com.autoflow.core.model.MediaCommand
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.Variable
import com.autoflow.platform.actions.handler.MediaControlActionHandler
import com.autoflow.platform.actions.handler.PlayMusicFolderActionHandler
import com.autoflow.platform.actions.handler.StopMusicActionHandler
import com.autoflow.platform.actions.music.AudioFolderScanner
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MediaActionsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val automationContext = AutomationContext(
        automation = Automation(id = "m", name = "Music"),
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
    fun mediaCommandsMapToMediaKeys() = runTest {
        assertEquals(KeyEvent.KEYCODE_MEDIA_NEXT, MediaControlActionHandler.keyCode(MediaCommand.NEXT))
        assertEquals(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, MediaControlActionHandler.keyCode(MediaCommand.PLAY_PAUSE))
        val result = MediaControlActionHandler(context).execute(ActionSpec.MediaControl(MediaCommand.PAUSE), automationContext)
        assertTrue(result is ActionResult.Success)
    }

    @Test
    fun audioFilesAreRecognised() {
        assertTrue(AudioFolderScanner.isAudio("audio/mpeg", "song.mp3"))
        assertTrue(AudioFolderScanner.isAudio("application/ogg", "a.ogg"))
        assertTrue(AudioFolderScanner.isAudio("application/octet-stream", "Track.FLAC"))
        assertFalse(AudioFolderScanner.isAudio("image/jpeg", "cover.jpg"))
        assertFalse(AudioFolderScanner.isAudio("application/octet-stream", "notes.txt"))
    }

    @Test
    fun folderWithoutGrantFailsWithPermissionError() = runTest {
        val handler = PlayMusicFolderActionHandler(context)
        val missing = handler.execute(ActionSpec.PlayMusicFolder(folderUri = ""), automationContext)
        assertEquals(FailureKind.INVALID_CONFIGURATION, (missing as ActionResult.Failure).kind)
        val revoked = handler.execute(
            ActionSpec.PlayMusicFolder("content://com.android.externalstorage.documents/tree/primary%3AMusic"),
            automationContext,
        )
        assertEquals(FailureKind.PERMISSION_DENIED, (revoked as ActionResult.Failure).kind)
    }

    @Test
    fun stopMusicIsSkippedWhenNothingPlays() = runTest {
        assertTrue(StopMusicActionHandler().execute(ActionSpec.StopMusic, automationContext) is ActionResult.Skipped)
    }
}
