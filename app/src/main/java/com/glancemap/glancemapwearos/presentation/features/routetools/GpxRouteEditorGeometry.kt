@file:Suppress(
    "ReturnCount",
    "TooManyFunctions",
)

package com.glancemap.glancemapwearos.presentation.features.routetools

import com.glancemap.glancemapwearos.core.routing.RouteGeometryPoint
import com.glancemap.glancemapwearos.presentation.features.gpx.TrackPoint
import com.glancemap.glancemapwearos.presentation.features.gpx.TrackPosition
import com.glancemap.glancemapwearos.presentation.features.gpx.TrackProfile
import com.glancemap.glancemapwearos.presentation.features.gpx.totalDistance
import org.mapsforge.core.model.LatLong
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal const val POSITION_EPSILON = 1e-9

internal fun slicePointsBetween(
    points: List<TrackPoint>,
    a: TrackPosition,
    b: TrackPosition,
): List<TrackPoint> {
    val (start, end) = orderedPositions(a, b)
    val output = ArrayList<TrackPoint>()
    appendUnique(output, pointAt(points, start))
    for (index in start.segmentIndex + 1..end.segmentIndex) {
        appendUnique(output, points[index])
    }
    appendUnique(output, pointAt(points, end))
    return output
}

internal fun trimTrackStart(
    points: List<TrackPoint>,
    start: TrackPosition,
): List<TrackPoint> {
    val output = ArrayList<TrackPoint>()
    appendUnique(output, pointAt(points, start))
    for (index in start.segmentIndex + 1 until points.size) {
        appendUnique(output, points[index])
    }
    return output
}

internal fun trimTrackEnd(
    points: List<TrackPoint>,
    end: TrackPosition,
): List<TrackPoint> {
    val output = ArrayList<TrackPoint>()
    for (index in 0..end.segmentIndex) {
        appendUnique(output, points[index])
    }
    appendUnique(output, pointAt(points, end))
    return output
}

internal fun pointAt(
    points: List<TrackPoint>,
    position: TrackPosition,
): TrackPoint {
    val index = position.segmentIndex.coerceIn(0, points.lastIndex - 1)
    val t = position.t.coerceIn(0.0, 1.0)
    if (t <= POSITION_EPSILON) return points[index]
    if (t >= 1.0 - POSITION_EPSILON) return points[index + 1]

    val start = points[index]
    val end = points[index + 1]
    val elevation =
        when {
            start.elevation != null && end.elevation != null -> {
                start.elevation + t * (end.elevation - start.elevation)
            }

            start.elevation != null -> start.elevation
            else -> end.elevation
        }
    return TrackPoint(
        latLong =
            LatLong(
                lerp(start.latLong.latitude, end.latLong.latitude, t),
                lerp(start.latLong.longitude, end.latLong.longitude, t),
            ),
        elevation = elevation,
        hasTimestamp = start.hasTimestamp && end.hasTimestamp,
    )
}

internal fun comparePositions(
    a: TrackPosition,
    b: TrackPosition,
): Int {
    val segmentCompare = a.segmentIndex.compareTo(b.segmentIndex)
    return if (segmentCompare != 0) segmentCompare else a.t.compareTo(b.t)
}

internal fun trackDistanceAt(
    profile: TrackProfile,
    position: TrackPosition,
): Double {
    if (profile.points.size <= 1) return 0.0
    val segmentIndex = position.segmentIndex.coerceIn(0, profile.segLen.lastIndex)
    val segmentStartDistance = profile.cumDist.getOrElse(segmentIndex) { 0.0 }
    val segmentLength = profile.segLen.getOrElse(segmentIndex) { 0.0 }
    return segmentStartDistance + segmentLength * position.t.coerceIn(0.0, 1.0)
}

internal fun routeGeometryDistanceMeters(points: List<RouteGeometryPoint>): Double {
    if (points.size < 2) return 0.0
    var totalDistance = 0.0
    var previous = points.first().latLong
    for (index in 1 until points.size) {
        val current = points[index].latLong
        totalDistance += haversineMeters(previous, current)
        previous = current
    }
    return totalDistance
}

internal fun positionAtDistance(
    profile: TrackProfile,
    distanceMeters: Double,
): TrackPosition {
    if (profile.points.size <= 1) {
        return TrackPosition(trackId = "", segmentIndex = 0, t = 0.0)
    }
    val target = distanceMeters.coerceIn(0.0, profile.totalDistance)
    if (target <= 0.0) {
        return TrackPosition(trackId = "", segmentIndex = 0, t = 0.0)
    }
    if (target >= profile.totalDistance) {
        return TrackPosition(
            trackId = "",
            segmentIndex = profile.points.lastIndex - 1,
            t = 1.0,
        )
    }

    val upperPointIndex =
        profile.cumDist
            .indexOfFirst { it >= target }
            .let { if (it < 0) profile.points.lastIndex else it }
    val segmentIndex = (upperPointIndex - 1).coerceIn(0, profile.segLen.lastIndex)
    val segmentStartDistance = profile.cumDist.getOrElse(segmentIndex) { 0.0 }
    val segmentLength = profile.segLen.getOrElse(segmentIndex) { 0.0 }
    val t =
        if (segmentLength <= POSITION_EPSILON) {
            0.0
        } else {
            ((target - segmentStartDistance) / segmentLength).coerceIn(0.0, 1.0)
        }
    return TrackPosition(trackId = "", segmentIndex = segmentIndex, t = t)
}

internal fun orderedPositions(
    a: TrackPosition,
    b: TrackPosition,
): Pair<TrackPosition, TrackPosition> = if (comparePositions(a, b) <= 0) a to b else b to a

internal fun appendUnique(
    target: MutableList<TrackPoint>,
    point: TrackPoint,
) {
    val last = target.lastOrNull()
    if (last != null && sameLocation(last.latLong, point.latLong)) {
        return
    }
    target += point
}

internal fun appendUniqueLatLong(
    target: MutableList<LatLong>,
    point: LatLong,
) {
    val last = target.lastOrNull()
    if (last != null && sameLocation(last, point)) {
        return
    }
    target += point
}

private fun sameLocation(
    a: LatLong,
    b: LatLong,
): Boolean =
    abs(a.latitude - b.latitude) < 1e-9 &&
        abs(a.longitude - b.longitude) < 1e-9

internal fun haversineMeters(
    a: LatLong,
    b: LatLong,
): Double {
    val r = 6_371_000.0
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val s = sin(dLat / 2.0)
    val t = sin(dLon / 2.0)
    val h = s * s + cos(lat1) * cos(lat2) * t * t
    return 2 * r * asin(min(1.0, sqrt(h)))
}

private fun lerp(
    start: Double,
    end: Double,
    t: Double,
): Double = start + t * (end - start)

internal fun RouteGeometryPoint.toTrackPoint(): TrackPoint =
    TrackPoint(
        latLong = latLong,
        elevation = elevation,
    )
