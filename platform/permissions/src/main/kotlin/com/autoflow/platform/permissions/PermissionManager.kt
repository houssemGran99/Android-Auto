package com.autoflow.platform.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.autoflow.core.model.Capability

enum class CapabilityStatus {
    GRANTED,
    MISSING,

    /** Not required on this Android version. */
    NOT_NEEDED,

    /** Android does not offer this on this version; the app uses a fallback. */
    UNAVAILABLE,
}

/** How the UI should ask for a capability. */
sealed interface GrantRequest {
    /** Request with ActivityResultContracts.RequestMultiplePermissions. */
    data class Runtime(val permissions: List<String>) : GrantRequest

    /** Special app access that can only be granted on a system settings screen. */
    data class SettingsScreen(val intent: Intent) : GrantRequest
}

/** User-facing texts explaining a capability. */
data class CapabilityInfo(
    @StringRes val title: Int,
    @StringRes val rationale: Int,
    @StringRes val deniedMessage: Int,
)

/**
 * Single place that knows how each [Capability] maps to Android permissions,
 * special-access settings screens and SDK-level differences.
 * Nothing is requested at startup: the UI asks only when an automation needs it.
 */
class PermissionManager(context: Context) {
    private val context = context.applicationContext

    fun status(capability: Capability): CapabilityStatus = when (capability) {
        Capability.NOTIFICATIONS -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted(Manifest.permission.POST_NOTIFICATIONS) ->
                CapabilityStatus.MISSING
            !NotificationManagerCompat.from(context).areNotificationsEnabled() -> CapabilityStatus.MISSING
            else -> CapabilityStatus.GRANTED
        }
        Capability.EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms().toStatus()
        } else {
            CapabilityStatus.NOT_NEEDED
        }
        Capability.WRITE_SETTINGS -> Settings.System.canWrite(context).toStatus()
        Capability.NOTIFICATION_POLICY ->
            context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted.toStatus()
        Capability.USAGE_ACCESS -> hasUsageAccess().toStatus()
        Capability.LOCATION -> granted(Manifest.permission.ACCESS_FINE_LOCATION).toStatus()
        Capability.BACKGROUND_LOCATION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION).toStatus()
        } else {
            CapabilityStatus.NOT_NEEDED
        }
        Capability.BLUETOOTH_CONNECT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(Manifest.permission.BLUETOOTH_CONNECT).toStatus()
        } else {
            CapabilityStatus.NOT_NEEDED
        }
        Capability.BACKGROUND_ACTIVITY_START -> if (backgroundActivityStartSupported()) {
            Settings.canDrawOverlays(context).toStatus()
        } else {
            CapabilityStatus.UNAVAILABLE
        }
        Capability.NOTIFICATION_LISTENER ->
            (context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)).toStatus()
        Capability.CALENDAR -> granted(Manifest.permission.READ_CALENDAR).toStatus()
    }

    fun isSatisfied(capability: Capability): Boolean =
        status(capability) in setOf(CapabilityStatus.GRANTED, CapabilityStatus.NOT_NEEDED)

    /** Capabilities from [capabilities] that are still missing, ordered so prerequisites come first. */
    fun missing(capabilities: Collection<Capability>): List<Capability> =
        capabilities.distinct().sortedBy { it.ordinal }.filter { status(it) == CapabilityStatus.MISSING }

    fun grantRequest(capability: Capability): GrantRequest? = when (capability) {
        Capability.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !granted(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            GrantRequest.Runtime(listOf(Manifest.permission.POST_NOTIFICATIONS))
        } else {
            GrantRequest.SettingsScreen(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
        Capability.EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GrantRequest.SettingsScreen(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri()))
        } else {
            null
        }
        Capability.WRITE_SETTINGS -> GrantRequest.SettingsScreen(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUri()))
        Capability.NOTIFICATION_POLICY -> GrantRequest.SettingsScreen(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        Capability.USAGE_ACCESS -> GrantRequest.SettingsScreen(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        Capability.LOCATION -> GrantRequest.Runtime(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
        Capability.BACKGROUND_LOCATION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 11+ shows a settings page for this; it must be requested after foreground location.
            GrantRequest.Runtime(listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        } else {
            null
        }
        Capability.BLUETOOTH_CONNECT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GrantRequest.Runtime(listOf(Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            null
        }
        Capability.BACKGROUND_ACTIVITY_START -> if (backgroundActivityStartSupported()) {
            GrantRequest.SettingsScreen(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri()))
        } else {
            null
        }
        Capability.NOTIFICATION_LISTENER -> GrantRequest.SettingsScreen(Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS))
        Capability.CALENDAR -> GrantRequest.Runtime(listOf(Manifest.permission.READ_CALENDAR))
    }

    /** App details page: fallback when a runtime permission was permanently denied. */
    fun appSettingsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())

    fun info(capability: Capability): CapabilityInfo = when (capability) {
        Capability.NOTIFICATIONS -> CapabilityInfo(
            R.string.capability_notifications_title,
            R.string.capability_notifications_rationale,
            R.string.capability_notifications_denied,
        )
        Capability.EXACT_ALARMS -> CapabilityInfo(
            R.string.capability_exact_alarms_title,
            R.string.capability_exact_alarms_rationale,
            R.string.capability_exact_alarms_denied,
        )
        Capability.WRITE_SETTINGS -> CapabilityInfo(
            R.string.capability_write_settings_title,
            R.string.capability_write_settings_rationale,
            R.string.capability_write_settings_denied,
        )
        Capability.NOTIFICATION_POLICY -> CapabilityInfo(
            R.string.capability_notification_policy_title,
            R.string.capability_notification_policy_rationale,
            R.string.capability_notification_policy_denied,
        )
        Capability.USAGE_ACCESS -> CapabilityInfo(
            R.string.capability_usage_access_title,
            R.string.capability_usage_access_rationale,
            R.string.capability_usage_access_denied,
        )
        Capability.LOCATION -> CapabilityInfo(
            R.string.capability_location_title,
            R.string.capability_location_rationale,
            R.string.capability_location_denied,
        )
        Capability.BACKGROUND_LOCATION -> CapabilityInfo(
            R.string.capability_background_location_title,
            R.string.capability_background_location_rationale,
            R.string.capability_background_location_denied,
        )
        Capability.BLUETOOTH_CONNECT -> CapabilityInfo(
            R.string.capability_bluetooth_title,
            R.string.capability_bluetooth_rationale,
            R.string.capability_bluetooth_denied,
        )
        Capability.BACKGROUND_ACTIVITY_START -> CapabilityInfo(
            R.string.capability_background_start_title,
            if (backgroundActivityStartSupported()) {
                R.string.capability_background_start_rationale
            } else {
                R.string.capability_background_start_unavailable
            },
            R.string.capability_background_start_denied,
        )
        Capability.NOTIFICATION_LISTENER -> CapabilityInfo(
            R.string.capability_notification_listener_title,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                R.string.capability_notification_listener_rationale_restricted
            } else {
                R.string.capability_notification_listener_rationale
            },
            R.string.capability_notification_listener_denied,
        )
        Capability.CALENDAR -> CapabilityInfo(
            R.string.capability_calendar_title,
            R.string.capability_calendar_rationale,
            R.string.capability_calendar_denied,
        )
    }

    /** Battery optimizations can delay or stop the monitoring service on some devices. */
    fun isIgnoringBatteryOptimizations(): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun batteryOptimizationSettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /**
     * Apps with "display over other apps" may start activities from the background up to
     * Android 14. Android 15 additionally requires a visible overlay window, which this app
     * does not create, so a tap-to-open notification is used instead.
     */
    fun backgroundActivityStartSupported(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM

    fun canStartActivitiesFromBackground(): Boolean =
        backgroundActivityStartSupported() && Settings.canDrawOverlays(context)

    private companion object {
        /** Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS (public since API 22, constant hidden in older SDK stubs). */
        const val ACTION_NOTIFICATION_LISTENER_SETTINGS = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun packageUri(): Uri = Uri.fromParts("package", context.packageName, null)

    private fun Boolean.toStatus() = if (this) CapabilityStatus.GRANTED else CapabilityStatus.MISSING
}
