package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import org.mapsforge.core.model.LatLong
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal fun shouldRenderMarkerMotion(
    previous: LatLong?,
    candidate: LatLong,
): Boolean =
    previous == null ||
        markerMotionDistanceMeters(previous, candidate) >= MARKER_MOTION_RENDER_THRESHOLD_M

private fun markerMotionDistanceMeters(
    from: LatLong,
    to: LatLong,
): Float {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val a =
        sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(lat1) * cos(lat2) * sin(dLon / 2.0) * sin(dLon / 2.0)
    return (EARTH_RADIUS_METERS * 2.0 * asin(sqrt(a))).toFloat()
}

private const val EARTH_RADIUS_METERS = 6_371_000.0
internal const val MARKER_MOTION_RENDER_THRESHOLD_M = 0.12f
