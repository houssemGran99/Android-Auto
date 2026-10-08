package com.autoflow.app.runtime

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.autoflow.app.MainActivity
import com.autoflow.app.R
import com.autoflow.core.model.Capability
import com.autoflow.platform.permissions.PermissionManager

/** Notifications owned by the app runtime (monitoring service, background runs). */
object AppNotifications {
    private const val CHANNEL_MONITOR = "monitor"
    const val MONITOR_NOTIFICATION_ID = 1001
    const val RUNNING_NOTIFICATION_ID = 1002
    private const val RESUME_NOTIFICATION_ID = 1003

    fun ensureChannels(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_MONITOR,
            context.getString(R.string.channel_monitor),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_monitor_description)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun monitorNotification(context: Context, watching: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(com.autoflow.platform.actions.R.drawable.ic_stat_autoflow)
            .setContentTitle(context.getString(R.string.monitor_notification_title))
            .setContentText(
                if (watching.isEmpty()) {
                    context.getString(R.string.monitor_notification_idle)
                } else {
                    context.getString(R.string.monitor_notification_text, watching)
                },
            )
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    fun runningNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(com.autoflow.platform.actions.R.drawable.ic_stat_autoflow)
            .setContentTitle(context.getString(R.string.running_notification_title))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    /** Shown when Android refused to start the monitoring service from the background. */
    @SuppressLint("MissingPermission")
    fun showResumeMonitoring(context: Context) {
        if (!PermissionManager(context).isSatisfied(Capability.NOTIFICATIONS)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(com.autoflow.platform.actions.R.drawable.ic_stat_autoflow)
            .setContentTitle(context.getString(R.string.resume_monitoring_title))
            .setContentText(context.getString(R.string.resume_monitoring_text))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(RESUME_NOTIFICATION_ID, notification)
    }

    fun cancelResumeMonitoring(context: Context) {
        NotificationManagerCompat.from(context).cancel(RESUME_NOTIFICATION_ID)
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
