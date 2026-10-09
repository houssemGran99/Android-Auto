package com.autoflow.platform.actions

/** A notification currently shown by another app (as seen by the notification listener). */
data class ActiveNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val clearable: Boolean,
)

/**
 * Access to other apps' notifications. Implemented by the app's NotificationListenerService
 * bridge; [isConnected] is false while notification access is disabled or the system has
 * not (re)bound the listener yet.
 */
interface NotificationController {
    val isConnected: Boolean
    fun activeNotifications(): List<ActiveNotification>
    fun dismiss(key: String)
}
