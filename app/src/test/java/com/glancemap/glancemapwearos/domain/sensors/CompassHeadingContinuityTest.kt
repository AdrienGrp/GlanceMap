package com.glancemap.glancemapwearos.domain.sensors

import org.junit.Assert.assertEquals
import org.junit.Test

class CompassHeadingContinuityTest {
    @Test
    fun fastSampleDuringRelockNeedsTimedConfirmation() {
        val tooSoon =
            resolveSensorLargeJumpAction(
                sensorJumpInput(inRelock = true, pendingAgeMs = 45L),
            )
        val confirmed =
            resolveSensorLargeJumpAction(
                sensorJumpInput(inRelock = true, pendingAgeMs = 220L),
            )

        assertEquals(LargeJumpAction.REJECT_PENDING, tooSoon)
        assertEquals(LargeJumpAction.ACCEPT_CONFIRMED, confirmed)
    }

    @Test
    fun steadyStateNeedsLongerCoherentConfirmation() {
        val tooSoon =
            resolveSensorLargeJumpAction(
                sensorJumpInput(inRelock = false, pendingAgeMs = 220L),
            )
        val confirmed =
            resolveSensorLargeJumpAction(
                sensorJumpInput(inRelock = false, pendingAgeMs = 320L),
            )
        val incoherent =
            resolveSensorLargeJumpAction(
                sensorJumpInput(
                    inRelock = false,
                    pendingAgeMs = 320L,
                    pendingDeltaDeg = 60f,
                ),
            )

        assertEquals(LargeJumpAction.REJECT_PENDING, tooSoon)
        assertEquals(LargeJumpAction.ACCEPT_CONFIRMED, confirmed)
        assertEquals(LargeJumpAction.REJECT_PENDING, incoherent)
    }

    @Test
    fun normalContinuousTurnStepRemainsResponsive() {
        val action =
            resolveSensorLargeJumpAction(
                sensorJumpInput(jumpDeg = 32f, hasPendingLargeJump = false),
            )

        assertEquals(LargeJumpAction.NONE, action)
        assertEquals(
            21f,
            stepHeadingTowardConfirmedReanchor(
                currentHeadingDeg = 10f,
                targetHeadingDeg = 21f,
            ),
            0.0001f,
        )
    }

    @Test
    fun continuousFullTurnPassesWithoutArtificialLag() {
        var displayedHeading = 0f
        val fullTurnSamples = (12..348 step 12).map(Int::toFloat) + 0f

        fullTurnSamples.forEach { targetHeading ->
            displayedHeading =
                stepHeadingTowardConfirmedReanchor(
                    currentHeadingDeg = displayedHeading,
                    targetHeadingDeg = targetHeading,
                )
            assertEquals(targetHeading, displayedHeading, 0.0001f)
        }
    }

    @Test
    fun isolatedSpikeIsRejectedAndNormalHeadingCanContinue() {
        val spike =
            resolveSensorLargeJumpAction(
                sensorJumpInput(hasPendingLargeJump = false),
            )
        val recovered =
            resolveSensorLargeJumpAction(
                sensorJumpInput(jumpDeg = 5f, hasPendingLargeJump = true),
            )

        assertEquals(LargeJumpAction.REJECT_PENDING, spike)
        assertEquals(LargeJumpAction.NONE, recovered)
    }

    @Test
    fun confirmedReanchorBoundsVisibleJumpAndWraps() {
        assertEquals(
            182f,
            stepHeadingTowardConfirmedReanchor(
                currentHeadingDeg = 200f,
                targetHeadingDeg = 50f,
            ),
            0.0001f,
        )
        assertEquals(
            8f,
            stepHeadingTowardConfirmedReanchor(
                currentHeadingDeg = 350f,
                targetHeadingDeg = 40f,
            ),
            0.0001f,
        )
    }

    private fun sensorJumpInput(
        jumpDeg: Float = 106f,
        inRelock: Boolean = true,
        hasPendingLargeJump: Boolean = true,
        pendingDeltaDeg: Float = 4f,
        pendingAgeMs: Long = 0L,
    ): SensorLargeJumpInput =
        SensorLargeJumpInput(
            jumpDeg = jumpDeg,
            inRelock = inRelock,
            hasPendingLargeJump = hasPendingLargeJump,
            pendingDeltaDeg = pendingDeltaDeg,
            pendingAgeMs = pendingAgeMs,
        )
}
