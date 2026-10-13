package com.autoflow.platform.triggers.source

import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import com.autoflow.core.engine.trigger.TriggerSource
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.TriggerFamily

/**
 * Media playback start / stop of any app, via [AudioManager.AudioPlaybackCallback]; needs no
 * permission. Callbacks only say that players changed, so the actual state is read from
 * [AudioManager.isMusicActive] after a short settle delay, and only real changes are reported
 * (pausing and resuming within the delay, or skipping tracks, fires nothing).
 */
class MediaPlaybackTriggerSource(context: Context) : TriggerSource {
    override val family = TriggerFamily.MEDIA

    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var callback: AudioManager.AudioPlaybackCallback? = null
    private var listener: ((TriggerEvent) -> Unit)? = null
    private var lastPlaying = false

    private val check = Runnable {
        val playing = audioManager.isMusicActive
        if (playing != lastPlaying) {
            lastPlaying = playing
            listener?.invoke(TriggerEvent.MediaPlaybackChanged(playing))
        }
    }

    override fun register(onEvent: (TriggerEvent) -> Unit) {
        unregister()
        listener = onEvent
        lastPlaying = audioManager.isMusicActive
        val newCallback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                handler.removeCallbacks(check)
                handler.postDelayed(check, SETTLE_DELAY_MS)
            }
        }
        callback = newCallback
        audioManager.registerAudioPlaybackCallback(newCallback, handler)
    }

    override fun unregister() {
        callback?.let { audioManager.unregisterAudioPlaybackCallback(it) }
        callback = null
        listener = null
        handler.removeCallbacks(check)
    }

    private companion object {
        const val SETTLE_DELAY_MS = 1_500L
    }
}
