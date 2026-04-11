package com.glancemap.glancemapcompanionapp.transfer.strategy

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CompletableDeferred

/**
 * Contract for a transfer strategy.
 * The Service owns ACK tracking; strategies receive the deferred to await.
 */
interface TransferStrategy {
    suspend fun transfer(
        context: Context,
        fileUri: Uri,
        targetNodeId: String,
        metadata: TransferMetadata,
        ackDeferred: CompletableDeferred<TransferResult>,
        awaitIfPaused: suspend () -> Unit,
        onProgress: (Float, String) -> Unit
    ): TransferResult
}

data class TransferMetadata(
    val transferId: String,
    val safeFileName: String,
    val displayFileName: String,
    val totalSize: Long,
    val isMapFile: Boolean,
    val checksumSha256: String? = null
)

data class TransferResult(
    val success: Boolean,
    val message: String
)
