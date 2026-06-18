package com.glancemap.glancemapwearos.data.repository

internal const val RECORDING_DASHBOARD_PAGE_SLOT_COUNT = 4
internal const val RECORDING_DASHBOARD_MIN_PAGE_COUNT = 1
internal const val RECORDING_DASHBOARD_MAX_PAGE_COUNT = 5
internal const val RECORDING_DASHBOARD_MAX_SLOT_COUNT =
    RECORDING_DASHBOARD_PAGE_SLOT_COUNT * RECORDING_DASHBOARD_MAX_PAGE_COUNT

internal fun normalizeRecordingDashboardMetricSlots(metricSlots: List<String>): List<String> {
    if (metricSlots.isEmpty()) return SettingsRepository.DEFAULT_RECORDING_DASHBOARD_ALL_METRICS
    if (metricSlots.size == RECORDING_DASHBOARD_PAGE_SLOT_COUNT &&
        metricSlots == LEGACY_RECORDING_DASHBOARD_PAGE_ONE_METRICS
    ) {
        return SettingsRepository.DEFAULT_RECORDING_DASHBOARD_ALL_METRICS
    }

    val boundedSlots = metricSlots.take(RECORDING_DASHBOARD_MAX_SLOT_COUNT)
    val targetSize =
        boundedSlots.size
            .coerceAtLeast(RECORDING_DASHBOARD_PAGE_SLOT_COUNT)
            .let { size ->
                val remainder = size % RECORDING_DASHBOARD_PAGE_SLOT_COUNT
                if (remainder == 0) size else size + RECORDING_DASHBOARD_PAGE_SLOT_COUNT - remainder
            }.coerceAtMost(RECORDING_DASHBOARD_MAX_SLOT_COUNT)
    return (
        boundedSlots +
            generateSequence { SettingsRepository.DEFAULT_RECORDING_DASHBOARD_NEW_PAGE_METRICS }
                .flatten()
                .take(targetSize - boundedSlots.size)
    ).take(targetSize)
}

private val LEGACY_RECORDING_DASHBOARD_PAGE_ONE_METRICS =
    listOf(
        SettingsRepository.RECORDING_METRIC_DISTANCE,
        SettingsRepository.RECORDING_METRIC_DURATION,
        SettingsRepository.RECORDING_METRIC_ELEVATION_GAIN,
        SettingsRepository.RECORDING_METRIC_ELEVATION_LOSS,
    )
