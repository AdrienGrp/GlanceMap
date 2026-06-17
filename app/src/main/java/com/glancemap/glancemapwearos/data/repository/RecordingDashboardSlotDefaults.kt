package com.glancemap.glancemapwearos.data.repository

internal const val RECORDING_DASHBOARD_PAGE_SLOT_COUNT = 4
internal const val RECORDING_DASHBOARD_TOTAL_SLOT_COUNT = 8

internal fun normalizeRecordingDashboardMetricSlots(metricSlots: List<String>): List<String> {
    val migratedSlots =
        if (metricSlots.take(RECORDING_DASHBOARD_PAGE_SLOT_COUNT) == LEGACY_RECORDING_DASHBOARD_PAGE_ONE_METRICS) {
            SettingsRepository.DEFAULT_RECORDING_DASHBOARD_METRICS +
                metricSlots.drop(RECORDING_DASHBOARD_PAGE_SLOT_COUNT)
        } else {
            metricSlots
        }
    return (
        migratedSlots.take(RECORDING_DASHBOARD_TOTAL_SLOT_COUNT) +
            SettingsRepository.DEFAULT_RECORDING_DASHBOARD_ALL_METRICS.drop(migratedSlots.size)
    ).take(RECORDING_DASHBOARD_TOTAL_SLOT_COUNT)
}

private val LEGACY_RECORDING_DASHBOARD_PAGE_ONE_METRICS =
    listOf(
        SettingsRepository.RECORDING_METRIC_DISTANCE,
        SettingsRepository.RECORDING_METRIC_DURATION,
        SettingsRepository.RECORDING_METRIC_ELEVATION_GAIN,
        SettingsRepository.RECORDING_METRIC_ELEVATION_LOSS,
    )
