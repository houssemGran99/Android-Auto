package com.autoflow.app.runtime

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.autoflow.app.di.ApplicationScope
import com.autoflow.core.engine.AutomationEngine
import com.autoflow.core.model.TriggerEvent
import com.autoflow.platform.actions.ActiveNotification
import com.autoflow.platform.actions.NotificationController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects the system-bound [AutoFlowNotificationListener] to the action layer. The system
 * creates and binds the listener only while notification access is enabled.
 */
@Singleton
class NotificationListenerBridge @Inject constructor() : NotificationController {
    @Volatile
    private var listener: AutoFlowNotificationListener? = null

    override val isConnected: Boolean get() = listener != null

    internal fun attach(service: AutoFlowNotificationListener?) {
        listener = service
    }

    override fun activeNotifications(): List<ActiveNotification> =
        runCatching { listener?.activeNotifications.orEmpty().map { it.toActive() } }.getOrDefault(emptyList())

    override fun dismiss(key: String) {
        runCatching { listener?.cancelNotification(key) }
    }

    private fun StatusBarNotification.toActive() = ActiveNotification(
        key = key,
        packageName = packageName,
        title = notification.title(),
        text = notification.text(),
        clearable = isClearable,
    )
}

internal fun Notification.title(): String = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

internal fun Notification.text(): String =
    (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
        ?.toString().orEmpty()

/**
 * Receives notifications posted by other apps and feeds "notification received" triggers.
 * Ongoing notifications (music players, downloads), group summaries and updates that do not
 * change the visible text are ignored to avoid trigger storms. Nothing is stored.
 */
@AndroidEntryPoint
class AutoFlowNotificationListener : NotificationListenerService() {
    @Inject lateinit var bridge: NotificationListenerBridge

    @Inject lateinit var engine: AutomationEngine

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    /** Last seen title/text per notification key, so silent updates do not re-trigger. */
    private val lastSeen = object : LinkedHashMap<String, Int>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > MAX_TRACKED
    }

    override fun onListenerConnected() {
        bridge.attach(this)
    }

    override fun onListenerDisconnected() {
        bridge.attach(null)
    }

    override fun onDestroy() {
        bridge.attach(null)
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || sbn.isOngoing) return
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val title = notification.title()
        val text = notification.text()
        if (title.isEmpty() && text.isEmpty()) return
        val fingerprint = (title + "\u0000" + text).hashCode()
        synchronized(lastSeen) {
            if (lastSeen[sbn.key] == fingerprint) return
            lastSeen[sbn.key] = fingerprint
        }
        val event = TriggerEvent.NotificationPosted(sbn.packageName, appLabel(sbn.packageName), title, text)
        appScope.launch {
            try {
                engine.handleEvent(event)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Notification trigger failed", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        synchronized(lastSeen) { lastSeen.remove(sbn.key) }
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    private companion object {
        const val TAG = "AutoFlow"
        const val MAX_TRACKED = 200
    }
}
