package com.autoflow.platform.actions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.autoflow.core.model.NotificationPriority

/** Notification channels used by automation actions. */
object NotificationChannels {
    const val DEFAULT = "automation_default"
    const val HIGH = "automation_high"
    const val LOW = "automation_low"
    const val SHORTCUTS = "automation_shortcuts"

    fun forPriority(priority: NotificationPriority): String = when (priority) {
        NotificationPriority.LOW -> LOW
        NotificationPriority.DEFAULT -> DEFAULT
        NotificationPriority.HIGH -> HIGH
    }

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(DEFAULT, context.getString(R.string.channel_automation_default), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(HIGH, context.getString(R.string.channel_automation_high), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(LOW, context.getString(R.string.channel_automation_low), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(SHORTCUTS, context.getString(R.string.channel_shortcuts), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_shortcuts_description)
                },
            ),
        )
    }
}
