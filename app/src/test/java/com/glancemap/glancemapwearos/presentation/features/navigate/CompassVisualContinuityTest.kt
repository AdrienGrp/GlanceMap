package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.domain.sensors.shortestAngleDiffDeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompassVisualContinuityTest {
    @Test
    fun startAnchorsTargetToDisplayedHeadingAcrossNorth() {
        val state =
            startCompassVisualContinuity(
                displayedHeadingDeg = 10f,
                rawHeadingDeg = 350f,
            )

        assertEquals(20f, state.offsetDeg, 0.001f)
        assertEquals(10f, state.targetHeadingDeg, 0.001f)
        assertTrue(state.active)
    }

    @Test
    fun rawRotationPassesThroughWithoutChangingOffset() {
        val started =
            startCompassVisualContinuity(
                displayedHeadingDeg = 100f,
                rawHeadingDeg = 350f,
            )
        val turned =
            advanceCompassVisualContinuity(
                state = started,
                rawHeadingDeg = 10f,
                elapsedMs = 0L,
            )

        assertEquals(started.offsetDeg, turned.offsetDeg, 0.001f)
        assertEquals(120f, turned.targetHeadingDeg, 0.001f)
    }

    @Test
    fun offsetCorrectionIsTimeBased() {
        val started =
            startCompassVisualContinuity(
                displayedHeadingDeg = 0f,
                rawHeadingDeg = 90f,
            )
        val advanced =
            advanceCompassVisualContinuity(
                state = started,
                rawHeadingDeg = 90f,
                elapsedMs = 100L,
            )

        assertEquals(-72f, advanced.offsetDeg, 0.001f)
        assertEquals(18f, advanced.targetHeadingDeg, 0.001f)
    }

    @Test
    fun movingCorrectionKeepsTheRawTurnDirection() {
        val started =
            startCompassVisualContinuity(
                displayedHeadingDeg = 0f,
                rawHeadingDeg = 90f,
            )
        val firstFrame =
            advanceCompassVisualContinuity(
                state = started,
                rawHeadingDeg = 120f,
                elapsedMs = 100L,
            )
        val secondTurn =
            advanceCompassVisualContinuity(
                state = firstFrame,
                rawHeadingDeg = 150f,
                elapsedMs = 100L,
            )

        assertEquals(48f, firstFrame.targetHeadingDeg, 0.001f)
        assertEquals(96f, secondTurn.targetHeadingDeg, 0.001f)
        assertEquals(-54f, secondTurn.offsetDeg, 0.001f)
    }

    @Test
    fun correctionCannotReverseAnOpposingSlowTurn() {
        val started =
            startCompassVisualContinuity(
                displayedHeadingDeg = 0f,
                rawHeadingDeg = 90f,
            )
        val turned =
            advanceCompassVisualContinuity(
                state = started,
                rawHeadingDeg = 85f,
                elapsedMs = 333L,
            )

        assertEquals(357.5f, turned.targetHeadingDeg, 0.001f)
        assertEquals(-87.5f, turned.offsetDeg, 0.001f)
    }

    @Test
    fun subThresholdOpposingTurnStillCannotBeReversed() {
        val started =
            startCompassVisualContinuity(
                displayedHeadingDeg = 0f,
                rawHeadingDeg = 90f,
            )
        val turned =
            advanceCompassVisualContinuity(
                state = started,
                rawHeadingDeg = 89.99f,
                elapsedMs = 100L,
            )

        assertEquals(359.995f, turned.targetHeadingDeg, 0.001f)
        assertTrue(turned.offsetDeg < -89.99f)
    }

    @Test
    fun sameDirectionSlowTurnCorrectsPromptly() {
        val started =
            startCompassVisualContinuity(
                displayedHeadingDeg = 0f,
                rawHeadingDeg = 90f,
            )
        val turned =
            advanceCompassVisualContinuity(
                state = started,
                rawHeadingDeg = 95f,
                elapsedMs = 100L,
            )

        assertEquals(23f, turned.targetHeadingDeg, 0.001f)
        assertEquals(-72f, turned.offsetDeg, 0.001f)
    }

    @Test
    fun positiveLargeTurnCannotWrapTheVisualTargetBackwards() {
        val started =
            startCompassVisualContinuity(
                displayedHeadingDeg = 0f,
                rawHeadingDeg = 90f,
            )
        val turned =
            advanceCompassVisualContinuity(
                state = started,
                rawHeadingDeg = 260f,
                elapsedMs = 250L,
            )

        assertTrue(
            shortestAngleDiffDeg(
                target = turned.targetHeadingDeg,
                current = started.targetHeadingDeg,
            ) > 0f,
        )
        assertEquals(179.5f, turned.targetHeadingDeg, 0.001f)
    }

    @Test
    fun negativeLargeTurnCannotWrapTheVisualTargetForwards() {
        val started =
            startCompassVisualContinuity(
                displayedHeadingDeg = 0f,
                rawHeadingDeg = 270f,
            )
        val turned =
            advanceCompassVisualContinuity(
                state = started,
                rawHeadingDeg = 100f,
                elapsedMs = 250L,
            )

        assertTrue(
            shortestAngleDiffDeg(
                target = turned.targetHeadingDeg,
                current = started.targetHeadingDeg,
            ) < 0f,
        )
        assertEquals(180.5f, turned.targetHeadingDeg, 0.001f)
    }

    @Test
    fun alternatingStationaryJitterDoesNotStallCorrection() {
        var state =
            startCompassVisualContinuity(
                displayedHeadingDeg = 0f,
                rawHeadingDeg = 90f,
            )

        repeat(12) { index ->
            val jitteredRawHeading = if (index % 2 == 0) 90.2f else 90f
            state =
                advanceCompassVisualContinuity(
                    state = state,
                    rawHeadingDeg = jitteredRawHeading,
                    elapsedMs = 50L,
                )
        }

        assertTrue(state.offsetDeg > -40f)
    }

    @Test
    fun slowFullTurnRetiresOffsetWithoutASecondCorrectionAfterStopping() {
        var state =
            startCompassVisualContinuity(
                displayedHeadingDeg = 0f,
                rawHeadingDeg = 90f,
            )

        repeat(72) {
            state =
                advanceCompassVisualContinuity(
                    state = state,
                    rawHeadingDeg = state.rawHeadingDeg - 5f,
                    elapsedMs = 333L,
                )
        }

        assertFalse(state.active)
        assertEquals(0f, state.offsetDeg, 0.001f)
        assertEquals(90f, state.targetHeadingDeg, 0.001f)
    }

    @Test
    fun correctionCompletesAcrossVariableFrameGaps() {
        val started =
            startCompassVisualContinuity(
                displayedHeadingDeg = 0f,
                rawHeadingDeg = 90f,
            )
        val halfway =
            advanceCompassVisualContinuity(
                state = started,
                rawHeadingDeg = 90f,
                elapsedMs = 250L,
            )
        val complete =
            advanceCompassVisualContinuity(
                state = halfway,
                rawHeadingDeg = 90f,
                elapsedMs = 250L,
            )

        assertEquals(45f, halfway.targetHeadingDeg, 0.001f)
        assertTrue(halfway.active)
        assertEquals(90f, complete.targetHeadingDeg, 0.001f)
        assertFalse(complete.active)
    }

    @Test
    fun sourceChangeCanReanchorWithoutStackingOldOffset() {
        val oldSource =
            startCompassVisualContinuity(
                displayedHeadingDeg = 30f,
                rawHeadingDeg = 90f,
            )
        val newSource =
            startCompassVisualContinuity(
                displayedHeadingDeg = oldSource.targetHeadingDeg,
                rawHeadingDeg = 250f,
            )

        assertEquals(30f, newSource.targetHeadingDeg, 0.001f)
        assertEquals(140f, newSource.offsetDeg, 0.001f)
    }

    @Test
    fun invalidRawUpdateKeepsLastValidState() {
        val started =
            startCompassVisualContinuity(
                displayedHeadingDeg = 20f,
                rawHeadingDeg = 80f,
            )

        assertEquals(
            started,
            advanceCompassVisualContinuity(
                state = started,
                rawHeadingDeg = Float.NaN,
                elapsedMs = 100L,
            ),
        )
    }
}
