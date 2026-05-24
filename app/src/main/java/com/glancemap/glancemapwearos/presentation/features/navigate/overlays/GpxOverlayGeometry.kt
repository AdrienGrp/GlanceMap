@file:Suppress("ReturnCount", "TooManyFunctions")

package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.presentation.features.gpx.GpxTrackDetails
import com.glancemap.glancemapwearos.presentation.features.gpx.TrackPoint
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.util.MercatorProjection
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val GPX_DIRECTION_ARROW_SPACING_PX = 190.0
private const val GPX_VISIBLE_DIRECTION_ARROW_SPACING_PX = 150.0
private const val GPX_VISIBLE_DIRECTION_ARROW_VIEWPORT_PADDING_PX = 24.0
private const val GPX_DIRECTION_ARROW_HEADING_SAMPLE_PX = 42.0
private const val GPX_DIRECTION_ARROW_MIN_SCREEN_SEPARATION_PX = 86.0
private const val MAX_GPX_DIRECTION_ARROWS_PER_TRACK = 36
internal const val MAX_VISIBLE_GPX_DIRECTION_ARROWS_PER_TRACK = 10

internal data class TrackLodLevels(
    val sourceSignature: Long,
    val low: List<TrackPoint>,
    val medium: List<TrackPoint>,
    val full: List<TrackPoint>,
) {
    fun pointsForZoom(zoom: Int): List<TrackPoint> =
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

private data class ProjectedViewportBounds(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    fun contains(point: XY): Boolean =
        point.x in left..right &&
            point.y in top..bottom
}

internal data class GpxDirectionArrow(
    val latLong: LatLong,
    val headingDeg: Float,
)

private data class DistanceInterval(
    val start: Double,
    val end: Double,
) {
    val length: Double
        get() = end - start
}

internal fun zoomBucketFor(zoom: Int): Int =
    when {
        zoom >= 16 -> 3
        zoom >= 14 -> 2
        else -> 1
    }

internal fun buildTrackLodLevels(points: List<TrackPoint>): TrackLodLevels {
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

internal fun List<TrackPoint>.latLongs(): List<LatLong> = map { it.latLong }

internal fun buildGpxDirectionArrows(
    points: List<TrackPoint>,
    zoom: Int,
    tileSize: Int,
): List<GpxDirectionArrow> {
    if (points.size < 2) return emptyList()

    val zoomLevel = zoom.coerceIn(0, 30).toByte()
    val mapSize = MercatorProjection.getMapSize(zoomLevel, tileSize)
    val projectedPoints =
        points.map { point ->
            val latLong = point.latLong
            XY(
                x = MercatorProjection.longitudeToPixelX(latLong.longitude, mapSize),
                y = MercatorProjection.latitudeToPixelY(latLong.latitude, mapSize),
            )
        }
    val cumulativeDistances = DoubleArray(projectedPoints.size)
    for (i in 0 until projectedPoints.lastIndex) {
        val start = projectedPoints[i]
        val end = projectedPoints[i + 1]
        cumulativeDistances[i + 1] =
            cumulativeDistances[i] + kotlin.math.hypot(end.x - start.x, end.y - start.y)
    }
    val totalDistance = cumulativeDistances.lastOrNull() ?: 0.0
    if (totalDistance <= 0.0) return emptyList()

    val arrows = ArrayList<GpxDirectionArrow>()
    val arrowPixels = ArrayList<XY>()
    var nextArrowDistance = min(GPX_DIRECTION_ARROW_SPACING_PX * 0.6, totalDistance * 0.5)

    while (
        arrows.size < MAX_GPX_DIRECTION_ARROWS_PER_TRACK &&
        nextArrowDistance < totalDistance
    ) {
        val arrowPoint =
            pointAtDistance(
                projectedPoints = projectedPoints,
                cumulativeDistances = cumulativeDistances,
                distance = nextArrowDistance,
            )
        if (!hasNearbyDirectionArrow(arrowPixels, arrowPoint.x, arrowPoint.y)) {
            arrows.add(
                GpxDirectionArrow(
                    latLong =
                        LatLong(
                            MercatorProjection.pixelYToLatitude(arrowPoint.y, mapSize),
                            MercatorProjection.pixelXToLongitude(arrowPoint.x, mapSize),
                        ),
                    headingDeg =
                        headingAtDistance(
                            projectedPoints = projectedPoints,
                            cumulativeDistances = cumulativeDistances,
                            distance = nextArrowDistance,
                        ),
                ),
            )
            arrowPixels.add(arrowPoint)
        }
        nextArrowDistance += GPX_DIRECTION_ARROW_SPACING_PX
    }

    return arrows
}

@Suppress("NestedBlockDepth")
internal fun buildVisibleGpxDirectionArrows(
    points: List<TrackPoint>,
    zoom: Int,
    tileSize: Int,
    boundingBox: org.mapsforge.core.model.BoundingBox,
    maxArrows: Int = MAX_VISIBLE_GPX_DIRECTION_ARROWS_PER_TRACK,
): List<GpxDirectionArrow> {
    if (points.size < 2 || maxArrows <= 0) return emptyList()

    val zoomLevel = zoom.coerceIn(0, 30).toByte()
    val mapSize = MercatorProjection.getMapSize(zoomLevel, tileSize)
    val viewport = projectedViewportBounds(boundingBox, mapSize)
    val projectedPoints =
        points.map { point ->
            project(point.latLong, mapSize)
        }
    val cumulativeDistances = cumulativeDistances(projectedPoints)
    val visibleIntervals = visibleDistanceIntervals(projectedPoints, cumulativeDistances, viewport)
    if (visibleIntervals.isEmpty()) return emptyList()

    val candidates = ArrayList<GpxDirectionArrow>()
    val arrowPixels = ArrayList<XY>(maxArrows)
    visibleIntervals.forEach { interval ->
        var distance =
            if (interval.length <= GPX_VISIBLE_DIRECTION_ARROW_SPACING_PX) {
                interval.start + interval.length * 0.5
            } else {
                interval.start + GPX_VISIBLE_DIRECTION_ARROW_SPACING_PX * 0.5
            }

        while (distance < interval.end) {
            val arrowPoint =
                pointAtDistance(
                    projectedPoints = projectedPoints,
                    cumulativeDistances = cumulativeDistances,
                    distance = distance,
                )
            if (
                viewport.contains(arrowPoint) &&
                !hasNearbyDirectionArrow(arrowPixels, arrowPoint.x, arrowPoint.y)
            ) {
                candidates +=
                    GpxDirectionArrow(
                        latLong =
                            LatLong(
                                MercatorProjection.pixelYToLatitude(arrowPoint.y, mapSize),
                                MercatorProjection.pixelXToLongitude(arrowPoint.x, mapSize),
                            ),
                        headingDeg =
                            headingAtDistance(
                                projectedPoints = projectedPoints,
                                cumulativeDistances = cumulativeDistances,
                                distance = distance,
                            ),
                    )
                arrowPixels += arrowPoint
            }
            distance += GPX_VISIBLE_DIRECTION_ARROW_SPACING_PX
        }
    }

    return downsampleEvenly(candidates, maxArrows)
}

private fun pointAtDistance(
    projectedPoints: List<XY>,
    cumulativeDistances: DoubleArray,
    distance: Double,
): XY {
    if (distance <= 0.0) return projectedPoints.first()
    val totalDistance = cumulativeDistances.last()
    if (distance >= totalDistance) return projectedPoints.last()

    val segmentIndex =
        cumulativeDistances
            .indexOfFirst { it >= distance }
            .let { index -> max(1, index) - 1 }
    val segmentStartDistance = cumulativeDistances[segmentIndex]
    val segmentEndDistance = cumulativeDistances[segmentIndex + 1]
    val segmentLength = segmentEndDistance - segmentStartDistance
    if (segmentLength <= 0.0) return projectedPoints[segmentIndex]

    val start = projectedPoints[segmentIndex]
    val end = projectedPoints[segmentIndex + 1]
    val t = ((distance - segmentStartDistance) / segmentLength).coerceIn(0.0, 1.0)
    return XY(
        x = start.x + (end.x - start.x) * t,
        y = start.y + (end.y - start.y) * t,
    )
}

private fun headingAtDistance(
    projectedPoints: List<XY>,
    cumulativeDistances: DoubleArray,
    distance: Double,
): Float {
    val totalDistance = cumulativeDistances.last()
    val before =
        pointAtDistance(
            projectedPoints = projectedPoints,
            cumulativeDistances = cumulativeDistances,
            distance = max(0.0, distance - GPX_DIRECTION_ARROW_HEADING_SAMPLE_PX),
        )
    val after =
        pointAtDistance(
            projectedPoints = projectedPoints,
            cumulativeDistances = cumulativeDistances,
            distance = min(totalDistance, distance + GPX_DIRECTION_ARROW_HEADING_SAMPLE_PX),
        )
    val dx = after.x - before.x
    val dy = after.y - before.y
    return Math.toDegrees(atan2(dx, -dy)).toFloat()
}

private fun cumulativeDistances(projectedPoints: List<XY>): DoubleArray {
    val cumulativeDistances = DoubleArray(projectedPoints.size)
    for (i in 0 until projectedPoints.lastIndex) {
        val start = projectedPoints[i]
        val end = projectedPoints[i + 1]
        cumulativeDistances[i + 1] =
            cumulativeDistances[i] + kotlin.math.hypot(end.x - start.x, end.y - start.y)
    }
    return cumulativeDistances
}

private fun visibleDistanceIntervals(
    projectedPoints: List<XY>,
    cumulativeDistances: DoubleArray,
    viewport: ProjectedViewportBounds,
): List<DistanceInterval> {
    val intervals = ArrayList<DistanceInterval>()
    for (index in 0 until projectedPoints.lastIndex) {
        val start = projectedPoints[index]
        val end = projectedPoints[index + 1]
        val segmentLength = cumulativeDistances[index + 1] - cumulativeDistances[index]
        if (segmentLength <= 1e-6) continue

        val clipped = clipSegmentToViewport(start, end, viewport) ?: continue
        val interval =
            DistanceInterval(
                start = cumulativeDistances[index] + segmentLength * clipped.start,
                end = cumulativeDistances[index] + segmentLength * clipped.end,
            )
        if (interval.length <= 1e-6) continue

        val last = intervals.lastOrNull()
        if (last != null && interval.start <= last.end + 1e-6) {
            intervals[intervals.lastIndex] = DistanceInterval(last.start, max(last.end, interval.end))
        } else {
            intervals += interval
        }
    }
    return intervals
}

private fun clipSegmentToViewport(
    start: XY,
    end: XY,
    viewport: ProjectedViewportBounds,
): DistanceInterval? {
    val dx = end.x - start.x
    val dy = end.y - start.y
    var t0 = 0.0
    var t1 = 1.0

    fun clip(
        p: Double,
        q: Double,
    ): Boolean {
        if (p == 0.0) return q >= 0.0
        val r = q / p
        return if (p < 0.0) {
            if (r > t1) {
                false
            } else {
                if (r > t0) t0 = r
                true
            }
        } else {
            if (r < t0) {
                false
            } else {
                if (r < t1) t1 = r
                true
            }
        }
    }

    return if (
        clip(-dx, start.x - viewport.left) &&
        clip(dx, viewport.right - start.x) &&
        clip(-dy, start.y - viewport.top) &&
        clip(dy, viewport.bottom - start.y)
    ) {
        DistanceInterval(t0, t1)
    } else {
        null
    }
}

private fun downsampleEvenly(
    candidates: List<GpxDirectionArrow>,
    maxArrows: Int,
): List<GpxDirectionArrow> {
    if (candidates.size <= maxArrows) return candidates
    if (maxArrows <= 1) return candidates.take(1)

    return List(maxArrows) { index ->
        val sourceIndex =
            (index * candidates.lastIndex.toDouble() / (maxArrows - 1))
                .roundToInt()
                .coerceIn(candidates.indices)
        candidates[sourceIndex]
    }
}

private fun hasNearbyDirectionArrow(
    arrowPixels: List<XY>,
    x: Double,
    y: Double,
): Boolean {
    val minDistanceSq =
        GPX_DIRECTION_ARROW_MIN_SCREEN_SEPARATION_PX *
            GPX_DIRECTION_ARROW_MIN_SCREEN_SEPARATION_PX
    return arrowPixels.any { arrow ->
        val dx = arrow.x - x
        val dy = arrow.y - y
        dx * dx + dy * dy < minDistanceSq
    }
}

private fun project(
    latLong: LatLong,
    mapSize: Long,
): XY =
    XY(
        x = MercatorProjection.longitudeToPixelX(latLong.longitude, mapSize),
        y = MercatorProjection.latitudeToPixelY(latLong.latitude, mapSize),
    )

private fun projectedViewportBounds(
    boundingBox: org.mapsforge.core.model.BoundingBox,
    mapSize: Long,
): ProjectedViewportBounds {
    val left =
        MercatorProjection.longitudeToPixelX(
            boundingBox.minLongitude,
            mapSize,
        ) - GPX_VISIBLE_DIRECTION_ARROW_VIEWPORT_PADDING_PX
    val right =
        MercatorProjection.longitudeToPixelX(
            boundingBox.maxLongitude,
            mapSize,
        ) + GPX_VISIBLE_DIRECTION_ARROW_VIEWPORT_PADDING_PX
    val top =
        MercatorProjection.latitudeToPixelY(
            boundingBox.maxLatitude,
            mapSize,
        ) - GPX_VISIBLE_DIRECTION_ARROW_VIEWPORT_PADDING_PX
    val bottom =
        MercatorProjection.latitudeToPixelY(
            boundingBox.minLatitude,
            mapSize,
        ) + GPX_VISIBLE_DIRECTION_ARROW_VIEWPORT_PADDING_PX

    return ProjectedViewportBounds(
        left = min(left, right),
        top = min(top, bottom),
        right = max(left, right),
        bottom = max(top, bottom),
    )
}

private fun segmentMayTouchViewport(
    start: XY,
    end: XY,
    viewport: ProjectedViewportBounds,
): Boolean =
    max(start.x, end.x) >= viewport.left &&
        min(start.x, end.x) <= viewport.right &&
        max(start.y, end.y) >= viewport.top &&
        min(start.y, end.y) <= viewport.bottom

private fun latLongListSignature(points: List<TrackPoint>): Long {
    var h = 1_469_598_103_934_665_603L
    val prime = 1_099_511_628_211L
    points.forEach { point ->
        val ll = point.latLong
        h = (h xor ll.latitude.toBits()) * prime
        h = (h xor ll.longitude.toBits()) * prime
        h = (h xor (point.elevation?.toBits() ?: 0L)) * prime
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
    points: List<TrackPoint>,
    toleranceMeters: Double,
): List<TrackPoint> {
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

    val out = ArrayList<TrackPoint>(points.size)
    for (i in points.indices) {
        if (keep[i]) out.add(points[i])
    }
    return if (out.size >= 2) out else listOf(points.first(), points.last())
}

private fun toLocalMeters(points: List<TrackPoint>): List<XY> {
    val r = 6_371_000.0
    val lat0 = Math.toRadians(points.first().latLong.latitude)
    val lon0 = Math.toRadians(points.first().latLong.longitude)
    val cosLat0 = kotlin.math.cos(lat0)

    return points.map { point ->
        val ll = point.latLong
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
