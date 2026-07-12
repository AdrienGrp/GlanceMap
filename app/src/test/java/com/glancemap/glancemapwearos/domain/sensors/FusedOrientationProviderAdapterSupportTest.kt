package com.glancemap.glancemapwearos.domain.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FusedOrientationProviderAdapterSupportTest {
    @Test
    fun firstRestartSampleIsHeldBackUntilThereIsConfirmation() {
        val decision =
            resolveFusedRestartHeadingDecision(
                pendingHeadingDeg = null,
                displayHeadingDeg = 4.2f,
                pendingAtElapsedMs = 0L,
                nowElapsedMs = 100L,
                pendingSampleCount = 0,
                timeoutMs = 160L,
                headingErrorDeg = 25f,
                conservativeHeadingErrorDeg = 180f,
            )

        assertEquals(FusedRestartHeadingAction.IGNORE_FIRST, decision.action)
        assertEquals(4.2f, decision.nextPendingHeadingDeg)
        assertEquals(100L, decision.nextPendingAtElapsedMs)
        assertEquals(1, decision.nextPendingSampleCount)
        assertEquals(1, decision.sampleCount)
        assertNull(decision.confirmReason)
    }

    @Test
    fun matchingRestartSamplesStayPendingWithinConfirmationWindow() {
        val decision =
            resolveFusedRestartHeadingDecision(
                pendingHeadingDeg = 4.2f,
                displayHeadingDeg = 4.8f,
                pendingAtElapsedMs = 100L,
                nowElapsedMs = 140L,
                pendingSampleCount = 1,
                timeoutMs = 160L,
                headingErrorDeg = 25f,
                conservativeHeadingErrorDeg = 180f,
            )

        assertEquals(FusedRestartHeadingAction.AWAIT_PENDING, decision.action)
        assertEquals(4.2f, decision.nextPendingHeadingDeg)
        assertEquals(100L, decision.nextPendingAtElapsedMs)
        assertEquals(2, decision.nextPendingSampleCount)
        assertEquals(2, decision.sampleCount)
        assertNull(decision.confirmReason)
        assertTrue(decision.deltaDeg < 2f)
    }

    @Test
    fun largeRestartJumpReseedsPendingHeadingUntilItStabilizes() {
        val decision =
            resolveFusedRestartHeadingDecision(
                pendingHeadingDeg = 4.2f,
                displayHeadingDeg = 163.5f,
                pendingAtElapsedMs = 100L,
                nowElapsedMs = 159L,
                pendingSampleCount = 1,
                timeoutMs = 160L,
                headingErrorDeg = 25f,
                conservativeHeadingErrorDeg = 180f,
            )

        assertEquals(FusedRestartHeadingAction.AWAIT_PENDING, decision.action)
        assertEquals(163.5f, decision.nextPendingHeadingDeg)
        assertEquals(159L, decision.nextPendingAtElapsedMs)
        assertEquals(1, decision.nextPendingSampleCount)
        assertEquals(2, decision.sampleCount)
        assertNull(decision.confirmReason)
        assertTrue(decision.deltaDeg > 100f)
    }

    @Test
    fun stableRestartSamplesWaitForSettleTimeWhenConfidenceIsWeak() {
        val decision =
            resolveFusedRestartHeadingDecision(
                pendingHeadingDeg = 4.2f,
                displayHeadingDeg = 5.0f,
                pendingAtElapsedMs = 100L,
                nowElapsedMs = 180L,
                pendingSampleCount = 2,
                timeoutMs = 160L,
                headingErrorDeg = 25f,
                conservativeHeadingErrorDeg = 180f,
            )

        assertEquals(FusedRestartHeadingAction.AWAIT_PENDING, decision.action)
        assertEquals(4.2f, decision.nextPendingHeadingDeg)
        assertEquals(3, decision.sampleCount)
        assertNull(decision.confirmReason)
        assertTrue(decision.deltaDeg < 15f)
    }

    @Test
    fun stableRestartSamplesConfirmAfterSettleTimeWhenConfidenceIsWeak() {
        val decision =
            resolveFusedRestartHeadingDecision(
                pendingHeadingDeg = 4.2f,
                displayHeadingDeg = 5.0f,
                pendingAtElapsedMs = 100L,
                nowElapsedMs = 280L,
                pendingSampleCount = 2,
                timeoutMs = 160L,
                headingErrorDeg = 25f,
                conservativeHeadingErrorDeg = 180f,
            )

        assertEquals(FusedRestartHeadingAction.CONFIRM, decision.action)
        assertEquals(3, decision.sampleCount)
        assertEquals("timeout", decision.confirmReason)
        assertTrue(decision.deltaDeg < 15f)
    }

    @Test
    fun trustedConservativeErrorCanConfirmChangedHeadingFaster() {
        val decision =
            resolveFusedRestartHeadingDecision(
                pendingHeadingDeg = 4.2f,
                displayHeadingDeg = 163.5f,
                pendingAtElapsedMs = 100L,
                nowElapsedMs = 159L,
                pendingSampleCount = 1,
                timeoutMs = 160L,
                headingErrorDeg = 10f,
                conservativeHeadingErrorDeg = 25f,
            )

        assertEquals(FusedRestartHeadingAction.CONFIRM, decision.action)
        assertEquals(2, decision.sampleCount)
        assertEquals("confidence", decision.confirmReason)
    }

    @Test
    fun timeoutConfirmsWhenStableSamplesPersistLongEnough() {
        val decision =
            resolveFusedRestartHeadingDecision(
                pendingHeadingDeg = 4.2f,
                displayHeadingDeg = 5.0f,
                pendingAtElapsedMs = 100L,
                nowElapsedMs = 280L,
                pendingSampleCount = 1,
                timeoutMs = 160L,
                headingErrorDeg = 25f,
                conservativeHeadingErrorDeg = 180f,
            )

        assertEquals(FusedRestartHeadingAction.CONFIRM, decision.action)
        assertEquals(2, decision.sampleCount)
        assertEquals("timeout", decision.confirmReason)
    }

    @Test
    fun timeoutConfirmedStartupIsUnstableWhenReseedHistoryIsChaotic() {
        val decision =
            resolveFusedRestartHeadingDecision(
                pendingHeadingDeg = 271.9f,
                displayHeadingDeg = 271.9f,
                pendingAtElapsedMs = 100L,
                nowElapsedMs = 280L,
                pendingSampleCount = 9,
                timeoutMs = 160L,
                headingErrorDeg = 25f,
                conservativeHeadingErrorDeg = 180f,
            )

        assertEquals(FusedRestartHeadingAction.CONFIRM, decision.action)
        assertTrue(
            isUnstableFusedStartup(
                decision = decision,
                overlapMaxDeltaDeg = 155.9f,
                overlapFinalDeltaDeg = 151.2f,
                reseedCount = 5,
                maxReseedDeltaDeg = 160.8f,
            ),
        )
    }

    @Test
    fun timeoutConfirmedStartupIsNotUnstableForSingleCoherentCluster() {
        val decision =
            resolveFusedRestartHeadingDecision(
                pendingHeadingDeg = 107.1f,
                displayHeadingDeg = 119.4f,
                pendingAtElapsedMs = 100L,
                nowElapsedMs = 280L,
                pendingSampleCount = 9,
                timeoutMs = 160L,
                headingErrorDeg = 25f,
                conservativeHeadingErrorDeg = 180f,
            )

        assertEquals(FusedRestartHeadingAction.CONFIRM, decision.action)
        assertFalse(
            isUnstableFusedStartup(
                decision = decision,
                overlapMaxDeltaDeg = 95.5f,
                overlapFinalDeltaDeg = 4.2f,
                reseedCount = 1,
                maxReseedDeltaDeg = 102.8f,
            ),
        )
    }

    @Test
    fun transientStartupMotionDoesNotTriggerRestartAfterProvidersConverge() {
        val decision =
            resolveFusedRestartHeadingDecision(
                pendingHeadingDeg = 212f,
                displayHeadingDeg = 214f,
                pendingAtElapsedMs = 100L,
                nowElapsedMs = 280L,
                pendingSampleCount = 9,
                timeoutMs = 160L,
                headingErrorDeg = 25f,
                conservativeHeadingErrorDeg = 180f,
            )

        assertEquals(FusedRestartHeadingAction.CONFIRM, decision.action)
        assertFalse(
            isUnstableFusedStartup(
                decision = decision,
                overlapMaxDeltaDeg = 170f,
                overlapFinalDeltaDeg = 3f,
                reseedCount = 6,
                maxReseedDeltaDeg = 165f,
            ),
        )
    }

    @Test
    fun unusableRestartSamplesStayPendingEvenAfterConfirmationBudget() {
        val decision =
            resolveFusedRestartHeadingDecision(
                pendingHeadingDeg = 4.2f,
                displayHeadingDeg = 5.0f,
                pendingAtElapsedMs = 100L,
                nowElapsedMs = 320L,
                pendingSampleCount = 3,
                timeoutMs = 160L,
                headingErrorDeg = 180f,
                conservativeHeadingErrorDeg = 180f,
            )

        assertEquals(FusedRestartHeadingAction.AWAIT_PENDING, decision.action)
        assertEquals(4.2f, decision.nextPendingHeadingDeg)
        assertEquals(4, decision.sampleCount)
        assertNull(decision.confirmReason)
    }

    @Test
    fun relockLargeJumpNeedsConfirmationWhenFusedConfidenceIsWeak() {
        val action =
            resolveFusedLargeJumpAction(
                FusedLargeJumpInput(
                    jumpDeg = 148f,
                    inRelock = true,
                    hasPendingLargeJump = false,
                    pendingDeltaDeg = Float.NaN,
                    pendingAgeMs = 0L,
                    pendingConsistentSampleCount = 0,
                    headingErrorDeg = 25f,
                    conservativeHeadingErrorDeg = 180f,
                ),
            )

        assertEquals(LargeJumpAction.REJECT_PENDING, action)
    }

    @Test
    fun relockLargeJumpCanStillPassImmediatelyWhenFusedConfidenceIsGood() {
        val action =
            resolveFusedLargeJumpAction(
                FusedLargeJumpInput(
                    jumpDeg = 148f,
                    inRelock = true,
                    hasPendingLargeJump = false,
                    pendingDeltaDeg = Float.NaN,
                    pendingAgeMs = 0L,
                    pendingConsistentSampleCount = 0,
                    headingErrorDeg = 8f,
                    conservativeHeadingErrorDeg = 30f,
                ),
            )

        assertEquals(LargeJumpAction.ACCEPT_IMMEDIATE, action)
    }

    @Test
    fun relockPendingJumpConfirmsAfterMinimumQuarantine() {
        val action =
            resolveFusedLargeJumpAction(
                FusedLargeJumpInput(
                    jumpDeg = 148f,
                    inRelock = true,
                    hasPendingLargeJump = true,
                    pendingDeltaDeg = 12f,
                    pendingAgeMs = 320L,
                    pendingConsistentSampleCount = 2,
                    headingErrorDeg = 25f,
                    conservativeHeadingErrorDeg = 180f,
                ),
            )

        assertEquals(LargeJumpAction.ACCEPT_CONFIRMED, action)
    }

    @Test
    fun relockPendingJumpStillWaitsWhenWeakConfidenceSamplesArriveTooQuickly() {
        val action =
            resolveFusedLargeJumpAction(
                FusedLargeJumpInput(
                    jumpDeg = 148f,
                    inRelock = true,
                    hasPendingLargeJump = true,
                    pendingDeltaDeg = 12f,
                    pendingAgeMs = 20L,
                    pendingConsistentSampleCount = 2,
                    headingErrorDeg = 25f,
                    conservativeHeadingErrorDeg = 180f,
                ),
            )

        assertEquals(LargeJumpAction.REJECT_PENDING, action)
    }

    @Test
    fun rapidLargeJumpStaysQuarantinedDespiteThreeQuickSamples() {
        val action =
            resolveFusedLargeJumpAction(
                FusedLargeJumpInput(
                    jumpDeg = 170f,
                    inRelock = false,
                    hasPendingLargeJump = true,
                    pendingDeltaDeg = 22f,
                    pendingAgeMs = 45L,
                    pendingConsistentSampleCount = 3,
                    headingErrorDeg = 25f,
                    conservativeHeadingErrorDeg = 180f,
                ),
            )

        assertEquals(LargeJumpAction.REJECT_PENDING, action)
    }

    @Test
    fun trustedLargeJumpOutsideRelockStillWaitsForQuarantine() {
        val action =
            resolveFusedLargeJumpAction(
                FusedLargeJumpInput(
                    jumpDeg = 150f,
                    inRelock = false,
                    hasPendingLargeJump = true,
                    pendingDeltaDeg = 2f,
                    pendingAgeMs = 45L,
                    pendingConsistentSampleCount = 3,
                    headingErrorDeg = 8f,
                    conservativeHeadingErrorDeg = 30f,
                ),
            )

        assertEquals(LargeJumpAction.REJECT_PENDING, action)
    }

    @Test
    fun sustainedLargeJumpConfirmsAfterQuarantine() {
        val action =
            resolveFusedLargeJumpAction(
                FusedLargeJumpInput(
                    jumpDeg = 170f,
                    inRelock = false,
                    hasPendingLargeJump = true,
                    pendingDeltaDeg = 22f,
                    pendingAgeMs = 320L,
                    pendingConsistentSampleCount = 8,
                    headingErrorDeg = 25f,
                    conservativeHeadingErrorDeg = 180f,
                ),
            )

        assertEquals(LargeJumpAction.ACCEPT_CONFIRMED, action)
    }

    @Test
    fun pendingLargeJumpIsAcceptedAfterAbsoluteTimeoutDespiteInconsistentSamples() {
        val action =
            resolveFusedLargeJumpAction(
                FusedLargeJumpInput(
                    jumpDeg = 148f,
                    inRelock = false,
                    hasPendingLargeJump = true,
                    pendingDeltaDeg = 80f,
                    pendingAgeMs = 500L,
                    pendingConsistentSampleCount = 1,
                    headingErrorDeg = 25f,
                    conservativeHeadingErrorDeg = 180f,
                ),
            )

        assertEquals(LargeJumpAction.ACCEPT_CONFIRMED, action)
        assertEquals(
            FusedLargeJumpAcceptanceReason.TIMEOUT_500_MS,
            fusedLargeJumpAcceptanceReason(action = action, pendingAgeMs = 500L),
        )
    }

    @Test
    fun coherentPendingLargeJumpReportsStableAcceptanceReason() {
        val action =
            resolveFusedLargeJumpAction(
                FusedLargeJumpInput(
                    jumpDeg = 148f,
                    inRelock = false,
                    hasPendingLargeJump = true,
                    pendingDeltaDeg = 12f,
                    pendingAgeMs = 320L,
                    pendingConsistentSampleCount = 2,
                    headingErrorDeg = 25f,
                    conservativeHeadingErrorDeg = 180f,
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
    fun bootstrapSensorHeadingCanBridgeGoogleFusedWarmup() {
        val fusedState = initialCompassRenderState(providerType = CompassProviderType.GOOGLE_FUSED)
        val bootstrapState =
            initialCompassRenderState(providerType = CompassProviderType.SENSOR_MANAGER).copy(
                headingDeg = 212f,
                accuracy = android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                headingSource = HeadingSource.ROTATION_VECTOR,
                headingSampleElapsedRealtimeMs = 900L,
                headingSampleStale = false,
            )

        assertTrue(
            shouldUseFusedBootstrapHeading(
                fusedRenderState = fusedState,
                bootstrapRenderState = bootstrapState,
                nowElapsedMs = 1_000L,
            ),
        )

        val bridged =
            bootstrapFusedRenderState(
                fusedRenderState = fusedState,
                bootstrapRenderState = bootstrapState,
            )

        assertEquals(CompassProviderType.GOOGLE_FUSED, bridged.providerType)
        assertEquals(212f, bridged.headingDeg)
        assertEquals(HeadingSource.ROTATION_VECTOR, bridged.headingSource)
        assertEquals(900L, bridged.headingSampleElapsedRealtimeMs)
        assertFalse(bridged.headingSampleStale)
    }

    @Test
    fun bootstrapSensorHeadingStopsOnceFreshFusedHeadingExists() {
        val fusedState =
            initialCompassRenderState(providerType = CompassProviderType.GOOGLE_FUSED).copy(
                headingSource = HeadingSource.FUSED_ORIENTATION,
                accuracy = android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                headingSampleElapsedRealtimeMs = 1_000L,
                headingSampleStale = false,
            )
        val bootstrapState =
            initialCompassRenderState(providerType = CompassProviderType.SENSOR_MANAGER).copy(
                headingDeg = 212f,
                accuracy = android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                headingSource = HeadingSource.ROTATION_VECTOR,
                headingSampleElapsedRealtimeMs = 1_100L,
                headingSampleStale = false,
            )

        assertFalse(
            shouldUseFusedBootstrapHeading(
                fusedRenderState = fusedState,
                bootstrapRenderState = bootstrapState,
                nowElapsedMs = 1_200L,
            ),
        )
    }

    @Test
    fun bootstrapSensorHeadingContinuesWhenFreshFusedHeadingIsUnreliable() {
        val fusedState =
            initialCompassRenderState(providerType = CompassProviderType.GOOGLE_FUSED).copy(
                headingSource = HeadingSource.FUSED_ORIENTATION,
                accuracy = android.hardware.SensorManager.SENSOR_STATUS_UNRELIABLE,
                headingSampleElapsedRealtimeMs = 1_000L,
                headingSampleStale = false,
            )
        val bootstrapState =
            initialCompassRenderState(providerType = CompassProviderType.SENSOR_MANAGER).copy(
                headingDeg = 212f,
                accuracy = android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                headingSource = HeadingSource.ROTATION_VECTOR,
                headingSampleElapsedRealtimeMs = 1_100L,
                headingSampleStale = false,
            )

        assertTrue(
            shouldUseFusedBootstrapHeading(
                fusedRenderState = fusedState,
                bootstrapRenderState = bootstrapState,
                nowElapsedMs = 1_200L,
            ),
        )
    }

    @Test
    fun recentCachedFusedHeadingSuppressesBootstrapBridgeDuringWarmRestart() {
        val fusedState =
            initialCompassRenderState(providerType = CompassProviderType.GOOGLE_FUSED).copy(
                headingDeg = 184f,
                accuracy = android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                headingSampleElapsedRealtimeMs = 10_000L,
                headingSampleStale = true,
                headingSource = HeadingSource.FUSED_ORIENTATION,
            )
        val bootstrapState =
            initialCompassRenderState(providerType = CompassProviderType.SENSOR_MANAGER).copy(
                headingDeg = 212f,
                accuracy = android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                headingSource = HeadingSource.ROTATION_VECTOR,
                headingSampleElapsedRealtimeMs = 13_900L,
                headingSampleStale = false,
            )

        assertFalse(
            shouldUseFusedBootstrapHeading(
                fusedRenderState = fusedState,
                bootstrapRenderState = bootstrapState,
                nowElapsedMs = 14_000L,
            ),
        )
    }

    @Test
    fun unusableFusedHeadingTriggersFallbackAfterSamplesAndDuration() {
        val first =
            computeFusedUnusableHeadingUpdate(
                nowElapsedMs = 1_000L,
                consecutiveUnusableSamples = 0,
                firstUnusableSampleAtElapsedMs = 0L,
                minSamples = 3,
                minDurationMs = 1_000L,
            )
        val second =
            computeFusedUnusableHeadingUpdate(
                nowElapsedMs = 1_500L,
                consecutiveUnusableSamples = first.state.consecutiveSamples,
                firstUnusableSampleAtElapsedMs = first.state.firstSampleAtElapsedMs,
                minSamples = 3,
                minDurationMs = 1_000L,
            )
        val third =
            computeFusedUnusableHeadingUpdate(
                nowElapsedMs = 2_100L,
                consecutiveUnusableSamples = second.state.consecutiveSamples,
                firstUnusableSampleAtElapsedMs = second.state.firstSampleAtElapsedMs,
                minSamples = 3,
                minDurationMs = 1_000L,
            )

        assertFalse(first.shouldFallback)
        assertFalse(second.shouldFallback)
        assertTrue(third.shouldFallback)
        assertEquals(3, third.state.consecutiveSamples)
        assertEquals(1_100L, third.durationMs)
    }
}
