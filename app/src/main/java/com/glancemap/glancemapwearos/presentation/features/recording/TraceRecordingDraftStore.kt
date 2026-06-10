package com.glancemap.glancemapwearos.presentation.features.recording

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.mapsforge.core.model.LatLong
import java.io.File

class TraceRecordingDraftStore(
    context: Context,
) {
    private val draftDir: File = context.getDir("recording_drafts", Context.MODE_PRIVATE)
    private val metadataFile = File(draftDir, "current.json")
    private val metadataTempFile = File(draftDir, "current.json.tmp")
    private val gpxFile = File(draftDir, "current.gpx")
    private val gpxTempFile = File(draftDir, "current.gpx.tmp")

    suspend fun load(): TraceRecordingDraft? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!metadataFile.exists()) return@withContext null
                val json = JSONObject(metadataFile.readText())
                val pointsJson = json.optJSONArray("points") ?: JSONArray()
                val points =
                    buildList {
                        for (index in 0 until pointsJson.length()) {
                            val pointJson = pointsJson.optJSONObject(index) ?: continue
                            add(
                                RecordedTracePoint(
                                    latLong =
                                        LatLong(
                                            pointJson.getDouble("lat"),
                                            pointJson.getDouble("lon"),
                                        ),
                                    elevationMeters = pointJson.optionalDouble("elevationMeters"),
                                    timeMillis = pointJson.optLong("timeMillis", 0L).takeIf { it > 0L }
                                        ?: continue,
                                    accuracyMeters = pointJson.optionalFloat("accuracyMeters"),
                                    speedMps = pointJson.optionalFloat("speedMps"),
                                    elevationSource = pointJson.optionalString("elevationSource"),
                                ),
                            )
                        }
                    }
                TraceRecordingDraft(
                    active = json.optBoolean("active", true),
                    paused = json.optBoolean("paused", false),
                    startedAtMillis = json.optLong("startedAtMillis", 0L).takeIf { it > 0L },
                    pausedAtMillis = json.optLong("pausedAtMillis", 0L).takeIf { it > 0L },
                    accumulatedPausedMillis = json.optLong("accumulatedPausedMillis", 0L).coerceAtLeast(0L),
                    distanceMeters = json.optDouble("distanceMeters", 0.0).takeIf { it.isFinite() } ?: 0.0,
                    gpsActiveDurationMillis = json.optLong("gpsActiveDurationMillis", 0L).coerceAtLeast(0L),
                    recordingGapCount = json.optInt("recordingGapCount", 0).coerceAtLeast(0),
                    recordingMaxGapMillis = json.optLong("recordingMaxGapMillis", 0L).coerceAtLeast(0L),
                    lastUiAction = json.optionalString("lastUiAction"),
                    points = points,
                )
            }.getOrNull()
        }

    suspend fun save(
        state: TraceRecordingUiState,
        lastUiAction: String?,
    ) = withContext(Dispatchers.IO) {
        if (!draftDir.exists()) {
            draftDir.mkdirs()
        }
        val json =
            JSONObject()
                .put("active", state.active)
                .put("paused", state.paused)
                .put("startedAtMillis", state.startedAtMillis ?: 0L)
                .put("pausedAtMillis", state.pausedAtMillis ?: 0L)
                .put("accumulatedPausedMillis", state.accumulatedPausedMillis)
                .put("distanceMeters", state.distanceMeters)
                .put("gpsActiveDurationMillis", state.gpsActiveDurationMillis)
                .put("recordingGapCount", state.recordingGapCount)
                .put("recordingMaxGapMillis", state.recordingMaxGapMillis)
                .put("lastUiAction", lastUiAction ?: JSONObject.NULL)
                .put(
                    "points",
                    JSONArray().also { array ->
                        state.points.forEach { point ->
                            array.put(point.toJson())
                        }
                    },
                )
        metadataTempFile.writeText(json.toString())
        metadataTempFile.renameAtomicallyTo(metadataFile)

        val title = buildRecordingTitle(state.startedAtMillis ?: System.currentTimeMillis())
        gpxTempFile.writeBytes(encodeRecordedTraceAsGpx(title = title, points = state.points))
        gpxTempFile.renameAtomicallyTo(gpxFile)
    }

    suspend fun clear() =
        withContext(Dispatchers.IO) {
            metadataFile.delete()
            metadataTempFile.delete()
            gpxFile.delete()
            gpxTempFile.delete()
        }

    fun draftPath(): String = gpxFile.absolutePath
}

data class TraceRecordingDraft(
    val active: Boolean,
    val paused: Boolean,
    val startedAtMillis: Long?,
    val pausedAtMillis: Long?,
    val accumulatedPausedMillis: Long,
    val distanceMeters: Double,
    val gpsActiveDurationMillis: Long,
    val recordingGapCount: Int,
    val recordingMaxGapMillis: Long,
    val lastUiAction: String?,
    val points: List<RecordedTracePoint>,
)

private fun RecordedTracePoint.toJson(): JSONObject =
    JSONObject()
        .put("lat", latLong.latitude)
        .put("lon", latLong.longitude)
        .put("elevationMeters", elevationMeters ?: JSONObject.NULL)
        .put("timeMillis", timeMillis)
        .put("accuracyMeters", accuracyMeters ?: JSONObject.NULL)
        .put("speedMps", speedMps ?: JSONObject.NULL)
        .put("elevationSource", elevationSource ?: JSONObject.NULL)

private fun JSONObject.optionalDouble(key: String): Double? =
    if (isNull(key)) {
        null
    } else {
        optDouble(key).takeIf { it.isFinite() }
    }

private fun JSONObject.optionalFloat(key: String): Float? =
    optionalDouble(key)?.toFloat()

private fun JSONObject.optionalString(key: String): String? =
    if (isNull(key)) {
        null
    } else {
        optString(key).takeIf { it.isNotBlank() }
    }

private fun File.renameAtomicallyTo(target: File) {
    if (target.exists()) {
        target.delete()
    }
    if (!renameTo(target)) {
        copyTo(target, overwrite = true)
        delete()
    }
}
