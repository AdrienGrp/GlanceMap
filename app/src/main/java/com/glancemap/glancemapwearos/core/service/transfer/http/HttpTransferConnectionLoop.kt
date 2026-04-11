package com.glancemap.glancemapwearos.core.service.transfer.http

import android.net.Network
import android.os.SystemClock
import android.util.Log
import com.glancemap.glancemapwearos.core.service.diagnostics.TransferDiagnostics
import com.glancemap.glancemapwearos.core.service.transfer.contract.ReceiverMetadata
import com.glancemap.glancemapwearos.core.service.transfer.runtime.TransferRuntimeHost
import com.glancemap.shared.transfer.TransferDataLayerContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

internal class HttpTransferConnectionLoop(
    private val host: TransferRuntimeHost,
    private val networkSession: HttpTransferNetworkSession,
    private val connectTimeoutMs: Int,
    private val readTimeoutMs: Int,
    private val connectRetryWindowMs: Long,
    private val connectRetryDelayMs: Long,
    private val networkPauseTimeoutMs: Long,
    private val networkRecheckMs: Long
) {
    private sealed interface ResumePreparation {
        data class Continue(val offset: Long) : ResumePreparation
        data class Completed(val sha256: String?) : ResumePreparation
    }

    internal data class ReceiveResult(
        val sha256: String?,
        val finalNetwork: Network
    )

    suspend fun receive(
        metadata: ReceiverMetadata,
        baseUrlStr: String,
        url: URL,
        initialNetwork: Network,
        resumeOffset: Long,
        skipInitialProbe: Boolean,
        onTransferState: (phase: String, detail: String) -> Unit,
        onProgress: (Long) -> Unit
    ): ReceiveResult {
        var activeWifi = initialNetwork
        when (val prepared = prepareResumeOffset(metadata, resumeOffset, onTransferState)) {
            is ResumePreparation.Completed -> {
                TransferDiagnostics.log(
                    "HttpConn",
                    "Recovered completed partial id=${metadata.transferId} file=${metadata.fileName}"
                )
                return ReceiveResult(
                    sha256 = prepared.sha256,
                    finalNetwork = activeWifi
                )
            }
            is ResumePreparation.Continue -> Unit
        }
        if (!skipInitialProbe) {
            probeServer(activeWifi, URL("$baseUrlStr/"), metadata)
        } else {
            Log.d(TAG, "Skipping HTTP root probe for warm session")
            TransferDiagnostics.log(
                "HttpConn",
                "Skip root probe id=${metadata.transferId} for warm session file=${metadata.fileName}"
            )
        }

        var conn: HttpURLConnection? = null
        var networkPaused = false
        var pausedSinceMs = 0L

        try {
            var connectDeadlineMs = SystemClock.elapsedRealtime() + connectRetryWindowMs
            var currentRetryBudgetMs = connectRetryWindowMs
            var lastError: Throwable? = null
            var attempt = 0

            var desiredOffset = when (val prepared = prepareResumeOffset(metadata, resumeOffset, onTransferState)) {
                is ResumePreparation.Completed -> {
                    return ReceiveResult(
                        sha256 = prepared.sha256,
                        finalNetwork = activeWifi
                    )
                }
                is ResumePreparation.Continue -> prepared.offset
            }

            TransferDiagnostics.log(
                "HttpConn",
                "Session start id=${metadata.transferId} file=${metadata.fileName} " +
                    "resumeOffset=$desiredOffset totalSize=${metadata.totalSize} " +
                    "isResume=${desiredOffset > 0L} budgetMs=$connectRetryWindowMs"
            )

            while (SystemClock.elapsedRealtime() < connectDeadlineMs) {
                coroutineContext.ensureActive()
                attempt++

                runCatching { conn?.disconnect() }
                conn = null

                try {
                    val connection = (activeWifi.openConnection(url) as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = connectTimeoutMs
                        readTimeout = readTimeoutMs
                        doInput = true
                        useCaches = false
                        instanceFollowRedirects = true
                        setRequestProperty("Connection", "Keep-Alive")
                        setRequestProperty("Accept-Encoding", "identity")
                        metadata.authToken?.let {
                            setRequestProperty(TransferDataLayerContract.HTTP_AUTH_HEADER, it)
                        }

                        if (desiredOffset > 0L) {
                            setRequestProperty("Range", "bytes=$desiredOffset-")
                        }
                    }
                    conn = connection

                    Log.d(TAG, "HTTP connect id=${metadata.transferId} attempt=$attempt rangeOffset=$desiredOffset")
                    connection.connect()

                    val code = connection.responseCode
                    Log.d(TAG, "HTTP Response: $code")

                    if (desiredOffset > 0L) {
                        when (code) {
                            HttpURLConnection.HTTP_PARTIAL -> {
                                // expected resume behavior
                            }

                            HttpURLConnection.HTTP_OK -> {
                                Log.w(TAG, "Server ignored Range; restarting from 0")
                                TransferDiagnostics.warn(
                                    "HttpConn",
                                    "Server ignored range id=${metadata.transferId} file=${metadata.fileName} offset=$desiredOffset; restarting"
                                )
                                desiredOffset = 0L
                                connection.disconnect()
                                delay(connectRetryDelayMs)
                                continue
                            }

                            HTTP_REQUESTED_RANGE_NOT_SATISFIABLE -> {
                                when (val repaired = recoverFromInvalidRange(metadata, onTransferState)) {
                                    is ResumePreparation.Completed -> {
                                        return ReceiveResult(
                                            sha256 = repaired.sha256,
                                            finalNetwork = activeWifi
                                        )
                                    }
                                    is ResumePreparation.Continue -> {
                                        desiredOffset = repaired.offset
                                        connection.disconnect()
                                        delay(connectRetryDelayMs)
                                        continue
                                    }
                                }
                            }

                            else -> throw IOException("Server Error $code: ${connection.responseMessage}")
                        }
                    } else if (code != HttpURLConnection.HTTP_OK) {
                        throw IOException("Server Error $code: ${connection.responseMessage}")
                    }

                    if (networkPaused) {
                        networkPaused = false
                        pausedSinceMs = 0L
                        attempt = 0
                        TransferDiagnostics.log(
                            "HttpConn",
                            "Connection restored id=${metadata.transferId} file=${metadata.fileName} resumeOffset=$desiredOffset"
                        )
                        onTransferState("RESUMED", "Connection restored. Resuming transfer…")
                    }

                    val receivedSha256 = connection.inputStream.use { input ->
                        host.saveFile(
                            fileName = metadata.fileName,
                            inputStream = input,
                            expectedSize = metadata.totalSize.takeIf { it > 0 },
                            resumeOffset = desiredOffset,
                            keepPartialOnFailure = true,
                            computeSha256 = shouldComputeInlineChecksumForHttp(desiredOffset),
                            onProgress = onProgress
                        )
                    }

                    Log.d(TAG, "✅ HTTP Receive Complete")
                    TransferDiagnostics.log(
                        "HttpConn",
                        "HTTP receive complete id=${metadata.transferId} file=${metadata.fileName}"
                    )
                    return ReceiveResult(
                        sha256 = receivedSha256,
                        finalNetwork = activeWifi
                    )

                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: IOException) {
                    lastError = e
                    desiredOffset = host.getPartialSize(metadata.fileName)
                    val hasPartialData = desiredOffset > 0L
                    val nowMs = SystemClock.elapsedRealtime()
                    val currentWifi = networkSession.findWifiNetwork()
                    if (!networkPaused) {
                        networkPaused = true
                        pausedSinceMs = nowMs
                        val detail = if (currentWifi == null) {
                            "Network lost. Waiting for Wi-Fi…"
                        } else {
                            "Connection interrupted. Waiting to resume…"
                        }
                        Log.w(
                            TAG,
                            "HTTP paused file=${metadata.fileName} partialBytes=$desiredOffset " +
                                "hasPartial=$hasPartialData currentWifi=${currentWifi != null} " +
                                "attempt=$attempt error=${e.message}"
                        )
                        TransferDiagnostics.warn(
                            "HttpConn",
                            "Paused id=${metadata.transferId} file=${metadata.fileName} partialBytes=$desiredOffset hasPartial=$hasPartialData wifiAvailable=${currentWifi != null} attempt=$attempt"
                        )
                        onTransferState("PAUSED", detail)
                    }

                    if (networkPaused && pausedSinceMs > 0L) {
                        val pausedForMs = nowMs - pausedSinceMs
                        val pauseBudgetMs = if (hasPartialData) {
                            networkPauseTimeoutMs
                        } else {
                            connectRetryWindowMs
                        }
                        currentRetryBudgetMs = pauseBudgetMs
                        connectDeadlineMs = maxOf(connectDeadlineMs, pausedSinceMs + pauseBudgetMs)
                        Log.d(
                            TAG,
                            "HTTP retry budget file=${metadata.fileName} partialBytes=$desiredOffset " +
                                "pausedForMs=$pausedForMs budgetMs=$pauseBudgetMs deadlineInMs=${connectDeadlineMs - nowMs}"
                        )
                        TransferDiagnostics.log(
                            "HttpConn",
                            "Retry budget id=${metadata.transferId} file=${metadata.fileName} partialBytes=$desiredOffset pausedForMs=$pausedForMs budgetMs=$pauseBudgetMs"
                        )
                        if (pausedForMs > pauseBudgetMs) {
                            throw IOException(
                                "Transfer paused for more than ${pauseBudgetMs / 1000}s",
                                e
                            )
                        }
                    }

                    if (currentWifi == null) {
                        val remainingPauseBudgetMs = (networkPauseTimeoutMs - (nowMs - pausedSinceMs))
                            .coerceAtLeast(0L)
                        val restoredNetwork = networkSession.waitForWifiReconnect(
                            timeoutMs = remainingPauseBudgetMs,
                            recheckMs = networkRecheckMs
                        ) ?: throw IOException(
                            "No Wi-Fi network after ${networkPauseTimeoutMs / 1000}s",
                            e
                        )

                        Log.i(
                            TAG,
                            "Wi-Fi restored for file=${metadata.fileName} partialBytes=$desiredOffset " +
                                "remainingPauseBudgetMs=$remainingPauseBudgetMs"
                        )
                        TransferDiagnostics.log(
                            "HttpConn",
                            "Wi-Fi restored id=${metadata.transferId} file=${metadata.fileName} partialBytes=$desiredOffset remainingBudgetMs=$remainingPauseBudgetMs"
                        )
                        activeWifi = restoredNetwork
                        networkSession.bindToNetwork(restoredNetwork)
                        desiredOffset = when (val prepared = prepareResumeOffset(metadata, desiredOffset, onTransferState)) {
                            is ResumePreparation.Completed -> {
                                return ReceiveResult(
                                    sha256 = prepared.sha256,
                                    finalNetwork = activeWifi
                                )
                            }
                            is ResumePreparation.Continue -> prepared.offset
                        }
                        attempt = 0
                        // Start a fresh connection window once Wi-Fi is back.
                        connectDeadlineMs = SystemClock.elapsedRealtime() + connectRetryWindowMs
                        currentRetryBudgetMs = if (desiredOffset > 0L) {
                            networkPauseTimeoutMs
                        } else {
                            connectRetryWindowMs
                        }
                        continue
                    }

                    if (shouldRefreshBinding(attempt, e)) {
                        Log.i(
                            TAG,
                            "Refreshing Wi-Fi binding file=${metadata.fileName} partialBytes=$desiredOffset " +
                                "attempt=$attempt error=${e.message}"
                        )
                        TransferDiagnostics.warn(
                            "HttpConn",
                            "Refreshing Wi-Fi binding file=${metadata.fileName} partialBytes=$desiredOffset attempt=$attempt"
                        )
                        val reboundWifi = networkSession.acquireWifi(REFRESH_BIND_TIMEOUT_MS)
                            ?: currentWifi
                        activeWifi = reboundWifi
                        networkSession.bindToNetwork(reboundWifi)
                        runCatching { probeServer(reboundWifi, URL("$baseUrlStr/"), metadata) }
                            .onSuccess {
                                Log.d(TAG, "HTTP probe recovered after reconnect attempt")
                                TransferDiagnostics.log(
                                    "HttpConn",
                                    "Probe recovered after rebind id=${metadata.transferId} file=${metadata.fileName}"
                                )
                                attempt = 0
                            }
                            .onFailure {
                                Log.w(TAG, "HTTP probe still failing after Wi-Fi rebind: ${it.message}")
                                TransferDiagnostics.warn(
                                    "HttpConn",
                                    "Probe still failing after rebind id=${metadata.transferId} file=${metadata.fileName}"
                                )
                            }
                    } else if (currentWifi != activeWifi) {
                        activeWifi = currentWifi
                        networkSession.bindToNetwork(currentWifi)
                    }

                    Log.w(TAG, "HTTP failed (${e.message}). Retrying...", e)
                    val backoff = (connectRetryDelayMs * attempt).coerceAtMost(3_000L)
                    delay(backoff)
                }
            }

            Log.e(
                TAG,
                "HTTP connect window exhausted file=${metadata.fileName} partialBytes=$desiredOffset " +
                    "budgetMs=$currentRetryBudgetMs lastError=${lastError?.message}"
            )
            TransferDiagnostics.error(
                "HttpConn",
                "Connect window exhausted id=${metadata.transferId} file=${metadata.fileName} partialBytes=$desiredOffset budgetMs=$currentRetryBudgetMs",
                lastError
            )
            throw IOException(
                "Failed to connect to phone HTTP server within ${currentRetryBudgetMs}ms. Last error: ${lastError?.message}",
                lastError
            )
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private suspend fun prepareResumeOffset(
        metadata: ReceiverMetadata,
        resumeOffset: Long,
        onTransferState: (phase: String, detail: String) -> Unit
    ): ResumePreparation {
        val expectedSize = metadata.totalSize.takeIf { it > 0L }
        var desiredOffset = maxOf(
            resumeOffset.coerceAtLeast(0L),
            host.getPartialSize(metadata.fileName)
        )

        if (expectedSize == null || desiredOffset < expectedSize) {
            return ResumePreparation.Continue(desiredOffset)
        }

        val expectedSha = metadata.checksumSha256?.lowercase()
        if (desiredOffset > expectedSize) {
            TransferDiagnostics.warn(
                "HttpConn",
                "Oversized partial id=${metadata.transferId} file=${metadata.fileName} partialBytes=$desiredOffset expectedBytes=$expectedSize"
            )
            onTransferState("VERIFYING", "Repairing partial file…")
            val truncated = host.truncatePartial(metadata.fileName, expectedSize)
            if (!truncated) {
                host.deletePartial(metadata.fileName)
                return ResumePreparation.Continue(0L)
            }
            desiredOffset = host.getPartialSize(metadata.fileName)
        }

        if (desiredOffset == expectedSize && !expectedSha.isNullOrBlank()) {
            onTransferState("VERIFYING", "Checking partial file…")
            val partialSha = host.computePartialFileSha256(metadata.fileName)?.lowercase()
            if (partialSha == expectedSha && host.promotePartialToFinal(metadata.fileName)) {
                return ResumePreparation.Completed(partialSha)
            }
            TransferDiagnostics.warn(
                "HttpConn",
                "Invalid complete partial reset id=${metadata.transferId} file=${metadata.fileName}"
            )
            host.deletePartial(metadata.fileName)
            return ResumePreparation.Continue(0L)
        }

        if (desiredOffset >= expectedSize) {
            TransferDiagnostics.warn(
                "HttpConn",
                "Partial at/over expected size without valid checksum id=${metadata.transferId} file=${metadata.fileName}; resetting"
            )
            host.deletePartial(metadata.fileName)
            return ResumePreparation.Continue(0L)
        }

        return ResumePreparation.Continue(desiredOffset)
    }

    private suspend fun recoverFromInvalidRange(
        metadata: ReceiverMetadata,
        onTransferState: (phase: String, detail: String) -> Unit
    ): ResumePreparation {
        TransferDiagnostics.warn(
            "HttpConn",
            "Server rejected range id=${metadata.transferId} file=${metadata.fileName}; repairing partial"
        )
        return prepareResumeOffset(metadata, 0L, onTransferState)
    }

    private fun shouldRefreshBinding(attempt: Int, error: IOException): Boolean {
        val message = error.message?.lowercase().orEmpty()
        return attempt % 4 == 0 || "timeout" in message || "timed out" in message
    }

    private fun probeServer(network: Network, url: URL, metadata: ReceiverMetadata) {
        var c: HttpURLConnection? = null
        try {
            c = (network.openConnection(url) as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1500
                readTimeout = 1500
                doInput = true
                useCaches = false
                setRequestProperty("Connection", "close")
                setRequestProperty("Accept-Encoding", "identity")
            }
            c.connect()
            val code = c.responseCode
            Log.d(TAG, "✅ Probe / => $code")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Probe failed for $url", e)
            TransferDiagnostics.warn(
                "HttpConn",
                "Probe failed id=${metadata.transferId} file=${metadata.fileName} url=$url"
            )
            throw IOException("Cannot reach phone HTTP server at $url (${e.message})", e)
        } finally {
            runCatching { c?.disconnect() }
        }
    }

    private companion object {
        const val TAG = "HttpConnLoop"
        const val REFRESH_BIND_TIMEOUT_MS = 2_000L
        const val HTTP_REQUESTED_RANGE_NOT_SATISFIABLE = 416
    }
}

internal fun shouldComputeInlineChecksumForHttp(resumeOffset: Long): Boolean {
    return resumeOffset <= 0L
}
