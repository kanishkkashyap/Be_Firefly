package com.firefly.befirefly.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class NetworkMonitor(context: Context) {

    private val TAG = "NetworkMonitor"
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isWifiConnected: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val connected = checkInternetConnection()
                Log.d(TAG, "Network available: connected=$connected")
                trySend(connected)
                
                // Verify actual internet reachability (some networks have captive portals)
                if (connected) {
                    verifyReachability { isReachable ->
                        if (!isReachable) {
                            Log.w(TAG, "⚠️ Network available but not reachable (captive portal?)")
                        }
                        trySend(isReachable)
                    }
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                trySend(checkInternetConnection())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val validated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                Log.d(TAG, "Capabilities changed: internet=$hasInternet validated=$validated")
                // Use VALIDATED capability — this means Android has verified actual internet access
                trySend(hasInternet && validated)
            }
        }

        // Listen for ANY internet-capable network (WiFi, cellular, ethernet/emulator)
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            // Permission might be denied or other error
            Log.e(TAG, "Failed to register network callback", e)
            trySend(false)
        }

        // Initial state — also verify reachability
        val initialState = checkInternetConnection()
        trySend(initialState)
        if (initialState) {
            verifyReachability { isReachable ->
                trySend(isReachable)
            }
        }

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister network callback", e)
            }
        }
    }.distinctUntilChanged()

    private fun checkInternetConnection(): Boolean {
        try {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            // Accept WiFi, cellular, or ethernet (emulator uses ethernet)
            val hasTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            // VALIDATED means Android has confirmed real internet access (not just a network connection)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            
            return hasTransport && hasInternet && isValidated
        } catch (e: Exception) {
            Log.e(TAG, "Error checking internet connection", e)
            return false
        }
    }

    /**
     * Verify actual internet reachability by attempting a lightweight connection.
     * This catches cases where the device has a network connection but no real internet
     * (e.g., captive portals, restricted networks).
     */
    private fun verifyReachability(callback: (Boolean) -> Unit) {
        Thread {
            try {
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress("broker.emqx.io", 1883), 5000)
                socket.close()
                Log.d(TAG, "✅ Internet reachability verified (MQTT broker reachable)")
                callback(true)
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Internet reachability check failed: ${e.message}")
                // Fall back to transport-level check — don't mark as offline just because
                // the MQTT broker is temporarily unreachable
                callback(checkInternetConnection())
            }
        }.start()
    }
}
