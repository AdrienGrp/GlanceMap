package com.glancemap.glancemapwearos.presentation.features.maps

import com.glancemap.glancemapwearos.presentation.features.gpx.GpxTrackDetails
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mapsforge.core.model.LatLong

class OfflineStartCenterContextKeyTest {
    @Test
    fun `map only context uses map path`() {
        val key =
            buildOfflineStartCenterContextKey(
                selectedMapPath = "/maps/alps.map",
                activeGpxDetails = emptyList(),
            )

        assertEquals("map=/maps/alps.map", key)
    }

    @Test
    fun `map with active gpx includes gpx ids`() {
        val key =
            buildOfflineStartCenterContextKey(
                selectedMapPath = "/maps/alps.map",
                activeGpxDetails = listOf(track("beta.gpx"), track("alpha.gpx")),
            )

        assertEquals("map=/maps/alps.map;gpx=alpha.gpx|beta.gpx", key)
    }

    @Test
    fun `gpx only context remains stable`() {
        val key =
            buildOfflineStartCenterContextKey(
                selectedMapPath = null,
                activeGpxDetails = listOf(track("loop.gpx")),
            )

        assertEquals("gpx=loop.gpx", key)
    }

    private fun track(id: String): GpxTrackDetails =
        GpxTrackDetails(
            id = id,
            points = listOf(LatLong(45.0, 6.0)),
            title = id,
            distance = 0.0,
            elevationGain = 0.0,
            startPoint = LatLong(45.0, 6.0),
            endPoint = LatLong(45.0, 6.0),
        )
}
