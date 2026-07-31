package com.glancemap.glancemapwearos.presentation.features.recording

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mapsforge.core.model.LatLong

class RecordingTraceSmoothingTest {
    @Test
    fun lowSpeedMovementInsideAccuracyDeadbandIsSuppressed() {
        val previous = point(latitude = 45.0, longitude = 6.0, timeMillis = 1_000L, speedMps = 0.1f)
        val candidate = point(latitude = 45.00001, longitude = 6.0, timeMillis = 8_000L, speedMps = 0.2f)

        assertNotNull(recordingJitterDistanceToSuppress(previous, candidate))
    }

    @Test
    fun normalWalkingMovementIsPreserved() {
        val previous = point(latitude = 45.0, longitude = 6.0, timeMillis = 1_000L, speedMps = 1.1f)
        val candidate = point(latitude = 45.00001, longitude = 6.0, timeMillis = 8_000L, speedMps = 1.2f)

        assertNull(recordingJitterDistanceToSuppress(previous, candidate))
    }

    @Test
    fun stationaryKeepalivePointIsPreserved() {
        val previous = point(latitude = 45.0, longitude = 6.0, timeMillis = 1_000L, speedMps = 0.1f)
        val candidate = point(latitude = 45.00001, longitude = 6.0, timeMillis = 31_000L, speedMps = 0.1f)

        assertNull(recordingJitterDistanceToSuppress(previous, candidate))
    }

    private fun point(
        latitude: Double,
        longitude: Double,
        timeMillis: Long,
        speedMps: Float,
    ): RecordedTracePoint =
        RecordedTracePoint(
            latLong = LatLong(latitude, longitude),
            elevationMeters = null,
            timeMillis = timeMillis,
            accuracyMeters = 12f,
            speedMps = speedMps,
        )
}
