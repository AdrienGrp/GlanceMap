package com.glancemap.glancemapwearos.presentation.features.maps

import android.os.SystemClock
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

internal class ReliefTileRenderer(
    private val terrainRepository: ReliefDemRepository,
    private val isBuildEnabled: () -> Boolean,
) {
    fun buildOverlayTile(
        key: OverlayTileKey,
        quality: OverlayBuildQuality,
    ): OverlayTileEntry {
        if (!isBuildEnabled()) {
            return emptyEntry(quality)
        }
        val tileSize = key.tileSize
        val tileSizeD = tileSize.toDouble()
        val mapSize = MercatorProjection.getMapSize(key.zoom, tileSize)
        val mapSizeD = mapSize.toDouble()

        val sampleStep = sampleStepPxForZoom(key.zoom, quality)
        val tileWorldLeft = key.tileX.toDouble() * tileSizeD
        val tileWorldTop = key.tileY.toDouble() * tileSizeD

        val pixels = IntArray(tileSize * tileSize)
        var hasAnyColoredPixel = false

        var y = 0
        while (y < tileSize) {
            if (!isBuildEnabled()) {
                return emptyEntry(quality)
            }
            val blockH = min(sampleStep, tileSize - y)
            var x = 0
            while (x < tileSize) {
                val blockW = min(sampleStep, tileSize - x)

                val centerX = tileWorldLeft + x + blockW * 0.5
                val centerY = tileWorldTop + y + blockH * 0.5
                val centerLon =
                    MercatorProjection.pixelXToLongitude(
                        wrapPixelX(centerX, mapSizeD),
                        mapSize,
                    )
                val centerLat =
                    MercatorProjection.pixelYToLatitude(
                        centerY.coerceIn(0.0, mapSizeD),
                        mapSize,
                    )

                val color =
                    computeReliefColor(
                        lat = centerLat,
                        lon = centerLon,
                        quality = quality,
                    )
                if (color != 0) {
                    hasAnyColoredPixel = true
                }

                fillBlock(
                    pixels = pixels,
                    stride = tileSize,
                    xStart = x,
                    yStart = y,
                    width = blockW,
                    height = blockH,
                    color = color,
                )

                x += sampleStep
            }
            y += sampleStep
        }

        if (!hasAnyColoredPixel) {
            return emptyEntry(quality)
        }

        val bitmap = AndroidGraphicFactory.INSTANCE.createBitmap(tileSize, tileSize, true)
        AndroidGraphicFactory.getBitmap(bitmap).setPixels(
            pixels,
            0,
            tileSize,
            0,
            0,
            tileSize,
            tileSize,
        )

        return OverlayTileEntry(
            bitmap = bitmap,
            builtElapsedMs = SystemClock.elapsedRealtime(),
            status = OverlayTileStatus.READY,
            drawMode =
                if (quality == OverlayBuildQuality.FINE) {
                    OverlayTileDrawMode.FADE_FROM_FALLBACK
                } else {
                    OverlayTileDrawMode.STEADY
                },
            quality = quality,
        )
    }

    private fun emptyEntry(quality: OverlayBuildQuality): OverlayTileEntry =
        OverlayTileEntry(
            bitmap = null,
            builtElapsedMs = SystemClock.elapsedRealtime(),
            status = OverlayTileStatus.NO_DATA,
            drawMode = OverlayTileDrawMode.STEADY,
            quality = quality,
        )

    private fun sampleStepPxForZoom(
        zoomLevel: Byte,
        quality: OverlayBuildQuality,
    ): Int {
        val zoom = zoomLevel.toInt()
        return when (quality) {
            OverlayBuildQuality.COARSE ->
                when {
                    zoom >= 16 -> ReliefOverlayAlgorithm.COARSE_STEP_HIGH
                    zoom >= 14 -> ReliefOverlayAlgorithm.COARSE_STEP_MID
                    else -> ReliefOverlayAlgorithm.COARSE_STEP_LOW
                }

            OverlayBuildQuality.FINE ->
                when {
                    zoom >= 16 -> ReliefOverlayAlgorithm.FINE_STEP_HIGH
                    zoom >= 14 -> ReliefOverlayAlgorithm.FINE_STEP_MID
                    else -> ReliefOverlayAlgorithm.FINE_STEP_LOW
                }
        }
    }

    private fun fillBlock(
        pixels: IntArray,
        stride: Int,
        xStart: Int,
        yStart: Int,
        width: Int,
        height: Int,
        color: Int,
    ) {
        if (width <= 0 || height <= 0) return
        for (yy in yStart until (yStart + height)) {
            var index = yy * stride + xStart
            repeat(width) {
                pixels[index] = color
                index += 1
            }
        }
    }

    private fun wrapPixelX(
        pixelX: Double,
        mapSize: Double,
    ): Double {
        if (mapSize <= 0.0 || !pixelX.isFinite()) return 0.0
        val wrapped = pixelX % mapSize
        return if (wrapped < 0.0) wrapped + mapSize else wrapped
    }

    private fun computeReliefColor(
        lat: Double,
        lon: Double,
        quality: OverlayBuildQuality,
    ): Int {
        val slopeDegrees = computeSlopeDegrees(lat, lon, quality) ?: return 0
        val bandAlphaScale = if (quality == OverlayBuildQuality.COARSE) 0.9 else 1.0

        return when {
            slopeDegrees < ReliefOverlayAlgorithm.SLOPE_BAND_1_DEG -> 0
            slopeDegrees < ReliefOverlayAlgorithm.SLOPE_BAND_2_DEG ->
                argb(alpha = 96.0 * bandAlphaScale, red = 246, green = 239, blue = 0)
            slopeDegrees < ReliefOverlayAlgorithm.SLOPE_BAND_3_DEG ->
                argb(alpha = 116.0 * bandAlphaScale, red = 245, green = 198, blue = 0)
            slopeDegrees < ReliefOverlayAlgorithm.SLOPE_BAND_4_DEG ->
                argb(alpha = 148.0 * bandAlphaScale, red = 248, green = 153, blue = 0)
            slopeDegrees < ReliefOverlayAlgorithm.SLOPE_BAND_5_DEG ->
                argb(alpha = 164.0 * bandAlphaScale, red = 255, green = 109, blue = 0)
            else -> argb(alpha = 180.0 * bandAlphaScale, red = 255, green = 61, blue = 0)
        }
    }

    private fun argb(
        alpha: Double,
        red: Int,
        green: Int,
        blue: Int,
    ): Int {
        val a = round(alpha).toInt().coerceIn(0, 255)
        return (a shl 24) or (red shl 16) or (green shl 8) or blue
    }

    @Suppress("ReturnCount")
    private fun computeSlopeDegrees(
        lat: Double,
        lon: Double,
        quality: OverlayBuildQuality,
    ): Double? {
        val baseTile = terrainRepository.loadDemTileFor(lat, lon) ?: return null
        val epsDeg = 1.0 / baseTile.axisLen.coerceAtLeast(1200).toDouble()
        val baseLatTile = kotlin.math.floor(lat).toInt()
        val baseLonTile = kotlin.math.floor(lon).toInt()

        fun elevationAt(
            sampleLat: Double,
            sampleLon: Double,
        ): Double? =
            terrainRepository.elevationAtUsingTile(
                tile = baseTile,
                tileLat = baseLatTile,
                tileLon = baseLonTile,
                lat = sampleLat,
                lon = sampleLon,
            )

        val northLat = min(89.999999, lat + epsDeg)
        val southLat = max(-89.999999, lat - epsDeg)
        val eastLon = min(179.999999, lon + epsDeg)
        val westLon = max(-179.999999, lon - epsDeg)
        val eNorth = elevationAt(northLat, lon) ?: return null
        val eSouth = elevationAt(southLat, lon) ?: return null
        val eEast = elevationAt(lat, eastLon) ?: return null
        val eWest = elevationAt(lat, westLon) ?: return null

        val latRad = Math.toRadians(lat)
        val metersPerDegreeLat = 111132.954 - (559.822 * cos(2 * latRad)) + (1.175 * cos(4 * latRad))
        val metersPerDegreeLon = max(1.0, 111320.0 * cos(latRad))

        val cellY = max(0.5, (northLat - lat) * metersPerDegreeLat)
        val cellX = max(0.5, (eastLon - lon) * metersPerDegreeLon)

        val centralSlopeDegrees =
            slopeDegreesForGradient(
                dzDx = (eEast - eWest) / (2.0 * cellX),
                dzDy = (eSouth - eNorth) / (2.0 * cellY),
            )
        if (quality == OverlayBuildQuality.COARSE) {
            return centralSlopeDegrees
        }

        val eNorthEast = elevationAt(northLat, eastLon) ?: return centralSlopeDegrees
        val eNorthWest = elevationAt(northLat, westLon) ?: return centralSlopeDegrees
        val eSouthEast = elevationAt(southLat, eastLon) ?: return centralSlopeDegrees
        val eSouthWest = elevationAt(southLat, westLon) ?: return centralSlopeDegrees
        return hornSlopeDegrees(
            north = eNorth,
            south = eSouth,
            east = eEast,
            west = eWest,
            northEast = eNorthEast,
            northWest = eNorthWest,
            southEast = eSouthEast,
            southWest = eSouthWest,
            cellX = cellX,
            cellY = cellY,
        )
    }
}

internal fun slopeDegreesForGradient(
    dzDx: Double,
    dzDy: Double,
): Double = Math.toDegrees(atan(hypot(dzDx, dzDy)))

@Suppress("LongParameterList")
internal fun hornSlopeDegrees(
    north: Double,
    south: Double,
    east: Double,
    west: Double,
    northEast: Double,
    northWest: Double,
    southEast: Double,
    southWest: Double,
    cellX: Double,
    cellY: Double,
): Double {
    val safeCellX = max(0.5, cellX)
    val safeCellY = max(0.5, cellY)
    val dzDx =
        ((northEast + (2.0 * east) + southEast) - (northWest + (2.0 * west) + southWest)) /
            (8.0 * safeCellX)
    val dzDy =
        ((southWest + (2.0 * south) + southEast) - (northWest + (2.0 * north) + northEast)) /
            (8.0 * safeCellY)
    return slopeDegreesForGradient(dzDx, dzDy)
}
