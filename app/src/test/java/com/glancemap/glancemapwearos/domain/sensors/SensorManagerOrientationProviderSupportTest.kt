package com.glancemap.glancemapwearos.domain.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorManagerOrientationProviderSupportTest {
    @Test
    fun initialHeadingHasNoTimestampAndIsStale() {
        val freshness = SensorHeadingSampleFreshness()

        assertNull(freshness.sampleAtElapsedRealtimeMs)
        assertTrue(freshness.stale)
    }

    @Test
    fun publishedHeadingCarriesItsElapsedRealtimeAndIsFresh() {
        val freshness =
            SensorHeadingSampleFreshness.afterPublish(
                sampleAtElapsedRealtimeMs = 12_345L,
            )

        assertEquals(12_345L, freshness.sampleAtElapsedRealtimeMs)
        assertFalse(freshness.stale)
    }

    @Test
    fun lifecycleStopRetainsTimestampButMarksHeadingStale() {
        val stoppedFreshness =
            SensorHeadingSampleFreshness
                .afterPublish(sampleAtElapsedRealtimeMs = 12_345L)
                .markStale()

        assertEquals(12_345L, stoppedFreshness.sampleAtElapsedRealtimeMs)
        assertTrue(stoppedFreshness.stale)
    }
}
