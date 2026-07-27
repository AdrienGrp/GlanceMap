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

internal enum class FirstVisibleBaseTileSource(
    val telemetryToken: String,
) {
    WARM_CACHE("warm"),
    COLD_RENDER("cold"),
}

/** Reports when an exact tile from the visible viewport is first available to draw. */
internal class FirstVisibleTileRendererLayer(
    tileCache: TileCache,
    mapDataStore: MapDataStore,
    mapViewPosition: MapViewPosition,
    graphicFactory: GraphicFactory,
    hillsRenderConfig: HillsRenderConfig?,
    private val onFirstVisibleBaseTile: (
        layer: FirstVisibleTileRendererLayer,
        source: FirstVisibleBaseTileSource,
    ) -> Unit,
) : TileRendererLayer(
        tileCache,
        mapDataStore,
        mapViewPosition,
        false,
        true,
        false,
        graphicFactory,
        hillsRenderConfig,
    ) {
    private val firstVisibleTileReported = AtomicBoolean(false)
    private var hasDrawn = false

    override fun draw(
        boundingBox: BoundingBox,
        zoomLevel: Byte,
        canvas: Canvas,
        topLeftPoint: Point,
        rotation: Rotation,
    ) {
        val firstDraw = !hasDrawn
        val cachedBeforeDraw = hasCachedVisibleBaseTile(boundingBox, zoomLevel)
        hasDrawn = true

        super.draw(boundingBox, zoomLevel, canvas, topLeftPoint, rotation)

        val visibleTileReady = cachedBeforeDraw || hasCachedVisibleBaseTile(boundingBox, zoomLevel)
        if (visibleTileReady && firstVisibleTileReported.compareAndSet(false, true)) {
            onFirstVisibleBaseTile(
                this,
                if (firstDraw && cachedBeforeDraw) {
                    FirstVisibleBaseTileSource.WARM_CACHE
                } else {
                    FirstVisibleBaseTileSource.COLD_RENDER
                },
            )
        }
    }

    private fun hasCachedVisibleBaseTile(
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
