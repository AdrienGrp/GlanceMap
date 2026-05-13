package com.glancemap.glancemapwearos.presentation.features.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

data class OamDownloadNetworkState(
    val isWifi: Boolean,
    val hasInternet: Boolean,
    val isValidated: Boolean,
) {
    val isValidatedWifi: Boolean
        get() = isWifi && hasInternet && isValidated

    val userLabel: String
        get() =
            when {
                isValidatedWifi -> "Wi-Fi ready"
                isWifi && hasInternet -> "Wi-Fi connecting"
                isWifi -> "Wi-Fi unavailable"
                hasInternet -> "Not on Wi-Fi"
                else -> "No internet"
            }
}

class OamDownloadNetworkMonitor(
    context: Context,
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    fun currentState(): OamDownloadNetworkState {
        val network = connectivityManager?.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        return capabilities.toDownloadNetworkState()
    }

    fun watchForValidatedWifi(onValidatedWifi: () -> Unit): AutoCloseable {
        val manager = connectivityManager ?: return AutoCloseable {}
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    maybeNotify(manager, network, onValidatedWifi)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    if (networkCapabilities.toDownloadNetworkState().isValidatedWifi) {
                        onValidatedWifi()
                    }
                }
            }
        val request =
            NetworkRequest
                .Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
        manager.registerNetworkCallback(request, callback)
        return AutoCloseable {
            runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }

    private fun maybeNotify(
        manager: ConnectivityManager,
        network: Network,
        onValidatedWifi: () -> Unit,
    ) {
        val capabilities = manager.getNetworkCapabilities(network)
        if (capabilities.toDownloadNetworkState().isValidatedWifi) {
            onValidatedWifi()
        }
    }
}

private fun NetworkCapabilities?.toDownloadNetworkState(): OamDownloadNetworkState =
    OamDownloadNetworkState(
        isWifi = this?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
        hasInternet = this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
        isValidated = this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
    )
