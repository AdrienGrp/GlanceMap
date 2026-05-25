package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.view.MapView
import kotlin.math.ceil

internal fun MapView.setCenterForNavigationMarker(
    markerLatLong: LatLong,
    markerAnchorMode: String,
) {
    setCenter(resolveMapCenterForNavigationMarker(markerLatLong, markerAnchorMode))
}

@Suppress("ReturnCount")
internal fun MapView.resolveMapCenterForNavigationMarker(
    markerLatLong: LatLong,
    markerAnchorMode: String,
): LatLong {
    if (markerAnchorMode == SettingsRepository.NAVIGATION_MARKER_ANCHOR_LOWER) {
        return markerLatLong
    }
    return markerLatLong
}

internal fun MapView.resolveNavigationMarkerScreenAnchor(markerAnchorMode: String): ScreenAnchor {
    val widthPx = width.toDouble()
    val heightPx = height.toDouble()
    val centerX = widthPx / 2.0
    val centerY = heightPx / 2.0
    if (
        widthPx <= 0.0 ||
        heightPx <= 0.0 ||
        markerAnchorMode != SettingsRepository.NAVIGATION_MARKER_ANCHOR_LOWER
    ) {
        return ScreenAnchor(centerX, centerY)
    }

    return ScreenAnchor(centerX, centerY)
}

internal fun navigationMarkerMapSurfaceHeightPx(
    visibleHeightPx: Int,
    density: Float,
    markerAnchorMode: String,
): Int {
    if (
        visibleHeightPx <= 0 ||
        markerAnchorMode != SettingsRepository.NAVIGATION_MARKER_ANCHOR_LOWER
    ) {
        return visibleHeightPx.coerceAtLeast(0)
    }
    val lowerY = lowerNavigationMarkerAnchorY(visibleHeightPx.toDouble(), density)
    return ceil(lowerY * 2.0).toInt().coerceAtLeast(visibleHeightPx)
}

internal fun lowerNavigationMarkerAnchorY(
    visibleHeightPx: Double,
    density: Float,
): Double {
    if (visibleHeightPx <= 0.0) return 0.0
    val lowerMarkerRaisePx = LOWER_MARKER_RAISE_DP * density
    return (visibleHeightPx * LOWER_MARKER_SCREEN_FRACTION - lowerMarkerRaisePx).coerceIn(
        visibleHeightPx * LOWER_MARKER_MIN_SCREEN_FRACTION,
        visibleHeightPx * LOWER_MARKER_MAX_SCREEN_FRACTION,
    )
}

private const val LOWER_MARKER_SCREEN_FRACTION = 0.82
private const val LOWER_MARKER_RAISE_DP = 8f
private const val LOWER_MARKER_MIN_SCREEN_FRACTION = 0.74
private const val LOWER_MARKER_MAX_SCREEN_FRACTION = 0.86
