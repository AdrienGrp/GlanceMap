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
): Pair<String, String> =
    when {
        !meters.isFinite() || meters < 0.0 -> "--" to ""
        isMetric -> formatMetricLiveDistance(meters)
        else -> formatImperialLiveDistance(meters)
    }

internal fun formatLiveDistanceLabel(
    meters: Double,
    isMetric: Boolean,
): String {
    val (value, unit) = formatLiveDistance(meters = meters, isMetric = isMetric)
    return if (unit.isBlank()) value else "$value $unit"
}

private fun formatMetricLiveDistance(
    meters: Double,
): Pair<String, String> =
    if (meters < 1000.0) {
        meters.roundToInt().toString() to "m"
    } else {
        formatLargeDistanceValue(value = meters / 1000.0, unit = "km")
    }

private fun formatImperialLiveDistance(
    meters: Double,
): Pair<String, String> {
    val feet = meters * 3.28084
    return if (feet < 2640.0) {
        feet.roundToInt().toString() to "ft"
    } else {
        formatLargeDistanceValue(value = meters * 0.000621371, unit = "mi")
    }
}

private fun formatLargeDistanceValue(
    value: Double,
    unit: String,
): Pair<String, String> {
    val displayValue =
        if (value >= 10.0) {
            value.roundToInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }
    return displayValue to unit
}
