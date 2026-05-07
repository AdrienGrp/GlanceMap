package com.glancemap.glancemapwearos.core.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapZoomScaleTest {
    @Test
    fun `farthest out zoom adapts to latitude for the same displayed scale`() {
        val lowLatitudeZoom =
            zoomLevelForScaleAtLeast(
                scaleMeters = 50_000.0,
                viewportWidthPx = MAP_ZOOM_REPRESENTATIVE_VIEWPORT_WIDTH_PX,
                latitudeDegrees = 42.5,
            )
        val highLatitudeZoom =
            zoomLevelForScaleAtLeast(
                scaleMeters = 50_000.0,
                viewportWidthPx = MAP_ZOOM_REPRESENTATIVE_VIEWPORT_WIDTH_PX,
                latitudeDegrees = 54.5,
            )

        assertTrue(highLatitudeZoom < lowLatitudeZoom)
        assertTrue(
            scaleMetersForZoomLevel(
                zoom = highLatitudeZoom,
                viewportWidthPx = MAP_ZOOM_REPRESENTATIVE_VIEWPORT_WIDTH_PX,
                latitudeDegrees = 54.5,
            ) >= 50_000.0,
        )
    }

    @Test
    fun `resolved zoom levels keep default between farthest and closest bounds`() {
        val levels =
            mapZoomLevelsForScaleSettings(
                defaultScaleMeters = 200,
                minScaleMeters = 50_000,
                maxScaleMeters = 20,
                viewportWidthPx = MAP_ZOOM_REPRESENTATIVE_VIEWPORT_WIDTH_PX,
                latitudeDegrees = 54.5,
            )

        assertEquals(7, levels.min)
        assertEquals(20, levels.max)
        assertTrue(levels.default in levels.min..levels.max)
    }
}
