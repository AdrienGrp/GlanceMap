package com.glancemap.glancemapwearos.data.repository

internal const val TURN_BY_TURN_DASHBOARD_PAGE_SLOT_COUNT = 4
internal const val TURN_BY_TURN_DASHBOARD_MIN_PAGE_COUNT = 1
internal const val TURN_BY_TURN_DASHBOARD_MAX_PAGE_COUNT = 5
internal const val TURN_BY_TURN_DASHBOARD_MAX_SLOT_COUNT =
    TURN_BY_TURN_DASHBOARD_PAGE_SLOT_COUNT * TURN_BY_TURN_DASHBOARD_MAX_PAGE_COUNT

internal fun normalizeTurnByTurnDashboardMetricSlots(metricSlots: List<String>): List<String> {
    val boundedSlots = metricSlots.take(TURN_BY_TURN_DASHBOARD_MAX_SLOT_COUNT)
    if (boundedSlots.isEmpty()) return SettingsRepository.DEFAULT_TURN_BY_TURN_DASHBOARD_METRICS
    val targetSize =
        boundedSlots.size
            .coerceAtLeast(TURN_BY_TURN_DASHBOARD_PAGE_SLOT_COUNT)
            .let { size ->
                val remainder = size % TURN_BY_TURN_DASHBOARD_PAGE_SLOT_COUNT
                if (remainder == 0) size else size + TURN_BY_TURN_DASHBOARD_PAGE_SLOT_COUNT - remainder
            }.coerceAtMost(TURN_BY_TURN_DASHBOARD_MAX_SLOT_COUNT)
    return (
        boundedSlots +
            generateSequence { SettingsRepository.DEFAULT_TURN_BY_TURN_DASHBOARD_NEW_PAGE_METRICS }
                .flatten()
                .take(targetSize - boundedSlots.size)
    ).take(targetSize)
}
