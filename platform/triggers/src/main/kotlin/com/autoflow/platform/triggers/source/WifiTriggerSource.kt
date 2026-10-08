package com.autoflow.platform.triggers.source

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import com.autoflow.core.engine.normalizeSsid
import com.autoflow.core.engine.trigger.TriggerSource
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.TriggerFamily
import java.util.concurrent.ConcurrentHashMap

/**
 * Wi-Fi connect / disconnect through a NetworkCallback.
 * The SSID is only available with location permission (Android restriction); without it
 * events carry no network name and only "any network" triggers match.
 * Networks already connected when monitoring starts do not fire.
 */
class WifiTriggerSource(context: Context) : TriggerSource {
    override val family = TriggerFamily.WIFI

    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val networks = ConcurrentHashMap<Network, String>()
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var registeredAt = 0L

    override fun register(onEvent: (TriggerEvent) -> Unit) {
        unregister()
        registeredAt = SystemClock.elapsedRealtime()
        val newCallback = createCallback(onEvent)
        callback = newCallback
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        connectivity.registerNetworkCallback(request, newCallback)
    }

    override fun unregister() {
        callback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        callback = null
        networks.clear()
    }

    private fun createCallback(onEvent: (TriggerEvent) -> Unit): ConnectivityManager.NetworkCallback {
        val handler = object : Handler {
            override fun capabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (networks.containsKey(network)) return
                val ssid = ssidFrom(capabilities).orEmpty()
                networks[network] = ssid
                if (SystemClock.elapsedRealtime() - registeredAt > INITIAL_STATE_WINDOW_MS) {
                    onEvent(TriggerEvent.WifiConnected(ssid.ifEmpty { null }))
                }
            }

            override fun lost(network: Network) {
                val ssid = networks.remove(network) ?: return
                onEvent(TriggerEvent.WifiDisconnected(ssid.ifEmpty { null }))
            }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
                    handler.capabilitiesChanged(network, networkCapabilities)

                override fun onLost(network: Network) = handler.lost(network)
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
                    handler.capabilitiesChanged(network, networkCapabilities)

                override fun onLost(network: Network) = handler.lost(network)
            }
        }
    }

    private interface Handler {
        fun capabilitiesChanged(network: Network, capabilities: NetworkCapabilities)
        fun lost(network: Network)
    }

    private fun ssidFrom(capabilities: NetworkCapabilities): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (capabilities.transportInfo as? WifiInfo)?.ssid?.let(::normalizeSsid)?.let { return it }
        }
        return currentSsid(appContext)
    }

    companion object {
        private const val INITIAL_STATE_WINDOW_MS = 1500L

        /** Legacy API, still the only synchronous way to read the SSID; requires location permission. */
        @Suppress("DEPRECATION")
        fun currentSsid(context: Context): String? = try {
            normalizeSsid(context.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid)
        } catch (e: SecurityException) {
            null
        }
    }
}
