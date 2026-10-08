package com.autoflow.platform.actions

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.autoflow.core.model.ActionResult
import com.autoflow.core.model.Capability
import com.autoflow.core.model.FailureKind
import com.autoflow.platform.permissions.PermissionManager

/**
 * Starts activities on behalf of automations while respecting Android's background
 * activity start restrictions (Android 10+):
 *  - app visible, or "display over other apps" granted (Android ≤ 14) → start directly
 *  - otherwise → post a high-priority tap-to-open notification (supported alternative)
 */
class ActivityLauncher(
    context: Context,
    private val permissions: PermissionManager,
    private val isAppInForeground: () -> Boolean,
) {
    private val context = context.applicationContext

    fun launch(intent: Intent, label: String, automationName: String): ActionResult {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            return ActionResult.Failure(FailureKind.NOT_SUPPORTED, "Nothing on this device can open $label")
        }
        if (isAppInForeground() || permissions.canStartActivitiesFromBackground()) {
            return try {
                context.startActivity(intent)
                ActionResult.Success("Opened $label")
            } catch (e: ActivityNotFoundException) {
                ActionResult.Failure(FailureKind.NOT_SUPPORTED, "Cannot open $label")
            }
        }
        return postTapToOpen(intent, label, automationName)
    }

    @SuppressLint("MissingPermission")
    private fun postTapToOpen(intent: Intent, label: String, automationName: String): ActionResult {
        if (!permissions.isSatisfied(Capability.NOTIFICATIONS)) {
            return ActionResult.Failure(
                FailureKind.PERMISSION_DENIED,
                "Android blocked opening $label in the background and notifications are disabled",
            )
        }
        val requestCode = (label + automationName).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.SHORTCUTS)
            .setSmallIcon(R.drawable.ic_stat_autoflow)
            .setContentTitle(context.getString(R.string.tap_to_open_title, label))
            .setContentText(context.getString(R.string.tap_to_open_text, automationName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(requestCode, notification)
        return ActionResult.Fallback("Android blocks background launches; posted a tap-to-open notification for $label")
    }
}
