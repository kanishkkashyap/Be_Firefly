package com.firefly.befirefly.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.channels.awaitClose

/**
 * Reports whether the device currently has a *validated* internet connection.
 *
 * IMPORTANT: this tracks ONLY the system's single "default" network (the one Android actually
 * routes traffic through). Registering a per-network callback instead would fire separate events
 * for Wi-Fi AND cellular at the same time — and since the non-default network usually reports
 * "not validated", the flow would rapidly flap true/false even though connectivity is perfectly
 * stable. Using the default-network callback gives one stable signal that follows hand-offs
 * (Wi-Fi -> cellular) cleanly.
 */
class NetworkMonitor(context: Context) {

    private val TAG = "NetworkMonitor"
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isWifiConnected: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isDefaultValidated())
            }

            override fun onLost(network: Network) {
                // Default network dropped; re-evaluate (a fallback network may already be default).
                trySend(isDefaultValidated())
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val ok = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                trySend(ok)
            }
        }

        try {
            // API 24+: only the active default network is reported — no dual-network flapping.
            connectivityManager.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register default network callback", e)
            trySend(false)
        }

        // Emit the current state immediately.
        trySend(isDefaultValidated())

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister network callback", e)
            }
        }
    }.distinctUntilChanged()

    /** True if the current default network has validated (real) internet access. */
    private fun isDefaultValidated(): Boolean {
        return try {
            val active = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking connectivity", e)
            false
        }
    }
}
