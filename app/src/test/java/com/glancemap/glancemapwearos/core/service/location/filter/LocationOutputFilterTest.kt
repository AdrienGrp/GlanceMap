package com.glancemap.glancemapwearos.core.service.location.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationOutputFilterTest {
    @Test
    fun prefersTrustedRawSpeedWhenAvailable() {
        val speedMps =
            resolveOutputSpeed(
                hasRawSpeed = true,
                rawSpeedMps = 1.4f,
                accuracyM = 18f,
                estimatedSpeedMps = 0.2f,
                positionStdDevMeters = 14f,
            )?.value

        assertEquals(1.4f, speedMps ?: 0f, 0.001f)
    }

    @Test
    fun prefersEstimatedSpeedWhenRawSpeedLooksUnderreported() {
        val speedMps =
            resolveOutputSpeed(
                hasRawSpeed = true,
                rawSpeedMps = 0.3f,
                accuracyM = 8f,
                estimatedSpeedMps = 1.2f,
                positionStdDevMeters = 4f,
            )?.value

        assertEquals(1.2f, speedMps ?: 0f, 0.001f)
    }

    @Test
    fun suppressesEstimatedSpeedWhenFixAccuracyIsPoor() {
        val speedMps =
            resolveOutputSpeed(
                hasRawSpeed = false,
                rawSpeedMps = null,
                accuracyM = 28f,
                estimatedSpeedMps = 1.3f,
                positionStdDevMeters = 4f,
            )?.value

        assertNull(speedMps)
    }

    @Test
    fun suppressesEstimatedSpeedWhenFilterConfidenceIsWeak() {
        val speedMps =
            resolveOutputSpeed(
                hasRawSpeed = false,
                rawSpeedMps = null,
                accuracyM = 8f,
                estimatedSpeedMps = 1.3f,
                positionStdDevMeters = 14f,
            )?.value

        assertNull(speedMps)
    }

    @Test
    fun exposesEstimatedSpeedWhenFixAndFilterAreBothStable() {
        val speedMps =
            resolveOutputSpeed(
                hasRawSpeed = false,
                rawSpeedMps = null,
                accuracyM = 6f,
                estimatedSpeedMps = 1.2f,
                positionStdDevMeters = 5f,
            )?.value

        assertEquals(1.2f, speedMps ?: 0f, 0.001f)
    }

    @Test
    fun retainedProviderSpeedKeepsProviderSpeedAccuracy() {
        val speed =
            resolveOutputSpeed(
                hasRawSpeed = true,
                rawSpeedMps = 1.4f,
                accuracyM = 18f,
                estimatedSpeedMps = 0.2f,
                positionStdDevMeters = 14f,
            )

        val retention =
            resolveOutputAccuracyRetention(
                speed = speed,
                bearing = null,
            )

        assertEquals(LocationOutputValueSource.PROVIDER, speed?.source)
        assertTrue(retention.retainSpeedAccuracy)
    }

    @Test
    fun estimatedSpeedDoesNotRetainProviderSpeedAccuracy() {
        val speed =
            resolveOutputSpeed(
                hasRawSpeed = true,
                rawSpeedMps = 0.3f,
                accuracyM = 8f,
                estimatedSpeedMps = 1.2f,
                positionStdDevMeters = 4f,
            )

        val retention =
            resolveOutputAccuracyRetention(
                speed = speed,
                bearing = null,
            )

        assertEquals(LocationOutputValueSource.FILTER_ESTIMATE, speed?.source)
        assertFalse(retention.retainSpeedAccuracy)
    }

    @Test
    fun estimatedBearingDoesNotRetainProviderBearingAccuracy() {
        val bearing =
            ResolvedLocationOutputValue(
                value = 84f,
                source = LocationOutputValueSource.FILTER_ESTIMATE,
            )

        val retention =
            resolveOutputAccuracyRetention(
                speed = null,
                bearing = bearing,
            )

        assertFalse(retention.retainBearingAccuracy)
    }

    @Test
    fun firstEstimateRetainsProviderBearingWhenFilterVelocityIsNotReady() {
        val bearing =
            resolveOutputBearing(
                LocationOutputBearingInput(
                    hasRawBearing = true,
                    rawBearingDeg = 92f,
                    rawBearingAccuracyDeg = 12f,
                    outputSpeed = ResolvedLocationOutputValue(1.5f, LocationOutputValueSource.PROVIDER),
                    estimatedSpeedMps = 0f,
                    estimatedBearingDeg = 0f,
                    positionStdDevMeters = 5f,
                ),
            )

        assertEquals(92f, bearing?.value ?: 0f, 0.001f)
        assertEquals(LocationOutputValueSource.PROVIDER, bearing?.source)
        assertTrue(resolveOutputAccuracyRetention(speed = null, bearing = bearing).retainBearingAccuracy)
    }

    @Test
    fun trustworthyFilterVelocityOwnsBearingWhenProviderAccuracyIsPoor() {
        val bearing =
            resolveOutputBearing(
                LocationOutputBearingInput(
                    hasRawBearing = true,
                    rawBearingDeg = 132f,
                    rawBearingAccuracyDeg = 80f,
                    outputSpeed = ResolvedLocationOutputValue(1.4f, LocationOutputValueSource.PROVIDER),
                    estimatedSpeedMps = 1.2f,
                    estimatedBearingDeg = 84f,
                    positionStdDevMeters = 5f,
                ),
            )

        assertEquals(84f, bearing?.value ?: 0f, 0.001f)
        assertEquals(LocationOutputValueSource.FILTER_ESTIMATE, bearing?.source)
        assertFalse(resolveOutputAccuracyRetention(speed = null, bearing = bearing).retainBearingAccuracy)
    }

    @Test
    fun reliableProviderBearingWinsDuringAFilterTurnLag() {
        val bearing =
            resolveOutputBearing(
                LocationOutputBearingInput(
                    hasRawBearing = true,
                    rawBearingDeg = 92f,
                    rawBearingAccuracyDeg = 10f,
                    outputSpeed = ResolvedLocationOutputValue(1.4f, LocationOutputValueSource.PROVIDER),
                    estimatedSpeedMps = 1.2f,
                    estimatedBearingDeg = 272f,
                    positionStdDevMeters = 5f,
                ),
            )

        assertEquals(92f, bearing?.value ?: 0f, 0.001f)
        assertEquals(LocationOutputValueSource.PROVIDER, bearing?.source)
        assertTrue(resolveOutputAccuracyRetention(speed = null, bearing = bearing).retainBearingAccuracy)
    }

    @Test
    fun omitsBearingWhenNeitherFilterNorProviderDirectionIsUsable() {
        val bearing =
            resolveOutputBearing(
                LocationOutputBearingInput(
                    hasRawBearing = true,
                    rawBearingDeg = 92f,
                    rawBearingAccuracyDeg = 10f,
                    outputSpeed = ResolvedLocationOutputValue(0.2f, LocationOutputValueSource.PROVIDER),
                    estimatedSpeedMps = 0f,
                    estimatedBearingDeg = 0f,
                    positionStdDevMeters = 5f,
                ),
            )

        assertNull(bearing)
    }
}
