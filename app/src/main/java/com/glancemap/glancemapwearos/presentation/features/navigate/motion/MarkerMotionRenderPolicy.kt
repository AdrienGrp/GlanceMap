package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import org.mapsforge.core.model.LatLong
import org.mapsforge.core.util.MercatorProjection
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Inputs that decide whether visual motion needs a periodic render loop.
 *
 * GPS fixes still render when they arrive. A null [MarkerMotionRenderDecision.tickIntervalMs]
 * therefore means event-driven rendering, not that marker updates are disabled.
 */
internal data class MarkerMotionRenderInputs(
    val isInteractive: Boolean,
    val isFollowingPosition: Boolean,
    val isMoving: Boolean,
    val isFresh: Boolean,
    val hasPendingVisualCorrection: Boolean = false,
    val metersPerPixel: Double? = null,
)

internal data class MarkerMotionRenderDecision(
    val tickIntervalMs: Long?,
    val minimumDisplacementM: Float,
)

/**
 * Keeps active follow-position movement smooth while avoiding periodic map work when a GPS fix can
 * be rendered directly. Free-map movement remains intentionally less frequent because only the
 * marker moves; the considerably more visible map-under-marker motion receives the 10 Hz cadence.
 */
internal fun markerMotionRenderDecision(inputs: MarkerMotionRenderInputs): MarkerMotionRenderDecision {
    val hasVisualMotion = inputs.isMoving || inputs.hasPendingVisualCorrection
    val tickIntervalMs =
        when {
            !inputs.isInteractive || !inputs.isFresh || !hasVisualMotion -> null
            inputs.isFollowingPosition -> ACTIVE_FOLLOW_RENDER_INTERVAL_MS
            else -> ACTIVE_FREE_MAP_RENDER_INTERVAL_MS
        }
    return MarkerMotionRenderDecision(
        tickIntervalMs = tickIntervalMs,
        minimumDisplacementM = markerMotionRenderThresholdMeters(inputs.metersPerPixel),
    )
}

internal fun shouldRenderMarkerMotion(
    previous: LatLong?,
    candidate: LatLong,
): Boolean =
    previous == null ||
        markerMotionDistanceMeters(previous, candidate) >= MARKER_MOTION_RENDER_THRESHOLD_M

internal fun shouldRenderMarkerMotion(
    previous: LatLong?,
    candidate: LatLong,
    decision: MarkerMotionRenderDecision,
): Boolean =
    previous == null ||
        markerMotionDistanceMeters(previous, candidate) >= decision.minimumDisplacementM

/** Mapsforge/Web-Mercator ground resolution at [latitude] for an integer zoom level. */
internal fun markerMotionMetersPerPixel(
    latitude: Double,
    zoomLevel: Byte,
    tileSize: Int,
): Double? {
    if (!latitude.isFinite() || zoomLevel < 0 || tileSize <= 0) return null
    val mapSize = MercatorProjection.getMapSize(zoomLevel, tileSize)
    val metersPerPixel =
        MercatorProjection.calculateGroundResolution(
            latitude.coerceIn(MercatorProjection.LATITUDE_MIN, MercatorProjection.LATITUDE_MAX),
            mapSize,
        )
    return metersPerPixel.takeIf { it.isFinite() && it > 0.0 }
}

internal fun markerMotionRenderThresholdMeters(metersPerPixel: Double?): Float {
    if (metersPerPixel == null || !metersPerPixel.isFinite() || metersPerPixel <= 0.0) {
        return MARKER_MOTION_RENDER_THRESHOLD_M
    }
    return (metersPerPixel * TARGET_RENDER_DISPLACEMENT_PX)
        .coerceAtMost(Float.MAX_VALUE.toDouble())
        .toFloat()
}

internal fun markerMotionDistanceMeters(
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
internal const val ACTIVE_FOLLOW_RENDER_INTERVAL_MS = 100L
internal const val ACTIVE_FREE_MAP_RENDER_INTERVAL_MS = 250L
internal const val TARGET_RENDER_DISPLACEMENT_PX = 0.4
