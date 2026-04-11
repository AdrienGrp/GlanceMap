package com.glancemap.glancemapwearos.core.service.location.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationTimingProfileTest {
    @Test
    fun oneSecondIntervalTightensPredictionAndFreshnessWindows() {
        val profile = resolveLocationTimingProfile(1_000L)

        assertEquals(1_500L, profile.markerPredictionFreshnessMaxAgeMs)
        assertEquals(3_000L, profile.strictFreshFixMaxAgeMs)
        assertEquals(4_000L, profile.selfHealFixGapMs)
        assertEquals(8_000L, profile.autoFusedNoFixFailoverGapMs)
    }

    @Test
    fun threeSecondIntervalMatchesExpectedWalkingProfile() {
        val profile = resolveLocationTimingProfile(3_000L)

        assertEquals(4_500L, profile.markerPredictionFreshnessMaxAgeMs)
        assertEquals(6_000L, profile.strictFreshFixMaxAgeMs)
        assertEquals(9_000L, profile.selfHealFixGapMs)
        assertEquals(12_000L, profile.autoFusedNoFixFailoverGapMs)
    }

    @Test
    fun longIntervalsStillCapPredictionWindow() {
        val profile = resolveLocationTimingProfile(60_000L)

        assertEquals(12_000L, profile.markerPredictionFreshnessMaxAgeMs)
        assertEquals(120_000L, profile.strictFreshFixMaxAgeMs)
        assertEquals(60_000L, profile.wakeAnchorMaxAgeMs)
    }
}
