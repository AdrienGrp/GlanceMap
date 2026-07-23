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
    fun `closest zoom uses configured five meter scale`() {
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

        assertEquals("5 m", indicator.label)
        assertTrue(indicator.widthRatio > 1f)
    }

    @Test
    fun `five meter limit produces stable close zoom labels`() {
        val labels =
            (18..22).map { zoomLevel ->
                val preferredScaleMeters =
                    preferredScaleMetersForZoomLevel(
                        currentZoomLevel = zoomLevel,
                        zoomMin = 6,
                        zoomMax = 22,
                        zoomMinScaleMeters = 200_000,
                        zoomMaxScaleMeters = 5,
                    )
                requireNotNull(
                    calculateScaleIndicatorForZoom(
                        zoomLevel = zoomLevel,
                        viewportWidthPx = MAP_ZOOM_REPRESENTATIVE_VIEWPORT_WIDTH_PX,
                        latitudeDegrees = MAP_ZOOM_REPRESENTATIVE_LATITUDE_DEGREES,
                        isMetric = true,
                        preferredScaleMeters = preferredScaleMeters,
                    ),
                ).label
            }

        assertEquals(
            listOf("50 m", "25 m", "20 m", "10 m", "5 m"),
            labels,
        )
    }

    @Test
    fun `twenty meter limit remains the final close zoom label`() {
        val labels =
            (18..20).map { zoomLevel ->
                val preferredScaleMeters =
                    preferredScaleMetersForZoomLevel(
                        currentZoomLevel = zoomLevel,
                        zoomMin = 6,
                        zoomMax = 20,
                        zoomMinScaleMeters = 200_000,
                        zoomMaxScaleMeters = 20,
                    )
                requireNotNull(
                    calculateScaleIndicatorForZoom(
                        zoomLevel = zoomLevel,
                        viewportWidthPx = MAP_ZOOM_REPRESENTATIVE_VIEWPORT_WIDTH_PX,
                        latitudeDegrees = MAP_ZOOM_REPRESENTATIVE_LATITUDE_DEGREES,
                        isMetric = true,
                        preferredScaleMeters = preferredScaleMeters,
                    ),
                ).label
            }

        assertEquals(listOf("50 m", "25 m", "20 m"), labels)
    }

    @Test
    fun `ten meter limit remains the final close zoom label`() {
        val labels =
            (18..21).map { zoomLevel ->
                val preferredScaleMeters =
                    preferredScaleMetersForZoomLevel(
                        currentZoomLevel = zoomLevel,
                        zoomMin = 6,
                        zoomMax = 21,
                        zoomMinScaleMeters = 200_000,
                        zoomMaxScaleMeters = 10,
                    )
                requireNotNull(
                    calculateScaleIndicatorForZoom(
                        zoomLevel = zoomLevel,
                        viewportWidthPx = MAP_ZOOM_REPRESENTATIVE_VIEWPORT_WIDTH_PX,
                        latitudeDegrees = MAP_ZOOM_REPRESENTATIVE_LATITUDE_DEGREES,
                        isMetric = true,
                        preferredScaleMeters = preferredScaleMeters,
                    ),
                ).label
            }

        assertEquals(listOf("50 m", "25 m", "20 m", "10 m"), labels)
    }
}
