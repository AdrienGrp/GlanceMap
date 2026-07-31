package com.glancemap.glancemapwearos.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingDashboardProfilePolicyTest {
    @Test
    fun hikeAndBikeUseIndependentDefaults() {
        assertEquals(
            SettingsRepository.DEFAULT_RECORDING_DASHBOARD_ALL_METRICS,
            defaultRecordingDashboardMetricSlotsForProfile(SettingsRepository.ACTIVITY_PROFILE_HIKE),
        )
        assertEquals(
            SettingsRepository.DEFAULT_BIKE_RECORDING_DASHBOARD_METRICS,
            defaultRecordingDashboardMetricSlotsForProfile(SettingsRepository.ACTIVITY_PROFILE_BIKE),
        )
    }

    @Test
    fun bikeExcludesWalkingMetricsButKeepsCyclingMetrics() {
        val bike = SettingsRepository.ACTIVITY_PROFILE_BIKE

        assertFalse(isRecordingDashboardMetricAllowedForProfile(bike, SettingsRepository.RECORDING_METRIC_STEPS))
        assertFalse(isRecordingDashboardMetricAllowedForProfile(bike, SettingsRepository.RECORDING_METRIC_CURRENT_PACE))
        assertFalse(isRecordingDashboardMetricAllowedForProfile(bike, SettingsRepository.RECORDING_METRIC_AVERAGE_PACE))
        assertFalse(isRecordingDashboardMetricAllowedForProfile(bike, SettingsRepository.RECORDING_METRIC_MAX_PACE))
        assertTrue(isRecordingDashboardMetricAllowedForProfile(bike, SettingsRepository.RECORDING_METRIC_CADENCE))
        assertTrue(isRecordingDashboardMetricAllowedForProfile(bike, SettingsRepository.RECORDING_METRIC_POWER))
    }

    @Test
    fun bikeNewPageDefaultsContainOnlyBikeMetrics() {
        val newPage = newRecordingDashboardPageMetricSlotsForProfile(SettingsRepository.ACTIVITY_PROFILE_BIKE)

        assertEquals(RECORDING_DASHBOARD_PAGE_SLOT_COUNT, newPage.size)
        assertTrue(
            newPage.all { metricId ->
                isRecordingDashboardMetricAllowedForProfile(SettingsRepository.ACTIVITY_PROFILE_BIKE, metricId)
            },
        )
    }

    @Test
    fun bikePaddingDoesNotIntroducePace() {
        val slots =
            normalizeRecordingDashboardMetricSlots(
                metricSlots = listOf(SettingsRepository.RECORDING_METRIC_DISTANCE),
                defaultMetricSlots =
                    defaultRecordingDashboardMetricSlotsForProfile(SettingsRepository.ACTIVITY_PROFILE_BIKE),
                newPageMetricSlots =
                    newRecordingDashboardPageMetricSlotsForProfile(SettingsRepository.ACTIVITY_PROFILE_BIKE),
            )

        assertEquals(RECORDING_DASHBOARD_PAGE_SLOT_COUNT, slots.size)
        assertTrue(
            slots.all { metricId ->
                isRecordingDashboardMetricAllowedForProfile(SettingsRepository.ACTIVITY_PROFILE_BIKE, metricId)
            },
        )
    }
}
