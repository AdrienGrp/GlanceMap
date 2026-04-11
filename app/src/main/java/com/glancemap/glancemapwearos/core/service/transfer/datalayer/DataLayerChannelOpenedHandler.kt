package com.glancemap.glancemapwearos.core.service.transfer.datalayer

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.glancemap.glancemapwearos.core.service.DataLayerListenerService
import com.glancemap.glancemapwearos.core.service.diagnostics.EnergyDiagnostics
import com.glancemap.glancemapwearos.core.service.diagnostics.TransferDiagnostics
import com.glancemap.glancemapwearos.core.service.transfer.contract.TransferConstants
import com.glancemap.glancemapwearos.core.service.transfer.notifications.NotificationHelper
import com.glancemap.glancemapwearos.core.service.transfer.contract.ReceiverMetadata
import com.glancemap.glancemapwearos.core.service.transfer.storage.WatchFileOps
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

internal class DataLayerChannelOpenedHandler(
    private val service: DataLayerListenerService,
    private val notificationHelper: NotificationHelper,
    private val fileOps: WatchFileOps,
    private val transferMutex: Mutex,
    private val channelReceiver: ChannelClientStrategy,
    private val sendAck: suspend (sourceNodeId: String, transferId: String, status: String, detail: String) -> Unit,
    private val popChannelChecksum: (transferId: String) -> String? = { null }
) {
    suspend fun handleChannelOpened(channel: ChannelClient.Channel) {
        if (!channel.path.startsWith(TransferConstants.CHANNEL_PREFIX)) return

        val parsed = parseChannelPath(channel.path) ?: run {
            Log.w(TAG, "Invalid channel path: ${channel.path}")
            TransferDiagnostics.warn("Channel", "Invalid channel path=${channel.path}")
            return
        }

        val transferId = parsed.first
        val fileName = fileOps.sanitizeFileName(parsed.second)
        val notificationId = transferId.hashCode()

        val expectedChecksum = popChannelChecksum(transferId)
        val metadata = ReceiverMetadata(
            transferId = transferId,
            fileName = fileName,
            totalSize = -1L,
            sourceNodeId = channel.nodeId,
            notificationId = notificationId,
            checksumSha256 = expectedChecksum
        )
        EnergyDiagnostics.recordEvent(
            reason = "channel_transfer_start",
            detail = "file=$fileName transferId=$transferId"
        )
        TransferDiagnostics.log(
            "Channel",
            "Open id=$transferId file=$fileName node=${channel.nodeId}"
        )

        transferMutex.withLock {
            if (fileOps.fileExistsOnWatch(fileName)) {
                val msg = "FILE_EXISTS:$fileName"
                TransferDiagnostics.warn("Channel", "Target file already exists id=$transferId file=$fileName")
                notificationHelper.startForeground(metadata.notificationId, metadata.fileName, "Already exists")
                notificationHelper.stopForeground(metadata.notificationId)
                notificationHelper.showError(metadata.notificationId, metadata.fileName, "Already exists")

                sendAck(metadata.sourceNodeId, metadata.transferId, "ERROR", msg)
                runCatching { Wearable.getChannelClient(service).close(channel).await() }
                return
            }

            val wakeLock = service.acquireWakeLock("GlanceMap::ChannelTransfer", TransferConstants.WAKELOCK_MAX_MS)
            service.releasePrewarmWakeLock("channel_transfer_start:$fileName")
            notificationHelper.startForeground(metadata.notificationId, metadata.fileName, "Receiving (Bluetooth)…")

            try {
                service.onTransferStarted()
                val startMs = SystemClock.elapsedRealtime()
                var lastBytesCopied = 0L
                channelReceiver.receiveFromChannel(service, channel, metadata) { bytesCopied ->
                    lastBytesCopied = bytesCopied
                    // Channel progress is optional; keep minimal to avoid overhead
                    if (bytesCopied > 0L) {
                        notificationHelper.updateForeground(
                            metadata.notificationId,
                            metadata.fileName,
                            "Receiving… ${bytesCopied / (1024 * 1024)} MB",
                            -1 // Indeterminate progress for channel transfers
                        )
                    }
                }
                val durationMs = SystemClock.elapsedRealtime() - startMs

                // Verify checksum if available
                val expectedSha = metadata.checksumSha256?.lowercase()
                if (!expectedSha.isNullOrBlank()) {
                    val actualSha = fileOps.computeFinalFileSha256(metadata.fileName)?.lowercase()
                    if (actualSha != null && actualSha != expectedSha) {
                        TransferDiagnostics.warn(
                            "Channel",
                            "Checksum mismatch id=${metadata.transferId} file=${metadata.fileName}"
                        )
                        fileOps.deleteLocalFile(metadata.fileName)
                        throw IllegalStateException("CHECKSUM_MISMATCH")
                    }
                    TransferDiagnostics.log(
                        "Channel",
                        "Checksum verified id=${metadata.transferId} file=${metadata.fileName}"
                    )
                }

                notificationHelper.stopForeground(metadata.notificationId)
                notificationHelper.showCompletion(metadata.notificationId, metadata.fileName, "Saved ✓")
                sendAck(metadata.sourceNodeId, metadata.transferId, "DONE", "")
                val sizeMB = lastBytesCopied / (1024.0 * 1024.0)
                val speedMBps = if (durationMs > 0) sizeMB / (durationMs / 1000.0) else 0.0
                TransferDiagnostics.log(
                    "Channel",
                    "Summary id=${metadata.transferId} file=${metadata.fileName} size=$lastBytesCopied durationMs=$durationMs speedMBps=${String.format("%.2f", speedMBps)}"
                )
                EnergyDiagnostics.recordEvent(
                    reason = "channel_transfer_done",
                    detail = "file=${metadata.fileName} transferId=${metadata.transferId}"
                )

            } catch (e: Exception) {
                Log.e(TAG, "❌ Channel transfer failed", e)
                TransferDiagnostics.error(
                    "Channel",
                    "Failed id=${metadata.transferId} file=${metadata.fileName}",
                    e
                )
                notificationHelper.stopForeground(metadata.notificationId)
                notificationHelper.showError(metadata.notificationId, metadata.fileName, "Failed: ${e.message}")
                sendAck(metadata.sourceNodeId, metadata.transferId, "ERROR", e.message ?: "Unknown error")
                EnergyDiagnostics.recordEvent(
                    reason = "channel_transfer_error",
                    detail = "file=${metadata.fileName} transferId=${metadata.transferId} msg=${e.message ?: "unknown"}"
                )
            } finally {
                service.onTransferFinished()
                service.releaseWakeLock(wakeLock)
            }
        }
    }

    private fun parseChannelPath(path: String): Pair<String, String>? {
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.size < 4) return null
        val transferId = parts[2]
        val safeName = parts[3]
        return transferId to Uri.decode(safeName)
    }

    private companion object {
        const val TAG = "DataLayerChOpen"
    }
}
