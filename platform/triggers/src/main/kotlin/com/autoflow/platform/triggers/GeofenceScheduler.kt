package com.autoflow.platform.triggers

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.autoflow.core.model.Automation
import com.autoflow.core.model.Capability
import com.autoflow.core.model.GeoPlace
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.TriggerSpec
import com.autoflow.platform.permissions.PermissionManager
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

/**
 * Registers location triggers as geofences with Google Play services. The system delivers
 * transitions to [receiver] even when AutoFlow is not running, so no monitoring service is needed.
 *
 * Geofences are cleared by Android on reboot, app update and when location is turned off, so
 * [refresh] re-registers every active geofence (same ids are replaced, not duplicated).
 */
class GeofenceScheduler(
    context: Context,
    private val receiver: Class<out BroadcastReceiver>,
    private val permissions: PermissionManager,
) {
    private val context = context.applicationContext
    private val client by lazy { LocationServices.getGeofencingClient(this.context) }
    private val prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    enum class Status { REGISTERED, NOTHING_TO_REGISTER, PERMISSION_MISSING, LOCATION_UNAVAILABLE, FAILED }

    data class Registration(val id: String, val automationId: String, val triggerIndex: Int, val place: GeoPlace, val enter: Boolean)

    /** Re-synchronizes geofences with the location triggers of [automations] (empty list removes all). */
    suspend fun refresh(automations: List<Automation>): Status {
        val wanted = registrations(automations)
        val stale = prefs.all.keys - wanted.map { it.id }.toSet()
        if (stale.isNotEmpty()) {
            runCatching { client.removeGeofences(stale.toList()).await() }
            prefs.edit().apply { stale.forEach(::remove) }.apply()
        }
        if (wanted.isEmpty()) return Status.NOTHING_TO_REGISTER
        if (!permissions.isSatisfied(Capability.LOCATION) || !permissions.isSatisfied(Capability.BACKGROUND_LOCATION)) {
            return Status.PERMISSION_MISSING
        }
        return try {
            add(wanted)
            prefs.edit().apply { wanted.forEach { putString(it.id, it.place.name) } }.apply()
            Status.REGISTERED
        } catch (e: ApiException) {
            if (e.statusCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) Status.LOCATION_UNAVAILABLE else Status.FAILED
        } catch (e: SecurityException) {
            Status.PERMISSION_MISSING
        }
    }

    // Permission is checked by refresh() before calling this.
    @SuppressLint("MissingPermission")
    private suspend fun add(registrations: List<Registration>) {
        val geofences = registrations.map { registration ->
            Geofence.Builder()
                .setRequestId(registration.id)
                .setCircularRegion(registration.place.latitude, registration.place.longitude, registration.place.radiusMeters.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(if (registration.enter) Geofence.GEOFENCE_TRANSITION_ENTER else Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        }
        val request = GeofencingRequest.Builder()
            // No initial trigger: re-registering while already inside a place must not fire "arrive" again.
            .setInitialTrigger(0)
            .addGeofences(geofences)
            .build()
        client.addGeofences(request, pendingIntent()).await()
    }

    /** Google Play services fills in the transition extras, so the PendingIntent must be mutable. */
    private fun pendingIntent(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, 0, Intent(context, receiver).setAction(ACTION_GEOFENCE), flags)
    }

    companion object {
        const val ACTION_GEOFENCE = "com.autoflow.action.GEOFENCE"
        private const val PREFS = "geofences"
        private const val SEPARATOR = "/"

        fun id(automationId: String, triggerIndex: Int) = "$automationId$SEPARATOR$triggerIndex"

        fun registrations(automations: List<Automation>): List<Registration> = automations
            .filter { it.enabled }
            .flatMap { automation ->
                automation.triggers.mapIndexedNotNull { index, trigger ->
                    when (trigger) {
                        is TriggerSpec.LocationEnter -> Registration(id(automation.id, index), automation.id, index, trigger.place, enter = true)
                        is TriggerSpec.LocationExit -> Registration(id(automation.id, index), automation.id, index, trigger.place, enter = false)
                        else -> null
                    }
                }
            }

        /** Converts a geofence broadcast into engine events; returns an empty list for errors. */
        fun parse(intent: Intent, placeNames: (String) -> String?): List<TriggerEvent.LocationTransition> {
            val event = GeofencingEvent.fromIntent(intent) ?: return emptyList()
            if (event.hasError()) return emptyList()
            val entered = when (event.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> true
                Geofence.GEOFENCE_TRANSITION_EXIT -> false
                else -> return emptyList()
            }
            return event.triggeringGeofences.orEmpty().mapNotNull { geofence -> toEvent(geofence.requestId, entered, placeNames) }
        }

        fun toEvent(requestId: String, entered: Boolean, placeNames: (String) -> String?): TriggerEvent.LocationTransition? {
            val automationId = requestId.substringBeforeLast(SEPARATOR, missingDelimiterValue = "")
            val index = requestId.substringAfterLast(SEPARATOR).toIntOrNull()
            if (automationId.isEmpty() || index == null) return null
            return TriggerEvent.LocationTransition(automationId, index, entered, placeNames(requestId).orEmpty())
        }
    }

    /** Place name stored at registration time, used for `%trigger_place%`. */
    fun placeName(requestId: String): String? = prefs.getString(requestId, null)
}
