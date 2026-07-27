package com.glancemap.glancemapwearos.presentation.features.navigate

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigateMarkerIntervalTest {
    @Test
    fun serviceEffectiveIntervalOverridesConfiguredEstimate() {
        assertEquals(
            12_000L,
            resolveMarkerGpsIntervalMs(
                serviceEffectiveIntervalMs = 12_000L,
                configuredIntervalMs = 3_000L,
            ),
        )
    }

    @Test
    fun configuredIntervalIsFallbackUntilServiceReportsOne() {
        assertEquals(
            60_000L,
            resolveMarkerGpsIntervalMs(
                serviceEffectiveIntervalMs = 0L,
                configuredIntervalMs = 60_000L,
            ),
        )
    }
}
