package com.glancemap.glancemapwearos.presentation.features.maps.theme

import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

internal const val DEM_NO_INTERNET_MESSAGE =
    "No internet on watch. Connect Wi-Fi or phone internet, then retry DEM download."

internal fun classifyDemFailureAsNetworkUnavailable(
    throwable: Throwable,
    internetAvailableNow: Boolean
): Boolean {
    if (hasDirectDemOfflineCause(throwable)) return true
    if (hasDemTimeoutCause(throwable)) return !internetAvailableNow

    val message = throwable.message.orEmpty().lowercase(Locale.ROOT)
    if (
        message.contains("unable to resolve host") ||
        message.contains("failed to connect") ||
        message.contains("network is unreachable") ||
        message.contains("enetunreach") ||
        message.contains("ehostunreach")
    ) {
        return true
    }

    if (message.contains("timed out") || message.contains("timeout")) {
        return !internetAvailableNow
    }

    return false
}

internal fun buildDemFailureMessage(
    throwable: Throwable,
    networkUnavailable: Boolean
): String {
    if (networkUnavailable) return DEM_NO_INTERNET_MESSAGE
    if (hasDemTimeoutCause(throwable)) {
        return "DEM download timed out. Retry when the watch internet connection is stable."
    }
    return throwable.message?.takeIf { it.isNotBlank() } ?: "DEM download failed."
}

internal fun isRetryableDemDownloadFailure(throwable: Throwable?): Boolean {
    if (throwable == null) return false
    if (throwable is FileNotFoundException) return false
    if (hasDirectDemOfflineCause(throwable) || hasDemTimeoutCause(throwable)) return true

    val message = throwable.message.orEmpty().lowercase(Locale.ROOT)
    return message.contains("http 500") ||
        message.contains("http 502") ||
        message.contains("http 503") ||
        message.contains("http 504") ||
        message.contains("unexpected end of stream") ||
        message.contains("connection reset") ||
        message.contains("broken pipe") ||
        message.contains("timed out") ||
        message.contains("timeout")
}

private fun hasDirectDemOfflineCause(throwable: Throwable): Boolean {
    var current: Throwable? = throwable
    while (current != null) {
        when (current) {
            is UnknownHostException,
            is ConnectException,
            is NoRouteToHostException -> return true
        }
        current = current.cause
    }
    return false
}

private fun hasDemTimeoutCause(throwable: Throwable): Boolean {
    var current: Throwable? = throwable
    while (current != null) {
        if (current is SocketTimeoutException) return true
        current = current.cause
    }
    return false
}
