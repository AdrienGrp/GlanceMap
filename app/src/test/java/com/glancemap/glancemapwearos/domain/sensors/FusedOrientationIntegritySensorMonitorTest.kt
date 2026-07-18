package com.glancemap.glancemapwearos.domain.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class FusedOrientationIntegritySensorMonitorTest {
    @Test
    fun worldYawProducesAndroidHeadingDelta() {
        assertEquals(
            -90f,
            requireNotNull(
                gameRotationHeadingDeltaDeg(
                    previous = quaternion(axisX = 0f, axisY = 0f, axisZ = 1f, angleDeg = 0f),
                    current = quaternion(axisX = 0f, axisY = 0f, axisZ = 1f, angleDeg = 90f),
                ),
            ),
            ANGLE_TOLERANCE_DEG,
        )
    }

    @Test
    fun wristPitchDoesNotCreateAHeadingTurn() {
        assertEquals(
            0f,
            requireNotNull(
                gameRotationHeadingDeltaDeg(
                    previous = quaternion(axisX = 1f, axisY = 0f, axisZ = 0f, angleDeg = 80f),
                    current = quaternion(axisX = 1f, axisY = 0f, axisZ = 0f, angleDeg = 100f),
                ),
            ),
            ANGLE_TOLERANCE_DEG,
        )
    }

    @Test
    fun undefinedTwistIsIgnoredInsteadOfCreatingAJitterStep() {
        assertNull(
            gameRotationHeadingDeltaDeg(
                previous = quaternion(axisX = 1f, axisY = 0f, axisZ = 0f, angleDeg = 0f),
                current = quaternion(axisX = 1f, axisY = 0f, axisZ = 0f, angleDeg = 180f),
            ),
        )
    }

    @Test
    fun implausibleSingleSampleJumpIsRejected() {
        assertTrue(isPlausibleRelativeHeadingStep(20f, elapsedMs = 20L))
        assertFalse(isPlausibleRelativeHeadingStep(45f, elapsedMs = 20L))
        assertTrue(isPlausibleRelativeHeadingStep(90f, elapsedMs = 200L))
    }

    private fun quaternion(
        axisX: Float,
        axisY: Float,
        axisZ: Float,
        angleDeg: Float,
    ): OrientationQuaternion {
        val halfAngleRad = Math.toRadians(angleDeg.toDouble()) / 2.0
        val sine = sin(halfAngleRad).toFloat()
        return OrientationQuaternion(
            w = cos(halfAngleRad).toFloat(),
            x = axisX * sine,
            y = axisY * sine,
            z = axisZ * sine,
        )
    }

    private companion object {
        const val ANGLE_TOLERANCE_DEG = 0.01f
    }
}
