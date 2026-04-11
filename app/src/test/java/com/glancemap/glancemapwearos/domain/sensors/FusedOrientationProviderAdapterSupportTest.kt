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
    fun largeRestartJumpConfirmsReplacementHeading() {
        val decision =
            resolveFusedRestartHeadingDecision(
                pendingHeadingDeg = 4.2f,
                displayHeadingDeg = 163.5f,
                pendingAtElapsedMs = 100L,
                nowElapsedMs = 159L,
                pendingSampleCount = 1,
                timeoutMs = 160L,
            )

        assertEquals(FusedRestartHeadingAction.CONFIRM, decision.action)
        assertNull(decision.nextPendingHeadingDeg)
        assertEquals(0L, decision.nextPendingAtElapsedMs)
        assertEquals(0, decision.nextPendingSampleCount)
        assertEquals(2, decision.sampleCount)
        assertEquals("changed", decision.confirmReason)
        assertTrue(decision.deltaDeg > 100f)
    }

    @Test
    fun timeoutAlsoConfirmsLatestHeading() {
        val decision =
            resolveFusedRestartHeadingDecision(
                pendingHeadingDeg = 4.2f,
                displayHeadingDeg = 5.0f,
                pendingAtElapsedMs = 100L,
                nowElapsedMs = 280L,
                pendingSampleCount = 2,
                timeoutMs = 160L,
            )

        assertEquals(FusedRestartHeadingAction.CONFIRM, decision.action)
        assertEquals(3, decision.sampleCount)
        assertEquals("timeout", decision.confirmReason)
        assertTrue(decision.deltaDeg < 2f)
    }

    @Test
    fun bootstrapSensorHeadingCanBridgeGoogleFusedWarmup() {
        val fusedState = initialCompassRenderState(providerType = CompassProviderType.GOOGLE_FUSED)
        val bootstrapState =
            initialCompassRenderState(providerType = CompassProviderType.SENSOR_MANAGER).copy(
                headingDeg = 212f,
                accuracy = android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                headingSource = HeadingSource.ROTATION_VECTOR,
            )

        assertTrue(
            shouldUseFusedBootstrapHeading(
                fusedRenderState = fusedState,
                bootstrapRenderState = bootstrapState,
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
            )

        assertFalse(
            shouldUseFusedBootstrapHeading(
                fusedRenderState = fusedState,
                bootstrapRenderState = bootstrapState,
            ),
        )
    }
}
