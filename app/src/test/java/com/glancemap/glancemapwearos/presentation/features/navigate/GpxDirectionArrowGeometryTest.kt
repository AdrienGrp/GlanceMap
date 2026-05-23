package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.presentation.features.gpx.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.LatLong

class GpxDirectionArrowGeometryTest {
    @Test
    fun buildsEastboundArrowsWithEastHeading() {
        val arrows =
            buildGpxDirectionArrows(
                points =
                    listOf(
                        trackPoint(lat = 45.0, lon = 6.0),
                        trackPoint(lat = 45.0, lon = 6.05),
                    ),
                zoom = 16,
                tileSize = 256,
            )

        assertTrue(arrows.isNotEmpty())
        assertEquals(90f, arrows.first().headingDeg, 0.5f)
    }

    @Test
    fun buildsNorthboundArrowsWithNorthHeading() {
        val arrows =
            buildGpxDirectionArrows(
                points =
                    listOf(
                        trackPoint(lat = 45.0, lon = 6.0),
                        trackPoint(lat = 45.05, lon = 6.0),
                    ),
                zoom = 16,
                tileSize = 256,
            )

        assertTrue(arrows.isNotEmpty())
        assertEquals(0f, arrows.first().headingDeg, 0.5f)
    }

    @Test
    fun capsVeryLongTracks() {
        val arrows =
            buildGpxDirectionArrows(
                points =
                    listOf(
                        trackPoint(lat = 45.0, lon = 6.0),
                        trackPoint(lat = 45.0, lon = 9.0),
                    ),
                zoom = 16,
                tileSize = 256,
            )

        assertEquals(36, arrows.size)
    }

    @Test
    fun buildsArrowsAcrossDenseShortSegments() {
        val points =
            (0..400).map { index ->
                trackPoint(
                    lat = 45.0,
                    lon = 6.0 + index * 0.000125,
                )
            }

        val arrows =
            buildGpxDirectionArrows(
                points = points,
                zoom = 16,
                tileSize = 256,
            )

        assertTrue(arrows.size >= 10)
        assertEquals(90f, arrows.first().headingDeg, 0.5f)
    }

    private fun trackPoint(
        lat: Double,
        lon: Double,
    ) = TrackPoint(
        latLong = LatLong(lat, lon),
        elevation = null,
    )
}
