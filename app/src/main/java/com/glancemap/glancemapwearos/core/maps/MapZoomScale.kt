package com.glancemap.glancemapwearos.core.maps

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

const val MAP_ZOOM_MIN_LEVEL = 1
const val MAP_ZOOM_MAX_LEVEL = 20
const val MAP_SCALE_TARGET_RATIO = 0.28
const val MAP_ZOOM_METERS_PER_PIXEL_EQUATOR_ZOOM_0 = 156543.03392804097
const val MAP_ZOOM_REPRESENTATIVE_LATITUDE_DEGREES = 45.0
const val MAP_ZOOM_REPRESENTATIVE_VIEWPORT_WIDTH_PX = 466.0

val mapZoomScaleStepsMeters: List<Int> =
    listOf(
        5,
        10,
        20,
        25,
        50,
        100,
        200,
        250,
        500,
        1000,
        2000,
        2500,
        5000,
        10000,
        20000,
        25000,
        50000,
        100000,
        200000,
        250000,
        500000,
        1000000,
        2000000,
        2500000,
        5000000,
    )

data class MapZoomLevels(
    val default: Int,
    val min: Int,
    val max: Int,
)

fun scaleMetersForZoomLevel(
    zoom: Int,
    viewportWidthPx: Double,
    latitudeDegrees: Double,
): Double {
    val safeZoom = zoom.coerceIn(MAP_ZOOM_MIN_LEVEL, MAP_ZOOM_MAX_LEVEL)
    val safeViewportWidthPx = safeViewportWidthPx(viewportWidthPx)
    val latitudeScale = latitudeScale(latitudeDegrees)
    val metersPerPixelAtEquator =
        MAP_ZOOM_METERS_PER_PIXEL_EQUATOR_ZOOM_0 / 2.0.pow(safeZoom.toDouble())
    return (metersPerPixelAtEquator * latitudeScale * safeViewportWidthPx * MAP_SCALE_TARGET_RATIO)
        .coerceAtLeast(mapZoomScaleStepsMeters.first().toDouble())
}

fun zoomLevelForScaleNearest(
    scaleMeters: Double,
    viewportWidthPx: Double,
    latitudeDegrees: Double,
): Int {
    val rawZoom = rawZoomForScaleMeters(scaleMeters, viewportWidthPx, latitudeDegrees)
    val roundedZoom =
        if (rawZoom.isFinite()) {
            rawZoom.roundToInt()
        } else {
            MAP_ZOOM_MAX_LEVEL
        }
    return roundedZoom.coerceIn(MAP_ZOOM_MIN_LEVEL, MAP_ZOOM_MAX_LEVEL)
}

fun zoomLevelForScaleAtLeast(
    scaleMeters: Double,
    viewportWidthPx: Double,
    latitudeDegrees: Double,
): Int {
    val rawZoom = rawZoomForScaleMeters(scaleMeters, viewportWidthPx, latitudeDegrees)
    val flooredZoom =
        if (rawZoom.isFinite()) {
            floor(rawZoom).toInt()
        } else {
            MAP_ZOOM_MIN_LEVEL
        }
    return flooredZoom.coerceIn(MAP_ZOOM_MIN_LEVEL, MAP_ZOOM_MAX_LEVEL)
}

fun zoomLevelForScaleAtMost(
    scaleMeters: Double,
    viewportWidthPx: Double,
    latitudeDegrees: Double,
): Int {
    val rawZoom = rawZoomForScaleMeters(scaleMeters, viewportWidthPx, latitudeDegrees)
    val ceiledZoom =
        if (rawZoom.isFinite()) {
            ceil(rawZoom).toInt()
        } else {
            MAP_ZOOM_MAX_LEVEL
        }
    return ceiledZoom.coerceIn(MAP_ZOOM_MIN_LEVEL, MAP_ZOOM_MAX_LEVEL)
}

fun mapZoomLevelsForScaleSettings(
    defaultScaleMeters: Int,
    minScaleMeters: Int,
    maxScaleMeters: Int,
    viewportWidthPx: Double,
    latitudeDegrees: Double,
): MapZoomLevels {
    val minZoom =
        zoomLevelForScaleAtLeast(
            scaleMeters = sanitizeMapZoomScaleMeters(minScaleMeters).toDouble(),
            viewportWidthPx = viewportWidthPx,
            latitudeDegrees = latitudeDegrees,
        )
    val maxZoom =
        zoomLevelForScaleAtMost(
            scaleMeters = sanitizeMapZoomScaleMeters(maxScaleMeters).toDouble(),
            viewportWidthPx = viewportWidthPx,
            latitudeDegrees = latitudeDegrees,
        )
    val effectiveMinZoom = minOf(minZoom, maxZoom)
    val effectiveMaxZoom = maxOf(minZoom, maxZoom)
    val defaultZoom =
        zoomLevelForScaleNearest(
            scaleMeters = sanitizeMapZoomScaleMeters(defaultScaleMeters).toDouble(),
            viewportWidthPx = viewportWidthPx,
            latitudeDegrees = latitudeDegrees,
        ).coerceIn(effectiveMinZoom, effectiveMaxZoom)
    return MapZoomLevels(
        default = defaultZoom,
        min = effectiveMinZoom,
        max = effectiveMaxZoom,
    )
}

fun sanitizeMapZoomScaleMeters(scaleMeters: Int): Int =
    scaleMeters.coerceIn(
        mapZoomScaleStepsMeters.first(),
        mapZoomScaleStepsMeters.last(),
    )

fun nearestMetricScaleStepIndex(scaleMeters: Int): Int {
    val sanitized = sanitizeMapZoomScaleMeters(scaleMeters)
    var bestIndex = 0
    var bestDistance = Int.MAX_VALUE
    mapZoomScaleStepsMeters.forEachIndexed { index, step ->
        val distance = kotlin.math.abs(step - sanitized)
        if (distance < bestDistance) {
            bestIndex = index
            bestDistance = distance
        }
    }
    return bestIndex
}

private fun rawZoomForScaleMeters(
    scaleMeters: Double,
    viewportWidthPx: Double,
    latitudeDegrees: Double,
): Double {
    val safeScaleMeters =
        scaleMeters.coerceIn(
            mapZoomScaleStepsMeters.first().toDouble(),
            mapZoomScaleStepsMeters.last().toDouble(),
        )
    val desiredVisibleMeters = safeScaleMeters / MAP_SCALE_TARGET_RATIO
    val desiredMetersPerPixel = desiredVisibleMeters / safeViewportWidthPx(viewportWidthPx)
    val rawZoom =
        log2(
            (MAP_ZOOM_METERS_PER_PIXEL_EQUATOR_ZOOM_0 * latitudeScale(latitudeDegrees)) /
                desiredMetersPerPixel,
        )
    return if (rawZoom.isFinite()) rawZoom else MAP_ZOOM_MAX_LEVEL.toDouble()
}

private fun safeViewportWidthPx(viewportWidthPx: Double): Double =
    viewportWidthPx
        .takeIf { it.isFinite() && it > 0.0 }
        ?: MAP_ZOOM_REPRESENTATIVE_VIEWPORT_WIDTH_PX

private fun latitudeScale(latitudeDegrees: Double): Double {
    val safeLatitude = latitudeDegrees.coerceIn(-85.0, 85.0)
    return cos(Math.toRadians(safeLatitude))
        .takeIf { it.isFinite() && it > 0.0 }
        ?: cos(Math.toRadians(MAP_ZOOM_REPRESENTATIVE_LATITUDE_DEGREES))
}
