package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.core.maps.MAP_ZOOM_REPRESENTATIVE_LATITUDE_DEGREES
import com.glancemap.glancemapwearos.core.maps.MAP_ZOOM_REPRESENTATIVE_VIEWPORT_WIDTH_PX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigateScaleIndicatorTest {
    @Test
    fun `farthest bound shows configured two hundred kilometer scale with truthful bar width`() {
        val indicator =
            requireNotNull(
                calculateScaleIndicatorForZoom(
                    zoomLevel = 6,
                    viewportWidthPx = MAP_ZOOM_REPRESENTATIVE_VIEWPORT_WIDTH_PX,
                    latitudeDegrees = MAP_ZOOM_REPRESENTATIVE_LATITUDE_DEGREES,
                    isMetric = true,
                    preferredScaleMeters = 200_000,
                ),
            )

        assertEquals("200 km", indicator.label)
        assertTrue(indicator.widthRatio < 1f)
    }

    @Test
    fun `closest bound shows configured twenty meter scale with truthful bar width`() {
        val indicator =
            requireNotNull(
                calculateScaleIndicatorForZoom(
                    zoomLevel = 20,
                    viewportWidthPx = MAP_ZOOM_REPRESENTATIVE_VIEWPORT_WIDTH_PX,
                    latitudeDegrees = MAP_ZOOM_REPRESENTATIVE_LATITUDE_DEGREES,
                    isMetric = true,
                    preferredScaleMeters = 20,
                ),
            )

        assertEquals("20 m", indicator.label)
        assertTrue(indicator.widthRatio > 1f)
    }

    @Test
    fun `closest zoom uses two point five meters instead of repeating five meters`() {
        val indicator =
            requireNotNull(
                calculateScaleIndicatorForZoom(
                    zoomLevel = 22,
                    viewportWidthPx = MAP_ZOOM_REPRESENTATIVE_VIEWPORT_WIDTH_PX,
                    latitudeDegrees = MAP_ZOOM_REPRESENTATIVE_LATITUDE_DEGREES,
                    isMetric = true,
                    preferredScaleMeters = 5,
                ),
            )

        assertEquals("2.5 m", indicator.label)
        assertTrue(indicator.widthRatio < 1f)
    }

    @Test
    fun `closest zoom sequence does not repeat scale labels`() {
        val labels =
            (18..22).map { zoomLevel ->
                requireNotNull(
                    calculateScaleIndicatorForZoom(
                        zoomLevel = zoomLevel,
                        viewportWidthPx = MAP_ZOOM_REPRESENTATIVE_VIEWPORT_WIDTH_PX,
                        latitudeDegrees = MAP_ZOOM_REPRESENTATIVE_LATITUDE_DEGREES,
                        isMetric = true,
                        preferredScaleMeters = 5.takeIf { zoomLevel == 22 },
                    ),
                ).label
            }

        assertEquals(
            listOf("50 m", "25 m", "10 m", "5 m", "2.5 m"),
            labels,
        )
    }
}
