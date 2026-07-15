package com.glancemap.glancemapwearos.domain.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FusedOrientationProviderAdapterSupportTest {
    @Test
    fun relockLargeJumpNeedsConfirmationWhenFusedConfidenceIsWeak() {
        assertEquals(
            LargeJumpAction.REJECT_PENDING,
            resolveFusedLargeJumpAction(
                defaultLargeJumpInput.copy(
                    inRelock = true,
                    headingErrorDeg = 25f,
                    conservativeHeadingErrorDeg = 180f,
                ),
            ),
        )
    }

    @Test
    fun relockLargeJumpCanPassImmediatelyWhenFusedConfidenceIsGood() {
        assertEquals(
            LargeJumpAction.ACCEPT_IMMEDIATE,
            resolveFusedLargeJumpAction(
                defaultLargeJumpInput.copy(
                    inRelock = true,
                    headingErrorDeg = 8f,
                    conservativeHeadingErrorDeg = 30f,
                ),
            ),
        )
    }

    @Test
    fun rapidLargeJumpStaysQuarantinedDespiteRepeatedSamples() {
        assertEquals(
            LargeJumpAction.REJECT_PENDING,
            resolveFusedLargeJumpAction(
                defaultLargeJumpInput.copy(
                    hasPendingLargeJump = true,
                    pendingDeltaDeg = 22f,
                    pendingAgeMs = 45L,
                    pendingConsistentSampleCount = 3,
                ),
            ),
        )
    }

    @Test
    fun sustainedLargeJumpConfirmsAfterQuarantine() {
        assertEquals(
            LargeJumpAction.ACCEPT_CONFIRMED,
            resolveFusedLargeJumpAction(
                defaultLargeJumpInput.copy(
                    hasPendingLargeJump = true,
                    pendingDeltaDeg = 22f,
                    pendingAgeMs = 320L,
                    pendingConsistentSampleCount = 8,
                ),
            ),
        )
    }

    @Test
    fun pendingLargeJumpIsAcceptedAfterAbsoluteTimeout() {
        val action =
            resolveFusedLargeJumpAction(
                defaultLargeJumpInput.copy(
                    hasPendingLargeJump = true,
                    pendingDeltaDeg = 80f,
                    pendingAgeMs = 500L,
                    pendingConsistentSampleCount = 1,
                ),
            )

        assertEquals(LargeJumpAction.ACCEPT_CONFIRMED, action)
        assertEquals(
            FusedLargeJumpAcceptanceReason.TIMEOUT_500_MS,
            fusedLargeJumpAcceptanceReason(action = action, pendingAgeMs = 500L),
        )
    }

    @Test
    fun coherentPendingLargeJumpReportsStableAcceptance() {
        val action =
            resolveFusedLargeJumpAction(
                defaultLargeJumpInput.copy(
                    hasPendingLargeJump = true,
                    pendingDeltaDeg = 12f,
                    pendingAgeMs = 320L,
                    pendingConsistentSampleCount = 2,
                ),
            )

        assertEquals(LargeJumpAction.ACCEPT_CONFIRMED, action)
        assertEquals(
            FusedLargeJumpAcceptanceReason.STABLE,
            fusedLargeJumpAcceptanceReason(action = action, pendingAgeMs = 320L),
        )
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
                nowElapsedMs = 1_033L,
                lastPublishAtElapsedMs = 1_000L,
                lowPowerMode = false,
                force = false,
            ),
        )
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

    private val defaultLargeJumpInput =
        FusedLargeJumpInput(
            jumpDeg = 148f,
            inRelock = false,
            hasPendingLargeJump = false,
            pendingDeltaDeg = Float.NaN,
            pendingAgeMs = 0L,
            pendingConsistentSampleCount = 0,
            headingErrorDeg = 25f,
            conservativeHeadingErrorDeg = 180f,
        )

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
