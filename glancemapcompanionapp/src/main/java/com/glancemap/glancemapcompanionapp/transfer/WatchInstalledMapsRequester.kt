package com.glancemap.glancemapcompanionapp.transfer

import android.util.Log
import com.glancemap.glancemapcompanionapp.WatchInstalledMap
import com.glancemap.glancemapcompanionapp.transfer.datalayer.DataLayerPaths
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException

/**
 * Phone-side helper: asks the watch for installed map files and their bbox.
 *
 * Contract:
 * Phone -> Watch:  PATH_LIST_MAPS
 *   JSON: { "id": "<requestId>" }
 *
 * Watch -> Phone: PATH_LIST_MAPS_RESULT
 *   JSON: { "id": "<requestId>", "maps": [ { "name": "...", "path": "...", "bbox": "..." } ] }
 */
class WatchInstalledMapsRequester(
    private val sendMessage: suspend (nodeId: String, path: String, payload: ByteArray) -> Unit,
) {
    sealed interface Result {
        data class Success(
            val maps: List<WatchInstalledMap>,
        ) : Result

        data class Timeout(
            val exception: TimeoutException,
        ) : Result

        data class Error(
            val exception: Exception,
        ) : Result
    }

    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<List<WatchInstalledMap>>>()

    suspend fun list(nodeId: String): Result {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<List<WatchInstalledMap>>()
        pendingRequests[requestId] = deferred

        val payload =
            JSONObject()
                .put("id", requestId)
                .toString()
                .toByteArray(Charsets.UTF_8)

        return try {
            runCatching {
                sendMessage(nodeId, DataLayerPaths.PATH_PREPARE_CHANNEL, ByteArray(0))
                delay(PREWARM_SETTLE_MS)
            }.onFailure {
                Log.w(TAG, "Map-list prewarm failed for node=$nodeId", it)
            }
            sendMessage(nodeId, DataLayerPaths.PATH_LIST_MAPS, payload)
            val maps = withTimeoutOrNull(REQUEST_TIMEOUT_MS) { deferred.await() }
            if (maps != null) {
                Result.Success(maps)
            } else {
                Result.Timeout(
                    TimeoutException("Watch did not answer in time while reading maps."),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Map list request failed for node=$nodeId", e)
            Result.Error(e)
        } finally {
            pendingRequests.remove(requestId)?.cancel()
        }
    }

    /**
     * Call from service MessageClient listener when PATH_LIST_MAPS_RESULT arrives.
     */
    fun handleMapListResult(data: ByteArray) {
        runCatching {
            val json = JSONObject(String(data, Charsets.UTF_8))
            val requestId = json.optString("id", "")
            if (requestId.isBlank()) return

            val items = json.optJSONArray("maps")
            val maps =
                buildList {
                    if (items != null) {
                        for (i in 0 until items.length()) {
                            val row = items.optJSONObject(i) ?: continue
                            val name = row.optString("name", "").trim()
                            val path = row.optString("path", "").trim()
                            val bbox = row.optString("bbox", "").trim()
                            if (name.isBlank() || bbox.isBlank()) continue
                            add(
                                WatchInstalledMap(
                                    fileName = name,
                                    filePath = path.ifBlank { name },
                                    bbox = bbox,
                                ),
                            )
                        }
                    }
                }.distinctBy { map ->
                    val key = map.filePath.trim()
                    if (key.isNotBlank()) key else map.fileName.lowercase()
                }

            pendingRequests.remove(requestId)?.complete(maps)
        }.onFailure {
            Log.w(TAG, "Failed to parse watch map list response", it)
        }
    }

    private companion object {
        private const val TAG = "WatchMapListRequester"
        private const val REQUEST_TIMEOUT_MS = 8_000L
        private const val PREWARM_SETTLE_MS = 300L
    }
}
