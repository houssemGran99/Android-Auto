package com.autoflow.platform.actions.handler

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.autoflow.core.engine.AutomationContext
import com.autoflow.core.engine.action.ActionHandler
import com.autoflow.core.model.ActionResult
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.FailureKind
import com.autoflow.core.model.SoundType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume

/** Speaks text with the system text-to-speech engine and waits until speech finishes. */
class SpeakActionHandler(private val context: Context) : ActionHandler<ActionSpec.Speak> {
    override suspend fun execute(action: ActionSpec.Speak, context: AutomationContext): ActionResult {
        val text = context.resolve(action.text).take(TextToSpeech.getMaxSpeechInputLength())
        if (text.isBlank()) return ActionResult.Skipped("Nothing to say")

        val tts = createEngine() ?: return ActionResult.Failure(FailureKind.NOT_SUPPORTED, "No text-to-speech engine available")
        try {
            val done = CompletableDeferred<Boolean>()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    done.complete(true)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    done.complete(false)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    done.complete(false)
                }

                // Called instead of onDone when speech is stopped or flushed; without it await() could hang.
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    done.complete(false)
                }
            })
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), UUID.randomUUID().toString())
            if (result != TextToSpeech.SUCCESS) return ActionResult.Failure(FailureKind.ERROR, "Text-to-speech rejected the text")
            return if (done.await()) ActionResult.Success(text) else ActionResult.Failure(FailureKind.ERROR, "Text-to-speech failed")
        } finally {
            tts.stop()
            tts.shutdown()
        }
    }

    private suspend fun createEngine(): TextToSpeech? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            var engine: TextToSpeech? = null
            engine = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    continuation.resume(engine)
                } else {
                    engine?.shutdown()
                    continuation.resume(null)
                }
            }
            continuation.invokeOnCancellation { engine?.shutdown() }
        }
    }
}

/** Plays the device's default notification / alarm / ringtone sound, at most [ActionSpec.PlaySound.maxDurationSeconds]. */
class PlaySoundActionHandler(private val context: Context) : ActionHandler<ActionSpec.PlaySound> {
    override suspend fun execute(action: ActionSpec.PlaySound, context: AutomationContext): ActionResult {
        val (ringtoneType, usage) = when (action.sound) {
            SoundType.NOTIFICATION -> RingtoneManager.TYPE_NOTIFICATION to AudioAttributes.USAGE_NOTIFICATION
            SoundType.ALARM -> RingtoneManager.TYPE_ALARM to AudioAttributes.USAGE_ALARM
            SoundType.RINGTONE -> RingtoneManager.TYPE_RINGTONE to AudioAttributes.USAGE_NOTIFICATION_RINGTONE
        }
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this.context, ringtoneType)
            ?: RingtoneManager.getDefaultUri(ringtoneType)
            ?: return ActionResult.Failure(FailureKind.NOT_SUPPORTED, "No default ${action.sound} sound set")

        val player = MediaPlayer()
        try {
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(usage).build())
            withContext(Dispatchers.IO) {
                player.setDataSource(this@PlaySoundActionHandler.context, uri)
                player.prepare()
            }
            val finished = CompletableDeferred<Unit>()
            player.setOnCompletionListener { finished.complete(Unit) }
            player.start()
            val maxMs = action.maxDurationSeconds.coerceIn(1, MAX_SECONDS) * 1000L
            withTimeoutOrNull(maxMs) { finished.await() }
            return ActionResult.Success(action.sound.name)
        } catch (e: IOException) {
            return ActionResult.Failure(FailureKind.ERROR, "Cannot play sound: ${e.message}")
        } finally {
            runCatching { if (player.isPlaying) player.stop() }
            player.release()
        }
    }

    private companion object {
        const val MAX_SECONDS = 300
    }
}
