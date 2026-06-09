package com.glancemap.glancemapwearos.presentation.features.recording

import android.location.Location
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.data.repository.GpxRepository
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.mapsforge.core.model.LatLong
import java.io.ByteArrayInputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class TraceRecordingViewModel(
    private val gpxRepository: GpxRepository,
    private val settingsRepository: SettingsRepository,
    private val syncManager: SyncManager,
    private val elevationProvider: RecordingElevationProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TraceRecordingUiState())
    val uiState: StateFlow<TraceRecordingUiState> = _uiState.asStateFlow()

    private var sampleIntervalSeconds = SettingsRepository.DEFAULT_RECORDING_SAMPLE_INTERVAL_SECONDS
    private var recordingElevationSource = SettingsRepository.DEFAULT_RECORDING_ELEVATION_SOURCE
    private var lastAcceptedElapsedMs: Long = Long.MIN_VALUE
    private val locationPointMutex = Mutex()
    private var skippedIntervalCount = 0
    private var skippedPausedCount = 0
    private var skippedUnusableLocationCount = 0
    private var demElevationHitCount = 0
    private var demElevationMissCount = 0
    private var gpsElevationUsedCount = 0
    private var acceptedAccuracySumMeters = 0.0
    private var acceptedAccuracyCount = 0
    private var acceptedAccuracyMinMeters: Float? = null
    private var acceptedAccuracyMaxMeters: Float? = null
    private var lastAcceptedPointTimeMillis: Long? = null
    private var gpsActiveDurationMillis: Long = 0L
    private var recordingGapCount: Int = 0
    private var recordingMaxGapMillis: Long = 0L

    init {
        settingsRepository.recordingSampleIntervalSeconds
            .onEach { sampleIntervalSeconds = it }
            .launchIn(viewModelScope)
        settingsRepository.recordingElevationSource
            .onEach { recordingElevationSource = it }
            .launchIn(viewModelScope)
    }

    fun toggleRecording() {
        val state = _uiState.value
        when {
            state.saving -> Unit
            state.active && state.paused -> resumeRecording()
            state.active -> pauseRecording()
            else -> startRecording()
        }
    }

    fun startRecording() {
        val now = System.currentTimeMillis()
        lastAcceptedElapsedMs = Long.MIN_VALUE
        resetSessionTelemetry()
        _uiState.value =
            TraceRecordingUiState(
                active = true,
                paused = false,
                startedAtMillis = now,
                message = "Recording started",
            )
        DebugTelemetry.log(
            "TraceRecording",
            "event=start sampleIntervalSeconds=$sampleIntervalSeconds elevationSource=$recordingElevationSource",
        )
    }

    fun onLocation(location: Location?) {
        if (location == null) return
        val state = _uiState.value
        if (!state.active || state.saving) return
        if (state.paused) {
            skippedPausedCount += 1
            return
        }
        if (!isUsableLocation(location)) {
            skippedUnusableLocationCount += 1
            return
        }

        val nowElapsedMs = SystemClock.elapsedRealtime()
        if (lastAcceptedElapsedMs != Long.MIN_VALUE) {
            val elapsedMs = nowElapsedMs - lastAcceptedElapsedMs
            if (elapsedMs < sampleIntervalSeconds * 1_000L) {
                skippedIntervalCount += 1
                return
            }
        }

        lastAcceptedElapsedMs = nowElapsedMs
        val latitude = location.latitude
        val longitude = location.longitude
        val gpsAltitudeMeters = location.altitude.takeIf { location.hasAltitude() && it.isFinite() }
        val timeMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
        val accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() }
        val speedMps = location.speed.takeIf { location.hasSpeed() }
        val selectedElevationSource = recordingElevationSource
        viewModelScope.launch {
            locationPointMutex.withLock {
                val elevation =
                    elevationProvider.resolveElevation(
                        latitude = latitude,
                        longitude = longitude,
                        gpsAltitudeMeters = gpsAltitudeMeters,
                        source = selectedElevationSource,
                    )
                if (elevation.demAttempted) {
                    if (elevation.demHit) {
                        demElevationHitCount += 1
                    } else {
                        demElevationMissCount += 1
                    }
                }
                if (elevation.gpsUsed) {
                    gpsElevationUsedCount += 1
                }
                val point =
                    RecordedTracePoint(
                        latLong = LatLong(latitude, longitude),
                        elevationMeters = elevation.elevationMeters,
                        timeMillis = timeMillis,
                        accuracyMeters = accuracyMeters,
                        speedMps = speedMps,
                        elevationSource = elevation.resolvedSource,
                    )
                val currentState = _uiState.value
                if (!currentState.active || currentState.saving) return@withLock
                val previous = currentState.points.lastOrNull()
                val addedDistance = previous?.let { haversineMeters(it.latLong, point.latLong) } ?: 0.0
                updateGapTelemetry(point.timeMillis)
                updateAccuracyTelemetry(point.accuracyMeters)
                _uiState.value =
                    currentState.copy(
                        points = currentState.points + point,
                        distanceMeters = currentState.distanceMeters + addedDistance,
                        gpsActiveDurationMillis = gpsActiveDurationMillis,
                        recordingGapCount = recordingGapCount,
                        recordingMaxGapMillis = recordingMaxGapMillis,
                        message = null,
                    )
                val pointCount = currentState.points.size + 1
                if (pointCount == 1 || pointCount % RECORDING_TELEMETRY_POINT_INTERVAL == 0) {
                    DebugTelemetry.log(
                        "TraceRecording",
                        "event=point points=$pointCount " +
                            "distanceMeters=${(currentState.distanceMeters + addedDistance).toInt()} " +
                            "accuracyMeters=${point.accuracyMeters?.toInt() ?: -1} " +
                            "elevationMeters=${point.elevationMeters?.toInt() ?: -1} " +
                            "elevationSource=${point.elevationSource ?: "na"} " +
                            "demHits=$demElevationHitCount demMisses=$demElevationMissCount " +
                            "gpsElevationUsed=$gpsElevationUsedCount " +
                            "gpsActiveDurationMs=$gpsActiveDurationMillis " +
                            "recordingGapCount=$recordingGapCount recordingMaxGapMs=$recordingMaxGapMillis " +
                            "skippedInterval=$skippedIntervalCount skippedPaused=$skippedPausedCount " +
                            "skippedUnusable=$skippedUnusableLocationCount",
                    )
                }
            }
        }
    }

    fun pauseRecording() {
        val state = _uiState.value
        if (!state.active || state.paused || state.saving) return
        _uiState.value =
            state.copy(
                paused = true,
                pausedAtMillis = System.currentTimeMillis(),
                message = "Recording paused",
            )
        DebugTelemetry.log(
            "TraceRecording",
            "event=pause ${recordingSummaryTokens(state, System.currentTimeMillis())}",
        )
    }

    fun resumeRecording() {
        val state = _uiState.value
        if (!state.active || !state.paused || state.saving) return
        val now = System.currentTimeMillis()
        val addedPausedMillis = state.pausedAtMillis?.let { now - it }?.coerceAtLeast(0L) ?: 0L
        lastAcceptedElapsedMs = Long.MIN_VALUE
        _uiState.value =
            state.copy(
                paused = false,
                pausedAtMillis = null,
                accumulatedPausedMillis = state.accumulatedPausedMillis + addedPausedMillis,
                message = "Recording resumed",
            )
        DebugTelemetry.log(
            "TraceRecording",
            "event=resume ${recordingSummaryTokens(_uiState.value, now)}",
        )
    }

    fun finishAndSaveRecording() {
        val state = _uiState.value
        if (!state.active || state.saving) return
        if (state.points.size < 2) {
            _uiState.value = TraceRecordingUiState(message = "Not enough points")
            DebugTelemetry.log(
                "TraceRecording",
                "event=discard reason=not_enough_points ${recordingSummaryTokens(state, System.currentTimeMillis())}",
            )
            return
        }

        val now = System.currentTimeMillis()
        val finalPausedMillis =
            if (state.paused) {
                state.pausedAtMillis?.let { now - it }?.coerceAtLeast(0L) ?: 0L
            } else {
                0L
            }
        _uiState.value =
            state.copy(
                active = false,
                paused = false,
                saving = true,
                accumulatedPausedMillis = state.accumulatedPausedMillis + finalPausedMillis,
                message = "Saving recording",
            )
        DebugTelemetry.log(
            "TraceRecording",
            "event=save_start ${recordingSummaryTokens(state, now, finalPausedMillis)}",
        )
        viewModelScope.launch {
            val saveResult =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val title = buildRecordingTitle(state.startedAtMillis ?: now)
                        val fileName = uniqueRecordingFileName(now)
                        val bytes = encodeRecordedTraceAsGpx(title = title, points = state.points)
                        gpxRepository.saveGpxFileAtomic(
                            fileName = fileName,
                            inputStream = ByteArrayInputStream(bytes),
                            onProgress = {},
                            expectedSize = bytes.size.toLong(),
                        )
                        RecordingSaveInfo(fileName = fileName, byteSize = bytes.size)
                    }
                }
            if (saveResult.isSuccess) {
                val saveInfo = saveResult.getOrNull()
                syncManager.requestGpxSync()
                _uiState.value = TraceRecordingUiState(message = "Recording saved")
                DebugTelemetry.log(
                    "TraceRecording",
                    "event=save_success ${recordingSummaryTokens(state, now, finalPausedMillis)} " +
                        "fileName=${saveInfo?.fileName ?: "na"} byteSize=${saveInfo?.byteSize ?: -1}",
                )
            } else {
                val errorMessage =
                    saveResult.exceptionOrNull()?.localizedMessage
                        ?.takeIf { it.isNotBlank() }
                        ?: "Recording save failed"
                _uiState.value =
                    state.copy(
                        active = true,
                        saving = false,
                        message = errorMessage,
                    )
                DebugTelemetry.log(
                    "TraceRecording",
                    "event=save_failure ${recordingSummaryTokens(state, now, finalPausedMillis)} " +
                        "error=${sanitizeTelemetryValue(errorMessage)}",
                )
            }
        }
    }

    fun discardRecording() {
        val state = _uiState.value
        if (!state.active && !state.saving) return
        _uiState.value = TraceRecordingUiState(message = "Recording discarded")
        DebugTelemetry.log(
            "TraceRecording",
            "event=discard reason=user ${recordingSummaryTokens(state, System.currentTimeMillis())}",
        )
    }

    private suspend fun uniqueRecordingFileName(nowMillis: Long): String {
        val base = buildRecordingFileName(nowMillis).removeSuffix(".gpx")
        var candidate = "$base.gpx"
        var index = 2
        while (gpxRepository.fileExists(candidate)) {
            candidate = "$base-$index.gpx"
            index += 1
        }
        return candidate
    }

    private fun resetSessionTelemetry() {
        skippedIntervalCount = 0
        skippedPausedCount = 0
        skippedUnusableLocationCount = 0
        demElevationHitCount = 0
        demElevationMissCount = 0
        gpsElevationUsedCount = 0
        acceptedAccuracySumMeters = 0.0
        acceptedAccuracyCount = 0
        acceptedAccuracyMinMeters = null
        acceptedAccuracyMaxMeters = null
        lastAcceptedPointTimeMillis = null
        gpsActiveDurationMillis = 0L
        recordingGapCount = 0
        recordingMaxGapMillis = 0L
    }

    private fun updateGapTelemetry(pointTimeMillis: Long) {
        val previousPointTimeMillis = lastAcceptedPointTimeMillis
        if (previousPointTimeMillis != null) {
            val gapMillis = (pointTimeMillis - previousPointTimeMillis).coerceAtLeast(0L)
            val expectedActiveGapMillis = expectedActivePointGapMillis()
            gpsActiveDurationMillis += minOf(gapMillis, expectedActiveGapMillis)
            if (gapMillis > recordingGapThresholdMillis()) {
                recordingGapCount += 1
                recordingMaxGapMillis = maxOf(recordingMaxGapMillis, gapMillis)
            }
        }
        lastAcceptedPointTimeMillis = pointTimeMillis
    }

    private fun expectedActivePointGapMillis(): Long =
        (sampleIntervalSeconds * 1_000L)
            .coerceAtLeast(RECORDING_GPS_ACTIVE_GAP_FLOOR_MS)
            .coerceAtMost(RECORDING_GPS_ACTIVE_GAP_CAP_MS)

    private fun recordingGapThresholdMillis(): Long =
        maxOf(sampleIntervalSeconds * 2_000L, RECORDING_GAP_MIN_THRESHOLD_MS)

    private fun updateAccuracyTelemetry(accuracyMeters: Float?) {
        val accuracy = accuracyMeters ?: return
        if (!accuracy.isFinite() || accuracy < 0f) return
        acceptedAccuracySumMeters += accuracy.toDouble()
        acceptedAccuracyCount += 1
        acceptedAccuracyMinMeters = minOf(acceptedAccuracyMinMeters ?: accuracy, accuracy)
        acceptedAccuracyMaxMeters = maxOf(acceptedAccuracyMaxMeters ?: accuracy, accuracy)
    }

    private fun recordingSummaryTokens(
        state: TraceRecordingUiState,
        nowMillis: Long,
        extraPausedMillis: Long = 0L,
    ): String {
        val pausedMillis =
            state.accumulatedPausedMillis +
                if (extraPausedMillis > 0L) {
                    extraPausedMillis
                } else if (state.paused) {
                    state.pausedAtMillis?.let { nowMillis - it }?.coerceAtLeast(0L) ?: 0L
                } else {
                    0L
                }
        val durationMillis =
            state.startedAtMillis
                ?.let { nowMillis - it - pausedMillis }
                ?.coerceAtLeast(0L)
                ?: 0L
        val elevation = elevationGainLossMeters(state.points)
        val avgAccuracy =
            if (acceptedAccuracyCount > 0) {
                acceptedAccuracySumMeters / acceptedAccuracyCount.toDouble()
            } else {
                null
            }
        return "points=${state.points.size} distanceMeters=${state.distanceMeters.toInt()} " +
            "durationMs=$durationMillis pausedMs=$pausedMillis " +
            "gpsActiveDurationMs=${state.gpsActiveDurationMillis} " +
            "recordingGapCount=${state.recordingGapCount} recordingMaxGapMs=${state.recordingMaxGapMillis} " +
            "lastPointAgeMs=${state.points.lastOrNull()?.timeMillis?.let { nowMillis - it }?.coerceAtLeast(0L) ?: -1} " +
            "elevationGainMeters=${elevation.first.toInt()} elevationLossMeters=${elevation.second.toInt()} " +
            "elevationSource=$recordingElevationSource demHits=$demElevationHitCount " +
            "demMisses=$demElevationMissCount gpsElevationUsed=$gpsElevationUsedCount " +
            "accuracySamples=$acceptedAccuracyCount " +
            "accuracyAvgMeters=${avgAccuracy?.toInt() ?: -1} " +
            "accuracyMinMeters=${acceptedAccuracyMinMeters?.toInt() ?: -1} " +
            "accuracyMaxMeters=${acceptedAccuracyMaxMeters?.toInt() ?: -1} " +
            "skippedInterval=$skippedIntervalCount skippedPaused=$skippedPausedCount " +
            "skippedUnusable=$skippedUnusableLocationCount"
    }
}

private const val RECORDING_GAP_MIN_THRESHOLD_MS = 15_000L
private const val RECORDING_GPS_ACTIVE_GAP_FLOOR_MS = 1_000L
private const val RECORDING_GPS_ACTIVE_GAP_CAP_MS = 15_000L

private data class RecordingSaveInfo(
    val fileName: String,
    val byteSize: Int,
)

private fun isUsableLocation(location: Location): Boolean =
    location.latitude.isFinite() &&
        location.longitude.isFinite() &&
        location.latitude in -90.0..90.0 &&
        location.longitude in -180.0..180.0

private fun haversineMeters(
    a: LatLong,
    b: LatLong,
): Double {
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val h =
        sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 2.0 * EARTH_RADIUS_METERS * atan2(sqrt(h), sqrt(1.0 - h))
}

private fun elevationGainLossMeters(points: List<RecordedTracePoint>): Pair<Double, Double> {
    var gain = 0.0
    var loss = 0.0
    var previous = points.firstOrNull()?.elevationMeters ?: return 0.0 to 0.0
    points.drop(1).forEach { point ->
        val elevation = point.elevationMeters ?: return@forEach
        val delta = elevation - previous
        if (delta > 0.0) {
            gain += delta
        } else {
            loss += -delta
        }
        previous = elevation
    }
    return gain to loss
}

private fun sanitizeTelemetryValue(value: String): String =
    value
        .replace(Regex("\\s+"), "_")
        .take(80)

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val RECORDING_TELEMETRY_POINT_INTERVAL = 10
