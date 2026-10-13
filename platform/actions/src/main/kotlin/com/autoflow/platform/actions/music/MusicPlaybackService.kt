package com.autoflow.platform.actions.music

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.autoflow.platform.actions.NotificationChannels
import com.autoflow.platform.actions.R

/**
 * Plays a queue of audio files in a foreground service of type `mediaPlayback`, with a
 * media notification (previous / play-pause / next / stop) and a media session, so the
 * user always sees and controls what an automation started. Respects audio focus and
 * pauses when headphones are unplugged.
 */
class MusicPlaybackService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager
    private lateinit var session: MediaSession
    private var focusRequest: AudioFocusRequest? = null
    private var player: MediaPlayer? = null
    private var queue: List<Track> = emptyList()
    private var index = 0
    private var playing = false
    private var consecutiveErrors = 0
    private var noisyRegistered = false

    private val stopAtLimit = Runnable { stopPlayback() }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> stopPlayback()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> pause(keepFocus = true)
            AudioManager.AUDIOFOCUS_GAIN -> if (player != null && !playing) resume()
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        session = MediaSession(this, "AutoFlowMusic").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onSkipToNext() = skip(1)
                override fun onSkipToPrevious() = skip(-1)
                override fun onStop() = stopPlayback()
            })
        }
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every start must be followed by startForeground, even for control commands.
        if (!enterForeground()) return START_NOT_STICKY
        val action = intent?.action
        when {
            action == ACTION_PLAY -> {
                val request = pendingRequest
                pendingRequest = null
                if (request != null) startQueue(request) else stopPlayback()
            }
            // E.g. a stale notification button after the process was killed.
            player == null -> stopPlayback()
            action == ACTION_TOGGLE -> if (playing) pause() else resume()
            action == ACTION_NEXT -> skip(1)
            action == ACTION_PREVIOUS -> skip(-1)
            else -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        abandonFocus()
        unregisterNoisy()
        session.release()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun startQueue(request: Request) {
        queue = request.tracks
        index = 0
        consecutiveErrors = 0
        handler.removeCallbacks(stopAtLimit)
        if (request.maxMinutes > 0) handler.postDelayed(stopAtLimit, request.maxMinutes * 60_000L)
        session.isActive = true
        registerNoisy()
        playCurrent()
    }

    private fun playCurrent() {
        releasePlayer()
        val track = queue.getOrNull(index) ?: return stopPlayback()
        if (!requestFocus()) {
            Log.w(TAG, "Audio focus denied (e.g. during a call)")
            return stopPlayback()
        }
        val media = MediaPlayer()
        player = media
        media.setAudioAttributes(AUDIO_ATTRIBUTES)
        media.setOnPreparedListener {
            consecutiveErrors = 0
            it.start()
            playing = true
            refresh()
        }
        media.setOnCompletionListener { skip(1, wrap = false) }
        media.setOnErrorListener { _, what, extra ->
            Log.w(TAG, "Cannot play ${track.title}: $what/$extra")
            onTrackError()
            true
        }
        try {
            media.setDataSource(this, track.uri)
            media.prepareAsync()
            refresh()
        } catch (e: Exception) {
            // IOException, SecurityException (grant revoked), IllegalArgumentException...
            Log.w(TAG, "Cannot open ${track.title}", e)
            onTrackError()
        }
    }

    private fun onTrackError() {
        consecutiveErrors++
        if (consecutiveErrors >= queue.size.coerceAtMost(MAX_CONSECUTIVE_ERRORS)) stopPlayback() else skip(1, wrap = false)
    }

    private fun skip(delta: Int, wrap: Boolean = true) {
        if (queue.isEmpty()) return
        val next = index + delta
        index = when {
            next in queue.indices -> next
            !wrap && next >= queue.size -> return stopPlayback()
            else -> 0 // "previous" on the first track restarts it; "next" on the last one wraps.
        }
        playCurrent()
    }

    private fun pause(keepFocus: Boolean = false) {
        val media = player ?: return
        if (playing) runCatching { media.pause() }
        playing = false
        if (!keepFocus) abandonFocus()
        refresh()
    }

    private fun resume() {
        val media = player ?: return
        if (playing || !requestFocus()) return
        runCatching { media.start() }.onSuccess { playing = true }
        refresh()
    }

    private fun stopPlayback() {
        handler.removeCallbacks(stopAtLimit)
        releasePlayer()
        playing = false
        queue = emptyList()
        abandonFocus()
        unregisterNoisy()
        if (::session.isInitialized) {
            session.setPlaybackState(state(PlaybackState.STATE_STOPPED))
            session.isActive = false
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releasePlayer() {
        player?.let { media ->
            runCatching { media.stop() }
            media.release()
        }
        player = null
    }

    private fun refresh() {
        val title = queue.getOrNull(index)?.title.orEmpty()
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, (index + 1).toLong())
                .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, queue.size.toLong())
                .build(),
        )
        session.setPlaybackState(state(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED))
        if (queue.isNotEmpty()) enterForeground()
    }

    private fun state(value: Int): PlaybackState = PlaybackState.Builder()
        .setActions(
            PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP,
        )
        .setState(value, runCatching { player?.currentPosition?.toLong() }.getOrNull() ?: 0L, if (value == PlaybackState.STATE_PLAYING) 1f else 0f)
        .build()

    private fun enterForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        true
    } catch (e: IllegalStateException) {
        Log.w(TAG, "Music playback is not allowed to start in the foreground now", e)
        stopSelf()
        false
    }

    private fun buildNotification(): Notification {
        val track = queue.getOrNull(index)
        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return Notification.Builder(this, NotificationChannels.MUSIC)
            .setSmallIcon(R.drawable.ic_stat_autoflow)
            .setContentTitle(track?.title ?: getString(R.string.music_starting))
            .setContentText(
                if (queue.isEmpty()) "" else getString(R.string.music_position, index + 1, queue.size),
            )
            .setContentIntent(openApp)
            .setDeleteIntent(command(ACTION_STOP))
            .setOngoing(playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(android.R.drawable.ic_media_previous, R.string.music_previous, ACTION_PREVIOUS))
            .addAction(
                if (playing) {
                    action(android.R.drawable.ic_media_pause, R.string.music_pause, ACTION_TOGGLE)
                } else {
                    action(android.R.drawable.ic_media_play, R.string.music_play, ACTION_TOGGLE)
                },
            )
            .addAction(action(android.R.drawable.ic_media_next, R.string.music_next, ACTION_NEXT))
            .addAction(action(android.R.drawable.ic_menu_close_clear_cancel, R.string.music_stop, ACTION_STOP))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun action(icon: Int, title: Int, commandAction: String): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(this, icon), getString(title), command(commandAction)).build()

    private fun command(commandAction: String): PendingIntent = PendingIntent.getService(
        this,
        commandAction.hashCode(),
        Intent(this, MusicPlaybackService::class.java).setAction(commandAction),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun requestFocus(): Boolean {
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AUDIO_ATTRIBUTES)
            .setOnAudioFocusChangeListener(focusListener, handler)
            .build()
            .also { focusRequest = it }
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(
            this,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        runCatching { unregisterReceiver(noisyReceiver) }
        noisyRegistered = false
    }

    /** What an automation asked to play. */
    data class Request(val tracks: List<Track>, val maxMinutes: Int)

    companion object {
        private const val TAG = "AutoFlow"
        private const val NOTIFICATION_ID = 4_201
        private const val MAX_CONSECUTIVE_ERRORS = 5
        private const val ACTION_PLAY = "com.autoflow.music.PLAY"
        private const val ACTION_TOGGLE = "com.autoflow.music.TOGGLE"
        private const val ACTION_NEXT = "com.autoflow.music.NEXT"
        private const val ACTION_PREVIOUS = "com.autoflow.music.PREVIOUS"
        private const val ACTION_STOP = "com.autoflow.music.STOP"

        private val AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        // The queue can hold thousands of URIs, too many for an Intent; it is handed over in memory.
        @Volatile
        private var pendingRequest: Request? = null

        @Volatile
        private var instance: MusicPlaybackService? = null

        val isRunning: Boolean get() = instance != null

        /**
         * Starts playback. Throws [IllegalStateException] (ForegroundServiceStartNotAllowedException
         * on Android 12+) when Android does not allow starting a foreground service right now.
         */
        fun play(context: Context, request: Request) {
            pendingRequest = request
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, MusicPlaybackService::class.java).setAction(ACTION_PLAY),
                )
            } catch (e: IllegalStateException) {
                pendingRequest = null
                throw e
            }
        }

        /** Stops playback started by an automation; returns false when nothing was playing. */
        fun stop(): Boolean {
            val service = instance ?: return false
            service.handler.post { service.stopPlayback() }
            return true
        }
    }
}
