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
    if (!minLat.isFinite() || !maxLat.isFinite() || !minLon.isFinite() || !maxLon.isFinite()) {
        return null
    }
    if (minLat > maxLat || minLon > maxLon) return null

    val clampedMinLat = minLat.coerceIn(-90.0, 90.0)
    val clampedMaxLat = maxLat.coerceIn(-90.0, 90.0)
    val clampedMinLon = minLon.coerceIn(-180.0, 180.0)
    val clampedMaxLon = maxLon.coerceIn(-180.0, 180.0)
    if (clampedMinLat > clampedMaxLat || clampedMinLon > clampedMaxLon) return null

    return GeoBounds(
        minLat = clampedMinLat,
        maxLat = clampedMaxLat,
        minLon = clampedMinLon,
        maxLon = clampedMaxLon,
    )
}
