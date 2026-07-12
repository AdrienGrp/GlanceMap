package com.glancemap.glancemapwearos.data.repository

internal const val RECORDING_DASHBOARD_PAGE_SLOT_COUNT = 4
internal const val RECORDING_DASHBOARD_MIN_PAGE_COUNT = 1
internal const val RECORDING_DASHBOARD_MAX_PAGE_COUNT = 5
internal const val RECORDING_DASHBOARD_MAX_SLOT_COUNT =
    RECORDING_DASHBOARD_PAGE_SLOT_COUNT * RECORDING_DASHBOARD_MAX_PAGE_COUNT

internal fun normalizeRecordingDashboardMetricSlots(
    metricSlots: List<String>,
    defaultMetricSlots: List<String> = SettingsRepository.DEFAULT_RECORDING_DASHBOARD_ALL_METRICS,
    newPageMetricSlots: List<String> = SettingsRepository.DEFAULT_RECORDING_DASHBOARD_NEW_PAGE_METRICS,
): List<String> {
    val useDefaults =
        metricSlots.isEmpty() ||
            (
                metricSlots.size == RECORDING_DASHBOARD_PAGE_SLOT_COUNT &&
                    metricSlots == LEGACY_RECORDING_DASHBOARD_PAGE_ONE_METRICS
            )
    return if (useDefaults) defaultMetricSlots else padRecordingDashboardSlots(metricSlots, newPageMetricSlots)
}

internal fun defaultRecordingDashboardMetricSlotsForProfile(profile: String): List<String> =
    if (profile == SettingsRepository.ACTIVITY_PROFILE_BIKE) {
        SettingsRepository.DEFAULT_BIKE_RECORDING_DASHBOARD_METRICS
    } else {
        SettingsRepository.DEFAULT_RECORDING_DASHBOARD_ALL_METRICS
    }

internal fun newRecordingDashboardPageMetricSlotsForProfile(profile: String): List<String> =
    if (profile == SettingsRepository.ACTIVITY_PROFILE_BIKE) {
        DEFAULT_BIKE_RECORDING_DASHBOARD_NEW_PAGE_METRICS
    } else {
        SettingsRepository.DEFAULT_RECORDING_DASHBOARD_NEW_PAGE_METRICS
    }

internal fun isRecordingDashboardMetricAllowedForProfile(
    profile: String,
    metricId: String,
): Boolean =
    metricId in RECORDING_DASHBOARD_ALL_METRIC_IDS &&
        (profile != SettingsRepository.ACTIVITY_PROFILE_BIKE || metricId !in BIKE_EXCLUDED_RECORDING_METRIC_IDS)

internal fun fallbackRecordingDashboardMetricForSlot(
    profile: String,
    slotIndex: Int,
): String {
    val defaults = defaultRecordingDashboardMetricSlotsForProfile(profile)
    if (slotIndex in defaults.indices) return defaults[slotIndex]
    val newPageDefaults = newRecordingDashboardPageMetricSlotsForProfile(profile)
    return newPageDefaults[slotIndex.mod(newPageDefaults.size)]
}

private fun padRecordingDashboardSlots(
    metricSlots: List<String>,
    newPageMetricSlots: List<String>,
): List<String> {
    val boundedSlots = metricSlots.take(RECORDING_DASHBOARD_MAX_SLOT_COUNT)
    val minimumSize = boundedSlots.size.coerceAtLeast(RECORDING_DASHBOARD_PAGE_SLOT_COUNT)
    val remainder = minimumSize % RECORDING_DASHBOARD_PAGE_SLOT_COUNT
    val paddedSize = if (remainder == 0) minimumSize else minimumSize + RECORDING_DASHBOARD_PAGE_SLOT_COUNT - remainder
    val targetSize = paddedSize.coerceAtMost(RECORDING_DASHBOARD_MAX_SLOT_COUNT)
    val padding =
        generateSequence { newPageMetricSlots }
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

private val DEFAULT_BIKE_RECORDING_DASHBOARD_NEW_PAGE_METRICS =
    listOf(
        SettingsRepository.RECORDING_METRIC_CURRENT_SPEED,
        SettingsRepository.RECORDING_METRIC_AVERAGE_SPEED,
        SettingsRepository.RECORDING_METRIC_CADENCE,
        SettingsRepository.RECORDING_METRIC_HEART_RATE,
    )

private val BIKE_EXCLUDED_RECORDING_METRIC_IDS =
    setOf(
        SettingsRepository.RECORDING_METRIC_CURRENT_PACE,
        SettingsRepository.RECORDING_METRIC_AVERAGE_PACE,
        SettingsRepository.RECORDING_METRIC_MAX_PACE,
        SettingsRepository.RECORDING_METRIC_STEPS,
    )

private val RECORDING_DASHBOARD_ALL_METRIC_IDS =
    setOf(
        SettingsRepository.RECORDING_METRIC_DISTANCE,
        SettingsRepository.RECORDING_METRIC_TOTAL_TIME,
        SettingsRepository.RECORDING_METRIC_DURATION,
        SettingsRepository.RECORDING_METRIC_ELEVATION_GAIN,
        SettingsRepository.RECORDING_METRIC_ELEVATION_LOSS,
        SettingsRepository.RECORDING_METRIC_CURRENT_ELEVATION,
        SettingsRepository.RECORDING_METRIC_CURRENT_SPEED,
        SettingsRepository.RECORDING_METRIC_AVERAGE_SPEED,
        SettingsRepository.RECORDING_METRIC_MAX_SPEED,
        SettingsRepository.RECORDING_METRIC_CURRENT_PACE,
        SettingsRepository.RECORDING_METRIC_AVERAGE_PACE,
        SettingsRepository.RECORDING_METRIC_MAX_PACE,
        SettingsRepository.RECORDING_METRIC_HEART_RATE,
        SettingsRepository.RECORDING_METRIC_MAX_HEART_RATE,
        SettingsRepository.RECORDING_METRIC_STEPS,
        SettingsRepository.RECORDING_METRIC_CADENCE,
        SettingsRepository.RECORDING_METRIC_AVERAGE_CADENCE,
        SettingsRepository.RECORDING_METRIC_MAX_CADENCE,
        SettingsRepository.RECORDING_METRIC_POWER,
        SettingsRepository.RECORDING_METRIC_AVERAGE_POWER,
        SettingsRepository.RECORDING_METRIC_MAX_POWER,
        SettingsRepository.RECORDING_METRIC_BAROMETRIC_PRESSURE,
        SettingsRepository.RECORDING_METRIC_CALORIES,
        SettingsRepository.RECORDING_METRIC_ACTIVE_CALORIES,
        SettingsRepository.RECORDING_METRIC_RESTING_CALORIES,
    )
