package com.autoflow.app.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.autoflow.app.R
import com.autoflow.app.ui.theme.AutoFlowTheme
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.CameraFacing
import com.autoflow.platform.files.SafFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * Visible photo capture: live preview, a countdown the user can cancel, then one photo saved
 * to the picked folder. The camera is bound to this activity's lifecycle, so it only runs while
 * the screen is actually shown (never on the lock screen, never in the background).
 */
class PhotoCaptureActivity : ComponentActivity() {
    private var requestId: String? = null
    private var finished = false
    private var status by mutableStateOf("")
    private var countdown by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestId = intent.getStringExtra(EXTRA_REQUEST)
        val folderUri = intent.getStringExtra(EXTRA_FOLDER).orEmpty()
        val fileName = intent.getStringExtra(EXTRA_FILE).orEmpty()
        val facing = runCatching { CameraFacing.valueOf(intent.getStringExtra(EXTRA_CAMERA).orEmpty()) }.getOrDefault(CameraFacing.BACK)
        val seconds = intent.getIntExtra(EXTRA_COUNTDOWN, 3).coerceIn(0, ActionSpec.TakePhoto.MAX_COUNTDOWN)
        val automationName = intent.getStringExtra(EXTRA_AUTOMATION).orEmpty()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return end(CaptureOutcome.Failed("Camera permission not granted"))
        }
        val folder = SafFolder.parse(this, folderUri)
        if (folder == null || SafFolder.validFileName(fileName) == null) {
            return end(CaptureOutcome.Failed("No folder or file name"))
        }

        val previewView = PreviewView(this)
        val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
        setContent {
            AutoFlowTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView({ previewView }, Modifier.fillMaxSize())
                    Column(
                        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.capture_photo_banner, automationName),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(8.dp),
                        )
                        countdown?.let {
                            Text(it.toString(), color = Color.White, fontSize = 96.sp, style = MaterialTheme.typography.displayLarge)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (status.isNotEmpty()) Text(status, color = Color.White)
                            Button(onClick = { end(CaptureOutcome.Cancelled) }) { Text(stringResource(R.string.action_cancel)) }
                        }
                    }
                }
            }
        }

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            if (finished) return@addListener
            val provider = runCatching { providerFuture.get() }.getOrElse {
                return@addListener end(CaptureOutcome.Failed("Camera unavailable"))
            }
            val selector = if (facing == CameraFacing.FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            if (!runCatching { provider.hasCamera(selector) }.getOrDefault(false)) {
                return@addListener end(CaptureOutcome.Failed("This device has no ${facing.name.lowercase()} camera"))
            }
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, imageCapture)
            } catch (e: IllegalArgumentException) {
                return@addListener end(CaptureOutcome.Failed("Cannot open the camera: ${e.message}"))
            } catch (e: IllegalStateException) {
                return@addListener end(CaptureOutcome.Failed("Cannot open the camera: ${e.message}"))
            }
            lifecycleScope.launch {
                for (remaining in seconds downTo 1) {
                    countdown = remaining
                    delay(1_000)
                }
                countdown = null
                capture(imageCapture, folder, fileName)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private suspend fun capture(imageCapture: ImageCapture, folder: SafFolder, fileName: String) {
        if (finished) return
        status = getString(R.string.capture_saving)
        val stream: OutputStream = try {
            withContext(Dispatchers.IO) {
                val uri = folder.fileForWrite(fileName)
                contentResolver.openOutputStream(uri, "wt") ?: error("Cannot write $fileName")
            }
        } catch (e: Exception) {
            return end(CaptureOutcome.Failed("Cannot create $fileName: ${e.message}"))
        }
        val options = ImageCapture.OutputFileOptions.Builder(stream).build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    runCatching { stream.close() }
                    status = getString(R.string.capture_saved, fileName)
                    lifecycleScope.launch {
                        delay(800)
                        end(CaptureOutcome.Done("Saved $fileName"))
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    runCatching { stream.close() }
                    end(CaptureOutcome.Failed("Capture failed: ${exception.message}"))
                }
            },
        )
    }

    private fun end(outcome: CaptureOutcome) {
        if (finished) return
        finished = true
        CaptureRequests.complete(requestId, outcome)
        finish()
    }

    override fun onDestroy() {
        // Back button, task removed, etc.: the action is told it was cancelled.
        if (!finished) {
            finished = true
            CaptureRequests.complete(requestId, CaptureOutcome.Cancelled)
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_REQUEST = "request"
        private const val EXTRA_FOLDER = "folder"
        private const val EXTRA_FILE = "file"
        private const val EXTRA_CAMERA = "camera"
        private const val EXTRA_COUNTDOWN = "countdown"
        private const val EXTRA_AUTOMATION = "automation"

        fun intent(context: Context, requestId: String, action: ActionSpec.TakePhoto, fileName: String, automationName: String): Intent =
            Intent(context, PhotoCaptureActivity::class.java)
                .putExtra(EXTRA_REQUEST, requestId)
                .putExtra(EXTRA_FOLDER, action.folderUri)
                .putExtra(EXTRA_FILE, fileName)
                .putExtra(EXTRA_CAMERA, action.camera.name)
                .putExtra(EXTRA_COUNTDOWN, action.countdownSeconds)
                .putExtra(EXTRA_AUTOMATION, automationName)
    }
}
