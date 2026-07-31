package com.glancemap.glancemapwearos.presentation.features.maps

import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.model.Tile
import org.mapsforge.map.datastore.MapDataStore
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.hills.HillsRenderConfig
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.model.MapViewPosition
import org.mapsforge.map.util.LayerUtil
import java.util.concurrent.atomic.AtomicBoolean

internal data class HillshadeLayerCallbacks(
    val onWorkStarted: (
        FirstVisibleHillshadeTileRendererLayer,
        zoomLevel: Byte,
        visibleTileCount: Int,
        terrainCoverage: VisibleHillshadeTerrainCoverage,
    ) -> Unit,
    val onWorkPaused: (FirstVisibleHillshadeTileRendererLayer, reason: String) -> Unit,
    val resolveVisibleTerrainCoverage: (BoundingBox) -> VisibleHillshadeTerrainCoverage,
    val onTerrainUnavailable: (
        FirstVisibleHillshadeTileRendererLayer,
        zoomLevel: Byte,
        terrainCoverage: VisibleHillshadeTerrainCoverage,
    ) -> Unit,
    val onFirstVisibleTile: (FirstVisibleHillshadeTileRendererLayer) -> Unit,
)

/** Reports when the external hillshade layer has produced its first visible transparent tile. */
internal class FirstVisibleHillshadeTileRendererLayer(
    tileCache: TileCache,
    mapDataStore: MapDataStore,
    mapViewPosition: MapViewPosition,
    graphicFactory: GraphicFactory,
    hillsRenderConfig: HillsRenderConfig,
    private val callbacks: HillshadeLayerCallbacks,
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
        if (!shouldRenderHillshadeAtZoom(zoomLevel)) {
            if (
                !firstVisibleTileReported.get() &&
                visibleWorkStarted.compareAndSet(true, false)
            ) {
                callbacks.onWorkPaused(this, "below_min_zoom")
            }
            return
        }

        val terrainCoverage = callbacks.resolveVisibleTerrainCoverage(boundingBox)
        if (!terrainCoverage.hasAnyTerrain) {
            if (
                !firstVisibleTileReported.get() &&
                visibleWorkStarted.compareAndSet(true, false)
            ) {
                callbacks.onWorkPaused(this, "missing_visible_dem")
            }
            if (lastUnavailableTerrainKey != terrainCoverage.diagnosticKey) {
                lastUnavailableTerrainKey = terrainCoverage.diagnosticKey
                callbacks.onTerrainUnavailable(this, zoomLevel, terrainCoverage)
            }
            return
        }
        lastUnavailableTerrainKey = null

        val visibleTiles = visibleTiles(boundingBox, zoomLevel)
        if (
            !firstVisibleTileReported.get() &&
            visibleWorkStarted.compareAndSet(false, true)
        ) {
            callbacks.onWorkStarted(this, zoomLevel, visibleTiles.size, terrainCoverage)
        }

        super.draw(boundingBox, zoomLevel, canvas, topLeftPoint, rotation)

        if (
            !firstVisibleTileReported.get() &&
            hasCachedVisibleHillshadeTile(visibleTiles) &&
            firstVisibleTileReported.compareAndSet(false, true)
        ) {
            callbacks.onFirstVisibleTile(this)
        }
    }

    private val visibleWorkStarted = AtomicBoolean(false)
    private var lastUnavailableTerrainKey: String? = null

    private fun visibleTiles(
        boundingBox: BoundingBox,
        zoomLevel: Byte,
    ): Set<Tile> =
        runCatching {
            val tileSize = displayModel?.tileSize ?: return@runCatching emptySet()
            LayerUtil.getTiles(boundingBox, zoomLevel, tileSize)
        }.getOrDefault(emptySet())

    private fun hasCachedVisibleHillshadeTile(visibleTiles: Set<Tile>): Boolean {
        if (renderThemeFuture == null) return false
        return visibleTiles.any { tile -> tileCache.containsKey(createJob(tile)) }
    }
}
