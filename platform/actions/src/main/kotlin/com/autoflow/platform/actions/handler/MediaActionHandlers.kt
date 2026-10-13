package com.autoflow.platform.actions.handler

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import com.autoflow.core.engine.AutomationContext
import com.autoflow.core.engine.action.ActionHandler
import com.autoflow.core.model.ActionResult
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.FailureKind
import com.autoflow.core.model.MediaCommand
import com.autoflow.platform.actions.music.AudioFolderScanner
import com.autoflow.platform.actions.music.MusicPlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sends a media key to the app that currently owns media playback, exactly like a
 * headset button. Needs no permission; does nothing visible if no player is active.
 */
class MediaControlActionHandler(private val context: Context) : ActionHandler<ActionSpec.MediaControl> {
    override suspend fun execute(action: ActionSpec.MediaControl, context: AutomationContext): ActionResult {
        val audio = this.context.getSystemService(AudioManager::class.java)
            ?: return ActionResult.Failure(FailureKind.NOT_SUPPORTED, "Audio service unavailable")
        val code = keyCode(action.command)
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
        return ActionResult.Success(action.command.name)
    }

    companion object {
        fun keyCode(command: MediaCommand): Int = when (command) {
            MediaCommand.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            MediaCommand.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaCommand.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaCommand.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaCommand.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            MediaCommand.STOP -> KeyEvent.KEYCODE_MEDIA_STOP
        }
    }
}

/** Plays the audio files of a user-picked folder (shuffled or in name order) in [MusicPlaybackService]. */
class PlayMusicFolderActionHandler(
    private val context: Context,
    private val scanner: AudioFolderScanner = AudioFolderScanner(context),
) : ActionHandler<ActionSpec.PlayMusicFolder> {
    override suspend fun execute(action: ActionSpec.PlayMusicFolder, context: AutomationContext): ActionResult {
        val folder = runCatching { Uri.parse(action.folderUri) }.getOrNull()
            ?.takeIf { action.folderUri.startsWith("content://") }
            ?: return ActionResult.Failure(FailureKind.INVALID_CONFIGURATION, "No folder selected")
        if (!scanner.hasAccess(folder)) {
            return ActionResult.Failure(FailureKind.PERMISSION_DENIED, "Access to the folder was removed; pick the folder again")
        }
        val tracks = try {
            withContext(Dispatchers.IO) { scanner.scan(folder) }
        } catch (e: Exception) {
            // SecurityException / IllegalArgumentException when the folder was deleted or moved.
            return ActionResult.Failure(FailureKind.ERROR, "Cannot read the folder: ${e.message}")
        }
        if (tracks.isEmpty()) return ActionResult.Failure(FailureKind.INVALID_CONFIGURATION, "No audio files in the folder")
        val queue = if (action.shuffle) tracks.shuffled() else tracks.sortedBy { it.title.lowercase() }
        return try {
            withContext(Dispatchers.Main) {
                MusicPlaybackService.play(this@PlayMusicFolderActionHandler.context, MusicPlaybackService.Request(queue, action.maxMinutes))
            }
            ActionResult.Success("${queue.size} tracks")
        } catch (e: IllegalStateException) {
            ActionResult.Failure(
                FailureKind.NOT_SUPPORTED,
                "Android does not allow starting playback from the background right now. " +
                    "Allow \"Display over other apps\" or disable battery optimization for AutoFlow.",
            )
        }
    }
}

/** Stops music started by [PlayMusicFolderActionHandler]. */
class StopMusicActionHandler : ActionHandler<ActionSpec.StopMusic> {
    override suspend fun execute(action: ActionSpec.StopMusic, context: AutomationContext): ActionResult =
        if (MusicPlaybackService.stop()) ActionResult.Success("Stopped") else ActionResult.Skipped("No AutoFlow music playing")
}
