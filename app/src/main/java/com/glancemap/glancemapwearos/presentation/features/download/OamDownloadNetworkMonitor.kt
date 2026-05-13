package com.glancemap.glancemapwearos.presentation.features.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry

data class OamDownloadNetworkState(
    val isWifi: Boolean,
    val isBluetooth: Boolean,
    val isCellular: Boolean,
    val isEthernet: Boolean,
    val isVpn: Boolean,
    val hasInternet: Boolean,
    val isValidated: Boolean,
    val isUnmetered: Boolean,
    val isMetered: Boolean,
) {
    val isValidatedWifi: Boolean
        get() = isWifi && hasInternet && isValidated

    val transportLabel: String
        get() =
            when {
                isWifi -> "wifi"
                isBluetooth -> "bluetooth"
                isCellular -> "cellular"
                isEthernet -> "ethernet"
                isVpn -> "vpn"
                else -> "none"
            }

    val userLabel: String
        get() =
            when {
                isValidatedWifi -> "Wi-Fi ready"
                isWifi && hasInternet -> "Wi-Fi connecting"
                isWifi -> "Wi-Fi unavailable"
                hasInternet -> "Not on Wi-Fi"
                else -> "No internet"
            }

    val telemetryFields: String
        get() =
            "transport=$transportLabel " +
                "wifi=$isWifi bluetooth=$isBluetooth cellular=$isCellular ethernet=$isEthernet vpn=$isVpn " +
                "internet=$hasInternet validated=$isValidated unmetered=$isUnmetered metered=$isMetered"
}

class OamDownloadNetworkMonitor(
    context: Context,
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    fun currentState(): OamDownloadNetworkState {
        val network = connectivityManager?.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        return capabilities.toDownloadNetworkState(isMetered = connectivityManager?.isActiveNetworkMetered)
    }

    fun logSnapshot(event: String) {
        DebugTelemetry.log(
            OAM_DOWNLOAD_TELEMETRY_TAG,
            "event=$event ${currentState().telemetryFields}",
        )
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
                    val state = networkCapabilities.toDownloadNetworkState()
                    DebugTelemetry.log(
                        OAM_DOWNLOAD_TELEMETRY_TAG,
                        "event=network_capabilities_changed ${state.telemetryFields}",
                    )
                    if (state.isValidatedWifi) {
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
        val state = capabilities.toDownloadNetworkState()
        DebugTelemetry.log(
            OAM_DOWNLOAD_TELEMETRY_TAG,
            "event=network_available ${state.telemetryFields}",
        )
        if (state.isValidatedWifi) {
            onValidatedWifi()
        }
    }

    private companion object {
        private const val OAM_DOWNLOAD_TELEMETRY_TAG = "OamDownload"
    }
}

private fun NetworkCapabilities?.toDownloadNetworkState(isMetered: Boolean? = null): OamDownloadNetworkState {
    val hasInternet = this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    val isUnmetered = this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
    return OamDownloadNetworkState(
        isWifi = this?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
        isBluetooth = this?.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) == true,
        isCellular = this?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
        isEthernet = this?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true,
        isVpn = this?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
        hasInternet = hasInternet,
        isValidated = this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
        isUnmetered = isUnmetered,
        isMetered = isMetered ?: (hasInternet && !isUnmetered),
    )
}
