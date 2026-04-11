package com.glancemap.glancemapwearos.core.service.location.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationOutputFilterTest {
    @Test
    fun prefersTrustedRawSpeedWhenAvailable() {
        val speedMps =
            resolveOutputSpeedMps(
                hasRawSpeed = true,
                rawSpeedMps = 1.4f,
                accuracyM = 18f,
                estimatedSpeedMps = 0.2f,
                positionStdDevMeters = 14f,
            )

        assertEquals(1.4f, speedMps ?: 0f, 0.001f)
    }

    @Test
    fun prefersEstimatedSpeedWhenRawSpeedLooksUnderreported() {
        val speedMps =
            resolveOutputSpeedMps(
                hasRawSpeed = true,
                rawSpeedMps = 0.3f,
                accuracyM = 8f,
                estimatedSpeedMps = 1.2f,
                positionStdDevMeters = 4f,
            )

        assertEquals(1.2f, speedMps ?: 0f, 0.001f)
    }

    @Test
    fun suppressesEstimatedSpeedWhenFixAccuracyIsPoor() {
        val speedMps =
            resolveOutputSpeedMps(
                hasRawSpeed = false,
                rawSpeedMps = null,
                accuracyM = 28f,
                estimatedSpeedMps = 1.3f,
                positionStdDevMeters = 4f,
            )

        assertNull(speedMps)
    }

    @Test
    fun suppressesEstimatedSpeedWhenFilterConfidenceIsWeak() {
        val speedMps =
            resolveOutputSpeedMps(
                hasRawSpeed = false,
                rawSpeedMps = null,
                accuracyM = 8f,
                estimatedSpeedMps = 1.3f,
                positionStdDevMeters = 14f,
            )

        assertNull(speedMps)
    }

    @Test
    fun exposesEstimatedSpeedWhenFixAndFilterAreBothStable() {
        val speedMps =
            resolveOutputSpeedMps(
                hasRawSpeed = false,
                rawSpeedMps = null,
                accuracyM = 6f,
                estimatedSpeedMps = 1.2f,
                positionStdDevMeters = 5f,
            )

        assertEquals(1.2f, speedMps ?: 0f, 0.001f)
    }
}
