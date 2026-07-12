package com.glancemap.glancemapwearos.data.repository

internal const val RECORDING_DASHBOARD_PAGE_SLOT_COUNT = 4
internal const val RECORDING_DASHBOARD_MIN_PAGE_COUNT = 1
internal const val RECORDING_DASHBOARD_MAX_PAGE_COUNT = 5
internal const val RECORDING_DASHBOARD_MAX_SLOT_COUNT =
    RECORDING_DASHBOARD_PAGE_SLOT_COUNT * RECORDING_DASHBOARD_MAX_PAGE_COUNT

internal fun normalizeRecordingDashboardMetricSlots(
    metricSlots: List<String>,
    defaultMetricSlots: List<String> = SettingsRepository.DEFAULT_RECORDING_DASHBOARD_ALL_METRICS,
): List<String> {
    val useDefaults =
        metricSlots.isEmpty() ||
            (
                metricSlots.size == RECORDING_DASHBOARD_PAGE_SLOT_COUNT &&
                    metricSlots == LEGACY_RECORDING_DASHBOARD_PAGE_ONE_METRICS
            )
    return if (useDefaults) defaultMetricSlots else padRecordingDashboardSlots(metricSlots)
}

private fun padRecordingDashboardSlots(metricSlots: List<String>): List<String> {
    val boundedSlots = metricSlots.take(RECORDING_DASHBOARD_MAX_SLOT_COUNT)
    val minimumSize = boundedSlots.size.coerceAtLeast(RECORDING_DASHBOARD_PAGE_SLOT_COUNT)
    val remainder = minimumSize % RECORDING_DASHBOARD_PAGE_SLOT_COUNT
    val paddedSize = if (remainder == 0) minimumSize else minimumSize + RECORDING_DASHBOARD_PAGE_SLOT_COUNT - remainder
    val targetSize = paddedSize.coerceAtMost(RECORDING_DASHBOARD_MAX_SLOT_COUNT)
    val padding =
        generateSequence { SettingsRepository.DEFAULT_RECORDING_DASHBOARD_NEW_PAGE_METRICS }
            .flatten()
            .take(targetSize - boundedSlots.size)
    return (boundedSlots + padding).take(targetSize)
}

private val LEGACY_RECORDING_DASHBOARD_PAGE_ONE_METRICS =
    listOf(
        SettingsRepository.RECORDING_METRIC_DISTANCE,
        SettingsRepository.RECORDING_METRIC_DURATION,
        SettingsRepository.RECORDING_METRIC_ELEVATION_GAIN,
        SettingsRepository.RECORDING_METRIC_ELEVATION_LOSS,
    )
