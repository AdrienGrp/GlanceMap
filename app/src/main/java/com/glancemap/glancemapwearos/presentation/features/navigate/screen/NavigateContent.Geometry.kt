package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.presentation.features.gpx.ClosestTrackPick
import com.glancemap.glancemapwearos.presentation.features.gpx.GpxTrackDetails
import com.glancemap.glancemapwearos.presentation.features.gpx.TrackPosition
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.view.MapView
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val RESHAPE_TRACK_TAP_RADIUS_DP = 18f

internal fun findTappedTrackPoint(
    tap: LatLong,
    tapX: Double,
    tapY: Double,
    mapView: MapView,
    track: GpxTrackDetails,
): ClosestTrackPick? {
    val points = track.points
    if (points.size < 2) return null

    val pick = nearestTrackPickMercator(tap = tap, trackId = track.id, points = points)
    val snappedPoint =
        runCatching { mapView.mapViewProjection.toPixels(pick.snapped) }.getOrNull()
            ?: return null
    val touchRadiusPx = RESHAPE_TRACK_TAP_RADIUS_DP * mapView.resources.displayMetrics.density
    val dx = snappedPoint.x - tapX
    val dy = snappedPoint.y - tapY
    val screenDistancePx = sqrt(dx * dx + dy * dy)
    return pick.takeIf { screenDistancePx <= touchRadiusPx }
}

private fun nearestTrackPickMercator(
    tap: LatLong,
    trackId: String,
    points: List<LatLong>,
): ClosestTrackPick {
    val zoom: Byte = 20
    val tileSize = 256
    val mapSize: Long = MercatorProjection.getMapSize(zoom, tileSize)

    fun toXY(ll: LatLong): Pair<Double, Double> {
        val x = MercatorProjection.longitudeToPixelX(ll.longitude, mapSize)
        val y = MercatorProjection.latitudeToPixelY(ll.latitude, mapSize)
        return x to y
    }

    fun toLatLong(
        x: Double,
        y: Double,
    ): LatLong {
        val lon = MercatorProjection.pixelXToLongitude(x, mapSize)
        val lat = MercatorProjection.pixelYToLatitude(y, mapSize)
        return LatLong(lat, lon)
    }

    val (px, py) = toXY(tap)
    var bestIndex = 0
    var bestT = 0.0
    var bestDistanceSquared = Double.MAX_VALUE
    var bestProjectedX = px
    var bestProjectedY = py

    for (i in 0 until points.lastIndex) {
        val (ax, ay) = toXY(points[i])
        val (bx, by) = toXY(points[i + 1])
        val abx = bx - ax
        val aby = by - ay
        val apx = px - ax
        val apy = py - ay
        val abLenSquared = abx * abx + aby * aby
        val t =
            if (abLenSquared > 0.0) {
                (apx * abx + apy * aby) / abLenSquared
            } else {
                0.0
            }
        val clampedT = t.coerceIn(0.0, 1.0)
        val projectedX = ax + clampedT * abx
        val projectedY = ay + clampedT * aby
        val dx = px - projectedX
        val dy = py - projectedY
        val distanceSquared = dx * dx + dy * dy
        if (distanceSquared < bestDistanceSquared) {
            bestDistanceSquared = distanceSquared
            bestIndex = i
            bestT = clampedT
            bestProjectedX = projectedX
            bestProjectedY = projectedY
        }
    }

    val snapped = toLatLong(bestProjectedX, bestProjectedY)
    return ClosestTrackPick(
        pos =
            TrackPosition(
                trackId = trackId,
                segmentIndex = bestIndex,
                t = bestT,
            ),
        distanceToLineMeters = navigateHaversineMeters(snapped, tap),
        snapped = snapped,
    )
}

internal fun navigateHaversineMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
): Double {
    val earthRadiusM = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val sinLat = sin(dLat / 2.0)
    val sinLon = sin(dLon / 2.0)
    val a =
        sinLat * sinLat +
            cos(Math.toRadians(lat1)) *
            cos(Math.toRadians(lat2)) *
            sinLon * sinLon
    val c = 2.0 * kotlin.math.atan2(sqrt(a), kotlin.math.sqrt(1.0 - a))
    return earthRadiusM * c
}

internal fun navigateHaversineMeters(
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
