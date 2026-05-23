package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.view.MapView

internal fun MapView.setCenterForNavigationMarker(
    markerLatLong: LatLong,
    markerAnchorMode: String,
) {
    setCenter(resolveMapCenterForNavigationMarker(markerLatLong, markerAnchorMode))
}

internal fun MapView.resolveMapCenterForNavigationMarker(
    markerLatLong: LatLong,
    markerAnchorMode: String,
): LatLong {
    if (markerAnchorMode != SettingsRepository.NAVIGATION_MARKER_ANCHOR_LOWER) {
        return markerLatLong
    }
    val widthPx = width.toDouble()
    val heightPx = height.toDouble()
    if (widthPx <= 0.0 || heightPx <= 0.0) return markerLatLong

    val lowerMarkerRaisePx = LOWER_MARKER_RAISE_DP * resources.displayMetrics.density
    val desiredMarkerScreenY =
        (heightPx * LOWER_MARKER_SCREEN_FRACTION - lowerMarkerRaisePx).coerceIn(
            heightPx * LOWER_MARKER_MIN_SCREEN_FRACTION,
            heightPx * LOWER_MARKER_MAX_SCREEN_FRACTION,
        )
    val (desiredMarkerMapX, desiredMarkerMapY) =
        unrotateTouchToMapSpace(
            x = widthPx / 2.0,
            y = desiredMarkerScreenY,
            mapWidth = widthPx,
            mapHeight = heightPx,
            mapRotationDeg = mapRotation.degrees.toDouble(),
        )

    val zoomLevel = model.mapViewPosition.zoomLevel
    val tileSize = model.displayModel.tileSize
    val mapSize = MercatorProjection.getMapSize(zoomLevel, tileSize)
    val markerPixelX = MercatorProjection.longitudeToPixelX(markerLatLong.longitude, mapSize)
    val markerPixelY = MercatorProjection.latitudeToPixelY(markerLatLong.latitude, mapSize)
    val centerPixelX = markerPixelX - desiredMarkerMapX + widthPx / 2.0
    val centerPixelY = markerPixelY - desiredMarkerMapY + heightPx / 2.0

    return LatLong(
        MercatorProjection.pixelYToLatitude(centerPixelY, mapSize),
        MercatorProjection.pixelXToLongitude(centerPixelX, mapSize),
    )
}

private const val LOWER_MARKER_SCREEN_FRACTION = 0.82
private const val LOWER_MARKER_RAISE_DP = 8f
private const val LOWER_MARKER_MIN_SCREEN_FRACTION = 0.74
private const val LOWER_MARKER_MAX_SCREEN_FRACTION = 0.86
