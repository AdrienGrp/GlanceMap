package com.glancemap.glancemapwearos.presentation.features.gpx

import com.glancemap.glancemapwearos.core.gpx.GpxElevationFilterDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mapsforge.core.model.LatLong

class GpxEtaEstimatorTest {
    @Test
    fun buildsSharedProjectionForTotalAndIntermediateTimes() {
        val profile =
            testProfile(
                segmentLengths = doubleArrayOf(100.0, 200.0),
                elevations = listOf(100.0, 100.0, 100.0),
            )

        val projection = buildEtaProjection(profile, flatSpeedMps = 2.0)

        checkNotNull(projection)
        assertEquals(0.0, projection.secondsAtPointIndex(0)!!, 0.0)
        assertEquals(50.0, projection.secondsAtPointIndex(1)!!, 1e-6)
        assertEquals(150.0, projection.totalSeconds!!, 1e-6)
        assertEquals(
            75.0,
            projection.secondsAtTrackPosition(
                TrackPosition(
                    trackId = "test",
                    segmentIndex = 1,
                    t = 0.25,
                ),
            )!!,
            1e-6,
        )
    }

    @Test
    fun returnsNullProjectionWhenFlatSpeedIsNotPositive() {
        val profile =
            testProfile(
                segmentLengths = doubleArrayOf(100.0),
                elevations = listOf(100.0, 100.0),
            )

        assertNull(buildEtaProjection(profile, flatSpeedMps = 0.0))
        assertNull(buildEtaProjection(profile, flatSpeedMps = -1.0))
    }

    @Test
    fun appliesGradeAdjustmentInsideSharedProjection() {
        val profile =
            testProfile(
                segmentLengths = doubleArrayOf(100.0),
                elevations = listOf(100.0, 110.0),
            )

        val projection = buildEtaProjection(profile, flatSpeedMps = 2.0)

        checkNotNull(projection)
        assertEquals(70.96, projection.totalSeconds!!, 0.02)
    }

    @Test
    fun capsSteepUphillWhenAdvancedRateIsEnabled() {
        val profile =
            testProfile(
                segmentLengths = doubleArrayOf(100.0),
                elevations = listOf(100.0, 130.0),
            )

        val projection =
            buildEtaProjection(
                profile = profile,
                config =
                    GpxEtaModelConfig(
                        flatSpeedMps = 2.0,
                        advancedVerticalRateEnabled = true,
                        uphillVerticalMetersPerHour = 300.0,
                        downhillVerticalMetersPerHour = 900.0,
                    ),
            )

        checkNotNull(projection)
        assertEquals(360.0, projection.totalSeconds!!, 0.02)
    }

    @Test
    fun speedsUpModerateUphillWhenAdvancedRateIsHigherThanBaseline() {
        val profile =
            testProfile(
                segmentLengths = doubleArrayOf(100.0),
                elevations = listOf(100.0, 110.0),
            )

        val projection =
            buildEtaProjection(
                profile = profile,
                config =
                    GpxEtaModelConfig(
                        flatSpeedMps = 2.0,
                        advancedVerticalRateEnabled = true,
                        uphillVerticalMetersPerHour = 600.0,
                        downhillVerticalMetersPerHour = 900.0,
                    ),
            )

        checkNotNull(projection)
        assertEquals(60.0, projection.totalSeconds!!, 0.02)
    }

    @Test
    fun capsSteepDownhillWhenAdvancedRateIsEnabled() {
        val profile =
            testProfile(
                segmentLengths = doubleArrayOf(100.0),
                elevations = listOf(130.0, 110.0),
            )

        val projection =
            buildEtaProjection(
                profile = profile,
                config =
                    GpxEtaModelConfig(
                        flatSpeedMps = 2.0,
                        advancedVerticalRateEnabled = true,
                        uphillVerticalMetersPerHour = 600.0,
                        downhillVerticalMetersPerHour = 600.0,
                    ),
            )

        checkNotNull(projection)
        assertEquals(120.0, projection.totalSeconds!!, 0.02)
    }

    @Test
    fun speedsUpSteepDownhillWhenAdvancedRateIsHigherThanBaseline() {
        val profile =
            testProfile(
                segmentLengths = doubleArrayOf(100.0),
                elevations = listOf(130.0, 110.0),
            )

        val projection =
            buildEtaProjection(
                profile = profile,
                config =
                    GpxEtaModelConfig(
                        flatSpeedMps = 2.0,
                        advancedVerticalRateEnabled = true,
                        uphillVerticalMetersPerHour = 600.0,
                        downhillVerticalMetersPerHour = 1200.0,
                    ),
            )

        checkNotNull(projection)
        assertEquals(60.0, projection.totalSeconds!!, 0.02)
    }

    private fun testProfile(
        segmentLengths: DoubleArray,
        elevations: List<Double>,
    ): TrackProfile {
        require(elevations.size == segmentLengths.size + 1) {
            "Need exactly one more elevation than segment length."
        }

        val cumulativeDistances = DoubleArray(elevations.size)
        for (index in segmentLengths.indices) {
            cumulativeDistances[index + 1] = cumulativeDistances[index] + segmentLengths[index]
        }

        val points =
            elevations.mapIndexed { index, elevation ->
                TrackPoint(
                    latLong = LatLong(0.0, index.toDouble()),
                    elevation = elevation,
                )
            }

        return TrackProfile(
            sig = FileSig(lastModified = 0L, length = points.size.toLong()),
            elevationFilterConfig = GpxElevationFilterDefaults.defaultConfig(),
            points = points,
            segLen = segmentLengths,
            cumDist = cumulativeDistances,
            cumAscent = DoubleArray(elevations.size),
            cumDescent = DoubleArray(elevations.size),
        )
    }
}
