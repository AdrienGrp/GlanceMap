package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.presentation.features.gpx.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mapsforge.core.model.LatLong

class GpxElevationTrackColorTest {
    @Test
    fun classifiesSteepUphillAsClimb() {
        val from = trackPoint(lat = 45.0, elevation = 100.0)
        val to = trackPoint(lat = 45.0009, elevation = 110.0)

        assertEquals(GpxElevationSegmentType.CLIMB, classifyElevationSegment(from, to))
    }

    @Test
    fun classifiesModerateDownhillAsDownhill() {
        val from = trackPoint(lat = 45.0, elevation = 100.0)
        val to = trackPoint(lat = 45.0009, elevation = 96.0)

        assertEquals(GpxElevationSegmentType.DOWNHILL, classifyElevationSegment(from, to))
    }

    @Test
    fun classifiesMissingElevationAsFlat() {
        val from = trackPoint(lat = 45.0, elevation = null)
        val to = trackPoint(lat = 45.0009, elevation = 110.0)

        assertEquals(GpxElevationSegmentType.FLAT, classifyElevationSegment(from, to))
    }

    private fun trackPoint(
        lat: Double,
        elevation: Double?,
    ) = TrackPoint(
        latLong = LatLong(lat, 6.0),
        elevation = elevation,
    )
}
