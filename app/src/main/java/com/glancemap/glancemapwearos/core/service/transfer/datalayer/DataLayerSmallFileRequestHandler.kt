package com.glancemap.glancemapwearos.core.service.transfer.datalayer

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.glancemap.glancemapwearos.core.service.DataLayerListenerService
import com.glancemap.glancemapwearos.core.service.diagnostics.EnergyDiagnostics
import com.glancemap.glancemapwearos.core.service.diagnostics.TransferDiagnostics
import com.glancemap.glancemapwearos.core.service.transfer.contract.TransferConstants
import com.glancemap.glancemapwearos.core.service.transfer.notifications.NotificationHelper
import com.glancemap.glancemapwearos.core.service.transfer.storage.WatchFileOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream

internal class DataLayerSmallFileRequestHandler(
    private val service: DataLayerListenerService,
    private val notificationHelper: NotificationHelper,
    private val fileOps: WatchFileOps,
    private val transferMutex: Mutex,
    private val sendAck: suspend (sourceNodeId: String, transferId: String, status: String, detail: String) -> Unit
) {
    private val appScope get() = service.appScope()

    fun handle(messageEvent: MessageEvent) {
        val path = messageEvent.path
        val sourceNodeId = messageEvent.sourceNodeId
        val bytes = messageEvent.data

        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.size < 4) {
            Log.w(TAG, "Invalid small file path: $path")
            TransferDiagnostics.warn("Small", "Invalid small-file path=$path")
            return
        }

        // Path formats:
        //   v1: /glancemap/small_file/{transferId}/{encodedName}          (4 parts)
        //   v2: /glancemap/small_file/{transferId}/{sha256}/{encodedName} (5 parts)
        val transferId = parts[2]
        val expectedChecksum: String?
        val encodedName: String
        if (parts.size >= 5) {
            expectedChecksum = parts[3].takeIf { it.isNotBlank() }
            encodedName = parts[4]
        } else {
            expectedChecksum = null
            encodedName = parts[3]
        }
        val fileName = fileOps.sanitizeFileName(Uri.decode(encodedName))
        val notificationId = transferId.hashCode()
        EnergyDiagnostics.recordEvent(
            reason = "small_transfer_start",
            detail = "file=$fileName transferId=$transferId bytes=${bytes.size}"
        )
        TransferDiagnostics.log(
            "Small",
            "Start id=$transferId file=$fileName bytes=${bytes.size}"
        )

        if (bytes.size > TransferConstants.SMALL_FILE_MAX_BYTES) {
            TransferDiagnostics.warn(
                "Small",
                "Too large for small-file path id=$transferId file=$fileName bytes=${bytes.size}"
            )
            appScope.launch(Dispatchers.IO) {
                sendAck(
                    sourceNodeId,
                    transferId,
                    "ERROR",
                    "Small file too large (${bytes.size} bytes). Use Channel."
                )
            }
            return
        }

        if (!fileOps.isSupportedTransferFileName(fileName)) {
            TransferDiagnostics.warn(
                "Small",
                "Unsupported file type id=$transferId file=$fileName"
            )
            appScope.launch(Dispatchers.IO) {
                sendAck(sourceNodeId, transferId, "ERROR", "Unsupported file type")
            }
            return
        }

        appScope.launch(Dispatchers.IO) {
            transferMutex.withLock {
                if (fileOps.fileExistsOnWatch(fileName)) {
                    val msg = "FILE_EXISTS:$fileName"
                    TransferDiagnostics.warn("Small", "Target file already exists id=$transferId file=$fileName")
                    notificationHelper.startForeground(notificationId, fileName, "Already exists")
                    notificationHelper.stopForeground(notificationId)
                    notificationHelper.showError(notificationId, fileName, "Already exists")
                    sendAck(sourceNodeId, transferId, "ERROR", msg)
                    return@withLock
                }

                val wakeLock = service.acquireWakeLock("GlanceMap::SmallTransfer", TransferConstants.SMALL_WAKELOCK_MS)
                service.releasePrewarmWakeLock("small_transfer_start:$fileName")
                try {
                    service.onTransferStarted()
                    notificationHelper.startForeground(notificationId, fileName, "Saving…")

                    val startMs = SystemClock.elapsedRealtime()
                    ByteArrayInputStream(bytes).use { input ->
                        service.saveFile(
                            fileName = fileName,
                            inputStream = input,
                            expectedSize = bytes.size.toLong(),
                            resumeOffset = 0L,
                            onProgress = { /* no progress */ }
                        )
                    }
                    val durationMs = SystemClock.elapsedRealtime() - startMs

                    // Verify checksum if available
                    val expectedSha = expectedChecksum?.lowercase()
                    if (!expectedSha.isNullOrBlank()) {
                        val actualSha = fileOps.computeFinalFileSha256(fileName)?.lowercase()
                        if (actualSha != null && actualSha != expectedSha) {
                            TransferDiagnostics.warn(
                                "Small",
                                "Checksum mismatch id=$transferId file=$fileName"
                            )
                            fileOps.deleteLocalFile(fileName)
                            throw IllegalStateException("CHECKSUM_MISMATCH")
                        }
                        TransferDiagnostics.log(
                            "Small",
                            "Checksum verified id=$transferId file=$fileName"
                        )
                    }

                    notificationHelper.stopForeground(notificationId)
                    notificationHelper.showCompletion(notificationId, fileName, "Saved ✓")

                    sendAck(sourceNodeId, transferId, "DONE", "")
                    val sizeMB = bytes.size / (1024.0 * 1024.0)
                    val speedMBps = if (durationMs > 0) sizeMB / (durationMs / 1000.0) else 0.0
                    TransferDiagnostics.log(
                        "Small",
                        "Summary id=$transferId file=$fileName size=${bytes.size} durationMs=$durationMs speedMBps=${String.format("%.2f", speedMBps)}"
                    )
                    EnergyDiagnostics.recordEvent(
                        reason = "small_transfer_done",
                        detail = "file=$fileName transferId=$transferId bytes=${bytes.size}"
                    )

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Small file save failed", e)
                    TransferDiagnostics.error(
                        "Small",
                        "Failed id=$transferId file=$fileName",
                        e
                    )
                    notificationHelper.stopForeground(notificationId)
                    notificationHelper.showError(notificationId, fileName, "Failed: ${e.message}")
                    sendAck(sourceNodeId, transferId, "ERROR", e.message ?: "Unknown error")
                    EnergyDiagnostics.recordEvent(
                        reason = "small_transfer_error",
                        detail = "file=$fileName transferId=$transferId msg=${e.message ?: "unknown"}"
                    )
                } finally {
                    service.onTransferFinished()
                    service.releaseWakeLock(wakeLock)
                }
            }
        }
    }

    private companion object {
        const val TAG = "DataLayerSmallReq"
    }
}
