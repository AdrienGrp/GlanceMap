package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.data.repository.PoiViewport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.LatLong

class PoiOverlayViewportQueryCacheTest {
    @Test
    fun rotationAndSmallFollowMovesReuseTheLastPoiQuery() {
        val cache = PoiOverlayViewportQueryCache()
        val center = LatLong(45.0, 6.0)
        val viewport =
            PoiViewport(
                minLat = 44.99,
                maxLat = 45.01,
                minLon = 5.99,
                maxLon = 6.01,
            )

        assertTrue(cache.shouldQuery(center, zoomLevel = 15))
        cache.recordSuccessfulQuery(center, zoomLevel = 15, viewport = viewport)

        // Rotation does not change the map center or zoom, so marker discovery need not query SQLite.
        assertFalse(cache.shouldQuery(center, zoomLevel = 15))
        assertFalse(cache.shouldQuery(LatLong(45.0, 6.002), zoomLevel = 15))
    }

    @Test
    fun movementPastViewportThresholdOrZoomChangeRefreshesPoiQuery() {
        val cache = PoiOverlayViewportQueryCache()
        val center = LatLong(45.0, 6.0)
        cache.recordSuccessfulQuery(
            center = center,
            zoomLevel = 15,
            viewport =
                PoiViewport(
                    minLat = 44.99,
                    maxLat = 45.01,
                    minLon = 5.99,
                    maxLon = 6.01,
                ),
        )

        // 15% of the displayed longitude span is 0.003 degrees.
        assertFalse(cache.shouldQuery(LatLong(45.0, 6.0029), zoomLevel = 15))
        assertTrue(cache.shouldQuery(LatLong(45.0, 6.003), zoomLevel = 15))
        assertTrue(cache.shouldQuery(center, zoomLevel = 16))
    }

    @Test
    fun invalidationForSourceOrStyleChangesForcesRefresh() {
        val cache = PoiOverlayViewportQueryCache()
        val center = LatLong(45.0, 6.0)
        cache.recordSuccessfulQuery(
            center = center,
            zoomLevel = 15,
            viewport = PoiViewport(44.99, 45.01, 5.99, 6.01),
        )

        cache.invalidate()

        assertTrue(cache.shouldQuery(center, zoomLevel = 15))
    }
}
