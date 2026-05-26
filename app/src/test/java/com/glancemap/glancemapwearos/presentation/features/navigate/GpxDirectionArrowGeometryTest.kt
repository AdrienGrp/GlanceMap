package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.presentation.features.gpx.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import kotlin.math.roundToInt

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

    @Test
    fun buildsVisibleArrowsForViewportSubset() {
        val points =
            (0..400).map { index ->
                trackPoint(
                    lat = 45.0,
                    lon = 6.0 + index * 0.000025,
                )
            }

        val arrows =
            buildVisibleGpxDirectionArrows(
                points = points,
                zoom = 15,
                tileSize = 256,
                boundingBox =
                    BoundingBox(
                        44.9995,
                        6.003,
                        45.0005,
                        6.006,
                    ),
            )

        assertTrue(arrows.isNotEmpty())
        assertTrue(arrows.size <= MAX_VISIBLE_GPX_DIRECTION_ARROWS_PER_TRACK)
        assertEquals(90f, arrows.first().headingDeg, 0.5f)
    }

    @Test
    fun capsVisibleArrowsPerTrack() {
        val points =
            (0..2400).map { index ->
                trackPoint(
                    lat = 45.0,
                    lon = 6.0 + index * 0.000025,
                )
            }

        val arrows =
            buildVisibleGpxDirectionArrows(
                points = points,
                zoom = 16,
                tileSize = 256,
                boundingBox =
                    BoundingBox(
                        44.999,
                        6.0,
                        45.001,
                        6.06,
                    ),
            )

        assertEquals(MAX_VISIBLE_GPX_DIRECTION_ARROWS_PER_TRACK, arrows.size)
    }

    @Test
    fun distributesCappedVisibleArrowsAcrossViewport() {
        val points =
            (0..1200).map { index ->
                trackPoint(
                    lat = 45.0,
                    lon = 6.0 + index * 0.000025,
                )
            }

        val arrows =
            buildVisibleGpxDirectionArrows(
                points = points,
                zoom = 16,
                tileSize = 256,
                boundingBox =
                    BoundingBox(
                        44.999,
                        6.0,
                        45.001,
                        6.03,
                    ),
                maxArrows = 4,
            )

        assertEquals(4, arrows.size)
        assertTrue(arrows.first().latLong.longitude < 6.005)
        assertTrue(arrows.last().latLong.longitude > 6.025)
    }

    @Test
    fun buildsVisibleArrowForClippedLongSegment() {
        val arrows =
            buildVisibleGpxDirectionArrows(
                points =
                    listOf(
                        trackPoint(lat = 45.0, lon = 6.0),
                        trackPoint(lat = 45.0, lon = 7.0),
                    ),
                zoom = 16,
                tileSize = 256,
                boundingBox =
                    BoundingBox(
                        44.999,
                        6.49,
                        45.001,
                        6.51,
                    ),
                maxArrows = 3,
            )

        assertTrue(arrows.isNotEmpty())
        assertTrue(arrows.first().latLong.longitude in 6.49..6.51)
    }

    @Test
    fun keepsVisibleArrowPositionsAnchoredWhenViewportPans() {
        val points =
            (0..2400).map { index ->
                trackPoint(
                    lat = 45.0,
                    lon = 6.0 + index * 0.000025,
                )
            }

        val firstViewport =
            buildVisibleGpxDirectionArrows(
                points = points,
                zoom = 16,
                tileSize = 256,
                boundingBox =
                    BoundingBox(
                        44.999,
                        6.0,
                        45.001,
                        6.03,
                    ),
            )
        val slightlyPannedViewport =
            buildVisibleGpxDirectionArrows(
                points = points,
                zoom = 16,
                tileSize = 256,
                boundingBox =
                    BoundingBox(
                        44.999,
                        6.001,
                        45.001,
                        6.031,
                    ),
            )

        val firstKeys = firstViewport.map { it.latLong.longitude.roundKey() }.toSet()
        val pannedKeys = slightlyPannedViewport.map { it.latLong.longitude.roundKey() }.toSet()

        assertTrue(firstKeys.intersect(pannedKeys).isNotEmpty())
    }

    private fun trackPoint(
        lat: Double,
        lon: Double,
    ) = TrackPoint(
        latLong = LatLong(lat, lon),
        elevation = null,
    )

    private fun Double.roundKey(): Int = (this * 1_000_000).roundToInt()
}
