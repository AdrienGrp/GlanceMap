package com.glancemap.glancemapwearos.core.service.transfer.http
import com.glancemap.glancemapwearos.core.service.transfer.contract.ReceiverMetadata
import com.glancemap.glancemapwearos.core.service.transfer.notifications.NotificationHelper

internal class HttpTransferResultNotifier(
    private val notificationHelper: NotificationHelper,
    private val sendStatus: suspend (sourceNodeId: String, transferId: String, phase: String, detail: String) -> Unit,
    private val sendAck: suspend (sourceNodeId: String, transferId: String, status: String, detail: String) -> Unit,
) {
    suspend fun onSuccess(metadata: ReceiverMetadata) {
        notificationHelper.stopForeground(metadata.notificationId)
        notificationHelper.showCompletion(metadata.notificationId, metadata.fileName, "Saved ✓")
        sendStatus(metadata.sourceNodeId, metadata.transferId, "DONE", "")
        sendAck(metadata.sourceNodeId, metadata.transferId, "DONE", "")
    }

    suspend fun onCancelled(metadata: ReceiverMetadata) {
        notificationHelper.stopForeground(metadata.notificationId)
        notificationHelper.showError(metadata.notificationId, metadata.fileName, "Cancelled")
        sendStatus(metadata.sourceNodeId, metadata.transferId, "CANCELLED", "")
        sendAck(metadata.sourceNodeId, metadata.transferId, "ERROR", "Cancelled")
    }

    suspend fun onError(
        metadata: ReceiverMetadata,
        error: Exception,
    ) {
        notificationHelper.stopForeground(metadata.notificationId)
        notificationHelper.showError(metadata.notificationId, metadata.fileName, "Failed: ${error.message}")
        val detail = error.message ?: "Unknown error"
        sendStatus(metadata.sourceNodeId, metadata.transferId, "ERROR", detail)
        sendAck(metadata.sourceNodeId, metadata.transferId, "ERROR", detail)
    }
}
