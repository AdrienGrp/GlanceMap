package com.glancemap.glancemapwearos.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class TurnByTurnDashboardSlotDefaultsTest {
    @Test
    fun emptySlotsUseOneDefaultMetricPage() {
        assertEquals(
            SettingsRepository.DEFAULT_TURN_BY_TURN_DASHBOARD_METRICS,
            normalizeTurnByTurnDashboardMetricSlots(emptyList()),
        )
    }

    @Test
    fun partialPageIsFilledToFourSlots() {
        val normalized =
            normalizeTurnByTurnDashboardMetricSlots(
                listOf(SettingsRepository.TURN_BY_TURN_METRIC_PROGRESS),
            )

        assertEquals(TURN_BY_TURN_DASHBOARD_PAGE_SLOT_COUNT, normalized.size)
        assertEquals(SettingsRepository.TURN_BY_TURN_METRIC_PROGRESS, normalized.first())
    }
}
