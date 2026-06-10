package com.glancemap.glancemapwearos.presentation.features.recording.dashboard

import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.features.recording.RecordedTracePoint
import com.glancemap.glancemapwearos.presentation.features.recording.TraceRecordingUiState
import org.mapsforge.core.model.LatLong
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingDashboardModelsTest {
    @Test
    fun normalizedRecordingDashboardSlotsPreservesLegacyPageAndAddsSecondPageDefaults() {
        val slots =
            normalizedRecordingDashboardSlots(
                listOf(
                    SettingsRepository.RECORDING_METRIC_DISTANCE,
                    SettingsRepository.RECORDING_METRIC_DURATION,
                    SettingsRepository.RECORDING_METRIC_ELEVATION_GAIN,
                    SettingsRepository.RECORDING_METRIC_ELEVATION_LOSS,
                ),
            )

        assertEquals(
            SettingsRepository.DEFAULT_RECORDING_DASHBOARD_ALL_METRICS,
            slots,
        )
    }

    @Test
    fun normalizedRecordingDashboardSlotsTrimsToTwoPages() {
        val slots =
            normalizedRecordingDashboardSlots(
                SettingsRepository.DEFAULT_RECORDING_DASHBOARD_ALL_METRICS +
                    listOf(
                        SettingsRepository.RECORDING_METRIC_AVERAGE_PACE,
                        SettingsRepository.RECORDING_METRIC_HEART_RATE,
                    ),
            )

        assertEquals(8, slots.size)
        assertEquals(SettingsRepository.DEFAULT_RECORDING_DASHBOARD_ALL_METRICS, slots)
    }

    @Test
    fun buildRecordingDashboardSnapshotExcludesPausedTimeFromDuration() {
        val snapshot =
            buildRecordingDashboardSnapshot(
                state =
                    TraceRecordingUiState(
                        active = true,
                        paused = true,
                        startedAtMillis = 1_000L,
                        pausedAtMillis = 7_000L,
                        accumulatedPausedMillis = 2_000L,
                    ),
                nowMillis = 10_000L,
            )

        assertEquals(4.0, snapshot.durationSeconds, 0.0)
    }

    @Test
    fun formattedRecordingMetricUsesClockDurationForRecordingDuration() {
        val metric =
            formattedRecordingMetric(
                metricId = SettingsRepository.RECORDING_METRIC_DURATION,
                snapshot =
                    RecordingDashboardSnapshot(
                        durationSeconds = 3_723.0,
                        distanceMeters = 0.0,
                        elevationGainMeters = 0.0,
                        elevationLossMeters = 0.0,
                        currentElevationMeters = null,
                        currentSpeedMps = null,
                        averageSpeedMps = null,
                        gpsAccuracyMeters = null,
                        pointCount = 0,
                        gpsActiveDurationSeconds = 0.0,
                        recordingGapCount = 0,
                        recordingMaxGapSeconds = 0.0,
                    ),
                isMetric = true,
            )

        assertEquals("01:02:03", metric.value)
    }

    @Test
    fun formattedRecordingMetricUsesUnitSettingForTwoDecimalDistance() {
        val snapshot =
            RecordingDashboardSnapshot(
                durationSeconds = 0.0,
                distanceMeters = 1_234.0,
                elevationGainMeters = 0.0,
                elevationLossMeters = 0.0,
                currentElevationMeters = null,
                currentSpeedMps = null,
                averageSpeedMps = null,
                gpsAccuracyMeters = null,
                pointCount = 0,
                gpsActiveDurationSeconds = 0.0,
                recordingGapCount = 0,
                recordingMaxGapSeconds = 0.0,
            )

        val metricDistance =
            formattedRecordingMetric(
                metricId = SettingsRepository.RECORDING_METRIC_DISTANCE,
                snapshot = snapshot,
                isMetric = true,
            )
        val imperialDistance =
            formattedRecordingMetric(
                metricId = SettingsRepository.RECORDING_METRIC_DISTANCE,
                snapshot = snapshot,
                isMetric = false,
            )

        assertEquals("1.23", metricDistance.value)
        assertEquals("km", metricDistance.unit)
        assertEquals("0.77", imperialDistance.value)
        assertEquals("mi", imperialDistance.unit)
    }

    @Test
    fun sensorMetricsAreSelectableAndShowUnavailableValuesWhenNoSensorDataExists() {
        val snapshot =
            RecordingDashboardSnapshot(
                durationSeconds = 0.0,
                distanceMeters = 0.0,
                elevationGainMeters = 0.0,
                elevationLossMeters = 0.0,
                currentElevationMeters = null,
                currentSpeedMps = null,
                averageSpeedMps = null,
                gpsAccuracyMeters = null,
                pointCount = 0,
                gpsActiveDurationSeconds = 0.0,
                recordingGapCount = 0,
                recordingMaxGapSeconds = 0.0,
            )

        val heartRate =
            formattedRecordingMetric(
                metricId = SettingsRepository.RECORDING_METRIC_HEART_RATE,
                snapshot = snapshot,
                isMetric = true,
            )
        val pressure =
            formattedRecordingMetric(
                metricId = SettingsRepository.RECORDING_METRIC_BAROMETRIC_PRESSURE,
                snapshot = snapshot,
                isMetric = true,
            )

        assertEquals("Heart rate", heartRate.label)
        assertEquals("--", heartRate.value)
        assertEquals("bpm", heartRate.unit)
        assertEquals("Pressure", pressure.label)
        assertEquals("--", pressure.value)
        assertEquals("hPa", pressure.unit)
        assertEquals(
            true,
            recordingMetricDefinitions.any { it.id == SettingsRepository.RECORDING_METRIC_CADENCE },
        )
    }

    @Test
    fun formattedRecordingMetricFormatsCurrentPaceFromSpeed() {
        val snapshot =
            RecordingDashboardSnapshot(
                durationSeconds = 0.0,
                distanceMeters = 0.0,
                elevationGainMeters = 0.0,
                elevationLossMeters = 0.0,
                currentElevationMeters = null,
                currentSpeedMps = 2.5f,
                averageSpeedMps = null,
                gpsAccuracyMeters = null,
                pointCount = 0,
                gpsActiveDurationSeconds = 0.0,
                recordingGapCount = 0,
                recordingMaxGapSeconds = 0.0,
            )

        val pace =
            formattedRecordingMetric(
                metricId = SettingsRepository.RECORDING_METRIC_CURRENT_PACE,
                snapshot = snapshot,
                isMetric = true,
            )

        assertEquals("Pace", pace.label)
        assertEquals("6:40", pace.value)
        assertEquals("min/km", pace.unit)
    }

    @Test
    fun formattedRecordingMetricEstimatesCaloriesFromWeightDistanceAndDuration() {
        val estimate =
            estimateRecordingCalories(
                points = oneHourFlatWalkPoints(),
                userWeightKg = 75f,
                backpackWeightKg = 0f,
            )

        val calories =
            formattedRecordingMetric(
                metricId = SettingsRepository.RECORDING_METRIC_CALORIES,
                snapshot = recordingSnapshot(calorieEstimate = estimate),
                isMetric = true,
            )
        assertEquals("Calories", calories.label)
        assertEquals("286", calories.value)
        assertEquals("kcal", calories.unit)
        assertEquals(75.0, estimate.restingKcal, 0.1)
        assertEquals(211.0, estimate.activeKcal, 1.0)
        assertEquals(286.0, estimate.pandolfBaseGrossKcal, 1.0)
        assertEquals(211.0, estimate.pandolfBaseActiveKcal, 1.0)
        assertEquals(75.0, estimate.pandolfBaseRestingKcal, 0.1)
        assertEquals(295.0, estimate.lcdaGrossKcal, 1.0)
        assertEquals(220.0, estimate.lcdaActiveKcal, 1.0)
        assertEquals(75.0, estimate.lcdaRestingKcal, 0.1)
    }

    @Test
    fun formattedRecordingMetricAddsBackpackLoadToCaloriesEstimate() {
        val estimate =
            estimateRecordingCalories(
                points = oneHourFlatWalkPoints(),
                userWeightKg = 75f,
                backpackWeightKg = 10f,
            )

        val calories =
            formattedRecordingMetric(
                metricId = SettingsRepository.RECORDING_METRIC_CALORIES,
                snapshot = recordingSnapshot(calorieEstimate = estimate),
                isMetric = true,
            )

        assertEquals("314", calories.value)
        assertEquals(323.0, estimate.lcdaGrossKcal, 1.0)
    }

    @Test
    fun estimateRecordingCaloriesAppliesDownhillCorrection() {
        val flat =
            estimateRecordingCalories(
                points = oneHourFlatWalkPoints(),
                userWeightKg = 75f,
                backpackWeightKg = 0f,
            )
        val downhill =
            estimateRecordingCalories(
                points =
                    oneHourWalkPoints(
                        startElevationMeters = 756.0,
                        endElevationMeters = 0.0,
                    ),
                userWeightKg = 75f,
                backpackWeightKg = 0f,
        )

        assertEquals(286.0, flat.grossKcal, 1.0)
        assertEquals(227.0, downhill.grossKcal, 1.0)
        assertEquals(0.0, downhill.pandolfBaseGrossKcal, 1.0)
    }

    private fun oneHourFlatWalkPoints(): List<RecordedTracePoint> =
        oneHourWalkPoints(startElevationMeters = 0.0, endElevationMeters = 0.0)

    private fun oneHourWalkPoints(
        startElevationMeters: Double,
        endElevationMeters: Double,
    ): List<RecordedTracePoint> =
        (0..ONE_HOUR_WALK_SEGMENT_COUNT).map { index ->
            val progress = index / ONE_HOUR_WALK_SEGMENT_COUNT.toDouble()
            recordingPoint(
                longitude = ONE_HOUR_WALK_LONGITUDE_DELTA * progress,
                elevationMeters = startElevationMeters + (endElevationMeters - startElevationMeters) * progress,
                timeMillis = (3_600_000L * progress).toLong(),
            )
        }

    private fun recordingSnapshot(calorieEstimate: RecordingCalorieEstimate): RecordingDashboardSnapshot =
        RecordingDashboardSnapshot(
            durationSeconds = 3_600.0,
            distanceMeters = 5_040.0,
            elevationGainMeters = 0.0,
            elevationLossMeters = 0.0,
            currentElevationMeters = null,
            currentSpeedMps = null,
            averageSpeedMps = 1.4,
            gpsAccuracyMeters = null,
            pointCount = 2,
            gpsActiveDurationSeconds = 3_600.0,
            recordingGapCount = 0,
            recordingMaxGapSeconds = 0.0,
            userWeightKg = 75f,
            calorieEstimate = calorieEstimate,
        )

    private fun recordingPoint(
        longitude: Double,
        elevationMeters: Double,
        timeMillis: Long,
    ): RecordedTracePoint =
        RecordedTracePoint(
            latLong = LatLong(0.0, longitude),
            elevationMeters = elevationMeters,
            timeMillis = timeMillis,
            accuracyMeters = null,
            speedMps = null,
        )

    private companion object {
        private const val ONE_HOUR_WALK_SEGMENT_COUNT = 6
        private const val ONE_HOUR_WALK_LONGITUDE_DELTA = 0.045319
    }
}
