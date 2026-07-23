package com.glancemap.glancemapwearos.domain.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FusedOrientationIntegritySensorMonitorTest {
    @Test
    fun screenTopProjectionUsesTheSamePhysicalAxisAsTheMapHeading() {
        val witness =
            gameRotationScreenTopWitness(
                rotationMatrix(
                    screenTopEast = 1f,
                    screenTopNorth = 0f,
                ),
            )

        assertEquals(90f, requireNotNull(witness.headingDeg), ANGLE_TOLERANCE_DEG)
        assertEquals(1f, witness.horizontalProjection, ANGLE_TOLERANCE_DEG)
    }

    @Test
    fun tiltedWatchWithoutHorizontalScreenTopHasNoHeadingWitness() {
        val witness =
            gameRotationScreenTopWitness(
                rotationMatrix(
                    screenTopEast = 0.1f,
                    screenTopNorth = 0.1f,
                ),
            )

        assertNull(witness.headingDeg)
        assertTrue(witness.horizontalProjection < 0.35f)
    }

    @Test
    fun normalWristPitchRetainsAStableScreenTopHeading() {
        val witness =
            gameRotationScreenTopWitness(
                rotationMatrix(
                    screenTopEast = 0f,
                    screenTopNorth = 0.8f,
                ),
            )

        assertEquals(0f, requireNotNull(witness.headingDeg), ANGLE_TOLERANCE_DEG)
        assertEquals(0.8f, witness.horizontalProjection, ANGLE_TOLERANCE_DEG)
    }

    @Test
    fun implausibleSingleSampleJumpIsRejected() {
        assertTrue(isPlausibleRelativeHeadingStep(20f, elapsedMs = 20L))
        assertFalse(isPlausibleRelativeHeadingStep(45f, elapsedMs = 20L))
        assertTrue(isPlausibleRelativeHeadingStep(90f, elapsedMs = 200L))
    }

    private fun rotationMatrix(
        screenTopEast: Float,
        screenTopNorth: Float,
    ): FloatArray =
        floatArrayOf(
            1f,
            screenTopEast,
            0f,
            0f,
            screenTopNorth,
            0f,
            0f,
            0f,
            1f,
        )

    private companion object {
        const val ANGLE_TOLERANCE_DEG = 0.01f
    }
}
