package com.glancemap.glancemapwearos.core.service.transfer.runtime

import android.os.SystemClock
import android.util.Log
import com.glancemap.glancemapwearos.core.service.diagnostics.TransferDiagnostics
import com.glancemap.glancemapwearos.core.service.transfer.contract.TransferConstants
import com.glancemap.glancemapwearos.core.service.transfer.http.HttpTransferStrategy
import com.glancemap.glancemapwearos.core.service.transfer.http.HttpTransferProgressCallbacks
import com.glancemap.glancemapwearos.core.service.transfer.http.HttpTransferResultNotifier
import com.glancemap.glancemapwearos.core.service.transfer.notifications.NotificationHelper
import com.glancemap.glancemapwearos.core.service.transfer.contract.ReceiverMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

internal class TransferRunner(
    private val host: TransferRuntimeHost,
    private val notificationHelper: NotificationHelper,
    private val httpReceiver: HttpTransferStrategy,
    private val sessionState: TransferSessionState,
    private val sendStatus: suspend (sourceNodeId: String, transferId: String, phase: String, detail: String) -> Unit,
    private val sendAck: suspend (sourceNodeId: String, transferId: String, status: String, detail: String) -> Unit
) {
    suspend fun runHttp(metadata: ReceiverMetadata, path: String) {
        val wakeLock = host.acquireWakeLock("GlanceMap::HttpTransfer", TransferConstants.WAKELOCK_MAX_MS)
        val wifiLock = host.acquireWifiLock("GlanceMap::WifiHighPerf")
        host.releasePrewarmWakeLock("http_transfer_start:${metadata.fileName}")

        val progressCallbacks = HttpTransferProgressCallbacks(
            host = host,
            notificationHelper = notificationHelper,
            metadata = metadata,
            sendStatus = sendStatus
        )
        val resultNotifier = HttpTransferResultNotifier(
            notificationHelper = notificationHelper,
            sendStatus = sendStatus,
            sendAck = sendAck
        )

        notificationHelper.startForeground(metadata.notificationId, metadata.fileName, "Preparing Download…")
        sessionState.registerActiveTransfer(
            transferId = metadata.transferId,
            job = currentCoroutineContext()[Job],
            fileName = metadata.fileName,
            sourceNodeId = metadata.sourceNodeId
        )
        host.onTransferStarted()
        TransferDiagnostics.log(
            "Runner",
            "HTTP start id=${metadata.transferId} file=${metadata.fileName} size=${metadata.totalSize} path=$path"
        )

        try {
            sendStatus(metadata.sourceNodeId, metadata.transferId, "REQUEST_RECEIVED", "Connecting to Phone (HTTP)…")

            if (metadata.checksumSha256.isNullOrBlank()) {
                TransferDiagnostics.warn(
                    "Runner",
                    "Missing checksum id=${metadata.transferId} file=${metadata.fileName}"
                )
                throw IllegalStateException("MISSING_CHECKSUM")
            }
            if (metadata.authToken.isNullOrBlank()) {
                TransferDiagnostics.warn(
                    "Runner",
                    "Missing HTTP token id=${metadata.transferId} file=${metadata.fileName}"
                )
                throw IllegalStateException("MISSING_HTTP_TOKEN")
            }

            val startMs = SystemClock.elapsedRealtime()
            val receivedSha256 = httpReceiver.receive(
                host = host,
                metadata = metadata,
                path = path,
                resumeOffset = 0L,
                onTransferState = progressCallbacks::onTransferState,
                onProgress = progressCallbacks::onProgress
            )
            val durationMs = SystemClock.elapsedRealtime() - startMs

            TransferDiagnostics.log(
                "Runner",
                "HTTP payload received id=${metadata.transferId} file=${metadata.fileName}; verifying checksum"
            )
            verifyChecksumIfNeeded(metadata, receivedSha256)
            val sizeMB = metadata.totalSize / (1024.0 * 1024.0)
            val speedMBps = if (durationMs > 0) sizeMB / (durationMs / 1000.0) else 0.0
            TransferDiagnostics.log(
                "Runner",
                "Summary id=${metadata.transferId} file=${metadata.fileName} size=${metadata.totalSize} durationMs=$durationMs speedMBps=${String.format("%.2f", speedMBps)}"
            )
            resultNotifier.onSuccess(metadata)

        } catch (ce: CancellationException) {
            Log.w(TAG, "⛔ HTTP transfer cancelled", ce)
            TransferDiagnostics.warn(
                "Runner",
                "HTTP cancelled id=${metadata.transferId} file=${metadata.fileName}"
            )
            resultNotifier.onCancelled(metadata)
            throw ce

        } catch (e: Exception) {
            Log.e(TAG, "❌ HTTP transfer failed", e)
            TransferDiagnostics.error(
                "Runner",
                "HTTP failed id=${metadata.transferId} file=${metadata.fileName}",
                e
            )
            resultNotifier.onError(metadata, e)

        } finally {
            TransferDiagnostics.log(
                "Runner",
                "HTTP finished id=${metadata.transferId} file=${metadata.fileName}"
            )
            sessionState.clearActiveTransfer(metadata.transferId)
            host.onTransferFinished()
            host.releaseWakeLock(wakeLock)
            host.releaseWifiLock(wifiLock)
        }
    }

    private suspend fun verifyChecksumIfNeeded(
        metadata: ReceiverMetadata,
        receivedSha256: String?
    ) {
        val expectedSha = metadata.checksumSha256?.lowercase()
        if (expectedSha.isNullOrBlank()) return

        val initialDetail = "Validating checksum…"
        sendStatus(metadata.sourceNodeId, metadata.transferId, "VERIFYING", initialDetail)
        notificationHelper.updateForeground(metadata.notificationId, metadata.fileName, initialDetail, -1)

        var lastVerifyProgressPercent = -1
        var lastVerifyProgressUpdateMs = 0L
        val actualSha = receivedSha256?.lowercase()
            ?: host.computeFinalFileSha256(metadata.fileName) verifyProgress@{ bytesRead, totalBytes ->
                val progressPercent = computeVerificationPercent(bytesRead, totalBytes)
                if (progressPercent < 0) return@verifyProgress

                val nowMs = SystemClock.elapsedRealtime()
                val shouldReport = progressPercent >= 100 ||
                    lastVerifyProgressPercent < 0 ||
                    progressPercent - lastVerifyProgressPercent >= VERIFY_PROGRESS_MIN_STEP_PERCENT ||
                    (nowMs - lastVerifyProgressUpdateMs) >= VERIFY_PROGRESS_MIN_INTERVAL_MS
                if (!shouldReport) return@verifyProgress

                lastVerifyProgressPercent = progressPercent
                lastVerifyProgressUpdateMs = nowMs
                val detail = buildVerificationDetail(progressPercent)
                notificationHelper.updateForeground(metadata.notificationId, metadata.fileName, detail, -1)
                host.appScope.launch {
                    runCatching {
                        sendStatus(metadata.sourceNodeId, metadata.transferId, "VERIFYING", detail)
                    }
                }
            }?.lowercase()
        if (actualSha == null || actualSha != expectedSha) {
            TransferDiagnostics.warn(
                "Runner",
                "Checksum mismatch id=${metadata.transferId} file=${metadata.fileName}"
            )
            host.deleteByName(metadata.fileName)
            throw IllegalStateException("CHECKSUM_MISMATCH")
        }
        TransferDiagnostics.log(
            "Runner",
            "Checksum verified id=${metadata.transferId} file=${metadata.fileName}"
        )
    }

    private companion object {
        const val TAG = "TransferRunner"
        const val VERIFY_PROGRESS_MIN_INTERVAL_MS = 1_000L
        const val VERIFY_PROGRESS_MIN_STEP_PERCENT = 4
    }
}

internal fun computeVerificationPercent(bytesRead: Long, totalBytes: Long): Int {
    if (totalBytes <= 0L) return -1
    return ((bytesRead.coerceAtLeast(0L) * 100L) / totalBytes)
        .toInt()
        .coerceIn(0, 100)
}

internal fun buildVerificationDetail(progressPercent: Int): String {
    return "Validating checksum… ${progressPercent.coerceIn(0, 100)}%"
}
