package com.glancemap.glancemapwearos.presentation.features.recording.dashboard

import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.features.recording.RecordedTracePoint
import com.glancemap.glancemapwearos.presentation.features.recording.TraceRecordingUiState
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.haversineMeters
import com.glancemap.glancemapwearos.presentation.formatting.UnitFormatter
import java.text.DecimalFormat
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

internal enum class RecordingMetricSource {
    INTERNAL_GPS,
    INTERNAL_SENSOR,
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

data class RecordingCalorieEstimate(
    val grossKcal: Double = 0.0,
    val activeKcal: Double = 0.0,
    val restingKcal: Double = 0.0,
    val pandolfBaseGrossKcal: Double = 0.0,
    val pandolfBaseActiveKcal: Double = 0.0,
    val pandolfBaseRestingKcal: Double = 0.0,
    val lcdaGrossKcal: Double = 0.0,
    val lcdaActiveKcal: Double = 0.0,
    val lcdaRestingKcal: Double = 0.0,
)

data class RecordingDashboardSnapshot(
    val durationSeconds: Double,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val currentElevationMeters: Double?,
    val currentSpeedMps: Float?,
    val averageSpeedMps: Double?,
    val gpsAccuracyMeters: Float?,
    val pointCount: Int,
    val gpsActiveDurationSeconds: Double,
    val recordingGapCount: Int,
    val recordingMaxGapSeconds: Double,
    val userWeightKg: Float = SettingsRepository.DEFAULT_USER_WEIGHT_KG,
    val backpackWeightKg: Float = SettingsRepository.DEFAULT_BACKPACK_WEIGHT_KG,
    val calorieEstimate: RecordingCalorieEstimate = RecordingCalorieEstimate(),
    val heartRateBpm: Int? = null,
    val stepCount: Int? = null,
    val cadenceSpm: Int? = null,
    val barometricPressureHpa: Double? = null,
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
        RecordingMetricDefinition(SettingsRepository.RECORDING_METRIC_CURRENT_PACE, "Pace"),
        RecordingMetricDefinition(SettingsRepository.RECORDING_METRIC_AVERAGE_PACE, "Avg pace"),
        RecordingMetricDefinition(
            SettingsRepository.RECORDING_METRIC_HEART_RATE,
            "Heart rate",
            RecordingMetricSource.INTERNAL_SENSOR,
        ),
        RecordingMetricDefinition(
            SettingsRepository.RECORDING_METRIC_STEPS,
            "Steps",
            RecordingMetricSource.INTERNAL_SENSOR,
        ),
        RecordingMetricDefinition(
            SettingsRepository.RECORDING_METRIC_CADENCE,
            "Cadence",
            RecordingMetricSource.INTERNAL_SENSOR,
        ),
        RecordingMetricDefinition(
            SettingsRepository.RECORDING_METRIC_BAROMETRIC_PRESSURE,
            "Pressure",
            RecordingMetricSource.INTERNAL_SENSOR,
        ),
        RecordingMetricDefinition(SettingsRepository.RECORDING_METRIC_CALORIES, "Calories"),
        RecordingMetricDefinition(SettingsRepository.RECORDING_METRIC_ACTIVE_CALORIES, "Active cal"),
        RecordingMetricDefinition(SettingsRepository.RECORDING_METRIC_RESTING_CALORIES, "Rest cal"),
    )

internal fun metricDefinitionFor(id: String): RecordingMetricDefinition =
    recordingMetricDefinitions.firstOrNull { it.id == id }
        ?: recordingMetricDefinitions.first()

internal fun buildRecordingDashboardSnapshot(
    state: TraceRecordingUiState,
    nowMillis: Long,
    userWeightKg: Float = SettingsRepository.DEFAULT_USER_WEIGHT_KG,
    backpackWeightKg: Float = SettingsRepository.DEFAULT_BACKPACK_WEIGHT_KG,
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
    val calorieEstimate =
        estimateRecordingCalories(
            points = state.points,
            userWeightKg = userWeightKg,
            backpackWeightKg = backpackWeightKg,
        )
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
        gpsActiveDurationSeconds = state.gpsActiveDurationMillis / 1000.0,
        recordingGapCount = state.recordingGapCount,
        recordingMaxGapSeconds = state.recordingMaxGapMillis / 1000.0,
        userWeightKg = userWeightKg,
        backpackWeightKg = backpackWeightKg,
        calorieEstimate = calorieEstimate,
        heartRateBpm = state.heartRateBpm,
        stepCount = state.stepCount,
        cadenceSpm = state.cadenceSpm,
        barometricPressureHpa = state.barometricPressureHpa,
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
            val (value, unit) = formatRecordingDistance(snapshot.distanceMeters, isMetric)
            RecordingMetricValue(definition.label, value, unit)
        }
        SettingsRepository.RECORDING_METRIC_DURATION ->
            RecordingMetricValue(definition.label, formatRecordingDurationClock(snapshot.durationSeconds))
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
        SettingsRepository.RECORDING_METRIC_CURRENT_PACE ->
            paceMetricValue(definition.label, snapshot.currentSpeedMps?.toDouble(), isMetric)
        SettingsRepository.RECORDING_METRIC_AVERAGE_PACE ->
            paceMetricValue(definition.label, snapshot.averageSpeedMps, isMetric)
        SettingsRepository.RECORDING_METRIC_HEART_RATE ->
            sensorIntegerMetricValue(definition.label, snapshot.heartRateBpm, "bpm")
        SettingsRepository.RECORDING_METRIC_STEPS ->
            sensorIntegerMetricValue(definition.label, snapshot.stepCount, null)
        SettingsRepository.RECORDING_METRIC_CADENCE ->
            sensorIntegerMetricValue(definition.label, snapshot.cadenceSpm, "spm")
        SettingsRepository.RECORDING_METRIC_BAROMETRIC_PRESSURE ->
            pressureMetricValue(definition.label, snapshot.barometricPressureHpa)
        SettingsRepository.RECORDING_METRIC_CALORIES ->
            caloriesMetricValue(definition.label, snapshot.calorieEstimate.grossKcal)
        SettingsRepository.RECORDING_METRIC_ACTIVE_CALORIES ->
            caloriesMetricValue(definition.label, snapshot.calorieEstimate.activeKcal)
        SettingsRepository.RECORDING_METRIC_RESTING_CALORIES ->
            caloriesMetricValue(definition.label, snapshot.calorieEstimate.restingKcal)
        else -> RecordingMetricValue(definition.label, "--")
    }
}

private fun formatRecordingDistance(
    meters: Double,
    isMetric: Boolean,
): Pair<String, String> =
    if (isMetric) {
        RECORDING_DISTANCE_FORMAT.format(meters / 1000.0) to "km"
    } else {
        RECORDING_DISTANCE_FORMAT.format(meters * METERS_TO_MILES) to "mi"
    }

private fun formatRecordingDurationClock(seconds: Double?): String {
    if (seconds == null || !seconds.isFinite() || seconds <= 0.0) return "00:00:00"
    val totalSeconds = seconds.roundToInt().coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return "${hours.twoDigits()}:${minutes.twoDigits()}:${secs.twoDigits()}"
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')

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

private fun sensorIntegerMetricValue(
    label: String,
    value: Int?,
    unit: String?,
): RecordingMetricValue =
    if (value == null || value < 0) {
        RecordingMetricValue(label, "--", unit)
    } else {
        RecordingMetricValue(label, value.toString(), unit)
    }

private fun pressureMetricValue(
    label: String,
    pressureHpa: Double?,
): RecordingMetricValue =
    if (pressureHpa == null || !pressureHpa.isFinite() || pressureHpa <= 0.0) {
        RecordingMetricValue(label, "--", "hPa")
    } else {
        RecordingMetricValue(
            label = label,
            value = (pressureHpa * 10.0).roundToInt().let { (it / 10.0).toString() },
            unit = "hPa",
        )
}

private fun caloriesMetricValue(
    label: String,
    calories: Double,
): RecordingMetricValue =
    RecordingMetricValue(
        label = label,
        value =
            if (!calories.isFinite() || calories <= 0.0) {
                "0"
            } else {
                calories.roundToInt().toString()
            },
        unit = "kcal",
    )

internal fun estimateRecordingCalories(
    points: List<RecordedTracePoint>,
    userWeightKg: Float,
    backpackWeightKg: Float,
    terrainFactor: Double = DEFAULT_TERRAIN_FACTOR,
): RecordingCalorieEstimate {
    if (points.size < 2) return RecordingCalorieEstimate()
    val bodyWeightKg =
        userWeightKg
            .takeIf { it.isFinite() }
            ?.coerceIn(SettingsRepository.MIN_USER_WEIGHT_KG, SettingsRepository.MAX_USER_WEIGHT_KG)
            ?: SettingsRepository.DEFAULT_USER_WEIGHT_KG
    val loadWeightKg =
        backpackWeightKg
            .takeIf { it.isFinite() }
            ?.coerceIn(SettingsRepository.MIN_BACKPACK_WEIGHT_KG, SettingsRepository.MAX_BACKPACK_WEIGHT_KG)
            ?: SettingsRepository.DEFAULT_BACKPACK_WEIGHT_KG

    var grossKcal = 0.0
    var pandolfBaseGrossKcal = 0.0
    var lcdaGrossKcal = 0.0
    var modeledDurationSeconds = 0.0
    points.zipWithNext().forEach { (start, end) ->
        val segmentDurationSeconds =
            ((end.timeMillis - start.timeMillis) / 1000.0)
                .takeIf { it.isFinite() && it > 0.0 }
                ?.coerceAtMost(MAX_CALORIE_SEGMENT_DURATION_SECONDS)
                ?: return@forEach
        val distanceMeters = haversineMeters(start.latLong, end.latLong).coerceAtLeast(0.0)
        val speedMetersPerSecond =
            (distanceMeters / segmentDurationSeconds)
                .takeIf { it.isFinite() }
                ?.coerceIn(0.0, MAX_PANDOLF_SPEED_MPS)
                ?: 0.0
        val elevationDeltaMeters =
            if (start.elevationMeters != null && end.elevationMeters != null) {
                end.elevationMeters - start.elevationMeters
            } else {
                0.0
            }
        val gradePercent =
            if (distanceMeters >= MIN_DISTANCE_METERS_FOR_GRADE) {
                ((elevationDeltaMeters / distanceMeters) * 100.0)
                    .coerceIn(-MAX_PANDOLF_GRADE_PERCENT, MAX_PANDOLF_GRADE_PERCENT)
            } else {
                0.0
            }
        val watts =
            pandolfSanteeWatts(
                bodyWeightKg = bodyWeightKg.toDouble(),
                loadWeightKg = loadWeightKg.toDouble(),
                speedMetersPerSecond = speedMetersPerSecond,
                gradePercent = gradePercent,
                terrainFactor = terrainFactor,
            )
        grossKcal += watts * segmentDurationSeconds / JOULES_PER_KILOCALORIE
        pandolfBaseGrossKcal +=
            pandolfWatts(
                bodyWeightKg = bodyWeightKg.toDouble(),
                loadWeightKg = loadWeightKg.toDouble(),
                speedMetersPerSecond = speedMetersPerSecond,
                gradePercent = gradePercent,
                terrainFactor = terrainFactor,
            ).coerceAtLeast(0.0) * segmentDurationSeconds / JOULES_PER_KILOCALORIE
        lcdaGrossKcal +=
            lcda2024WeightedLoadWatts(
                bodyWeightKg = bodyWeightKg.toDouble(),
                loadWeightKg = loadWeightKg.toDouble(),
                speedMetersPerSecond = speedMetersPerSecond,
                gradePercent = gradePercent,
                terrainFactor = terrainFactor,
            ) * segmentDurationSeconds / JOULES_PER_KILOCALORIE
        modeledDurationSeconds += segmentDurationSeconds
    }

    val restingKcal = bodyWeightKg * (modeledDurationSeconds / SECONDS_PER_HOUR) * RESTING_MET
    val activeKcal = (grossKcal - restingKcal).coerceAtLeast(0.0)
    val pandolfBaseRestingKcal = restingKcal
    val pandolfBaseActiveKcal = (pandolfBaseGrossKcal - pandolfBaseRestingKcal).coerceAtLeast(0.0)
    val lcdaRestingKcal = restingKcal
    val lcdaActiveKcal = (lcdaGrossKcal - lcdaRestingKcal).coerceAtLeast(0.0)
    return RecordingCalorieEstimate(
        grossKcal = grossKcal,
        activeKcal = activeKcal,
        restingKcal = restingKcal,
        pandolfBaseGrossKcal = pandolfBaseGrossKcal,
        pandolfBaseActiveKcal = pandolfBaseActiveKcal,
        pandolfBaseRestingKcal = pandolfBaseRestingKcal,
        lcdaGrossKcal = lcdaGrossKcal,
        lcdaActiveKcal = lcdaActiveKcal,
        lcdaRestingKcal = lcdaRestingKcal,
    )
}

private fun pandolfSanteeWatts(
    bodyWeightKg: Double,
    loadWeightKg: Double,
    speedMetersPerSecond: Double,
    gradePercent: Double,
    terrainFactor: Double,
): Double {
    val rawWatts =
        pandolfWatts(
            bodyWeightKg = bodyWeightKg,
            loadWeightKg = loadWeightKg,
            speedMetersPerSecond = speedMetersPerSecond,
            gradePercent = gradePercent,
            terrainFactor = terrainFactor,
        )
    if (gradePercent >= 0.0) return rawWatts

    val correctionWatts =
        santeeDownhillCorrectionWatts(
            bodyWeightKg = bodyWeightKg,
            loadWeightKg = loadWeightKg,
            speedMetersPerSecond = speedMetersPerSecond,
            gradePercent = gradePercent,
            terrainFactor = terrainFactor,
        )
    return max(rawWatts + correctionWatts, 0.0)
}

private fun pandolfWatts(
    bodyWeightKg: Double,
    loadWeightKg: Double,
    speedMetersPerSecond: Double,
    gradePercent: Double,
    terrainFactor: Double,
): Double =
    1.5 * bodyWeightKg +
        2.0 * (bodyWeightKg + loadWeightKg) * (loadWeightKg / bodyWeightKg) * (loadWeightKg / bodyWeightKg) +
        terrainFactor * (bodyWeightKg + loadWeightKg) *
        (
            1.5 * speedMetersPerSecond * speedMetersPerSecond +
                0.35 * speedMetersPerSecond * gradePercent
        )

private fun santeeDownhillCorrectionWatts(
    bodyWeightKg: Double,
    loadWeightKg: Double,
    speedMetersPerSecond: Double,
    gradePercent: Double,
    terrainFactor: Double,
): Double {
    val carriedWeightKg = bodyWeightKg + loadWeightKg
    val bracket =
        (gradePercent * carriedWeightKg * speedMetersPerSecond / SANTEE_SPEED_NORMALIZER) -
            (carriedWeightKg * (gradePercent + SANTEE_GRADE_OFFSET).pow(2.0) / bodyWeightKg) +
            (SANTEE_SPEED_SQUARED_COEFFICIENT * speedMetersPerSecond.pow(2.0))
    return -terrainFactor * bracket
}

private fun lcda2024WeightedLoadWatts(
    bodyWeightKg: Double,
    loadWeightKg: Double,
    speedMetersPerSecond: Double,
    gradePercent: Double,
    terrainFactor: Double,
): Double {
    val gradeFraction =
        (gradePercent / 100.0)
            .coerceIn(-MAX_PANDOLF_GRADE_PERCENT / 100.0, MAX_PANDOLF_GRADE_PERCENT / 100.0)
    val gradeShape =
        1.0 -
            LCDA_GRADE_OUTER_BASE.pow(
                1.0 -
                    LCDA_GRADE_INNER_BASE.pow(
                        LCDA_GRADE_SCALE * gradeFraction + LCDA_GRADE_OFFSET,
                    ),
            )
    val walkWattsPerKg =
        terrainFactor *
            (
                LCDA_SPEED_LINEAR_COEFFICIENT * speedMetersPerSecond.pow(LCDA_SPEED_LINEAR_EXPONENT) +
                    LCDA_SPEED_FAST_COEFFICIENT * speedMetersPerSecond.pow(LCDA_SPEED_FAST_EXPONENT) +
                    LCDA_GRADE_COEFFICIENT * speedMetersPerSecond * gradeFraction * gradeShape
            )
    val backpackLoadRatio =
        (loadWeightKg / bodyWeightKg)
            .takeIf { it.isFinite() }
            ?.coerceAtLeast(0.0)
            ?: 0.0
    val vestLoadRatio = 0.0
    val loadMultiplier =
        1.0 +
            LCDA_BACKPACK_LOAD_COEFFICIENT * backpackLoadRatio.pow(LCDA_BACKPACK_LOAD_EXPONENT) +
            LCDA_VEST_LOAD_COEFFICIENT * vestLoadRatio.pow(LCDA_VEST_LOAD_EXPONENT)
    val wattsPerKg =
        LCDA_RESTING_WATTS_PER_KG +
            (LCDA_STANDING_WATTS_PER_KG + walkWattsPerKg.coerceAtLeast(0.0)) *
            loadMultiplier
    return (wattsPerKg * bodyWeightKg).coerceAtLeast(0.0)
}

private fun paceMetricValue(
    label: String,
    speedMps: Double?,
    isMetric: Boolean,
): RecordingMetricValue {
    if (speedMps == null || !speedMps.isFinite() || speedMps <= 0.0) {
        return RecordingMetricValue(label, "--", if (isMetric) "min/km" else "min/mi")
    }
    val secondsPerUnit =
        if (isMetric) {
            1_000.0 / speedMps
        } else {
            METERS_PER_MILE / speedMps
        }
    val totalSeconds = secondsPerUnit.roundToInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return RecordingMetricValue(
        label = label,
        value = "$minutes:${seconds.twoDigits()}",
        unit = if (isMetric) "min/km" else "min/mi",
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

private const val METERS_TO_MILES = 0.000621371
private const val METERS_PER_MILE = 1_609.344
private const val JOULES_PER_KILOCALORIE = 4_184.0
private const val SECONDS_PER_HOUR = 3_600.0
private const val RESTING_MET = 1.0
private const val DEFAULT_TERRAIN_FACTOR = 1.0
private const val MAX_PANDOLF_SPEED_MPS = 3.0
private const val MAX_PANDOLF_GRADE_PERCENT = 35.0
private const val SANTEE_SPEED_NORMALIZER = 3.5
private const val SANTEE_GRADE_OFFSET = 6.0
private const val SANTEE_SPEED_SQUARED_COEFFICIENT = 25.0
private const val LCDA_RESTING_WATTS_PER_KG = RESTING_MET * JOULES_PER_KILOCALORIE / SECONDS_PER_HOUR
private const val LCDA_STANDING_WATTS_PER_KG = 0.21
private const val LCDA_SPEED_LINEAR_COEFFICIENT = 1.78
private const val LCDA_SPEED_LINEAR_EXPONENT = 0.58
private const val LCDA_SPEED_FAST_COEFFICIENT = 0.27
private const val LCDA_SPEED_FAST_EXPONENT = 4.0
private const val LCDA_GRADE_COEFFICIENT = 34.0
private const val LCDA_GRADE_OUTER_BASE = 1.05
private const val LCDA_GRADE_INNER_BASE = 1.1
private const val LCDA_GRADE_SCALE = 100.0
private const val LCDA_GRADE_OFFSET = 32.0
private const val LCDA_BACKPACK_LOAD_COEFFICIENT = 1.96
private const val LCDA_BACKPACK_LOAD_EXPONENT = 1.36
private const val LCDA_VEST_LOAD_COEFFICIENT = 1.38
private const val LCDA_VEST_LOAD_EXPONENT = 1.21
private const val MIN_DISTANCE_METERS_FOR_GRADE = 1.0
private const val MAX_CALORIE_SEGMENT_DURATION_SECONDS = 600.0
private val RECORDING_DISTANCE_FORMAT = DecimalFormat("0.00")
