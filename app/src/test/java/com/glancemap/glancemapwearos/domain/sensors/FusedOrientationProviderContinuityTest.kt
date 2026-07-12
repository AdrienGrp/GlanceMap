package com.glancemap.glancemapwearos.domain.sensors

import android.hardware.SensorManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FusedOrientationProviderContinuityTest {
    @Test
    fun retainedStaleSensorHeadingCannotBridgeFusedWarmup() {
        val fusedState = initialCompassRenderState(providerType = CompassProviderType.GOOGLE_FUSED)
        val retainedBootstrapState =
            initialCompassRenderState(providerType = CompassProviderType.SENSOR_MANAGER).copy(
                headingDeg = 212f,
                accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                headingSource = HeadingSource.ROTATION_VECTOR,
                headingSampleElapsedRealtimeMs = 900L,
                headingSampleStale = true,
            )

        assertFalse(
            shouldUseFusedBootstrapHeading(
                fusedRenderState = fusedState,
                bootstrapRenderState = retainedBootstrapState,
                nowElapsedMs = 1_000L,
            ),
        )
    }

    @Test
    fun unreliableRecentFusedCacheDoesNotHideFreshSensorBootstrap() {
        val unreliableFusedState =
            initialCompassRenderState(providerType = CompassProviderType.GOOGLE_FUSED).copy(
                headingDeg = 184f,
                accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE,
                headingSource = HeadingSource.NONE,
                headingSampleElapsedRealtimeMs = 900L,
                headingSampleStale = true,
            )
        val freshBootstrapState =
            initialCompassRenderState(providerType = CompassProviderType.SENSOR_MANAGER).copy(
                headingDeg = 212f,
                accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                headingSource = HeadingSource.ROTATION_VECTOR,
                headingSampleElapsedRealtimeMs = 950L,
                headingSampleStale = false,
            )

        assertTrue(
            shouldUseFusedBootstrapHeading(
                fusedRenderState = unreliableFusedState,
                bootstrapRenderState = freshBootstrapState,
                nowElapsedMs = 1_000L,
            ),
        )
    }

    @Test
    fun warmHandoffBoundsEachVisibleStepAcrossNorth() {
        assertEquals(
            0f,
            boundedFusedHandoffHeading(
                currentHeadingDeg = 350f,
                targetHeadingDeg = 20f,
                maxStepDeg = 10f,
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            boundedFusedHandoffHeading(
                currentHeadingDeg = 10f,
                targetHeadingDeg = 350f,
                maxStepDeg = 10f,
            ),
            0.0001f,
        )
    }

    @Test
    fun warmHandoffPublishesTargetOnceItIsWithinStepBudget() {
        assertEquals(
            5f,
            boundedFusedHandoffHeading(
                currentHeadingDeg = 358f,
                targetHeadingDeg = 5f,
                maxStepDeg = 10f,
            ),
            0.0001f,
        )
    }

    @Test
    fun loggedWakeHandoffConvergesWithoutReplayingItsNinetyDegreeJump() {
        var displayedHeadingDeg = 301.4f
        val targetHeadingDeg = 211.4f

        repeat(9) {
            val previousHeadingDeg = displayedHeadingDeg
            displayedHeadingDeg =
                boundedFusedHandoffHeading(
                    currentHeadingDeg = displayedHeadingDeg,
                    targetHeadingDeg = targetHeadingDeg,
                    maxStepDeg = 10f,
                )
            val visibleStepDeg =
                abs(
                    shortestAngleDiffDeg(
                        target = displayedHeadingDeg,
                        current = previousHeadingDeg,
                    ),
                )
            assertTrue(visibleStepDeg <= 10.0001f)
        }

        assertEquals(targetHeadingDeg, displayedHeadingDeg, 0.0001f)
    }

    @Test
    fun staleRetryAllowsLowPowerConfirmationCadence() {
        assertEquals(
            800L,
            resolveFusedFirstConfirmedSampleTimeoutMs(
                requestReason = "sample_stale_retry",
                lowPowerMode = true,
                recalibrationBoostActive = false,
            ),
        )
        assertEquals(
            500L,
            resolveFusedFirstConfirmedSampleTimeoutMs(
                requestReason = "sample_stale_retry",
                lowPowerMode = false,
                recalibrationBoostActive = false,
            ),
        )
        assertEquals(
            1_200L,
            resolveFusedFirstConfirmedSampleTimeoutMs(
                requestReason = "start",
                lowPowerMode = true,
                recalibrationBoostActive = false,
            ),
        )
    }
}
