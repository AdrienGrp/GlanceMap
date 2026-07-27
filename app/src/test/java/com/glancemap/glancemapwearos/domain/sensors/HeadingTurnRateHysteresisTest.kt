package com.glancemap.glancemapwearos.domain.sensors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingTurnRateHysteresisTest {
    @Test
    fun entersOnDeliberateTurnAndStaysActiveThroughSlowerSamples() {
        val tracker = createTracker()

        assertFalse(tracker.update(headingDeg = 0f, atElapsedMs = 1_000L))
        assertTrue(tracker.update(headingDeg = 1.2f, atElapsedMs = 1_040L))
        assertTrue(tracker.update(headingDeg = 2.0f, atElapsedMs = 1_080L))
        assertTrue(tracker.update(headingDeg = 2.7f, atElapsedMs = 1_120L))
    }

    @Test
    fun exitsOnlyAfterAngularMovementStaysBelowExitRate() {
        val tracker = createTracker()

        tracker.update(headingDeg = 0f, atElapsedMs = 1_000L)
        assertTrue(tracker.update(headingDeg = 1.2f, atElapsedMs = 1_040L))
        assertTrue(tracker.update(headingDeg = 1.4f, atElapsedMs = 1_080L))
        assertTrue(tracker.update(headingDeg = 6.0f, atElapsedMs = 1_300L))
        assertTrue(tracker.update(headingDeg = 6.1f, atElapsedMs = 1_340L))
        assertTrue(tracker.update(headingDeg = 6.2f, atElapsedMs = 1_600L))
        assertFalse(tracker.update(headingDeg = 6.3f, atElapsedMs = 1_680L))
    }

    @Test
    fun handlesNorthCrossingAndRejectsAnIsolatedSmallNoiseStep() {
        val tracker = createTracker()

        assertFalse(tracker.update(headingDeg = 359.5f, atElapsedMs = 1_000L))
        assertFalse(tracker.update(headingDeg = 359.8f, atElapsedMs = 1_020L))
        assertTrue(tracker.update(headingDeg = 0.8f, atElapsedMs = 1_060L))
    }

    @Test
    fun resetsTurnStateAfterAStaleSampleGap() {
        val tracker = createTracker()

        tracker.update(headingDeg = 0f, atElapsedMs = 1_000L)
        assertTrue(tracker.update(headingDeg = 1.2f, atElapsedMs = 1_040L))
        assertFalse(tracker.update(headingDeg = 20f, atElapsedMs = 1_500L))
    }

    private fun createTracker() =
        HeadingTurnRateHysteresis(
            enterRateDegPerSec = 25f,
            exitRateDegPerSec = 15f,
            exitHoldMs = 300L,
            minimumEntryStepDeg = 0.4f,
            maximumSampleGapMs = 300L,
        )
}
