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
                state = FusedWarmupState(),
                headingDeg = 10f,
                nowElapsedMs = 1_000L,
            )
        val tooSoon =
            updateFusedWarmup(
                state = first.state,
                headingDeg = 12f,
                nowElapsedMs = 1_080L,
            )
        val ready =
            updateFusedWarmup(
                state = tooSoon.state,
                headingDeg = 14f,
                nowElapsedMs = 1_120L,
            )

        assertFalse(first.ready)
        assertFalse(tooSoon.ready)
        assertTrue(ready.ready)
        assertEquals(3, ready.state.usableSampleCount)
        assertEquals(3, ready.state.stableSampleCount)
        assertEquals(120L, ready.warmupAgeMs)
        assertEquals(120L, ready.stableAgeMs)
    }

    @Test
    fun normalHeadingMovementDoesNotRestartWarmup() {
        val first =
            updateFusedWarmup(
                state = FusedWarmupState(),
                headingDeg = 20f,
                nowElapsedMs = 2_000L,
            )
        val readyAfterLargeTurn =
            updateFusedWarmup(
                state = first.state,
                headingDeg = 100f,
                nowElapsedMs = 2_200L,
            )

        assertTrue(readyAfterLargeTurn.ready)
        assertFalse(readyAfterLargeTurn.relockDetected)
        assertEquals(2_000L, readyAfterLargeTurn.state.firstUsableSampleAtElapsedMs)
        assertEquals(200L, readyAfterLargeTurn.warmupAgeMs)
    }

    @Test
    fun implausibleRelockRestartsOnlyStableCluster() {
        val first =
            updateFusedWarmup(
                state = FusedWarmupState(),
                headingDeg = 190f,
                nowElapsedMs = 3_000L,
            )
        val relock =
            updateFusedWarmup(
                state = first.state,
                headingDeg = 40f,
                nowElapsedMs = 3_020L,
            )
        val settling =
            updateFusedWarmup(
                state = relock.state,
                headingDeg = 42f,
                nowElapsedMs = 3_100L,
            )
        val ready =
            updateFusedWarmup(
                state = settling.state,
                headingDeg = 44f,
                nowElapsedMs = 3_140L,
            )

        assertTrue(relock.relockDetected)
        assertEquals(150f, relock.stepDeg ?: 0f, 0.01f)
        assertEquals(50f, relock.allowedStepDeg ?: 0f, 0.01f)
        assertEquals(1, relock.state.relockResetCount)
        assertFalse(relock.ready)
        assertFalse(settling.ready)
        assertTrue(ready.ready)
        assertEquals(140L, ready.warmupAgeMs)
        assertEquals(120L, ready.stableAgeMs)
        assertEquals(3, ready.state.stableSampleCount)
    }

    @Test
    fun smallerObservedWakeRelockStillExceedsHighCadenceAllowance() {
        val first =
            updateFusedWarmup(
                state = FusedWarmupState(),
                headingDeg = 326f,
                nowElapsedMs = 3_500L,
            )
        val relock =
            updateFusedWarmup(
                state = first.state,
                headingDeg = 25f,
                nowElapsedMs = 3_520L,
            )

        assertEquals(59f, relock.stepDeg ?: 0f, 0.01f)
        assertEquals(50f, relock.allowedStepDeg ?: 0f, 0.01f)
        assertTrue(relock.relockDetected)
        assertFalse(relock.ready)
    }

    @Test
    fun wraparoundMovementRemainsPlausible() {
        val first =
            updateFusedWarmup(
                state = FusedWarmupState(),
                headingDeg = 355f,
                nowElapsedMs = 4_000L,
            )
        val second =
            updateFusedWarmup(
                state = first.state,
                headingDeg = 5f,
                nowElapsedMs = 4_120L,
            )

        assertFalse(second.relockDetected)
        assertEquals(10f, second.stepDeg ?: 0f, 0.01f)
        assertTrue(second.ready)
    }

    @Test
    fun lowPowerCadenceCanReleaseOnItsSecondSample() {
        val first =
            updateFusedWarmup(
                state = FusedWarmupState(),
                headingDeg = 0f,
                nowElapsedMs = 5_000L,
            )
        val second =
            updateFusedWarmup(
                state = first.state,
                headingDeg = 90f,
                nowElapsedMs = 5_200L,
            )

        assertFalse(second.relockDetected)
        assertEquals(104f, second.allowedStepDeg ?: 0f, 0.01f)
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
