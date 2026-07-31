package com.glancemap.glancemapwearos.domain.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FusedOrientationProviderContinuityTest {
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
