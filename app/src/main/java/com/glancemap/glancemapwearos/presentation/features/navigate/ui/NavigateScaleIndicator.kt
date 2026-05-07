package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.core.maps.scaleMetersForZoomLevel
import org.mapsforge.map.android.view.MapView
import java.util.Locale
import kotlin.math.roundToInt

internal data class ScaleIndicatorUi(
    val label: String,
)

private val metricScaleStepsMeters =
    doubleArrayOf(
        5.0,
        10.0,
        20.0,
        25.0,
        50.0,
        100.0,
        200.0,
        250.0,
        500.0,
        1000.0,
        2000.0,
        2500.0,
        5000.0,
        10000.0,
        20000.0,
        25000.0,
        50000.0,
        100000.0,
        200000.0,
        250000.0,
        500000.0,
        1000000.0,
        2000000.0,
        2500000.0,
        5000000.0,
    )

private val imperialScaleStepsFeet =
    doubleArrayOf(
        20.0,
        50.0,
        100.0,
        150.0,
        200.0,
        250.0,
        300.0,
        500.0,
        800.0,
        1000.0,
        2000.0,
        3000.0,
        5000.0,
        8000.0,
    )

private val imperialScaleStepsMiles =
    doubleArrayOf(
        0.1,
        0.2,
        0.5,
        1.0,
        2.0,
        5.0,
        10.0,
        20.0,
        25.0,
        30.0,
        40.0,
        50.0,
        80.0,
        100.0,
        200.0,
        250.0,
        500.0,
        1000.0,
        2000.0,
        2500.0,
        5000.0,
    )

internal fun calculateScaleIndicator(
    mapView: MapView,
    isMetric: Boolean,
): ScaleIndicatorUi? {
    val widthPx = mapView.width
    if (widthPx <= 0) return null

    val zoomLevel =
        mapView.model.mapViewPosition.zoomLevel
            .toInt()
    if (zoomLevel < 0) return null
    val centerLat =
        mapView.model.mapViewPosition.center.latitude
            .coerceIn(-85.0, 85.0)
    val targetMeters =
        scaleMetersForZoomLevel(
            zoom = zoomLevel,
            viewportWidthPx = widthPx.toDouble(),
            latitudeDegrees = centerLat,
        )
    val scaleMeters = chooseScaleDistanceMeters(targetMeters = targetMeters, isMetric = isMetric)
    if (!scaleMeters.isFinite() || scaleMeters <= 0.0) return null

    return ScaleIndicatorUi(
        label = formatScaleDistance(meters = scaleMeters, isMetric = isMetric),
    )
}

private fun chooseScaleDistanceMeters(
    targetMeters: Double,
    isMetric: Boolean,
): Double {
    if (!targetMeters.isFinite() || targetMeters <= 0.0) return 0.0

    if (isMetric) {
        return pickLargestNotExceeding(metricScaleStepsMeters, targetMeters)
    }

    val targetFeet = targetMeters * 3.28084
    return if (targetFeet < 2640.0) {
        val feet = pickLargestNotExceeding(imperialScaleStepsFeet, targetFeet)
        feet / 3.28084
    } else {
        val targetMiles = targetMeters * 0.000621371
        val miles = pickLargestNotExceeding(imperialScaleStepsMiles, targetMiles)
        miles / 0.000621371
    }
}

private fun pickLargestNotExceeding(
    steps: DoubleArray,
    target: Double,
): Double {
    var candidate = steps.firstOrNull() ?: target
    for (step in steps) {
        if (step <= target) candidate = step else break
    }
    return candidate
}

private fun formatScaleDistance(
    meters: Double,
    isMetric: Boolean,
): String {
    if (isMetric) {
        return if (meters >= 1000.0) {
            val km = meters / 1000.0
            if (km >= 10.0) {
                "${km.roundToInt()} km"
            } else {
                String.format(Locale.getDefault(), "%.1f km", km)
            }
        } else {
            "${meters.roundToInt()} m"
        }
    }

    val feet = meters * 3.28084
    return if (feet < 2640.0) {
        "${feet.roundToInt()} ft"
    } else {
        val miles = meters * 0.000621371
        if (miles >= 10.0) {
            "${miles.roundToInt()} mi"
        } else {
            String.format(Locale.getDefault(), "%.1f mi", miles)
        }
    }
}
