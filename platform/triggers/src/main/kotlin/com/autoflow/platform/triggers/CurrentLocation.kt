package com.autoflow.platform.triggers

import android.annotation.SuppressLint
import android.content.Context
import com.autoflow.core.model.Capability
import com.autoflow.platform.permissions.PermissionManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

data class Coordinates(val latitude: Double, val longitude: Double)

/** One-shot location reads (picker "use current location", `%latitude%` variables). Never tracks continuously. */
class CurrentLocation(context: Context, private val permissions: PermissionManager) {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context.applicationContext) }

    /** Fresh high-accuracy fix, for the place picker while the app is visible. */
    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMs: Long = 15_000): Coordinates? {
        if (!permissions.isSatisfied(Capability.LOCATION)) return null
        val cancellation = CancellationTokenSource()
        return try {
            withTimeoutOrNull(timeoutMs) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token).await()
            }?.let { Coordinates(it.latitude, it.longitude) }
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalStateException) {
            null
        } finally {
            cancellation.cancel()
        }
    }

    /** Cached last known location (no new fix is requested); null without permission. */
    @SuppressLint("MissingPermission")
    suspend fun lastKnown(timeoutMs: Long = 2_000): Coordinates? {
        if (!permissions.isSatisfied(Capability.LOCATION)) return null
        return try {
            withTimeoutOrNull(timeoutMs) { client.lastLocation.await() }?.let { Coordinates(it.latitude, it.longitude) }
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalStateException) {
            null
        }
    }
}
