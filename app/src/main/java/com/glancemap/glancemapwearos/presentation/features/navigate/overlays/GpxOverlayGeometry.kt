package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.presentation.features.gpx.GpxTrackDetails
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.util.MercatorProjection
import kotlin.math.max
import kotlin.math.min

internal data class TrackLodLevels(
    val sourceSignature: Long,
    val low: List<LatLong>,
    val medium: List<LatLong>,
    val full: List<LatLong>,
) {
    fun pointsForZoom(zoom: Int): List<LatLong> =
        when {
            zoom >= 16 -> full
            zoom >= 14 -> medium
            else -> low
        }
}

private data class XY(
    val x: Double,
    val y: Double,
)

internal fun zoomBucketFor(zoom: Int): Int =
    when {
        zoom >= 16 -> 3
        zoom >= 14 -> 2
        else -> 1
    }

internal fun buildTrackLodLevels(points: List<LatLong>): TrackLodLevels {
    val signature = latLongListSignature(points)
    if (points.size <= 64) {
        return TrackLodLevels(
            sourceSignature = signature,
            low = points,
            medium = points,
            full = points,
        )
    }

    val low = simplifyTrackRdpMeters(points, toleranceMeters = 24.0)
    val medium = simplifyTrackRdpMeters(points, toleranceMeters = 8.0)
    return TrackLodLevels(
        sourceSignature = signature,
        low = low,
        medium = medium,
        full = points,
    )
}

private fun latLongListSignature(points: List<LatLong>): Long {
    var h = 1_469_598_103_934_665_603L
    val prime = 1_099_511_628_211L
    points.forEach { ll ->
        h = (h xor ll.latitude.toBits()) * prime
        h = (h xor ll.longitude.toBits()) * prime
    }
    return h
}

internal fun hasSameLatLongs(
    a: MutableList<LatLong>,
    b: List<LatLong>,
): Boolean {
    if (a.size != b.size) return false
    for (i in a.indices) {
        val p = a[i]
        val q = b[i]
        if (p.latitude != q.latitude || p.longitude != q.longitude) return false
    }
    return true
}

private fun simplifyTrackRdpMeters(
    points: List<LatLong>,
    toleranceMeters: Double,
): List<LatLong> {
    if (points.size <= 2) return points

    val xy = toLocalMeters(points)
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.lastIndex] = true

    val tol2 = toleranceMeters * toleranceMeters
    val stack = ArrayDeque<IntRange>()
    stack.addLast(0..points.lastIndex)

    while (stack.isNotEmpty()) {
        val segment = stack.removeLast()
        val start = segment.first
        val end = segment.last
        if (end <= start + 1) continue

        val a = xy[start]
        val b = xy[end]
        var bestIndex = -1
        var bestDist2 = -1.0

        for (i in start + 1 until end) {
            val d2 = perpendicularDistanceSq(xy[i], a, b)
            if (d2 > bestDist2) {
                bestDist2 = d2
                bestIndex = i
            }
        }

        if (bestIndex != -1 && bestDist2 > tol2) {
            keep[bestIndex] = true
            stack.addLast(start..bestIndex)
            stack.addLast(bestIndex..end)
        }
    }

    val out = ArrayList<LatLong>(points.size)
    for (i in points.indices) {
        if (keep[i]) out.add(points[i])
    }
    return if (out.size >= 2) out else listOf(points.first(), points.last())
}

private fun toLocalMeters(points: List<LatLong>): List<XY> {
    val r = 6_371_000.0
    val lat0 = Math.toRadians(points.first().latitude)
    val lon0 = Math.toRadians(points.first().longitude)
    val cosLat0 = kotlin.math.cos(lat0)

    return points.map { ll ->
        val lat = Math.toRadians(ll.latitude)
        val lon = Math.toRadians(ll.longitude)
        XY(
            x = (lon - lon0) * cosLat0 * r,
            y = (lat - lat0) * r,
        )
    }
}

private fun perpendicularDistanceSq(
    p: XY,
    a: XY,
    b: XY,
): Double {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val len2 = abx * abx + aby * aby

    if (len2 <= 1e-9) {
        val dx = p.x - a.x
        val dy = p.y - a.y
        return dx * dx + dy * dy
    }

    val apx = p.x - a.x
    val apy = p.y - a.y
    val t = ((apx * abx) + (apy * aby)) / len2
    val tt = t.coerceIn(0.0, 1.0)
    val sx = a.x + tt * abx
    val sy = a.y + tt * aby
    val dx = p.x - sx
    val dy = p.y - sy
    return dx * dx + dy * dy
}

internal fun snapToRenderedTrackOrNull(
    point: LatLong,
    tracks: List<GpxTrackDetails>,
): LatLong? {
    val zoom: Byte = 20
    val tileSize = 256
    val mapSize = MercatorProjection.getMapSize(zoom, tileSize)

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

    val (px, py) = toXY(point)

    var bestDist2 = Double.MAX_VALUE
    var bestX = 0.0
    var bestY = 0.0
    var found = false

    tracks.forEach { track ->
        val pts = track.points
        if (pts.size < 2) return@forEach

        for (i in 0 until pts.size - 1) {
            val (ax, ay) = toXY(pts[i])
            val (bx, by) = toXY(pts[i + 1])

            val abx = bx - ax
            val aby = by - ay
            val apx = px - ax
            val apy = py - ay

            val abLen2 = abx * abx + aby * aby
            val t = if (abLen2 > 0.0) (apx * abx + apy * aby) / abLen2 else 0.0
            val tt = min(1.0, max(0.0, t))

            val sx = ax + tt * abx
            val sy = ay + tt * aby

            val dx = px - sx
            val dy = py - sy
            val d2 = dx * dx + dy * dy
            if (d2 < bestDist2) {
                bestDist2 = d2
                bestX = sx
                bestY = sy
                found = true
            }
        }
    }

    return if (found) toLatLong(bestX, bestY) else null
}
