package com.glancemap.glancemapcompanionapp

import java.util.Locale
import java.util.concurrent.TimeoutException

internal fun isSelectedWatchReachable(
    selectedWatch: WatchNode?,
    availableWatches: List<WatchNode>,
): Boolean {
    if (selectedWatch == null) return false
    return availableWatches.any { it.id == selectedWatch.id }
}

internal fun selectedWatchDisconnectedStatusMessage(): String = "Selected watch is not currently connected to the phone over Bluetooth / Wear OS. Reconnect it and tap refresh."

internal fun selectedWatchTimeoutStatusMessage(): String = "The watch did not answer. Check the Bluetooth / Wear OS connection, open the watch app, then tap refresh."

internal fun normalizeWatchMapsStatusMessage(error: Throwable): String {
    if (error is TimeoutException) {
        return selectedWatchTimeoutStatusMessage()
    }

    val normalized = buildErrorMessageChain(error)
    return when {
        normalized.contains("target_node_not_connected") ||
            normalized.contains("target node not connected") ||
            normalized.contains("node not connected") ->
            selectedWatchDisconnectedStatusMessage()

        normalized.contains("did not answer in time") ||
            normalized.contains("timed out") ||
            normalized.contains("timeout") ->
            selectedWatchTimeoutStatusMessage()

        else ->
            error.localizedMessage?.takeIf { it.isNotBlank() } ?: "Failed to read watch maps."
    }
}

private fun buildErrorMessageChain(error: Throwable): String {
    val parts = ArrayList<String>()
    var current: Throwable? = error
    while (current != null) {
        current.message
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(parts::add)
        current = current.cause
    }
    return parts.joinToString(" | ").lowercase(Locale.ROOT)
}
