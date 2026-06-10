package com.glancemap.glancemapwearos.presentation.features.recording.dashboard

import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.features.recording.TraceRecordingUiState
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
}
