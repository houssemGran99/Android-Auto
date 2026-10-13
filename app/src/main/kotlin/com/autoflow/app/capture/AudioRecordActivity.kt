package com.autoflow.app.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoflow.app.R
import com.autoflow.app.ui.theme.AutoFlowTheme
import com.autoflow.core.model.ActionSpec
import kotlinx.coroutines.delay

/**
 * Visible start screen for recordings: starts [AudioRecordService] while the app is in the
 * foreground, shows the elapsed time and a Stop button. Closing it keeps recording, with the
 * service notification still visible.
 */
class AudioRecordActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val requestId = intent.getStringExtra(EXTRA_REQUEST)
        val automationName = intent.getStringExtra(EXTRA_AUTOMATION).orEmpty()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            CaptureRequests.complete(requestId, CaptureOutcome.Failed("Microphone permission not granted"))
            finish()
            return
        }
        // Only the first creation starts the service (not after a configuration change).
        if (savedInstanceState == null) {
            AudioRecordService.start(
                this,
                requestId,
                intent.getStringExtra(EXTRA_FOLDER).orEmpty(),
                intent.getStringExtra(EXTRA_FILE).orEmpty(),
                intent.getIntExtra(EXTRA_DURATION, 30),
                automationName,
            )
        }
        setContent {
            AutoFlowTheme {
                Surface(Modifier.fillMaxSize()) {
                    val state by AudioRecordService.state.collectAsStateWithLifecycle()
                    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            now = SystemClock.elapsedRealtime()
                            delay(500)
                        }
                    }
                    Column(
                        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Outlined.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            stringResource(R.string.recording_banner, automationName),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        when (val current = state) {
                            is RecordingState.Recording -> {
                                val elapsed = ((now - current.startedAtElapsed) / 1000).coerceAtLeast(0)
                                Text(
                                    "%d:%02d / %d:%02d".format(elapsed / 60, elapsed % 60, current.durationSeconds / 60, current.durationSeconds % 60),
                                    style = MaterialTheme.typography.displaySmall,
                                )
                                Text(current.fileName, style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { AudioRecordService.stop(this@AudioRecordActivity) }) {
                                    Text(stringResource(R.string.recording_stop))
                                }
                            }
                            RecordingState.Idle -> Text(stringResource(R.string.recording_idle))
                        }
                        OutlinedButton(onClick = { finish() }) { Text(stringResource(R.string.recording_close)) }
                        Text(
                            stringResource(R.string.recording_close_hint),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_REQUEST = "request"
        private const val EXTRA_FOLDER = "folder"
        private const val EXTRA_FILE = "file"
        private const val EXTRA_DURATION = "duration"
        private const val EXTRA_AUTOMATION = "automation"

        fun intent(context: Context, requestId: String, action: ActionSpec.RecordAudio, fileName: String, automationName: String): Intent =
            Intent(context, AudioRecordActivity::class.java)
                .putExtra(EXTRA_REQUEST, requestId)
                .putExtra(EXTRA_FOLDER, action.folderUri)
                .putExtra(EXTRA_FILE, fileName)
                .putExtra(EXTRA_DURATION, action.durationSeconds)
                .putExtra(EXTRA_AUTOMATION, automationName)
    }
}
