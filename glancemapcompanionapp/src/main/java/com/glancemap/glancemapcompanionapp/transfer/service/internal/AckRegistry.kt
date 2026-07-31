package com.glancemap.glancemapcompanionapp.transfer.service.internal

import android.util.Log
import com.glancemap.glancemapcompanionapp.diagnostics.PhoneTransferDiagnostics
import com.glancemap.glancemapcompanionapp.transfer.strategy.TransferResult
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

internal class AckRegistry {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<TransferResult>>()

    fun register(id: String): CompletableDeferred<TransferResult> {
        val d = CompletableDeferred<TransferResult>()
        pending[id] = d
        return d
    }

    fun remove(id: String) {
        pending.remove(id)?.cancel()
    }

    fun complete(
        id: String,
        result: TransferResult,
    ): Boolean {
        val deferred = pending.remove(id) ?: return false
        if (!deferred.isCompleted) deferred.complete(result)
        return true
    }

    fun completeAll(result: TransferResult) {
        pending.values.forEach { d ->
            if (!d.isCompleted) d.complete(result)
        }
        pending.clear()
    }

    fun handleAck(data: ByteArray) {
        runCatching {
            val json = JSONObject(String(data, Charsets.UTF_8))
            val id = json.getString("id")
            val status = json.optString("status", "UNKNOWN")
            val detail = json.optString("detail", "")

            if (status == "DONE" || status == "ERROR") {
                PhoneTransferDiagnostics.log(
                    "Ack",
                    "ACK id=$id status=$status detail=${detail.ifBlank { "na" }}",
                )
                val result =
                    if (status == "DONE") {
                        TransferResult(true, "Transfer successful")
                    } else {
                        TransferResult(false, detail.ifBlank { "Watch reported error" })
                    }
                pending.remove(id)?.complete(result)
            }
        }.onFailure {
            Log.w("AckRegistry", "Failed to parse ACK payload", it)
            PhoneTransferDiagnostics.error("Ack", "Failed to parse ACK payload", it)
        }
    }
}
