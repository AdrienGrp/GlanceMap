package com.glancemap.glancemapwearos.domain.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FusedOrientationProviderContinuityTest {
    @Test
    fun warmupWaitsForTimeAndASecondUsableSample() {
        val first =
            updateFusedWarmup(
                firstUsableSampleAtElapsedMs = 0L,
                usableSampleCount = 0,
                nowElapsedMs = 1_000L,
            )
        val tooSoon =
            updateFusedWarmup(
                firstUsableSampleAtElapsedMs = first.firstUsableSampleAtElapsedMs,
                usableSampleCount = first.usableSampleCount,
                nowElapsedMs = 1_080L,
            )
        val ready =
            updateFusedWarmup(
                firstUsableSampleAtElapsedMs = tooSoon.firstUsableSampleAtElapsedMs,
                usableSampleCount = tooSoon.usableSampleCount,
                nowElapsedMs = 1_120L,
            )

        assertFalse(first.ready)
        assertFalse(tooSoon.ready)
        assertTrue(ready.ready)
        assertEquals(3, ready.usableSampleCount)
        assertEquals(120L, ready.warmupAgeMs)
    }

    @Test
    fun headingMovementCannotRestartWarmup() {
        val first =
            updateFusedWarmup(
                firstUsableSampleAtElapsedMs = 0L,
                usableSampleCount = 0,
                nowElapsedMs = 2_000L,
            )
        val readyAfterLargeTurn =
            updateFusedWarmup(
                firstUsableSampleAtElapsedMs = first.firstUsableSampleAtElapsedMs,
                usableSampleCount = first.usableSampleCount,
                nowElapsedMs = 2_200L,
            )

        assertTrue(readyAfterLargeTurn.ready)
        assertEquals(2_000L, readyAfterLargeTurn.firstUsableSampleAtElapsedMs)
        assertEquals(200L, readyAfterLargeTurn.warmupAgeMs)
    }

    @Test
    fun lowPowerCadenceCanReleaseOnItsSecondSample() {
        val first =
            updateFusedWarmup(
                firstUsableSampleAtElapsedMs = 0L,
                usableSampleCount = 0,
                nowElapsedMs = 5_000L,
            )
        val second =
            updateFusedWarmup(
                firstUsableSampleAtElapsedMs = first.firstUsableSampleAtElapsedMs,
                usableSampleCount = first.usableSampleCount,
                nowElapsedMs = 5_200L,
            )

        assertTrue(second.ready)
        assertEquals(200L, second.warmupAgeMs)
    }

    @Test
    fun invalidHeadingNeverCountsAsUsableOrientation() {
        assertFalse(
            isUsableGoogleFusedOrientationSample(
                headingDeg = Float.NaN,
                headingErrorDeg = 25f,
            ),
        )
        assertFalse(
            isUsableGoogleFusedOrientationSample(
                headingDeg = 90f,
                headingErrorDeg = 180f,
            ),
        )
        assertTrue(
            isUsableGoogleFusedOrientationSample(
                headingDeg = 90f,
                headingErrorDeg = 25f,
            ),
        )
    }

    @Test
    fun readyTimeoutsAllowLowPowerStaleRecovery() {
        assertEquals(
            1_200L,
            resolveFusedReadyTimeoutMs(
                requestReason = FUSED_STALE_SAMPLE_RETRY_REASON,
                lowPowerMode = true,
                recalibrationBoostActive = false,
            ),
        )
        assertEquals(
            1_000L,
            resolveFusedReadyTimeoutMs(
                requestReason = FUSED_STALE_SAMPLE_RETRY_REASON,
                lowPowerMode = false,
                recalibrationBoostActive = false,
            ),
        )
        assertEquals(
            1_000L,
            resolveFusedReadyTimeoutMs(
                requestReason = "start",
                lowPowerMode = true,
                recalibrationBoostActive = false,
            ),
        )
    }
}
