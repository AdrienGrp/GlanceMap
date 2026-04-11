package com.glancemap.glancemapwearos.core.service.diagnostics

import android.content.Context
import android.util.Base64
import com.google.android.gms.wearable.Wearable
import com.glancemap.glancemapwearos.core.service.transfer.contract.TransferConstants
import com.glancemap.shared.transfer.TransferDataLayerContract
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

internal object DiagnosticsEmailHandoff {
    private const val MAX_MESSAGE_PAYLOAD_BYTES = 90 * 1024
    private val tailSizes = intArrayOf(220_000, 160_000, 110_000, 75_000, 50_000)

    suspend fun sendToPhone(context: Context, diagnosticsFile: File, subject: String): Boolean {
        val fullText = runCatching { diagnosticsFile.readText() }.getOrNull() ?: return false
        val payload = buildPayload(diagnosticsFile.name, subject, fullText) ?: return false

        val nodes = runCatching { Wearable.getNodeClient(context).connectedNodes.await() }.getOrNull()
            ?: return false
        if (nodes.isEmpty()) return false

        val messageClient = Wearable.getMessageClient(context)
        val sortedNodes = nodes.sortedByDescending { it.isNearby }
        for (node in sortedNodes) {
            val sent = runCatching {
                messageClient.sendMessage(
                    node.id,
                    TransferConstants.PATH_DIAGNOSTICS_EMAIL_REQUEST,
                    payload
                ).await()
            }.isSuccess
            if (sent) return true
        }
        return false
    }

    private fun buildPayload(fileName: String, subject: String, fullText: String): ByteArray? {
        val fullPayload = encodePayload(fileName, subject, fullText, truncated = false)
        if (fullPayload.size <= MAX_MESSAGE_PAYLOAD_BYTES) {
            return fullPayload
        }

        for (tailSize in tailSizes) {
            if (fullText.length <= tailSize) continue
            val truncatedText = buildString {
                appendLine("Diagnostics were truncated before phone handoff due payload size.")
                appendLine()
                append(fullText.takeLast(tailSize))
            }
            val payload = encodePayload(fileName, subject, truncatedText, truncated = true)
            if (payload.size <= MAX_MESSAGE_PAYLOAD_BYTES) {
                return payload
            }
        }

        return null
    }

    private fun encodePayload(fileName: String, subject: String, text: String, truncated: Boolean): ByteArray {
        val compressed = gzip(text.toByteArray(Charsets.UTF_8))
        val base64 = Base64.encodeToString(compressed, Base64.NO_WRAP)
        return JSONObject()
            .put("email", TransferDataLayerContract.DIAGNOSTICS_SUPPORT_EMAIL)
            .put("subject", subject)
            .put("fileName", fileName)
            .put("encoding", "gzip_base64_utf8_text")
            .put("truncated", truncated)
            .put("content", base64)
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val buffer = ByteArrayOutputStream()
        GZIPOutputStream(buffer).use { gzip ->
            gzip.write(bytes)
        }
        return buffer.toByteArray()
    }
}
