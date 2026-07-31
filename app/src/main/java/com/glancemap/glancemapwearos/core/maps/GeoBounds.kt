package com.glancemap.glancemapwearos.core.maps

data class GeoBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
)

fun geoBoundsOrNull(
    minLat: Double,
    maxLat: Double,
    minLon: Double,
    maxLon: Double,
): GeoBounds? {
    val source =
        GeoBounds(
            minLat = minLat,
            maxLat = maxLat,
            minLon = minLon,
            maxLon = maxLon,
        ).takeIf { it.hasFiniteCoordinates() }
            ?.takeIf { it.hasOrderedCoordinates() }
    val clamped =
        source?.let { bounds ->
            GeoBounds(
                minLat = bounds.minLat.coerceIn(-90.0, 90.0),
                maxLat = bounds.maxLat.coerceIn(-90.0, 90.0),
                minLon = bounds.minLon.coerceIn(-180.0, 180.0),
                maxLon = bounds.maxLon.coerceIn(-180.0, 180.0),
            )
        }
    return clamped?.takeIf { it.hasOrderedCoordinates() }
}

private fun GeoBounds.hasFiniteCoordinates(): Boolean = listOf(minLat, maxLat, minLon, maxLon).all(Double::isFinite)

private fun GeoBounds.hasOrderedCoordinates(): Boolean = minLat <= maxLat && minLon <= maxLon
