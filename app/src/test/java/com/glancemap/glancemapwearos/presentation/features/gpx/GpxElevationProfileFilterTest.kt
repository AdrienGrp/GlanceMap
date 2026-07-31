package com.glancemap.glancemapwearos.presentation.features.gpx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.LatLong
import kotlin.math.abs

class GpxElevationProfileFilterTest {
    @Test
    fun gradualRecordedClimbIsNotDiscardedAtDenseSampling() {
        val profile =
            profile(
                pointCount = 477,
                distanceMeters = 2_280.0,
            ) { progress ->
                180.0 + (32.0 * progress)
            }

        assertTrue(profile.totalAscent > 30.0)
        assertTrue(profile.totalDescent < 0.5)
    }

    @Test
    fun gradualClimbResultIsIndependentOfRecordingDensity() {
        val dense =
            profile(pointCount = 477, distanceMeters = 2_280.0) { progress ->
                180.0 + (32.0 * progress)
            }
        val sparse =
            profile(pointCount = 49, distanceMeters = 2_280.0) { progress ->
                180.0 + (32.0 * progress)
            }

        assertTrue(abs(dense.totalAscent - sparse.totalAscent) < 1.0)
        assertEquals(0.0, dense.totalDescent, 0.5)
        assertEquals(0.0, sparse.totalDescent, 0.5)
    }

    @Test
    fun lowReliefRecordedTrackRetainsConfirmedRollingTerrain() {
        val profile =
            profile(pointCount = 182, distanceMeters = 976.0) { progress ->
                when {
                    progress < 0.33 -> 212.0 - (4.0 * (progress / 0.33))
                    progress < 0.66 -> 208.0 + (5.0 * ((progress - 0.33) / 0.33))
                    else -> 213.0 - (5.0 * ((progress - 0.66) / 0.34))
                }
            }

        assertTrue(profile.totalAscent > 4.0)
        assertTrue(profile.totalDescent > 8.0)
    }

    @Test
    fun subThresholdFlatTerrainNoiseDoesNotAccumulateAsClimbing() {
        val profile =
            profile(pointCount = 240, distanceMeters = 1_200.0) { progress ->
                val sampleIndex = (progress * 239).toInt()
                200.0 + if (sampleIndex % 2 == 0) 0.12 else -0.12
            }

        assertTrue(profile.totalAscent < 0.5)
        assertTrue(profile.totalDescent < 0.5)
    }

    private fun profile(
        pointCount: Int,
        distanceMeters: Double,
        elevationAtProgress: (Double) -> Double,
    ): TrackProfile {
        val points =
            List(pointCount) { index ->
                val progress = index.toDouble() / (pointCount - 1).coerceAtLeast(1).toDouble()
                TrackPoint(
                    latLong = LatLong(0.0, (distanceMeters * progress) / METERS_PER_DEGREE_AT_EQUATOR),
                    elevation = elevationAtProgress(progress),
                    hasTimestamp = true,
                    timeMillis = index * 3_000L,
                )
            }
        return buildProfile(
            sig = FileSig(lastModified = 0L, length = pointCount.toLong()),
            pts = points,
        )
    }

    private companion object {
        const val METERS_PER_DEGREE_AT_EQUATOR = 111_320.0
    }
}
