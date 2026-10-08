package com.autoflow.platform.actions.handler

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.autoflow.core.engine.AutomationContext
import com.autoflow.core.engine.action.ActionHandler
import com.autoflow.core.model.ActionResult
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.AudioStream
import com.autoflow.core.model.Capability
import com.autoflow.core.model.FailureKind
import com.autoflow.core.model.NotificationPriority
import com.autoflow.platform.actions.NotificationChannels
import com.autoflow.platform.actions.R
import com.autoflow.platform.permissions.PermissionManager
import kotlin.math.roundToInt

class ShowNotificationActionHandler(
    private val context: Context,
    private val permissions: PermissionManager,
) : ActionHandler<ActionSpec.ShowNotification> {

    @SuppressLint("MissingPermission")
    override suspend fun execute(action: ActionSpec.ShowNotification, context: AutomationContext): ActionResult {
        if (!permissions.isSatisfied(Capability.NOTIFICATIONS)) {
            return ActionResult.Failure(FailureKind.PERMISSION_DENIED, "Notification permission not granted")
        }
        val title = context.resolve(action.title)
        val message = context.resolve(action.message)
        val openApp = this.context.packageManager.getLaunchIntentForPackage(this.context.packageName)?.let {
            PendingIntent.getActivity(this.context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(this.context, NotificationChannels.forPriority(action.priority))
            .setSmallIcon(R.drawable.ic_stat_autoflow)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(
                when (action.priority) {
                    NotificationPriority.LOW -> NotificationCompat.PRIORITY_LOW
                    NotificationPriority.DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
                    NotificationPriority.HIGH -> NotificationCompat.PRIORITY_HIGH
                },
            )
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this.context).notify((context.automation.id + action.title).hashCode(), notification)
        return ActionResult.Success(title)
    }
}

class SetBrightnessActionHandler(private val context: Context) : ActionHandler<ActionSpec.SetBrightness> {
    override suspend fun execute(action: ActionSpec.SetBrightness, context: AutomationContext): ActionResult {
        val resolver = this.context.contentResolver
        if (!Settings.System.canWrite(this.context)) {
            return ActionResult.Failure(FailureKind.PERMISSION_DENIED, "\"Modify system settings\" not allowed")
        }
        val value = (action.percent * MAX_BRIGHTNESS / 100.0).roundToInt().coerceIn(0, MAX_BRIGHTNESS)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
        return ActionResult.Success("${action.percent}% ($value/$MAX_BRIGHTNESS)")
    }

    private companion object {
        const val MAX_BRIGHTNESS = 255
    }
}

class SetVolumeActionHandler(private val context: Context) : ActionHandler<ActionSpec.SetVolume> {
    override suspend fun execute(action: ActionSpec.SetVolume, context: AutomationContext): ActionResult {
        val audio = this.context.getSystemService(AudioManager::class.java)
        val stream = streamType(action.stream)
        val max = audio.getStreamMaxVolume(stream)
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audio.getStreamMinVolume(stream) else 0
        val index = (min + (max - min) * action.percent / 100.0).roundToInt().coerceIn(min, max)
        return try {
            audio.setStreamVolume(stream, index, 0)
            ActionResult.Success("${action.stream} ${action.percent}% ($index/$max)")
        } catch (e: SecurityException) {
            // Changing ring/notification volume while Do Not Disturb is on needs notification policy access.
            ActionResult.Failure(FailureKind.PERMISSION_DENIED, "Do Not Disturb is on; grant Do Not Disturb access to change this volume")
        }
    }

    companion object {
        fun streamType(stream: AudioStream): Int = when (stream) {
            AudioStream.MEDIA -> AudioManager.STREAM_MUSIC
            AudioStream.RING -> AudioManager.STREAM_RING
            AudioStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
            AudioStream.ALARM -> AudioManager.STREAM_ALARM
            AudioStream.VOICE_CALL -> AudioManager.STREAM_VOICE_CALL
        }
    }
}

class SetDoNotDisturbActionHandler(private val context: Context) : ActionHandler<ActionSpec.SetDoNotDisturb> {
    override suspend fun execute(action: ActionSpec.SetDoNotDisturb, context: AutomationContext): ActionResult {
        val manager = this.context.getSystemService(NotificationManager::class.java)
        if (!manager.isNotificationPolicyAccessGranted) {
            return ActionResult.Failure(FailureKind.PERMISSION_DENIED, "Do Not Disturb access not granted")
        }
        manager.setInterruptionFilter(
            if (action.enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL,
        )
        return ActionResult.Success(if (action.enabled) "on" else "off")
    }
}

class VibrateActionHandler(private val context: Context) : ActionHandler<ActionSpec.Vibrate> {
    override suspend fun execute(action: ActionSpec.Vibrate, context: AutomationContext): ActionResult {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            this.context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            this.context.getSystemService(Vibrator::class.java)
        }
        if (vibrator == null || !vibrator.hasVibrator()) {
            return ActionResult.Failure(FailureKind.NOT_SUPPORTED, "This device has no vibrator")
        }
        val duration = action.durationMs.coerceIn(1, MAX_DURATION_MS)
        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        return ActionResult.Success("$duration ms")
    }

    private companion object {
        const val MAX_DURATION_MS = 10_000L
    }
}
