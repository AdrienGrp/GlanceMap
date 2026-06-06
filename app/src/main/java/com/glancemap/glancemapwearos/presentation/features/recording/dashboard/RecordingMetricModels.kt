package com.glancemap.glancemapwearos.presentation.features.recording.dashboard

import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.features.recording.RecordedTracePoint
import com.glancemap.glancemapwearos.presentation.features.recording.TraceRecordingUiState
import com.glancemap.glancemapwearos.presentation.formatting.DurationFormatter
import com.glancemap.glancemapwearos.presentation.formatting.UnitFormatter
import kotlin.math.roundToInt

internal enum class RecordingMetricSource {
    INTERNAL_GPS,
    EXTERNAL,
}

internal data class RecordingMetricDefinition(
    val id: String,
    val label: String,
    val source: RecordingMetricSource = RecordingMetricSource.INTERNAL_GPS,
)

internal data class RecordingMetricValue(
    val label: String,
    val value: String,
    val unit: String? = null,
)

internal data class RecordingDashboardSnapshot(
    val durationSeconds: Double,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val currentElevationMeters: Double?,
    val currentSpeedMps: Float?,
    val averageSpeedMps: Double?,
    val gpsAccuracyMeters: Float?,
    val pointCount: Int,
)

internal val recordingMetricDefinitions =
    listOf(
        RecordingMetricDefinition(SettingsRepository.RECORDING_METRIC_DISTANCE, "Distance"),
        RecordingMetricDefinition(SettingsRepository.RECORDING_METRIC_DURATION, "Duration"),
        RecordingMetricDefinition(SettingsRepository.RECORDING_METRIC_ELEVATION_GAIN, "Elev +"),
        RecordingMetricDefinition(SettingsRepository.RECORDING_METRIC_ELEVATION_LOSS, "Elev -"),
        RecordingMetricDefinition(SettingsRepository.RECORDING_METRIC_CURRENT_ELEVATION, "Altitude"),
        RecordingMetricDefinition(SettingsRepository.RECORDING_METRIC_CURRENT_SPEED, "Speed"),
        RecordingMetricDefinition(SettingsRepository.RECORDING_METRIC_AVERAGE_SPEED, "Avg speed"),
        RecordingMetricDefinition(SettingsRepository.RECORDING_METRIC_GPS_ACCURACY, "GPS acc."),
        RecordingMetricDefinition(SettingsRepository.RECORDING_METRIC_POINTS, "Points"),
    )

internal fun metricDefinitionFor(id: String): RecordingMetricDefinition =
    recordingMetricDefinitions.firstOrNull { it.id == id }
        ?: recordingMetricDefinitions.first()

internal fun buildRecordingDashboardSnapshot(
    state: TraceRecordingUiState,
    nowMillis: Long,
): RecordingDashboardSnapshot {
    val startedAt = state.startedAtMillis ?: nowMillis
    val currentPausedMillis =
        if (state.paused) {
            state.pausedAtMillis?.let { nowMillis - it }?.coerceAtLeast(0L) ?: 0L
        } else {
            0L
        }
    val activeDurationMillis =
        (nowMillis - startedAt - state.accumulatedPausedMillis - currentPausedMillis).coerceAtLeast(0L)
    val activeDurationSeconds = activeDurationMillis / 1000.0
    val elevationTotals = elevationGainLossMeters(state.points)
    return RecordingDashboardSnapshot(
        durationSeconds = activeDurationSeconds,
        distanceMeters = state.distanceMeters,
        elevationGainMeters = elevationTotals.first,
        elevationLossMeters = elevationTotals.second,
        currentElevationMeters = state.points.lastOrNull()?.elevationMeters,
        currentSpeedMps = state.points.lastOrNull()?.speedMps,
        averageSpeedMps =
            if (activeDurationSeconds > 0.0) {
                state.distanceMeters / activeDurationSeconds
            } else {
                null
            },
        gpsAccuracyMeters = state.points.lastOrNull()?.accuracyMeters,
        pointCount = state.points.size,
    )
}

internal fun formattedRecordingMetric(
    metricId: String,
    snapshot: RecordingDashboardSnapshot,
    isMetric: Boolean,
): RecordingMetricValue {
    val definition = metricDefinitionFor(metricId)
    return when (definition.id) {
        SettingsRepository.RECORDING_METRIC_DISTANCE -> {
            val (value, unit) = UnitFormatter.formatDistance(snapshot.distanceMeters, isMetric)
            RecordingMetricValue(definition.label, value, unit)
        }
        SettingsRepository.RECORDING_METRIC_DURATION ->
            RecordingMetricValue(definition.label, DurationFormatter.formatDurationShort(snapshot.durationSeconds))
        SettingsRepository.RECORDING_METRIC_ELEVATION_GAIN -> {
            val (value, unit) = UnitFormatter.formatElevation(snapshot.elevationGainMeters, isMetric)
            RecordingMetricValue(definition.label, value, unit)
        }
        SettingsRepository.RECORDING_METRIC_ELEVATION_LOSS -> {
            val (value, unit) = UnitFormatter.formatElevation(snapshot.elevationLossMeters, isMetric)
            RecordingMetricValue(definition.label, value, unit)
        }
        SettingsRepository.RECORDING_METRIC_CURRENT_ELEVATION -> {
            val elevation = snapshot.currentElevationMeters
            if (elevation == null) {
                RecordingMetricValue(definition.label, "--")
            } else {
                val (value, unit) = UnitFormatter.formatElevation(elevation, isMetric)
                RecordingMetricValue(definition.label, value, unit)
            }
        }
        SettingsRepository.RECORDING_METRIC_CURRENT_SPEED ->
            speedMetricValue(definition.label, snapshot.currentSpeedMps?.toDouble(), isMetric)
        SettingsRepository.RECORDING_METRIC_AVERAGE_SPEED ->
            speedMetricValue(definition.label, snapshot.averageSpeedMps, isMetric)
        SettingsRepository.RECORDING_METRIC_GPS_ACCURACY -> {
            val accuracy = snapshot.gpsAccuracyMeters
            if (accuracy == null) {
                RecordingMetricValue(definition.label, "--")
            } else {
                val (value, unit) = UnitFormatter.formatElevation(accuracy.toDouble(), isMetric)
                RecordingMetricValue(definition.label, value, unit)
            }
        }
        SettingsRepository.RECORDING_METRIC_POINTS ->
            RecordingMetricValue(definition.label, snapshot.pointCount.toString())
        else -> RecordingMetricValue(definition.label, "--")
    }
}

private fun speedMetricValue(
    label: String,
    speedMps: Double?,
    isMetric: Boolean,
): RecordingMetricValue {
    if (speedMps == null || !speedMps.isFinite() || speedMps <= 0.0) {
        return RecordingMetricValue(label, "--")
    }
    val value =
        if (isMetric) {
            speedMps * 3.6
        } else {
            speedMps * 2.2369362920544
        }
    return RecordingMetricValue(
        label = label,
        value = (value * 10.0).roundToInt().let { (it / 10.0).toString() },
        unit = if (isMetric) "km/h" else "mph",
    )
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
