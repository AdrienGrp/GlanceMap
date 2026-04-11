package com.glancemap.glancemapwearos.core.service.transfer.util
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CompletableDeferred

object TransferUtils {
    const val TAG = "TransferUtils"
    
    // ✅ Increased buffer size for better throughput
    const val SOCKET_BUFFER_BYTES = 2 * 1024 * 1024 // 2MB

    fun requestWifiNetwork(
        connectivityManager: ConnectivityManager,
        networkDeferred: CompletableDeferred<Network?>
    ): ConnectivityManager.NetworkCallback {

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            // IMPORTANT: do NOT require INTERNET, local hotspot networks may not report it reliably
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = connectivityManager.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    Log.d(TAG, "✅ Wi-Fi network became available")
                    if (!networkDeferred.isCompleted) networkDeferred.complete(network)
                }
            }

            override fun onUnavailable() {
                Log.w(TAG, "⚠️ Wi-Fi unavailable")
                if (!networkDeferred.isCompleted) networkDeferred.complete(null)
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "⚠️ Wi-Fi lost")
            }
        }

        runCatching { connectivityManager.requestNetwork(request, callback) }
            .onFailure {
                Log.e(TAG, "requestNetwork failed", it)
                if (!networkDeferred.isCompleted) networkDeferred.complete(null)
            }

        return callback
    }
}
