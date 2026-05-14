package com.glancemap.glancemapwearos.presentation.features.navigate.coverage

import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.Layer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class OfflineCoverageLayer(
    private val poiFillPaint: Paint,
    private val poiStrokePaint: Paint,
    private val routingFillPaint: Paint,
    private val routingStrokePaint: Paint,
) : Layer() {
    @Volatile
    var areas: List<OfflineMapCoverageArea> = emptyList()

    @Volatile private var cachedZoom: Byte = (-1).toByte()

    @Volatile private var cachedTileSize: Int = -1

    @Volatile private var cachedMapSize: Long = 0L

    override fun draw(
        boundingBox: BoundingBox,
        zoomLevel: Byte,
        canvas: Canvas,
        topLeft: Point,
        mapViewRotation: Rotation,
    ) {
        if (!isVisible) return
        val snapshot = areas
        if (snapshot.isEmpty()) return

        val mapSize = getCachedMapSize(zoomLevel, displayModel.tileSize)
        snapshot.forEach { area ->
            if (!area.intersects(boundingBox)) return@forEach
            drawArea(
                area = area,
                canvas = canvas,
                topLeft = topLeft,
                mapSize = mapSize,
            )
        }
    }

    private fun drawArea(
        area: OfflineMapCoverageArea,
        canvas: Canvas,
        topLeft: Point,
        mapSize: Long,
    ) {
        val bounds = area.bounds
        val x1 = MercatorProjection.longitudeToPixelX(bounds.minLon, mapSize) - topLeft.x
        val x2 = MercatorProjection.longitudeToPixelX(bounds.maxLon, mapSize) - topLeft.x
        val y1 = MercatorProjection.latitudeToPixelY(bounds.maxLat, mapSize) - topLeft.y
        val y2 = MercatorProjection.latitudeToPixelY(bounds.minLat, mapSize) - topLeft.y

        val left = min(x1, x2).roundToInt()
        val right = max(x1, x2).roundToInt()
        val top = min(y1, y2).roundToInt()
        val bottom = max(y1, y2).roundToInt()
        if (left == right || top == bottom) return

        val path =
            AndroidGraphicFactory.INSTANCE.createPath().apply {
                moveTo(left.toFloat(), top.toFloat())
                lineTo(right.toFloat(), top.toFloat())
                lineTo(right.toFloat(), bottom.toFloat())
                lineTo(left.toFloat(), bottom.toFloat())
                close()
            }

        val (fill, stroke) =
            when (area.kind) {
                OfflineMapCoverageKind.POI -> poiFillPaint to poiStrokePaint
                OfflineMapCoverageKind.ROUTING -> routingFillPaint to routingStrokePaint
            }
        canvas.drawPath(path, fill)
        canvas.drawPath(path, stroke)
    }

    private fun OfflineMapCoverageArea.intersects(boundingBox: BoundingBox): Boolean =
        bounds.maxLat >= boundingBox.minLatitude &&
            bounds.minLat <= boundingBox.maxLatitude &&
            bounds.maxLon >= boundingBox.minLongitude &&
            bounds.minLon <= boundingBox.maxLongitude

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
}
