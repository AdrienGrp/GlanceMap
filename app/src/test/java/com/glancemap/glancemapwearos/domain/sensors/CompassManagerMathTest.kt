package com.glancemap.glancemapwearos.domain.sensors

import android.hardware.SensorManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompassManagerMathTest {
    @Test
    fun resolveHeadingPipelineAutoPrefersHeadingSensor() {
        val pipeline =
            resolveHeadingPipeline(
                mode = CompassHeadingSourceMode.AUTO,
                headingSensorAvailable = true,
                rotationVectorAvailable = true,
                magAccelFallbackAvailable = true,
            )
        assertEquals(HeadingPipeline.HEADING_SENSOR, pipeline)
    }

    @Test
    fun resolveHeadingPipelineExplicitHeadingDoesNotSilentlyFallback() {
        val pipeline =
            resolveHeadingPipeline(
                mode = CompassHeadingSourceMode.TYPE_HEADING,
                headingSensorAvailable = false,
                rotationVectorAvailable = true,
                magAccelFallbackAvailable = true,
            )
        assertEquals(HeadingPipeline.NONE, pipeline)
    }

    @Test
    fun resolveHeadingPipelineExplicitRotationVectorDoesNotSilentlyFallback() {
        val pipeline =
            resolveHeadingPipeline(
                mode = CompassHeadingSourceMode.ROTATION_VECTOR,
                headingSensorAvailable = true,
                rotationVectorAvailable = false,
                magAccelFallbackAvailable = true,
            )
        assertEquals(HeadingPipeline.NONE, pipeline)
    }

    @Test
    fun resolveHeadingPipelineExplicitMagnetometerDoesNotSilentlyFallback() {
        val pipeline =
            resolveHeadingPipeline(
                mode = CompassHeadingSourceMode.MAGNETOMETER,
                headingSensorAvailable = true,
                rotationVectorAvailable = true,
                magAccelFallbackAvailable = false,
            )
        assertEquals(HeadingPipeline.NONE, pipeline)
    }

    @Test
    fun shortestAngleDiffDegPreservesDirectionAt180() {
        assertEquals(180f, shortestAngleDiffDeg(target = 180f, current = 0f), 0.0001f)
        assertEquals(-180f, shortestAngleDiffDeg(target = 0f, current = 180f), 0.0001f)
    }

    @Test
    fun shortestAngleDiffDegWrapsAcrossZero() {
        assertEquals(20f, shortestAngleDiffDeg(target = 10f, current = 350f), 0.0001f)
        assertEquals(-20f, shortestAngleDiffDeg(target = 350f, current = 10f), 0.0001f)
    }

    @Test
    fun inferHeadingAccuracyCapsFastTurnAtMediumWhenNoiseIsLow() {
        val inferred =
            inferHeadingAccuracy(
                noiseDeg = 0.8f,
                turnRateDegPerSec = 80f,
            )
        assertEquals(SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM, inferred)
    }

    @Test
    fun inferHeadingAccuracyKeepsFastTurnLowWhenNoiseIsHigh() {
        val inferred =
            inferHeadingAccuracy(
                noiseDeg = 7.2f,
                turnRateDegPerSec = 80f,
            )
        assertEquals(SensorManager.SENSOR_STATUS_ACCURACY_LOW, inferred)
    }

    @Test
    fun inferHeadingAccuracyAllowsModeratelyFastTurnWhenNoiseIsLow() {
        val inferred =
            inferHeadingAccuracy(
                noiseDeg = 0.8f,
                turnRateDegPerSec = 65f,
            )
        assertEquals(SensorManager.SENSOR_STATUS_ACCURACY_HIGH, inferred)
    }

    @Test
    fun inferHeadingAccuracyUsesRelaxedNoiseThresholds() {
        assertEquals(
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
            inferHeadingAccuracy(noiseDeg = 2.9f, turnRateDegPerSec = 0f),
        )
        assertEquals(
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
            inferHeadingAccuracy(noiseDeg = 5.3f, turnRateDegPerSec = 0f),
        )
        assertEquals(
            SensorManager.SENSOR_STATUS_ACCURACY_LOW,
            inferHeadingAccuracy(noiseDeg = 8.7f, turnRateDegPerSec = 0f),
        )
    }

    @Test
    fun combineCompassAccuracyUpgradesRotVecWhenSensorUnavailable() {
        val combined =
            combineCompassAccuracy(
                sensorAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE,
                inferredAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
                usingRotationVector = true,
            )
        assertEquals(SensorManager.SENSOR_STATUS_ACCURACY_LOW, combined)
    }

    @Test
    fun combineCompassAccuracyUsesWeakerSignalWhenBothPresent() {
        val combined =
            combineCompassAccuracy(
                sensorAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
                inferredAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                usingRotationVector = true,
            )
        assertEquals(SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM, combined)
    }

    @Test
    fun combineCompassAccuracyCapsAtLowWhenMagneticInterferenceDetected() {
        val combined =
            combineCompassAccuracy(
                sensorAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
                inferredAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
                usingRotationVector = true,
                hasMagneticInterference = true,
            )
        assertEquals(SensorManager.SENSOR_STATUS_ACCURACY_LOW, combined)
    }

    @Test
    fun combineCompassAccuracyKeepsLowWhenInferenceDropsButSensorStillGood() {
        val combined =
            combineCompassAccuracy(
                sensorAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
                inferredAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE,
                usingRotationVector = true,
            )
        assertEquals(SensorManager.SENSOR_STATUS_ACCURACY_LOW, combined)
    }

    @Test
    fun combineCompassAccuracyStaysUnreliableWhenBothSignalsAreUnreliable() {
        val combined =
            combineCompassAccuracy(
                sensorAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE,
                inferredAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE,
                usingRotationVector = true,
            )
        assertEquals(SensorManager.SENSOR_STATUS_UNRELIABLE, combined)
    }

    @Test
    fun headingWithNorthReferenceAppliesDeclinationInTrueMode() {
        val heading =
            headingWithNorthReference(
                azimuthDeg = 350f,
                declinationDeg = 12f,
                northReferenceMode = NorthReferenceMode.TRUE,
            )
        assertEquals(2f, heading, 0.0001f)
    }

    @Test
    fun headingWithNorthReferenceSkipsDeclinationInMagneticMode() {
        val heading =
            headingWithNorthReference(
                azimuthDeg = 350f,
                declinationDeg = 12f,
                northReferenceMode = NorthReferenceMode.MAGNETIC,
            )
        assertEquals(350f, heading, 0.0001f)
    }

    @Test
    fun remapHeadingForNorthReferenceSwitchAddsDeclinationWhenSwitchingToTrue() {
        val heading =
            remapHeadingForNorthReferenceSwitch(
                currentHeadingDeg = 350f,
                fromMode = NorthReferenceMode.MAGNETIC,
                toMode = NorthReferenceMode.TRUE,
                declinationDeg = 12f,
            )
        assertEquals(2f, heading, 0.0001f)
    }

    @Test
    fun remapHeadingForNorthReferenceSwitchSubtractsDeclinationWhenSwitchingToMagnetic() {
        val heading =
            remapHeadingForNorthReferenceSwitch(
                currentHeadingDeg = 2f,
                fromMode = NorthReferenceMode.TRUE,
                toMode = NorthReferenceMode.MAGNETIC,
                declinationDeg = 12f,
            )
        assertEquals(350f, heading, 0.0001f)
    }

    @Test
    fun deadbandConvergenceAlphaNudgesOnlyWhenStableAndInsideDeadband() {
        val enabled =
            deadbandConvergenceAlpha(
                diffDeg = 0.5f,
                minDeltaDeg = 0.9f,
                isFastTurn = false,
                isModerateTurn = false,
                isNoisy = false,
            )
        val disabledByTurn =
            deadbandConvergenceAlpha(
                diffDeg = 0.5f,
                minDeltaDeg = 0.9f,
                isFastTurn = true,
                isModerateTurn = false,
                isNoisy = false,
            )
        assertEquals(0.08f, enabled, 0.0001f)
        assertEquals(0f, disabledByTurn, 0.0001f)
    }

    @Test
    fun headingAccuracyFromUncertaintyMapsExpectedBands() {
        assertEquals(
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
            headingAccuracyFromUncertainty(6f),
        )
        assertEquals(
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
            headingAccuracyFromUncertainty(12f),
        )
        assertEquals(
            SensorManager.SENSOR_STATUS_ACCURACY_LOW,
            headingAccuracyFromUncertainty(22f),
        )
        assertEquals(
            SensorManager.SENSOR_STATUS_UNRELIABLE,
            headingAccuracyFromUncertainty(45f),
        )
    }

    @Test
    fun resolveLargeJumpActionAcceptsImmediatelyDuringRelock() {
        val action =
            resolveLargeJumpAction(
                jumpDeg = 140f,
                inRelock = true,
                hasPendingLargeJump = false,
                pendingDeltaDeg = Float.NaN,
            )
        assertEquals(LargeJumpAction.ACCEPT_IMMEDIATE, action)
    }

    @Test
    fun resolveLargeJumpActionRequiresCoherentConfirmationOutsideRelock() {
        val confirmed =
            resolveLargeJumpAction(
                jumpDeg = 140f,
                inRelock = false,
                hasPendingLargeJump = true,
                pendingDeltaDeg = 20f,
            )
        val rejected =
            resolveLargeJumpAction(
                jumpDeg = 140f,
                inRelock = false,
                hasPendingLargeJump = true,
                pendingDeltaDeg = 60f,
            )
        assertEquals(LargeJumpAction.ACCEPT_CONFIRMED, confirmed)
        assertEquals(LargeJumpAction.REJECT_PENDING, rejected)
    }

    @Test
    fun resolveStartupTransientActionFirstSampleAwaitsConfirmation() {
        val decision =
            resolveStartupTransientAction(
                rawDeg = 48f,
                candidateHeadingDeg = null,
                remainingSamplesToIgnore = 2,
                withinStartupWindow = true,
                usingRotationVector = true,
                hasInit = false,
            )
        require(decision != null)
        assertEquals(StartupTransientAction.IGNORE_AWAIT_CONFIRMATION, decision.action)
        assertEquals(48f, decision.nextCandidateHeadingDeg!!, 0.0001f)
        assertEquals(1, decision.nextRemainingSamplesToIgnore)
        assertNull(decision.acceptedHeadingDeg)
    }

    @Test
    fun resolveStartupTransientActionAcceptsConfirmedSample() {
        val decision =
            resolveStartupTransientAction(
                rawDeg = 60f,
                candidateHeadingDeg = 48f,
                remainingSamplesToIgnore = 1,
                withinStartupWindow = true,
                usingRotationVector = true,
                hasInit = false,
            )
        require(decision != null)
        assertEquals(StartupTransientAction.ACCEPT_CONFIRMED, decision.action)
        assertNull(decision.nextCandidateHeadingDeg)
        assertEquals(0, decision.nextRemainingSamplesToIgnore)
        assertEquals(60f, decision.acceptedHeadingDeg!!, 0.0001f)
    }

    @Test
    fun resolveStartupTransientActionReplacesCandidateWhenStillUnconfirmed() {
        val decision =
            resolveStartupTransientAction(
                rawDeg = 200f,
                candidateHeadingDeg = 48f,
                remainingSamplesToIgnore = 2,
                withinStartupWindow = true,
                usingRotationVector = true,
                hasInit = false,
            )
        require(decision != null)
        assertEquals(StartupTransientAction.IGNORE_REPLACE_CANDIDATE, decision.action)
        assertEquals(200f, decision.nextCandidateHeadingDeg!!, 0.0001f)
        assertEquals(1, decision.nextRemainingSamplesToIgnore)
        assertNull(decision.acceptedHeadingDeg)
    }

    @Test
    fun resolveStartupTransientActionStopsFilteringAfterBudgetExpires() {
        val decision =
            resolveStartupTransientAction(
                rawDeg = 200f,
                candidateHeadingDeg = 48f,
                remainingSamplesToIgnore = 1,
                withinStartupWindow = true,
                usingRotationVector = true,
                hasInit = false,
            )
        require(decision != null)
        assertEquals(StartupTransientAction.ACCEPT_FORCED, decision.action)
        assertNull(decision.nextCandidateHeadingDeg)
        assertEquals(0, decision.nextRemainingSamplesToIgnore)
        assertEquals(200f, decision.acceptedHeadingDeg!!, 0.0001f)
    }

    @Test
    fun shouldMaskStartupHeadingPublishOnlyWhenLargeJumpDuringWarmup() {
        val masked =
            shouldMaskStartupHeadingPublish(
                candidateHeadingDeg = 42f,
                displayedHeadingDeg = 20f,
                remainingPublishesToMask = 2,
                withinMaskWindow = true,
            )
        val notMaskedSmallDelta =
            shouldMaskStartupHeadingPublish(
                candidateHeadingDeg = 24f,
                displayedHeadingDeg = 20f,
                remainingPublishesToMask = 2,
                withinMaskWindow = true,
            )
        assertEquals(true, masked)
        assertEquals(false, notMaskedSmallDelta)
    }

    @Test
    fun shouldMaskStartupHeadingPublishStopsOutsideWindowOrBudget() {
        val noBudget =
            shouldMaskStartupHeadingPublish(
                candidateHeadingDeg = 42f,
                displayedHeadingDeg = 20f,
                remainingPublishesToMask = 0,
                withinMaskWindow = true,
            )
        val outOfWindow =
            shouldMaskStartupHeadingPublish(
                candidateHeadingDeg = 42f,
                displayedHeadingDeg = 20f,
                remainingPublishesToMask = 2,
                withinMaskWindow = false,
            )
        assertEquals(false, noBudget)
        assertEquals(false, outOfWindow)
    }

    @Test
    fun resolveHeadingSmoothingMinDeltaShrinksForTurns() {
        assertEquals(
            0.4f,
            resolveHeadingSmoothingMinDelta(
                isFastTurn = false,
                isModerateTurn = false,
                isNoisy = false,
            ),
            0.0001f,
        )
        assertEquals(
            0.6f,
            resolveHeadingSmoothingMinDelta(
                isFastTurn = false,
                isModerateTurn = true,
                isNoisy = false,
            ),
            0.0001f,
        )
        assertEquals(
            0.35f,
            resolveHeadingSmoothingMinDelta(
                isFastTurn = true,
                isModerateTurn = false,
                isNoisy = false,
            ),
            0.0001f,
        )
    }

    @Test
    fun resolveHeadingSmoothingAlphaGetsMoreResponsiveAsTurnRateRises() {
        assertEquals(
            0.19f,
            resolveHeadingSmoothingAlpha(
                diffDeg = 10f,
                isFastTurn = false,
                isModerateTurn = false,
                isNoisy = false,
            ),
            0.0001f,
        )
        assertEquals(
            0.28f,
            resolveHeadingSmoothingAlpha(
                diffDeg = 10f,
                isFastTurn = false,
                isModerateTurn = true,
                isNoisy = false,
            ),
            0.0001f,
        )
        assertEquals(
            0.54f,
            resolveHeadingSmoothingAlpha(
                diffDeg = 10f,
                isFastTurn = true,
                isModerateTurn = false,
                isNoisy = false,
            ),
            0.0001f,
        )
    }

    @Test
    fun stepMagneticInterferenceStartupGraceResetsBaseline() {
        val state =
            MagneticInterferenceState(
                strengthUt = 62f,
                emaUt = 61f,
                holdUntilElapsedMs = 50_000L,
                detected = true,
            )
        val duringGrace =
            stepMagneticInterferenceState(
                state = state,
                strengthUt = 68f,
                nowElapsedMs = 10_000L,
                startupGraceUntilElapsedMs = 11_000L,
            )
        assertEquals(false, duringGrace.state.strengthUt.isFinite())
        assertEquals(false, duringGrace.state.emaUt.isFinite())
        assertEquals(0L, duringGrace.state.holdUntilElapsedMs)
        assertEquals(false, duringGrace.state.detected)

        // First sample after grace must not trigger a synthetic spike against grace-era values.
        val firstAfterGrace =
            stepMagneticInterferenceState(
                state = duringGrace.state,
                strengthUt = 34f,
                nowElapsedMs = 11_100L,
                startupGraceUntilElapsedMs = 11_000L,
            )
        assertEquals(false, firstAfterGrace.state.detected)
        assertEquals(0f, firstAfterGrace.deltaUt, 0.0001f)
    }

    @Test
    fun stepMagneticInterferenceMaintainsHoldAndClearsAfterExpiry() {
        val initial =
            MagneticInterferenceState(
                strengthUt = 40f,
                emaUt = 40f,
                holdUntilElapsedMs = 0L,
                detected = false,
            )
        val spike =
            stepMagneticInterferenceState(
                state = initial,
                strengthUt = 70f,
                nowElapsedMs = 1_000L,
                startupGraceUntilElapsedMs = 0L,
            )
        assertEquals(true, spike.state.detected)
        assertEquals("spike", spike.reason)

        val held =
            stepMagneticInterferenceState(
                state = spike.state,
                strengthUt = 71f,
                nowElapsedMs = 2_000L,
                startupGraceUntilElapsedMs = 0L,
            )
        assertEquals(true, held.state.detected)

        val afterHold =
            stepMagneticInterferenceState(
                state = held.state,
                strengthUt = 71f,
                nowElapsedMs = 4_200L,
                startupGraceUntilElapsedMs = 0L,
            )
        assertEquals(false, afterHold.state.detected)
        assertEquals("hold_expired", afterHold.reason)
    }
}
