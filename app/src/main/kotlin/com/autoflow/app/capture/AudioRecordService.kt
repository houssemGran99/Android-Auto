package com.autoflow.app.capture

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.autoflow.app.R
import com.autoflow.platform.actions.NotificationChannels
import com.autoflow.platform.files.SafFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Recording(val fileName: String, val startedAtElapsed: Long, val durationSeconds: Int) : RecordingState
}

/**
 * Records audio in a `microphone` foreground service. It is only started from the visible
 * [AudioRecordActivity] (Android requires the app to be in the foreground to use the microphone),
 * and shows an ongoing notification with a Stop button for the whole recording.
 */
class AudioRecordService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recorder: MediaRecorder? = null
    private var descriptor: ParcelFileDescriptor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRecording()
            return START_NOT_STICKY
        }
        val requestId = intent?.getStringExtra(EXTRA_REQUEST)
        val folderUri = intent?.getStringExtra(EXTRA_FOLDER).orEmpty()
        val fileName = intent?.getStringExtra(EXTRA_FILE).orEmpty()
        val seconds = intent?.getIntExtra(EXTRA_DURATION, 30) ?: 30
        val automationName = intent?.getStringExtra(EXTRA_AUTOMATION).orEmpty()

        if (!enterForeground(fileName, automationName)) {
            CaptureRequests.complete(requestId, CaptureOutcome.Failed("Android did not allow starting the recording"))
            return START_NOT_STICKY
        }
        if (_state.value is RecordingState.Recording) {
            CaptureRequests.complete(requestId, CaptureOutcome.Failed("A recording is already running"))
            return START_NOT_STICKY
        }
        scope.launch { start(requestId, folderUri, fileName, seconds) }
        return START_NOT_STICKY
    }

    private suspend fun start(requestId: String?, folderUri: String, fileName: String, seconds: Int) {
        try {
            val folder = SafFolder.parse(this, folderUri) ?: error("No folder selected")
            val pfd = withContext(Dispatchers.IO) {
                contentResolver.openFileDescriptor(folder.fileForWrite(fileName), "wt") ?: error("Cannot write $fileName")
            }
            descriptor = pfd
            val media = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            recorder = media
            media.setAudioSource(MediaRecorder.AudioSource.MIC)
            media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            media.setAudioEncodingBitRate(128_000)
            media.setAudioSamplingRate(44_100)
            media.setOutputFile(pfd.fileDescriptor)
            media.setMaxDuration(seconds * 1000)
            media.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stopRecording()
            }
            media.prepare()
            media.start()
            _state.value = RecordingState.Recording(fileName, SystemClock.elapsedRealtime(), seconds)
            CaptureRequests.complete(requestId, CaptureOutcome.Done("Recording $fileName for $seconds s"))
        } catch (e: Exception) {
            Log.w(TAG, "Cannot start recording", e)
            CaptureRequests.complete(requestId, CaptureOutcome.Failed("Cannot start recording: ${e.message}"))
            stopRecording()
        }
    }

    private fun stopRecording() {
        recorder?.let { media ->
            // stop() throws when nothing was recorded yet; the file is then just empty.
            runCatching { media.stop() }
            media.release()
        }
        recorder = null
        runCatching { descriptor?.close() }
        descriptor = null
        _state.value = RecordingState.Idle
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (recorder != null) stopRecording()
        scope.cancel()
        super.onDestroy()
    }

    private fun enterForeground(fileName: String, automationName: String): Boolean = try {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(fileName, automationName),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
        )
        true
    } catch (e: RuntimeException) {
        // ForegroundServiceStartNotAllowedException / SecurityException (microphone while not visible).
        Log.w(TAG, "Recording not allowed to start", e)
        stopSelf()
        false
    }

    private fun notification(fileName: String, automationName: String): Notification {
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, AudioRecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, NotificationChannels.RECORDING)
            .setSmallIcon(com.autoflow.platform.actions.R.drawable.ic_stat_autoflow)
            .setContentTitle(getString(R.string.recording_notification_title, automationName))
            .setContentText(fileName)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.recording_stop), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "AutoFlow"
        private const val NOTIFICATION_ID = 4_202
        private const val ACTION_STOP = "com.autoflow.recording.STOP"
        private const val EXTRA_REQUEST = "request"
        private const val EXTRA_FOLDER = "folder"
        private const val EXTRA_FILE = "file"
        private const val EXTRA_DURATION = "duration"
        private const val EXTRA_AUTOMATION = "automation"

        private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
        val state: StateFlow<RecordingState> = _state.asStateFlow()

        /** Must be called while the app is visible (from [AudioRecordActivity]). */
        fun start(context: Context, requestId: String?, folderUri: String, fileName: String, seconds: Int, automationName: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AudioRecordService::class.java)
                    .putExtra(EXTRA_REQUEST, requestId)
                    .putExtra(EXTRA_FOLDER, folderUri)
                    .putExtra(EXTRA_FILE, fileName)
                    .putExtra(EXTRA_DURATION, seconds)
                    .putExtra(EXTRA_AUTOMATION, automationName),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AudioRecordService::class.java).setAction(ACTION_STOP))
        }
    }
}
