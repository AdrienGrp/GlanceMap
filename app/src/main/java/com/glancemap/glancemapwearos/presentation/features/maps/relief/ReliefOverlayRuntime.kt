package com.glancemap.glancemapwearos.presentation.features.maps

import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.Point
import org.mapsforge.core.util.MercatorProjection
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

internal data class ElevationSampleKey(
    val quantizedLat: Int,
    val quantizedLon: Int
)

internal data class ElevationSampleCacheEntry(
    val valueMeters: Double?,
    val sampledAtElapsedMs: Long
)

internal enum class RuntimeProfile {
    OVERLOADED,
    MOVING,
    SETTLING,
    IDLE
}

internal data class RuntimePolicy(
    val profile: RuntimeProfile,
    val visibleQuality: OverlayBuildQuality,
    val prefetchQuality: OverlayBuildQuality,
    val maxRenderTiles: Int,
    val maxPrefetchTiles: Int,
    val maxPendingJobs: Int
)

internal data class OverlayBuildRequest(
    val key: OverlayTileKey,
    val quality: OverlayBuildQuality
)

internal data class VisibleTile(
    val key: OverlayTileKey,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

internal data class FinePriorityState(
    val centerPriorityCount: Int,
    val allowFullFine: Boolean
)

internal data class FallbackTileSource(
    val bitmap: org.mapsforge.core.graphics.Bitmap,
    val srcLeft: Int,
    val srcTop: Int,
    val srcRight: Int,
    val srcBottom: Int
)

internal fun tileIntersectsViewport(
    tile: VisibleTile,
    viewportWidth: Int,
    viewportHeight: Int
): Boolean {
    if (viewportWidth <= 0 || viewportHeight <= 0) return false
    return tile.right > 0 &&
        tile.bottom > 0 &&
        tile.left < viewportWidth &&
        tile.top < viewportHeight
}

internal fun prioritizeVisibleTiles(
    tiles: List<VisibleTile>,
    viewportWidth: Int,
    viewportHeight: Int,
    maxTiles: Int
): List<VisibleTile> {
    if (tiles.isEmpty()) return emptyList()
    if (maxTiles <= 0) return emptyList()

    val centerX = viewportWidth * 0.5
    val centerY = viewportHeight * 0.5
    val prioritized = tiles
        .sortedBy { tile ->
            val tileCenterX = (tile.left + tile.right) * 0.5
            val tileCenterY = (tile.top + tile.bottom) * 0.5
            val dx = tileCenterX - centerX
            val dy = tileCenterY - centerY
            (dx * dx) + (dy * dy)
        }
    return if (prioritized.size <= maxTiles) prioritized else prioritized.take(maxTiles)
}

internal fun buildReliefFinePriorityState(
    renderTiles: List<VisibleTile>,
    policy: RuntimePolicy,
    idleFinePriorityTiles: Int,
    entryForKey: (OverlayTileKey) -> OverlayTileEntry?
): FinePriorityState {
    if (renderTiles.isEmpty() || policy.profile != RuntimeProfile.IDLE || policy.visibleQuality != OverlayBuildQuality.FINE) {
        return FinePriorityState(centerPriorityCount = 0, allowFullFine = true)
    }
    val centerPriorityCount = min(idleFinePriorityTiles, renderTiles.size)
    if (centerPriorityCount <= 0) {
        return FinePriorityState(centerPriorityCount = 0, allowFullFine = true)
    }
    val centerFineReadyCount = renderTiles
        .take(centerPriorityCount)
        .count { tile ->
            val entry = entryForKey(tile.key)
            entry?.status == OverlayTileStatus.READY &&
                entry.quality.rank >= OverlayBuildQuality.FINE.rank
        }
    val requiredFineCenterCount = ((centerPriorityCount * 3) + 3) / 4
    return FinePriorityState(
        centerPriorityCount = centerPriorityCount,
        allowFullFine = centerFineReadyCount >= requiredFineCenterCount
    )
}

internal fun resolveReliefVisibleQualityForTile(
    tileIndex: Int,
    policy: RuntimePolicy,
    finePriorityState: FinePriorityState
): OverlayBuildQuality {
    if (policy.profile != RuntimeProfile.IDLE || policy.visibleQuality != OverlayBuildQuality.FINE) {
        return policy.visibleQuality
    }
    if (tileIndex < finePriorityState.centerPriorityCount) {
        return OverlayBuildQuality.FINE
    }
    return if (finePriorityState.allowFullFine) {
        OverlayBuildQuality.FINE
    } else {
        OverlayBuildQuality.COARSE
    }
}

internal fun computeVisibleTiles(
    boundingBox: BoundingBox,
    zoomLevel: Byte,
    mapSize: Long,
    tileSize: Int,
    topLeftPoint: Point,
    marginTiles: Int
): List<VisibleTile> {
    val tileSizeD = tileSize.toDouble()
    val mapSizeD = mapSize.toDouble()
    if (tileSizeD <= 0.0 || mapSizeD <= 0.0) return emptyList()

    var leftPx = MercatorProjection.longitudeToPixelX(boundingBox.minLongitude, mapSize)
    var rightPx = MercatorProjection.longitudeToPixelX(boundingBox.maxLongitude, mapSize)
    val topPx = MercatorProjection.latitudeToPixelY(boundingBox.maxLatitude, mapSize)
    val bottomPx = MercatorProjection.latitudeToPixelY(boundingBox.minLatitude, mapSize)

    if (!leftPx.isFinite() || !rightPx.isFinite() || !topPx.isFinite() || !bottomPx.isFinite()) {
        return emptyList()
    }

    if (rightPx < leftPx) {
        rightPx += mapSizeD
    }

    val tileCountPerAxis = (mapSize / tileSize.toLong()).coerceAtLeast(1L)
    val maxTileY = tileCountPerAxis - 1L
    val margin = marginTiles.coerceAtLeast(0).toLong()

    val xStart = floor(leftPx / tileSizeD).toLong() - margin
    val xEnd = ceil(rightPx / tileSizeD).toLong() - 1L + margin
    val yStart = (floor(topPx / tileSizeD).toLong() - margin).coerceIn(0L, maxTileY)
    val yEnd = (ceil(bottomPx / tileSizeD).toLong() - 1L + margin).coerceIn(0L, maxTileY)
    if (xEnd < xStart || yEnd < yStart) return emptyList()

    val out = ArrayList<VisibleTile>()
    for (tileY in yStart..yEnd) {
        for (tileX in xStart..xEnd) {
            val wrappedTileX = reliefFloorMod(tileX, tileCountPerAxis)
            val key = OverlayTileKey(
                zoom = zoomLevel,
                tileX = wrappedTileX,
                tileY = tileY,
                tileSize = tileSize
            )

            val tileWorldX = tileX.toDouble() * tileSizeD
            val tileWorldY = tileY.toDouble() * tileSizeD

            val left = round(tileWorldX - topLeftPoint.x).toInt()
            val top = round(tileWorldY - topLeftPoint.y).toInt()
            out += VisibleTile(
                key = key,
                left = left,
                top = top,
                right = left + tileSize,
                bottom = top + tileSize
            )
        }
    }

    return out
}

internal fun readyTileAlpha(
    entry: OverlayTileEntry,
    nowElapsedMs: Long,
    fadeInMs: Long
): Float {
    if (entry.status != OverlayTileStatus.READY) return 1f
    if (entry.drawMode != OverlayTileDrawMode.FADE_FROM_FALLBACK) return 1f
    val elapsed = (nowElapsedMs - entry.builtElapsedMs).coerceAtLeast(0L)
    return (elapsed.toFloat() / fadeInMs.toFloat()).coerceIn(0f, 1f)
}

internal fun quantizedElevationSampleKey(
    lat: Double,
    lon: Double,
    quantizationFactor: Double
): ElevationSampleKey {
    val quantizedLat = round(lat * quantizationFactor).toInt()
    val quantizedLon = round(lon * quantizationFactor).toInt()
    return ElevationSampleKey(
        quantizedLat = quantizedLat,
        quantizedLon = quantizedLon
    )
}

private fun reliefFloorMod(value: Long, modulus: Long): Long {
    if (modulus <= 0L) return 0L
    val remainder = value % modulus
    return if (remainder < 0L) remainder + modulus else remainder
}
