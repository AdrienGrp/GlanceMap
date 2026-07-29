package com.glancemap.glancemapwearos.presentation.features.maps

import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.map.datastore.MapDataStore
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.hills.HillsRenderConfig
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.model.MapViewPosition
import org.mapsforge.map.util.LayerUtil
import java.util.concurrent.atomic.AtomicBoolean

/** Reports when the external hillshade layer has produced its first visible transparent tile. */
internal class FirstVisibleHillshadeTileRendererLayer(
    tileCache: TileCache,
    mapDataStore: MapDataStore,
    mapViewPosition: MapViewPosition,
    graphicFactory: GraphicFactory,
    hillsRenderConfig: HillsRenderConfig,
    private val onFirstVisibleHillshadeTile: (FirstVisibleHillshadeTileRendererLayer) -> Unit,
) : TileRendererLayer(
        tileCache,
        mapDataStore,
        mapViewPosition,
        true,
        false,
        false,
        graphicFactory,
        hillsRenderConfig,
    ) {
    private val firstVisibleTileReported = AtomicBoolean(false)

    override fun draw(
        boundingBox: BoundingBox,
        zoomLevel: Byte,
        canvas: Canvas,
        topLeftPoint: Point,
        rotation: Rotation,
    ) {
        super.draw(boundingBox, zoomLevel, canvas, topLeftPoint, rotation)

        if (
            !firstVisibleTileReported.get() &&
            hasCachedVisibleHillshadeTile(boundingBox, zoomLevel) &&
            firstVisibleTileReported.compareAndSet(false, true)
        ) {
            onFirstVisibleHillshadeTile(this)
        }
    }

    private fun hasCachedVisibleHillshadeTile(
        boundingBox: BoundingBox,
        zoomLevel: Byte,
    ): Boolean =
        runCatching {
            if (renderThemeFuture == null) return@runCatching false
            val tileSize = displayModel?.tileSize ?: return@runCatching false
            LayerUtil
                .getTiles(boundingBox, zoomLevel, tileSize)
                .any { tile -> tileCache.containsKey(createJob(tile)) }
        }.getOrDefault(false)
}
