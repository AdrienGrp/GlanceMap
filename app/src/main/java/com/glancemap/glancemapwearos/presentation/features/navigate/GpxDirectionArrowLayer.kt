package com.glancemap.glancemapwearos.presentation.features.navigate

import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.layer.Layer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

internal class GpxDirectionArrowLayer(
    private val bitmap: Bitmap,
) : Layer() {
    var trackLods: Map<String, TrackLodLevels> = emptyMap()
        set(value) {
            field = value
            directionArrowGeometryCache.clear()
        }

    @Volatile private var cachedZoom: Byte = (-1).toByte()

    @Volatile private var cachedTileSize: Int = -1

    @Volatile private var cachedMapSize: Long = 0L

    private val directionArrowGeometryCache = ConcurrentHashMap<String, CachedDirectionArrowGeometry>()

    override fun draw(
        boundingBox: BoundingBox,
        zoomLevel: Byte,
        canvas: Canvas,
        topLeft: Point,
        mapViewRotation: Rotation,
    ) {
        if (!isVisible || trackLods.isEmpty()) return

        val zoom = zoomLevel.toInt()
        val tileSize = displayModel.tileSize
        val mapSize = getCachedMapSize(zoomLevel, tileSize)
        trackLods.forEach { (trackId, lod) ->
            val geometry =
                cachedDirectionArrowGeometry(
                    trackId = trackId,
                    lod = lod,
                    zoom = zoom,
                    tileSize = tileSize,
                ) ?: return@forEach
            val arrows =
                buildVisibleGpxDirectionArrows(
                    geometry = geometry,
                    boundingBox = boundingBox,
                )
            arrows.forEach { arrow ->
                drawArrow(
                    arrow = arrow,
                    canvas = canvas,
                    topLeft = topLeft,
                    mapSize = mapSize,
                )
            }
        }
    }

    override fun onDestroy() {
        directionArrowGeometryCache.clear()
        super.onDestroy()
    }

    private fun cachedDirectionArrowGeometry(
        trackId: String,
        lod: TrackLodLevels,
        zoom: Int,
        tileSize: Int,
    ): GpxDirectionArrowGeometry? {
        val cached = directionArrowGeometryCache[trackId]
        val cacheMatches =
            cached?.let {
                it.sourceSignature == lod.sourceSignature &&
                    it.zoom == zoom &&
                    it.tileSize == tileSize
            } ?: false
        if (cacheMatches) return cached.geometry
        val geometry =
            buildGpxDirectionArrowGeometry(
                points = lod.pointsForZoom(zoom),
                zoom = zoom,
                tileSize = tileSize,
            )
        geometry?.let {
            directionArrowGeometryCache[trackId] =
                CachedDirectionArrowGeometry(
                    sourceSignature = lod.sourceSignature,
                    zoom = zoom,
                    tileSize = tileSize,
                    geometry = it,
                )
        }
        return geometry
    }

    private fun drawArrow(
        arrow: GpxDirectionArrow,
        canvas: Canvas,
        topLeft: Point,
        mapSize: Long,
    ) {
        val pixelX = MercatorProjection.longitudeToPixelX(arrow.latLong.longitude, mapSize) - topLeft.x
        val pixelY = MercatorProjection.latitudeToPixelY(arrow.latLong.latitude, mapSize) - topLeft.y
        val drawX = floor(pixelX - bitmap.width / 2f).toInt()
        val drawY = floor(pixelY - bitmap.height / 2f).toInt()
        val pivotX = drawX + bitmap.width / 2f
        val pivotY = drawY + bitmap.height / 2f
        val effective = normalize360(arrow.headingDeg)

        canvas.save()
        if (effective != 0f) {
            canvas.rotate(effective, pivotX, pivotY)
        }
        canvas.drawBitmap(bitmap, drawX, drawY)
        canvas.restore()
    }

    private fun getCachedMapSize(
        zoomLevel: Byte,
        tileSize: Int,
    ): Long {
        if (cachedZoom != zoomLevel || cachedTileSize != tileSize) {
            cachedZoom = zoomLevel
            cachedTileSize = tileSize
            cachedMapSize = MercatorProjection.getMapSize(zoomLevel, tileSize)
        }
        return cachedMapSize
    }

    private fun normalize360(deg: Float): Float = (deg % 360f + 360f) % 360f
}

private data class CachedDirectionArrowGeometry(
    val sourceSignature: Long,
    val zoom: Int,
    val tileSize: Int,
    val geometry: GpxDirectionArrowGeometry,
)
