package com.glancemap.glancemapwearos.domain.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FusedOrientationProviderAdapterSupportTest {
    @Test
    fun lowConfidenceGoogleHeadingRemainsUsableForDegradedTracking() {
        assertTrue(isUsableGoogleFusedHeadingError(45f))
        assertTrue(isUsableGoogleFusedHeadingError(179.9f))
        assertFalse(isUsableGoogleFusedHeadingError(180f))
        assertFalse(isUsableGoogleFusedHeadingError(Float.NaN))
    }

    @Test
    fun fusedHeadingPublishingCoalescesOverDeliveredHighPowerCallbacks() {
        assertTrue(
            shouldPublishFusedHeading(
                nowElapsedMs = 1_020L,
                lastPublishAtElapsedMs = 1_000L,
                lowPowerMode = false,
                force = true,
            ),
        )
        assertFalse(
            shouldPublishFusedHeading(
                nowElapsedMs = 1_020L,
                lastPublishAtElapsedMs = 1_000L,
                lowPowerMode = false,
                force = false,
            ),
        )
        assertTrue(
            shouldPublishFusedHeading(
                nowElapsedMs = 1_040L,
                lastPublishAtElapsedMs = 1_000L,
                lowPowerMode = false,
                force = false,
            ),
        )
    }

    @Test
    fun activeTurnPublishesAtFiftyHertzWithoutChangingTheSensorRequest() {
        assertFalse(
            shouldPublishFusedHeading(
                nowElapsedMs = 1_019L,
                lastPublishAtElapsedMs = 1_000L,
                lowPowerMode = false,
                activeTurn = true,
                force = false,
            ),
        )
        assertTrue(
            shouldPublishFusedHeading(
                nowElapsedMs = 1_020L,
                lastPublishAtElapsedMs = 1_000L,
                lowPowerMode = false,
                activeTurn = true,
                force = false,
            ),
        )
    }

    @Test
    fun activeTurnRequiresRelativeMovementRateInsteadOfAbsoluteHeadingNoise() {
        assertFalse(isActiveRelativeTurnStep(stepDeg = 0.3f, elapsedMs = 20L))
        assertFalse(isActiveRelativeTurnStep(stepDeg = 0.5f, elapsedMs = 40L))
        assertTrue(isActiveRelativeTurnStep(stepDeg = 1.2f, elapsedMs = 40L))
        assertFalse(isActiveRelativeTurnStep(stepDeg = 10f, elapsedMs = 500L))
    }

    @Test
    fun lowPowerHeadingPublishingKeepsFiveHertzCadence() {
        assertFalse(
            shouldPublishFusedHeading(
                nowElapsedMs = 1_179L,
                lastPublishAtElapsedMs = 1_000L,
                lowPowerMode = true,
                force = false,
            ),
        )
        assertTrue(
            shouldPublishFusedHeading(
                nowElapsedMs = 1_180L,
                lastPublishAtElapsedMs = 1_000L,
                lowPowerMode = true,
                force = false,
            ),
        )
    }

    @Test
    fun unusableFusedHeadingRequiresSamplesAndDurationBeforeFallback() {
        val first = unusableUpdate(nowMs = 1_000L, previous = null)
        val second = unusableUpdate(nowMs = 1_500L, previous = first)
        val third = unusableUpdate(nowMs = 2_100L, previous = second)

        assertFalse(first.shouldFallback)
        assertFalse(second.shouldFallback)
        assertTrue(third.shouldFallback)
        assertEquals(3, third.state.consecutiveSamples)
        assertEquals(1_100L, third.durationMs)
    }

    private fun unusableUpdate(
        nowMs: Long,
        previous: FusedUnusableHeadingUpdate?,
    ): FusedUnusableHeadingUpdate =
        computeFusedUnusableHeadingUpdate(
            nowElapsedMs = nowMs,
            consecutiveUnusableSamples = previous?.state?.consecutiveSamples ?: 0,
            firstUnusableSampleAtElapsedMs = previous?.state?.firstSampleAtElapsedMs ?: 0L,
            minSamples = 3,
            minDurationMs = 1_000L,
        )
}
