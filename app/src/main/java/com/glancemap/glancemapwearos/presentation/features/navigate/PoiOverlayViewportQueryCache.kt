package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.data.repository.PoiViewport
import org.mapsforge.core.model.LatLong
import kotlin.math.abs

/**
 * Avoids reopening the POI databases for map changes that cannot expose a meaningful new
 * portion of the viewport, such as compass rotation or a tiny follow-position update.
 *
 * Markers from the last successful query remain on the map while the cache is valid. Once the
 * map moves by [VIEWPORT_REFRESH_FRACTION] of the displayed area, or zoom changes, the next
 * observer event refreshes the query.
 */
internal class PoiOverlayViewportQueryCache {
    private var lastQuery: QuerySnapshot? = null

    fun shouldQuery(
        center: LatLong,
        zoomLevel: Int,
    ): Boolean {
        val previous = lastQuery
        return previous == null ||
            previous.zoomLevel != zoomLevel ||
            abs(center.latitude - previous.center.latitude) >= previous.refreshLatitudeDeltaDeg ||
            abs(shortestLongitudeDeltaDeg(center.longitude, previous.center.longitude)) >=
            previous.refreshLongitudeDeltaDeg
    }

    fun recordSuccessfulQuery(
        center: LatLong,
        zoomLevel: Int,
        viewport: PoiViewport,
    ) {
        val latitudeSpan = abs(viewport.maxLat - viewport.minLat)
        val longitudeSpan = abs(shortestLongitudeDeltaDeg(viewport.maxLon, viewport.minLon))
        lastQuery =
            QuerySnapshot(
                center = center,
                zoomLevel = zoomLevel,
                refreshLatitudeDeltaDeg =
                    (latitudeSpan * VIEWPORT_REFRESH_FRACTION).coerceAtLeast(MIN_REFRESH_DELTA_DEG),
                refreshLongitudeDeltaDeg =
                    (longitudeSpan * VIEWPORT_REFRESH_FRACTION).coerceAtLeast(MIN_REFRESH_DELTA_DEG),
            )
    }

    fun invalidate() {
        lastQuery = null
    }
}

private data class QuerySnapshot(
    val center: LatLong,
    val zoomLevel: Int,
    val refreshLatitudeDeltaDeg: Double,
    val refreshLongitudeDeltaDeg: Double,
)

private fun shortestLongitudeDeltaDeg(
    first: Double,
    second: Double,
): Double {
    var delta = (first - second) % 360.0
    if (delta > 180.0) delta -= 360.0
    if (delta < -180.0) delta += 360.0
    return delta
}

private const val VIEWPORT_REFRESH_FRACTION = 0.15
private const val MIN_REFRESH_DELTA_DEG = 0.000_000_1
