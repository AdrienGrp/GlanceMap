package com.glancemap.glancemapwearos.core.service.transfer.http

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import com.glancemap.glancemapwearos.core.service.transfer.util.TransferUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

internal class HttpTransferNetworkSession(
    private val connectivityManager: ConnectivityManager
) {
    private var callback: ConnectivityManager.NetworkCallback? = null

    suspend fun acquireWifi(timeoutMs: Long): Network? {
        val existing = findWifiNetwork()
        if (existing != null) {
            return existing
        }

        Log.d(TAG, "Requesting Wi-Fi...")
        val deferred = CompletableDeferred<Network?>()
        callback = TransferUtils.requestWifiNetwork(connectivityManager, deferred)
        return withTimeoutOrNull(timeoutMs) { deferred.await() }
    }

    fun findWifiNetwork(): Network? {
        val active = connectivityManager.activeNetwork ?: return null
        val caps = connectivityManager.getNetworkCapabilities(active)
        return if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            active
        } else {
            null
        }
    }

    fun isNetworkUsable(network: Network?): Boolean {
        if (network == null) return false
        val caps = connectivityManager.getNetworkCapabilities(network)
        return caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    suspend fun waitForWifiReconnect(timeoutMs: Long, recheckMs: Long): Network? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            coroutineContext.ensureActive()
            val wifi = findWifiNetwork()
            if (wifi != null) return wifi
            delay(recheckMs)
        }
        return null
    }

    fun bindToNetwork(network: Network) {
        runCatching {
            @Suppress("DEPRECATION")
            connectivityManager.bindProcessToNetwork(network)
        }.onSuccess {
            Log.d(TAG, "✅ Bound process to Wi-Fi for HTTP transfer")
        }.onFailure {
            Log.w(TAG, "⚠️ bindProcessToNetwork failed: ${it.message}")
        }
    }

    fun close() {
        callback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        callback = null

        runCatching {
            @Suppress("DEPRECATION")
            connectivityManager.bindProcessToNetwork(null)
        }
    }

    private companion object {
        const val TAG = "HttpNetworkSession"
    }
}
