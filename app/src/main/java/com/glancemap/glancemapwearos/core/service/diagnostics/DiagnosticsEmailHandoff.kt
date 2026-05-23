package com.glancemap.glancemapwearos.core.service.diagnostics

import android.content.Context
import android.util.Base64
import com.glancemap.glancemapwearos.core.service.transfer.contract.TransferConstants
import com.glancemap.shared.transfer.TransferDataLayerContract
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

internal object DiagnosticsEmailHandoff {
    private const val MAX_MESSAGE_PAYLOAD_BYTES = 90 * 1024
    private const val CHUNK_CONTENT_CHARS = 55_000

    suspend fun sendToPhone(
        context: Context,
        diagnosticsFile: File,
        subject: String,
    ): Boolean {
        val fullText = runCatching { diagnosticsFile.readText() }.getOrNull() ?: return false
        val payloads = buildPayloads(diagnosticsFile.name, subject, fullText) ?: return false

        val nodes =
            runCatching { Wearable.getNodeClient(context).connectedNodes.await() }.getOrNull()
                ?: return false
        if (nodes.isEmpty()) return false

        val messageClient = Wearable.getMessageClient(context)
        val sortedNodes = nodes.sortedByDescending { it.isNearby }
        for (node in sortedNodes) {
            val sent =
                runCatching {
                    payloads.forEach { payload ->
                        messageClient
                            .sendMessage(
                                node.id,
                                TransferConstants.PATH_DIAGNOSTICS_EMAIL_REQUEST,
                                payload,
                            ).await()
                    }
                }.isSuccess
            if (sent) return true
        }
        return false
    }

    private fun buildPayloads(
        fileName: String,
        subject: String,
        fullText: String,
    ): List<ByteArray>? {
        val compressed = gzip(fullText.toByteArray(Charsets.UTF_8))
        val base64 = Base64.encodeToString(compressed, Base64.NO_WRAP)
        val fullPayload =
            encodePayload(
                fileName = fileName,
                subject = subject,
                base64Content = base64,
                transferId = null,
                chunkIndex = 0,
                chunkCount = 1,
                rawTextBytes = fullText.toByteArray(Charsets.UTF_8).size,
                compressedBytes = compressed.size,
            )
        if (fullPayload.size <= MAX_MESSAGE_PAYLOAD_BYTES) {
            return listOf(fullPayload)
        }

        val chunks = base64.chunked(CHUNK_CONTENT_CHARS)
        val transferId = buildTransferId(fileName, compressed.size, chunks.size)
        val payloads =
            chunks.mapIndexed { index, chunk ->
                encodePayload(
                    fileName = fileName,
                    subject = subject,
                    base64Content = chunk,
                    transferId = transferId,
                    chunkIndex = index,
                    chunkCount = chunks.size,
                    rawTextBytes = fullText.toByteArray(Charsets.UTF_8).size,
                    compressedBytes = compressed.size,
                )
            }
        if (payloads.any { it.size > MAX_MESSAGE_PAYLOAD_BYTES }) {
            return null
        }
        return payloads
    }

    private fun encodePayload(
        fileName: String,
        subject: String,
        base64Content: String,
        transferId: String?,
        chunkIndex: Int,
        chunkCount: Int,
        rawTextBytes: Int,
        compressedBytes: Int,
    ): ByteArray {
        val json =
            JSONObject()
            .put("email", TransferDataLayerContract.DIAGNOSTICS_SUPPORT_EMAIL)
            .put("subject", subject)
            .put("fileName", fileName)
            .put("encoding", "gzip_base64_utf8_text")
            .put("truncated", false)
            .put("chunked", chunkCount > 1)
            .put("chunkIndex", chunkIndex)
            .put("chunkCount", chunkCount)
            .put("rawTextBytes", rawTextBytes)
            .put("compressedBytes", compressedBytes)
            .put("content", base64Content)
        transferId?.let { json.put("transferId", it) }
        return json
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    private fun buildTransferId(
        fileName: String,
        compressedBytes: Int,
        chunkCount: Int,
    ): String =
        "${System.currentTimeMillis()}_${fileName.hashCode()}_${compressedBytes}_$chunkCount"

    private fun gzip(bytes: ByteArray): ByteArray {
        val buffer = ByteArrayOutputStream()
        GZIPOutputStream(buffer).use { gzip ->
            gzip.write(bytes)
        }
        return buffer.toByteArray()
    }
}
