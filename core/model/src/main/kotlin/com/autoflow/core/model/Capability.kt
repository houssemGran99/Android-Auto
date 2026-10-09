package com.autoflow.core.model

/**
 * An OS capability (runtime permission or special app access) that a trigger,
 * condition or action needs. The Android permission layer maps each capability
 * to concrete permissions / settings screens.
 *
 * [optional] capabilities degrade gracefully when missing (e.g. inexact alarms
 * instead of exact ones) instead of making the feature unusable.
 */
enum class Capability(val optional: Boolean) {
    /** POST_NOTIFICATIONS (Android 13+). */
    NOTIFICATIONS(optional = false),

    /** SCHEDULE_EXACT_ALARM (Android 12+). Without it time triggers may be delayed by a few minutes. */
    EXACT_ALARMS(optional = true),

    /** WRITE_SETTINGS special access, needed to change brightness. */
    WRITE_SETTINGS(optional = false),

    /** Notification policy access, needed to change Do Not Disturb. */
    NOTIFICATION_POLICY(optional = false),

    /** PACKAGE_USAGE_STATS special access, needed to detect which app is in the foreground. */
    USAGE_ACCESS(optional = false),

    /** ACCESS_FINE_LOCATION: Android only reveals the Wi-Fi network name (SSID) with location access. */
    LOCATION(optional = false),

    /** ACCESS_BACKGROUND_LOCATION: needed to read the SSID while the app is not visible. */
    BACKGROUND_LOCATION(optional = false),

    /** BLUETOOTH_CONNECT (Android 12+), needed to read Bluetooth device names. */
    BLUETOOTH_CONNECT(optional = false),

    /**
     * "Display over other apps". Android blocks apps from opening activities while in
     * the background; without it, a tap-to-open notification is shown instead.
     */
    BACKGROUND_ACTIVITY_START(optional = true),

    /** Notification access (NotificationListenerService), needed to see and dismiss other apps' notifications. */
    NOTIFICATION_LISTENER(optional = false),

    /** READ_CALENDAR, needed for calendar event triggers. */
    CALENDAR(optional = false),
}
