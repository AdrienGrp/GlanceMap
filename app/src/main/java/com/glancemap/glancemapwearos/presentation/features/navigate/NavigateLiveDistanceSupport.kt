package com.glancemap.glancemapwearos.presentation.features.navigate

import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.view.MapView
import java.util.Locale
import kotlin.math.roundToInt

internal fun resolveLiveDistanceOrigin(
    currentMarkerLatLong: LatLong?,
    fallbackLatLong: LatLong?,
): LatLong? = currentMarkerLatLong ?: fallbackLatLong

internal fun resolveVisibleScreenCenterLatLong(mapView: MapView): LatLong? {
    if (mapView.width <= 0 || mapView.height <= 0) return null

    return runCatching {
        mapView.mapViewProjection.fromPixels(
            mapView.width / 2.0,
            mapView.height / 2.0,
        )
    }.getOrNull()
}

internal fun formatLiveDistance(
    meters: Double,
    isMetric: Boolean,
): Pair<String, String> {
    if (!meters.isFinite() || meters < 0.0) return "--" to ""

    if (isMetric) {
        return if (meters < 1000.0) {
            meters.roundToInt().toString() to "m"
        } else {
            val km = meters / 1000.0
            val value =
                if (km >= 10.0) {
                    km.roundToInt().toString()
                } else {
                    String.format(Locale.getDefault(), "%.1f", km)
                }
            value to "km"
        }
    }

    val feet = meters * 3.28084
    return if (feet < 2640.0) {
        feet.roundToInt().toString() to "ft"
    } else {
        val miles = meters * 0.000621371
        val value =
            if (miles >= 10.0) {
                miles.roundToInt().toString()
            } else {
                String.format(Locale.getDefault(), "%.1f", miles)
            }
        value to "mi"
    }
}

internal fun formatLiveDistanceLabel(
    meters: Double,
    isMetric: Boolean,
): String {
    val (value, unit) = formatLiveDistance(meters = meters, isMetric = isMetric)
    return if (unit.isBlank()) value else "$value $unit"
}
