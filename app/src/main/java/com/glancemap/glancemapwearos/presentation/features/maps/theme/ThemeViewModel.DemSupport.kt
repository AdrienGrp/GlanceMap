package com.glancemap.glancemapwearos.presentation.features.maps.theme

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.math.sqrt

internal const val DEM_NO_INTERNET_MESSAGE =
    "No internet on watch. Connect Wi-Fi or phone internet, then retry DEM download."

internal class DemInvalidTileException(
    message: String,
) : IOException(message)

internal class DemResumeRejectedException(
    message: String,
) : IOException(message)

internal fun classifyDemFailureAsNetworkUnavailable(
    throwable: Throwable,
    internetAvailableNow: Boolean,
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
    networkUnavailable: Boolean,
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
    if (throwable is DemInvalidTileException) return false
    if (throwable is ZipException) return false
    if (throwable is DemResumeRejectedException) return true
    if (hasDirectDemOfflineCause(throwable) || hasDemTimeoutCause(throwable)) return true

    val message = throwable.message.orEmpty().lowercase(Locale.ROOT)
    return message.contains("http 500") ||
        message.contains("http 502") ||
        message.contains("http 503") ||
        message.contains("http 504") ||
        message.contains("unexpected end of stream") ||
        message.contains("connection reset") ||
        message.contains("broken pipe") ||
        message.contains("connection closed") ||
        message.contains("connection refused") ||
        message.contains("http 408") ||
        message.contains("http 429") ||
        message.contains("timed out") ||
        message.contains("timeout")
}

internal fun validateDemTileFile(file: File) {
    if (!file.exists() || !file.isFile) {
        throw DemInvalidTileException("DEM tile was not saved.")
    }
    if (file.length() <= 0L) {
        throw DemInvalidTileException("DEM tile is empty.")
    }

    if (file.name.endsWith(".zip", ignoreCase = true)) {
        validateDemZip(file)
    } else if (!isPlausibleHgtByteSize(file.length())) {
        throw DemInvalidTileException("DEM tile has invalid HGT size: ${file.length()} bytes.")
    }
}

private fun validateDemZip(file: File) {
    val hgtEntries =
        ZipFile(file).use { zip ->
            zip
                .entries()
                .asSequence()
                .filter { entry ->
                    !entry.isDirectory && entry.name.endsWith(".hgt", ignoreCase = true)
                }.toList()
        }

    if (hgtEntries.isEmpty()) {
        throw DemInvalidTileException("DEM ZIP does not contain an HGT file.")
    }

    val firstInvalidSize =
        hgtEntries
            .mapNotNull { entry -> entry.size.takeIf { it > 0L } }
            .firstOrNull { size -> !isPlausibleHgtByteSize(size) }
    if (firstInvalidSize != null) {
        throw DemInvalidTileException("DEM ZIP contains invalid HGT size: $firstInvalidSize bytes.")
    }
}

private fun isPlausibleHgtByteSize(size: Long): Boolean {
    if (size <= 0L || size % Short.SIZE_BYTES != 0L) return false
    val sampleCount = size / Short.SIZE_BYTES
    val rowLen = sqrt(sampleCount.toDouble()).toInt()
    return rowLen * rowLen.toLong() == sampleCount && rowLen in 1201..3601
}

private fun hasDirectDemOfflineCause(throwable: Throwable): Boolean {
    var current: Throwable? = throwable
    while (current != null) {
        when (current) {
            is UnknownHostException,
            is ConnectException,
            is NoRouteToHostException,
            -> return true
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
