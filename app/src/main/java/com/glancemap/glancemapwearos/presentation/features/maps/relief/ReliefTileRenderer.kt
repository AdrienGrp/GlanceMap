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
import kotlin.math.sqrt

internal class ReliefTileRenderer(
    private val terrainRepository: ReliefDemRepository,
    private val isBuildEnabled: () -> Boolean,
) {
    companion object {
        private const val SAMPLE_STEP_PX_LOW_COARSE = 16
        private const val SAMPLE_STEP_PX_MID_COARSE = 14
        private const val SAMPLE_STEP_PX_HIGH_COARSE = 12
        private const val SAMPLE_STEP_PX_LOW = 12
        private const val SAMPLE_STEP_PX_MID = 10
        private const val SAMPLE_STEP_PX_HIGH = 8
        private const val LIGHT_VECTOR_X = -0.35
        private const val LIGHT_VECTOR_Y = 0.35
        private const val LIGHT_VECTOR_Z = 0.87
        private const val FILL_LIGHT_VECTOR_X = 0.30
        private const val FILL_LIGHT_VECTOR_Y = 0.14
        private const val FILL_LIGHT_VECTOR_Z = 0.94
    }

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
                    zoom >= 16 -> SAMPLE_STEP_PX_HIGH_COARSE
                    zoom >= 14 -> SAMPLE_STEP_PX_MID_COARSE
                    else -> SAMPLE_STEP_PX_LOW_COARSE
                }

            OverlayBuildQuality.FINE ->
                when {
                    zoom >= 16 -> SAMPLE_STEP_PX_HIGH
                    zoom >= 14 -> SAMPLE_STEP_PX_MID
                    else -> SAMPLE_STEP_PX_LOW
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
        val slopeDegrees = computeReliefSample(lat, lon, quality)?.slopeDegrees ?: return 0
        val bandAlphaScale = if (quality == OverlayBuildQuality.COARSE) 0.9 else 1.0

        return when {
            slopeDegrees < 15.0 -> 0
            slopeDegrees < 22.0 -> argb(alpha = 96.0 * bandAlphaScale, red = 246, green = 239, blue = 0)
            slopeDegrees < 29.0 -> argb(alpha = 116.0 * bandAlphaScale, red = 245, green = 198, blue = 0)
            slopeDegrees < 36.0 -> argb(alpha = 148.0 * bandAlphaScale, red = 248, green = 153, blue = 0)
            slopeDegrees < 43.0 -> argb(alpha = 164.0 * bandAlphaScale, red = 255, green = 109, blue = 0)
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

    private fun computeReliefSample(
        lat: Double,
        lon: Double,
        quality: OverlayBuildQuality,
    ): ReliefSample? {
        val baseTile = terrainRepository.loadDemTileFor(lat, lon) ?: return null
        val epsDeg = 1.0 / baseTile.axisLen.coerceAtLeast(1200).toDouble()
        val eCenter = terrainRepository.elevationAt(lat, lon) ?: return null

        val northLat = min(89.999999, lat + epsDeg)
        val southLat = max(-89.999999, lat - epsDeg)
        val eastLon = min(179.999999, lon + epsDeg)
        val westLon = max(-179.999999, lon - epsDeg)
        val eNorth = terrainRepository.elevationAt(northLat, lon) ?: return null
        val eSouth = terrainRepository.elevationAt(southLat, lon) ?: return null
        val eEast = terrainRepository.elevationAt(lat, eastLon) ?: return null
        val eWest = terrainRepository.elevationAt(lat, westLon) ?: return null

        val latRad = Math.toRadians(lat)
        val metersPerDegreeLat = 111132.954 - (559.822 * cos(2 * latRad)) + (1.175 * cos(4 * latRad))
        val metersPerDegreeLon = max(1.0, 111320.0 * cos(latRad))

        val cellY = max(0.5, (northLat - lat) * metersPerDegreeLat)
        val cellX = max(0.5, (eastLon - lon) * metersPerDegreeLon)

        val dzDxCentral = (eEast - eWest) / (2.0 * cellX)
        val dzDyCentral = (eSouth - eNorth) / (2.0 * cellY)
        var dzDx = dzDxCentral
        var dzDy = dzDyCentral
        var reliefDegrees = Math.toDegrees(atan(hypot(dzDxCentral, dzDyCentral)))
        if (quality == OverlayBuildQuality.COARSE) {
            return basicReliefSample(reliefDegrees, dzDx, dzDy)
        }

        val eNorthEast = terrainRepository.elevationAt(northLat, eastLon) ?: return basicReliefSample(reliefDegrees, dzDx, dzDy)
        val eNorthWest = terrainRepository.elevationAt(northLat, westLon) ?: return basicReliefSample(reliefDegrees, dzDx, dzDy)
        val eSouthEast = terrainRepository.elevationAt(southLat, eastLon) ?: return basicReliefSample(reliefDegrees, dzDx, dzDy)
        val eSouthWest = terrainRepository.elevationAt(southLat, westLon) ?: return basicReliefSample(reliefDegrees, dzDx, dzDy)

        val dzDxHorn =
            ((eNorthEast + (2.0 * eEast) + eSouthEast) - (eNorthWest + (2.0 * eWest) + eSouthWest)) /
                (8.0 * cellX)
        val dzDyHorn =
            ((eSouthWest + (2.0 * eSouth) + eSouthEast) - (eNorthWest + (2.0 * eNorth) + eNorthEast)) /
                (8.0 * cellY)

        val hornDegrees = Math.toDegrees(atan(hypot(dzDxHorn, dzDyHorn)))
        if (hornDegrees >= reliefDegrees) {
            dzDx = dzDxHorn
            dzDy = dzDyHorn
            reliefDegrees = hornDegrees
        }

        val neighborMean8 =
            (eNorth + eSouth + eEast + eWest + eNorthEast + eNorthWest + eSouthEast + eSouthWest) / 8.0
        val ridgeMeters = (eCenter - neighborMean8).coerceAtLeast(0.0)
        val gullyMeters = (neighborMean8 - eCenter).coerceAtLeast(0.0)
        val ruggednessMeters =
            sqrt(
                (
                    squareDiff(eNorth, eCenter) +
                        squareDiff(eSouth, eCenter) +
                        squareDiff(eEast, eCenter) +
                        squareDiff(eWest, eCenter) +
                        squareDiff(eNorthEast, eCenter) +
                        squareDiff(eNorthWest, eCenter) +
                        squareDiff(eSouthEast, eCenter) +
                        squareDiff(eSouthWest, eCenter)
                ) / 8.0,
            )

        return ReliefSample(
            slopeDegrees = reliefDegrees,
            primaryIllumination =
                illuminationForGradient(
                    dzDx = dzDx,
                    dzDy = dzDy,
                    lightX = LIGHT_VECTOR_X,
                    lightY = LIGHT_VECTOR_Y,
                    lightZ = LIGHT_VECTOR_Z,
                ),
            fillIllumination =
                illuminationForGradient(
                    dzDx = dzDx,
                    dzDy = dzDy,
                    lightX = FILL_LIGHT_VECTOR_X,
                    lightY = FILL_LIGHT_VECTOR_Y,
                    lightZ = FILL_LIGHT_VECTOR_Z,
                ),
            ridgeMeters = ridgeMeters,
            gullyMeters = gullyMeters,
            ruggednessMeters = ruggednessMeters,
        )
    }

    private fun basicReliefSample(
        reliefDegrees: Double,
        dzDx: Double,
        dzDy: Double,
    ): ReliefSample =
        ReliefSample(
            slopeDegrees = reliefDegrees,
            primaryIllumination =
                illuminationForGradient(
                    dzDx = dzDx,
                    dzDy = dzDy,
                    lightX = LIGHT_VECTOR_X,
                    lightY = LIGHT_VECTOR_Y,
                    lightZ = LIGHT_VECTOR_Z,
                ),
            fillIllumination =
                illuminationForGradient(
                    dzDx = dzDx,
                    dzDy = dzDy,
                    lightX = FILL_LIGHT_VECTOR_X,
                    lightY = FILL_LIGHT_VECTOR_Y,
                    lightZ = FILL_LIGHT_VECTOR_Z,
                ),
            ridgeMeters = 0.0,
            gullyMeters = 0.0,
            ruggednessMeters = 0.0,
        )

    private fun squareDiff(
        value: Double,
        center: Double,
    ): Double {
        val delta = value - center
        return delta * delta
    }

    private fun illuminationForGradient(
        dzDx: Double,
        dzDy: Double,
        lightX: Double,
        lightY: Double,
        lightZ: Double,
    ): Double {
        val normalX = -dzDx
        val normalY = -dzDy
        val normalZ = 1.0
        val normalLength = max(1e-9, hypot(hypot(normalX, normalY), normalZ))
        return (
            (normalX * lightX) +
                (normalY * lightY) +
                (normalZ * lightZ)
        ) / normalLength
    }
}
