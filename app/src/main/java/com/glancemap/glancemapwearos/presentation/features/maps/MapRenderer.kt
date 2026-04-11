package com.glancemap.glancemapwearos.presentation.features.maps

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.util.Log
import com.glancemap.glancemapwearos.core.maps.DemSignatureStore
import com.glancemap.glancemapwearos.core.service.diagnostics.MapHotPathDiagnostics
import com.glancemap.glancemapwearos.domain.model.maps.theme.mapsforge.MapsforgeThemeCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.datastore.MapDataStore
import org.mapsforge.map.layer.Layer
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.hills.DemFolderFS
import org.mapsforge.map.layer.hills.HillsRenderConfig
import org.mapsforge.map.layer.hills.MemoryCachingHgtReaderTileSource
import org.mapsforge.map.layer.hills.SimpleShadingAlgorithm
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.model.common.Observer as MapsforgeObserver
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.XmlRenderTheme
import java.io.File
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class MapRenderer(
    private val context: Context,
    private val mapView: MapView
) {

    private data class TileCacheConfig(
        val firstLevelTiles: Int,
        val memoryBudgetBytes: Long,
        val constrainedMemory: Boolean,
        val startupPrewarmEnabled: Boolean,
        val startupPrewarmZoomPlus: Int,
        val startupPrewarmZoomMinus: Int,
        val startupPrewarmTileMargin: Int,
        val startupPrewarmDurationMs: Long,
        val memoryClassMb: Int,
        val maxHeapBytes: Long
    )

    data class ThemeApplyResult(
        val requiresVisibleTileWait: Boolean = false,
        val tileUpdateBaselineVersion: Long = 0L
    )

    data class ReliefOverlayState(
        val enabled: Boolean,
        val processing: Boolean,
        val progressPercent: Int?
    )

    data class CacheDiagnosticsSnapshot(
        val activeTileCacheId: String?,
        val activeTileCacheLastUsedMs: Long?,
        val tileCacheBucketCount: Int,
        val tileCacheTotalSizeBytes: Long,
        val activeTileCacheSizeBytes: Long?,
        val lastCleanupMs: Long?,
        val reliefOverlayNamespaceCount: Int,
        val reliefOverlayCacheSizeBytes: Long,
        val bundledThemeCacheDirCount: Int,
        val bundledThemeCacheTotalSizeBytes: Long
    )

    companion object {
        private const val TAG = "MapRenderer"

        private const val FIRST_LEVEL_MIN_TILES = 64
        private const val FIRST_LEVEL_MAX_TILES = 256

        private const val MEMORY_BUDGET_FRACTION = 1.0 / 16.0
        private const val MEMORY_BUDGET_CAP_BYTES = 32L * 1024L * 1024L // 32MB
        private const val CONSTRAINED_MEMORY_CLASS_MB = 128
        private const val CONSTRAINED_MAX_HEAP_BYTES = 160L * 1024L * 1024L // 160MB
        private const val CONSTRAINED_FIRST_LEVEL_MIN_TILES = 24
        private const val CONSTRAINED_FIRST_LEVEL_MAX_TILES = 80
        private const val CONSTRAINED_MEMORY_BUDGET_FRACTION = 1.0 / 20.0
        private const val CONSTRAINED_MEMORY_BUDGET_CAP_BYTES = 8L * 1024L * 1024L // 8MB
        private const val DEM_DIRECTORY_NAME = "dem3"
        private const val DEM_SCAN_MAX_DEPTH = 6
        private const val STARTUP_PREWARM_ZOOM_STEPS = 2
        private const val STARTUP_PREWARM_TILE_MARGIN = 1
        private const val STARTUP_PREWARM_DURATION_MS = 8_000L
        private const val CONSTRAINED_STARTUP_PREWARM_ZOOM_STEPS = 2
        private const val CONSTRAINED_STARTUP_PREWARM_TILE_MARGIN = 0
        private const val CONSTRAINED_STARTUP_PREWARM_DURATION_MS = 4_000L

        fun captureCacheDiagnostics(context: Context): CacheDiagnosticsSnapshot {
            return captureMapRendererCacheDiagnostics(context)
        }
    }

    private var currentMapPath: String? = null
    private var currentMapSignature: String? = null
    private var currentThemeFile: File? = null
    private var currentMapsforgeThemeName: String? = null
    private var currentBundledThemeId: String = MapsforgeThemeCatalog.ELEVATE_THEME_ID
    private var currentHillShadingEnabled: Boolean = true
    private var currentReliefOverlayEnabled: Boolean = false
    private var currentElevationLabelsMetric: Boolean = true

    // Signature to detect changes even if same File path is reused
    private var currentThemeSignature: String = ""
    private var currentDemSignature: String? = null

    private val demRootDir: File by lazy {
        context.getExternalFilesDir(DEM_DIRECTORY_NAME)
            ?: File(context.getDir("maps", Context.MODE_PRIVATE), DEM_DIRECTORY_NAME)
    }
    private val reliefOverlayCacheRootDir: File by lazy {
        val root = context.externalCacheDir ?: context.cacheDir
        File(root, RELIEF_OVERLAY_CACHE_DIR_NAME)
    }
    private var hillsRenderConfig: HillsRenderConfig? = null
    private var hillsRenderConfigDemSignature: String? = null
    private var rebuildTileCacheRequested: Boolean = false
    private var currentTileCacheId: String = "$CACHE_ID_PREFIX-bootstrap"
    private var skipNextStartupTilePrewarm: Boolean = false
    @Volatile private var cacheCleanupInProgress: Boolean = false
    private val tileCacheUpdateCounter = AtomicLong(0L)
    private val tileCacheUpdateVersion = MutableStateFlow(0L)
    private val activityManager: ActivityManager? by lazy {
        context.getSystemService(ActivityManager::class.java)
    }
    private val tileCacheConfig: TileCacheConfig by lazy {
        buildTileCacheConfig()
    }
    private val tileCacheObserver = object : MapsforgeObserver {
        override fun onChange() {
            val nextVersion = tileCacheUpdateCounter.incrementAndGet()
            tileCacheUpdateVersion.value = nextVersion
        }
    }
    private val cacheMaintenancePrefs by lazy {
        context.getSharedPreferences(CACHE_CLEANUP_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var tileCache: TileCache = createTileCache(cacheId = currentTileCacheId)

    private fun createTileCache(cacheId: String): TileCache {
        val tileSize = mapView.model.displayModel.tileSize
        val config = tileCacheConfig

        val cache = AndroidUtil.createExternalStorageTileCache(
            context,
            cacheId,
            config.firstLevelTiles,
            tileSize,
            true
        )
        cache.addObserver(tileCacheObserver)
        Log.i(
            TAG,
            "createTileCache: cacheId=$cacheId tiles=${config.firstLevelTiles} " +
                "budgetMb=${"%.1f".format(Locale.US, config.memoryBudgetBytes / (1024f * 1024f))} " +
                "memoryClassMb=${config.memoryClassMb} " +
                "maxHeapMb=${"%.1f".format(Locale.US, config.maxHeapBytes / (1024f * 1024f))} " +
                "constrained=${config.constrainedMemory} " +
                "prewarm=${config.startupPrewarmEnabled} " +
                "prewarmZoom=${config.startupPrewarmZoomMinus}/${config.startupPrewarmZoomPlus} " +
                "prewarmMs=${config.startupPrewarmDurationMs}"
        )
        markMapRendererCacheBucketUsed(context, cacheMaintenancePrefs, cacheId)
        return cache
    }

    private fun buildTileCacheConfig(): TileCacheConfig {
        val tileSize = mapView.model.displayModel.tileSize
        val bytesPerPixel = when (AndroidGraphicFactory.INSTANCE.nonTransparentBitmapConfig) {
            android.graphics.Bitmap.Config.RGB_565 -> 2
            else -> 4
        }
        val approxTileBytes = tileSize.toLong() * tileSize.toLong() * bytesPerPixel.toLong()
        val maxHeap = Runtime.getRuntime().maxMemory()
        val memoryClassMb = activityManager?.memoryClass ?: 0
        val constrainedMemory =
            (memoryClassMb in 1..CONSTRAINED_MEMORY_CLASS_MB) ||
                maxHeap <= CONSTRAINED_MAX_HEAP_BYTES
        val memoryBudgetFraction = if (constrainedMemory) {
            CONSTRAINED_MEMORY_BUDGET_FRACTION
        } else {
            MEMORY_BUDGET_FRACTION
        }
        val memoryBudgetCapBytes = if (constrainedMemory) {
            CONSTRAINED_MEMORY_BUDGET_CAP_BYTES
        } else {
            MEMORY_BUDGET_CAP_BYTES
        }
        val memoryBudget = min((maxHeap * memoryBudgetFraction).toLong(), memoryBudgetCapBytes)
        val minTiles = if (constrainedMemory) {
            CONSTRAINED_FIRST_LEVEL_MIN_TILES
        } else {
            FIRST_LEVEL_MIN_TILES
        }
        val maxTiles = if (constrainedMemory) {
            CONSTRAINED_FIRST_LEVEL_MAX_TILES
        } else {
            FIRST_LEVEL_MAX_TILES
        }
        val computedTiles =
            if (approxTileBytes > 0) (memoryBudget / approxTileBytes).toInt() else minTiles
        val firstLevelTiles = computedTiles.coerceIn(minTiles, maxTiles)
        val startupPrewarmZoomSteps = if (constrainedMemory) {
            CONSTRAINED_STARTUP_PREWARM_ZOOM_STEPS
        } else {
            STARTUP_PREWARM_ZOOM_STEPS
        }
        val startupPrewarmTileMargin = if (constrainedMemory) {
            CONSTRAINED_STARTUP_PREWARM_TILE_MARGIN
        } else {
            STARTUP_PREWARM_TILE_MARGIN
        }
        val startupPrewarmDurationMs = if (constrainedMemory) {
            CONSTRAINED_STARTUP_PREWARM_DURATION_MS
        } else {
            STARTUP_PREWARM_DURATION_MS
        }
        return TileCacheConfig(
            firstLevelTiles = firstLevelTiles,
            memoryBudgetBytes = memoryBudget,
            constrainedMemory = constrainedMemory,
            startupPrewarmEnabled = true,
            startupPrewarmZoomPlus = startupPrewarmZoomSteps,
            startupPrewarmZoomMinus = startupPrewarmZoomSteps,
            startupPrewarmTileMargin = startupPrewarmTileMargin,
            startupPrewarmDurationMs = startupPrewarmDurationMs,
            memoryClassMb = memoryClassMb,
            maxHeapBytes = maxHeap
        )
    }

    private var currentLayer: TileRendererLayer? = null
    private var reliefOverlayLayer: ReliefOverlayLayer? = null
    private var liveElevationSampler: ReliefOverlayLayer? = null
    private var currentStore: MapDataStore? = null
    private val reliefOverlayStateListeners = CopyOnWriteArraySet<(ReliefOverlayState) -> Unit>()
    @Volatile
    private var lastPublishedReliefOverlayState: ReliefOverlayState? = null
    private val elevationLabelThemeCallback = ElevationLabelThemeCallback {
        currentElevationLabelsMetric
    }

    init {
        mapView.model.displayModel.backgroundColor = android.graphics.Color.BLACK
        mapView.model.displayModel.setThemeCallback(elevationLabelThemeCallback)
    }

    fun setThemeConfig(
        themeFile: File?,
        mapsforgeThemeName: String?,
        bundledThemeId: String,
        hillShadingEnabled: Boolean,
        reliefOverlayEnabled: Boolean
    ): ThemeApplyResult {
        val timingMarker = MapHotPathDiagnostics.begin("mapRenderer.setThemeConfig")
        var timingStatus = "ok"
        var usedLightweightReload = false
        var demChanged = false
        var themeApplyResult = ThemeApplyResult()
        val normalizedMapsforge = mapsforgeThemeName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.uppercase(Locale.ROOT)
        val normalizedBundledThemeId = if (MapsforgeThemeCatalog.isBundledAssetTheme(bundledThemeId)) {
            bundledThemeId
        } else {
            MapsforgeThemeCatalog.ELEVATE_THEME_ID
        }
        val reliefOverlayChanged = currentReliefOverlayEnabled != reliefOverlayEnabled
        val newSignature = computeMapRendererThemeSignature(
            file = themeFile,
            mapsforgeThemeName = normalizedMapsforge,
            bundledThemeId = normalizedBundledThemeId,
            hillShadingEnabled = hillShadingEnabled
        )
        // Avoid unnecessary work
        if (currentThemeSignature == newSignature) {
            timingStatus = if (reliefOverlayChanged) "relief_overlay_only" else "no_change"
            if (reliefOverlayChanged) {
                currentReliefOverlayEnabled = reliefOverlayEnabled
                updateReliefOverlayLayer()
                publishReliefOverlayState(force = true)
                forceRedraw()
            }
            MapHotPathDiagnostics.end(
                marker = timingMarker,
                status = timingStatus,
                detail = "mapsforge=${normalizedMapsforge != null} hill=$hillShadingEnabled reliefChanged=$reliefOverlayChanged"
            )
            return themeApplyResult
        }

        try {
            val theme = MapHotPathDiagnostics.measure(
                stage = "mapRenderer.buildRenderThemeOrNull",
                detail = "mapsforge=${normalizedMapsforge != null} bundled=$normalizedBundledThemeId"
            ) {
                buildMapRendererThemeOrNull(
                    context = context,
                    themeFile = themeFile,
                    mapsforgeThemeName = normalizedMapsforge,
                    bundledThemeId = normalizedBundledThemeId
                )
            } ?: run {
                timingStatus = "theme_unavailable"
                Log.w(TAG, "setThemeConfig: theme is null")
                return themeApplyResult
            }

            currentThemeFile = themeFile
            currentMapsforgeThemeName = normalizedMapsforge
            currentBundledThemeId = normalizedBundledThemeId
            currentHillShadingEnabled = hillShadingEnabled
            currentReliefOverlayEnabled = reliefOverlayEnabled
            currentThemeSignature = newSignature
            if (!currentHillShadingEnabled) {
                destroyHillsRenderConfig()
            }

            // Clear rendered tiles so new theme applies immediately.
            tileCache.tryPurge()

            val newDemSignature = computeDemSignatureOrNull()
            demChanged = newDemSignature != currentDemSignature
            val currentPath = currentMapPath
            if (currentPath.isNullOrBlank()) {
                timingStatus = "applied_without_map_reload"
                currentDemSignature = newDemSignature
                currentLayer?.setXmlRenderTheme(theme)
                updateReliefOverlayLayer()
                publishReliefOverlayState(force = true)
                forceRedraw()
                return themeApplyResult
            }

            timingStatus = if (demChanged) "full_reload_dem_changed" else "full_reload_theme_changed"
            skipNextStartupTilePrewarm = true
            // Mapsforge 0.27.0 showed incomplete viewport rendering when reusing the same
            // MapDataStore across TileRendererLayer theme swaps, so prefer a clean layer rebuild.
            rebuildTileCacheRequested = false
            themeApplyResult = ThemeApplyResult(
                requiresVisibleTileWait = true,
                tileUpdateBaselineVersion = tileCacheUpdateCounter.get()
            )
            forceReloadCurrentMapLayer(currentPath)
        } finally {
            MapHotPathDiagnostics.end(
                marker = timingMarker,
                status = timingStatus,
                detail = buildString {
                    append("mapsforge=").append(normalizedMapsforge != null)
                    append(" hill=").append(hillShadingEnabled)
                    append(" relief=").append(reliefOverlayEnabled)
                    append(" demChanged=").append(demChanged)
                    append(" lightweight=").append(usedLightweightReload)
                }
            )
        }
        return themeApplyResult
    }

    suspend fun awaitTileCacheUpdateAfter(
        baselineVersion: Long,
        timeoutMs: Long
    ): Boolean {
        if (tileCacheUpdateCounter.get() > baselineVersion) return true
        return withTimeoutOrNull(timeoutMs.coerceAtLeast(1L)) {
            tileCacheUpdateVersion.first { it > baselineVersion }
        } != null
    }

    fun currentTileCacheUpdateVersion(): Long = tileCacheUpdateCounter.get()

    fun setElevationLabelUnitsMetric(isMetric: Boolean) {
        if (currentElevationLabelsMetric == isMetric) return

        currentElevationLabelsMetric = isMetric
        mapView.model.displayModel.setThemeCallback(elevationLabelThemeCallback)
        rebuildTileCacheRequested = true
        skipNextStartupTilePrewarm = true
        updateMapLayer(currentMapPath)
    }

    fun updateMapLayer(mapPath: String?) {
        val timingMarker = MapHotPathDiagnostics.begin("mapRenderer.updateMapLayer")
        var timingStatus = "ok"
        var desiredCacheIdForTiming: String? = null
        var cacheRecreated = false
        var warmStartupCache = false
        val newMapSignature = computeMapRendererMapSignature(mapPath)
        val newDemSignature = computeDemSignatureOrNull()
        val desiredCacheId = resolveMapRendererDesiredCacheId(
            mapSignature = newMapSignature,
            demSignature = newDemSignature,
            hillShadingEnabled = currentHillShadingEnabled,
            elevationLabelsMetric = currentElevationLabelsMetric
        )
        desiredCacheIdForTiming = desiredCacheId
        if (
            mapPath == currentMapPath &&
            newMapSignature == currentMapSignature &&
            newDemSignature == currentDemSignature &&
            !rebuildTileCacheRequested &&
            desiredCacheId == currentTileCacheId
        ) {
            timingStatus = "no_change"
            MapHotPathDiagnostics.end(
                marker = timingMarker,
                status = timingStatus,
                detail = "cacheId=$desiredCacheId"
            )
            return
        }

        try {
            val cleared = clearCurrentLayer()
            if (rebuildTileCacheRequested || desiredCacheId != currentTileCacheId) {
                recreateTileCache(newCacheId = desiredCacheId)
                cacheRecreated = true
                rebuildTileCacheRequested = false
            }

            if (mapPath.isNullOrBlank()) {
                timingStatus = "map_disabled"
                currentMapPath = null
                currentMapSignature = null
                currentDemSignature = newDemSignature
                // Explicitly purge cache so disabling a map never leaves stale tiles visible.
                updateReliefOverlayLayer()
                tileCache.tryPurge()
                forceRedraw()
                return
            }

            val mapFile = File(mapPath)
            if (!mapFile.exists()) {
                timingStatus = "missing_map_file"
                currentMapPath = null
                currentMapSignature = null
                currentDemSignature = newDemSignature
                rebuildTileCacheRequested = true
                Log.w(TAG, "updateMapLayer: Map file does not exist: $mapPath")
                updateReliefOverlayLayer()
                tileCache.tryPurge()
                forceRedraw()
                return
            }

            val theme = MapHotPathDiagnostics.measure(
                stage = "mapRenderer.buildRenderThemeOrNull",
                detail = "mapsforge=${!currentMapsforgeThemeName.isNullOrBlank()} bundled=$currentBundledThemeId"
            ) {
                buildMapRendererThemeOrNull(
                    context = context,
                    themeFile = currentThemeFile,
                    mapsforgeThemeName = currentMapsforgeThemeName,
                    bundledThemeId = currentBundledThemeId
                )
            }
            if (theme == null) {
                timingStatus = "theme_unavailable"
                currentMapPath = null
                currentMapSignature = null
                currentDemSignature = newDemSignature
                rebuildTileCacheRequested = true
                Log.w(TAG, "updateMapLayer: theme is null, cannot render map.")
                updateReliefOverlayLayer()
                if (cleared) forceRedraw()
                return
            }

            val mapDataStore: MapDataStore = MapHotPathDiagnostics.measure(
                stage = "mapRenderer.openMapFile",
                detail = "file=${mapFile.name}"
            ) {
                MapFile(mapFile)
            }
            currentStore = mapDataStore
            warmStartupCache = tileCacheConfig.startupPrewarmEnabled && !skipNextStartupTilePrewarm
            val tileRendererLayer = createTileRendererLayer(
                mapDataStore = mapDataStore,
                theme = theme,
                demSignature = newDemSignature,
                warmStartupCache = warmStartupCache
            )
            skipNextStartupTilePrewarm = false
            currentLayer = tileRendererLayer
            mapView.layerManager.layers.add(0, tileRendererLayer)
            currentMapPath = mapPath
            currentMapSignature = newMapSignature
            currentDemSignature = newDemSignature
            updateReliefOverlayLayer()

            forceRedraw()
            timingStatus = "loaded"
        } catch (e: Exception) {
            timingStatus = "error_${e.javaClass.simpleName}"
            Log.e(TAG, "updateMapLayer: Error loading map file: $mapPath", e)
            clearCurrentLayer()
            currentMapPath = null
            currentMapSignature = null
            currentDemSignature = newDemSignature
            rebuildTileCacheRequested = true
            updateReliefOverlayLayer()
            tileCache.tryPurge()
            forceRedraw()
        } finally {
            MapHotPathDiagnostics.end(
                marker = timingMarker,
                status = timingStatus,
                detail = buildString {
                    append("mapPresent=").append(!mapPath.isNullOrBlank())
                    append(" cacheId=").append(desiredCacheIdForTiming)
                    append(" cacheRecreated=").append(cacheRecreated)
                    append(" warmStartupCache=").append(warmStartupCache)
                }
            )
        }
    }

    fun invalidateTileCache() {
        tileCache.tryPurge()
        forceRedraw()
    }

    fun onExternalCachesCleared() {
        clearCurrentLayer()
        destroyHillsRenderConfig()
        runCatching { tileCache.removeObserver(tileCacheObserver) }
        runCatching { tileCache.destroy() }
            .onFailure { Log.w(TAG, "onExternalCachesCleared: tileCache.destroy() failed", it) }
        currentTileCacheId = "$CACHE_ID_PREFIX-bootstrap"
        tileCache = createTileCache(cacheId = currentTileCacheId)
        rebuildTileCacheRequested = true
        currentDemSignature = null

        val mapPath = currentMapPath
        if (mapPath.isNullOrBlank()) {
            updateReliefOverlayLayer()
            publishReliefOverlayState(force = true)
            forceRedraw()
        } else {
            updateMapLayer(mapPath)
        }
    }

    fun isReliefOverlayEnabled(): Boolean {
        return computeReliefOverlayState().enabled
    }

    fun isReliefOverlayProcessing(): Boolean {
        return computeReliefOverlayState().processing
    }

    fun reliefOverlayProgressPercent(): Int? {
        return computeReliefOverlayState().progressPercent
    }

    fun addReliefOverlayStateListener(listener: (ReliefOverlayState) -> Unit) {
        reliefOverlayStateListeners.add(listener)
        publishReliefOverlayState(force = true)
    }

    fun removeReliefOverlayStateListener(listener: (ReliefOverlayState) -> Unit) {
        reliefOverlayStateListeners.remove(listener)
    }

    fun sampleElevationMeters(lat: Double, lon: Double): Double? {
        if (currentMapPath.isNullOrBlank()) return null
        val sampler = reliefOverlayLayer ?: getOrCreateLiveElevationSampler()
        return sampler?.sampleElevationMeters(lat, lon)
    }

    fun destroy() {
        clearCurrentLayer()
        destroyHillsRenderConfig()
        runCatching { tileCache.removeObserver(tileCacheObserver) }
        runCatching { tileCache.destroy() }
            .onFailure { Log.w(TAG, "destroy: tileCache.destroy() failed", it) }
        reliefOverlayStateListeners.clear()
        lastPublishedReliefOverlayState = null
    }

    private fun clearCurrentLayer(): Boolean {
        var removed = false
        val storeOwnedByCurrentLayer = currentLayer?.mapDataStore === currentStore

        currentLayer?.let { layer ->
            removed = mapView.layerManager.layers.remove(layer)
            runCatching { layer.onDestroy() }
                .onFailure { Log.w(TAG, "clearCurrentLayer: Failed to destroy TileRendererLayer", it) }
        }
        currentLayer = null

        reliefOverlayLayer?.let { layer ->
            mapView.layerManager.layers.remove(layer)
            runCatching { layer.onDestroy() }
        }
        reliefOverlayLayer = null

        liveElevationSampler?.let { sampler ->
            runCatching { sampler.onDestroy() }
        }
        liveElevationSampler = null

        if (!storeOwnedByCurrentLayer) {
            currentStore?.let { store ->
                runCatching { store.close() }
                    .onFailure { e -> Log.w(TAG, "clearCurrentLayer: Failed to close MapDataStore", e) }
            }
        }
        currentStore = null
        publishReliefOverlayState(force = true)

        return removed
    }

    private fun forceRedraw() {
        val timingMarker = MapHotPathDiagnostics.begin("mapRenderer.forceRedraw")
        var timingStatus = "redraw_layers"
        try {
            mapView.layerManager.redrawLayers()
        } catch (_: Throwable) {
            timingStatus = "post_invalidate_fallback"
            mapView.postInvalidate()
        } finally {
            MapHotPathDiagnostics.end(
                marker = timingMarker,
                status = timingStatus
            )
        }
    }

    private fun forceReloadCurrentMapLayer(mapPath: String) {
        currentMapPath = null
        currentMapSignature = null
        currentDemSignature = null
        updateMapLayer(mapPath)
    }

    private fun rebuildCurrentLayerWithExistingStore(
        theme: XmlRenderTheme,
        demSignature: String?
    ): Boolean {
        val timingMarker = MapHotPathDiagnostics.begin("mapRenderer.rebuildCurrentLayerWithExistingStore")
        var timingStatus = "ok"
        val mapDataStore = currentStore ?: return false
        return runCatching {
            currentLayer?.let { layer ->
                mapView.layerManager.layers.remove(layer)
                releaseTileRendererLayerForStoreReuse(layer)
            }
            currentLayer = null

            val tileRendererLayer = createTileRendererLayer(
                mapDataStore = mapDataStore,
                theme = theme,
                demSignature = demSignature,
                warmStartupCache = false
            )
            currentLayer = tileRendererLayer
            mapView.layerManager.layers.add(0, tileRendererLayer)
            currentDemSignature = demSignature
            updateReliefOverlayLayer()
            publishReliefOverlayState(force = true)
            forceRedraw()
            timingStatus = "lightweight_reload"
            true
        }.getOrElse { error ->
            timingStatus = "error_${error.javaClass.simpleName}"
            Log.w(TAG, "rebuildCurrentLayerWithExistingStore: lightweight theme reload failed", error)
            false
        }.also {
            MapHotPathDiagnostics.end(
                marker = timingMarker,
                status = timingStatus,
                detail = "demPresent=${demSignature != null}"
            )
        }
    }

    private fun releaseTileRendererLayerForStoreReuse(layer: TileRendererLayer) {
        runCatching {
            layer.renderThemeFuture?.decrementRefCount()
        }.onFailure { error ->
            Log.w(TAG, "releaseTileRendererLayerForStoreReuse: Failed to release render theme", error)
        }
    }

    private fun armStartupTilePrewarm(layer: TileRendererLayer) {
        val config = tileCacheConfig
        if (!config.startupPrewarmEnabled) return
        if (config.startupPrewarmZoomPlus <= 0 &&
            config.startupPrewarmZoomMinus <= 0 &&
            config.startupPrewarmTileMargin <= 0
        ) {
            return
        }

        // Warm adjacent zoom levels once on startup so first manual zoom feels immediate.
        layer.setCacheZoomPlus(config.startupPrewarmZoomPlus)
        layer.setCacheZoomMinus(config.startupPrewarmZoomMinus)
        layer.setCacheTileMargin(config.startupPrewarmTileMargin)

        mapView.postDelayed(
            {
                if (currentLayer !== layer) return@postDelayed
                layer.setCacheZoomPlus(0)
                layer.setCacheZoomMinus(0)
                layer.setCacheTileMargin(0)
            },
            config.startupPrewarmDurationMs
        )
    }

    private fun buildHillsRenderConfigOrNull(demSignature: String?): HillsRenderConfig? {
        val timingMarker = MapHotPathDiagnostics.begin("mapRenderer.buildHillsRenderConfigOrNull")
        var timingStatus = "ok"
        return try {
            if (!currentHillShadingEnabled) {
                timingStatus = "hill_shading_disabled"
                return null
            }
            if (demSignature == null) {
                timingStatus = "missing_dem"
                Log.d(
                    TAG,
                    "Hill shading enabled but no DEM files found in ${demRootDir.absolutePath}."
                )
                destroyHillsRenderConfig()
                return null
            }
            hillsRenderConfig?.let { existing ->
                if (hillsRenderConfigDemSignature == demSignature) {
                    timingStatus = "reuse_cached_config"
                    return existing
                }
            }

            destroyHillsRenderConfig()

            val config = runCatching {
                val demFolder = DemFolderFS(demRootDir)
                val tileSource = MemoryCachingHgtReaderTileSource(
                    demFolder,
                    SimpleShadingAlgorithm(),
                    AndroidGraphicFactory.INSTANCE
                )
                HillsRenderConfig(tileSource)
                    .setMagnitudeScaleFactor(1f)
                    .indexOnThread()
            }.getOrElse { e ->
                timingStatus = "error_${e.javaClass.simpleName}"
                Log.w(TAG, "Failed to initialize DEM hillshading from ${demRootDir.absolutePath}", e)
                return null
            }

            timingStatus = "built_new_config"
            hillsRenderConfig = config
            hillsRenderConfigDemSignature = demSignature
            config
        } finally {
            MapHotPathDiagnostics.end(
                marker = timingMarker,
                status = timingStatus,
                detail = "demPresent=${demSignature != null}"
            )
        }
    }

    private fun createTileRendererLayer(
        mapDataStore: MapDataStore,
        theme: XmlRenderTheme,
        demSignature: String?,
        warmStartupCache: Boolean
    ): TileRendererLayer {
        return MapHotPathDiagnostics.measure(
            stage = "mapRenderer.createTileRendererLayer",
            detail = "warmStartupCache=$warmStartupCache demPresent=${demSignature != null}"
        ) {
            val hillsConfig = buildHillsRenderConfigOrNull(demSignature)
            TileRendererLayer(
                tileCache,
                mapDataStore,
                mapView.model.mapViewPosition,
                false,
                true,
                false,
                AndroidGraphicFactory.INSTANCE,
                hillsConfig
            ).apply {
                setXmlRenderTheme(theme)
                trySetThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
                if (warmStartupCache) {
                    armStartupTilePrewarm(this)
                }
            }
        }
    }

    private fun destroyHillsRenderConfig() {
        hillsRenderConfig?.interruptAndDestroy()
        hillsRenderConfig = null
        hillsRenderConfigDemSignature = null
    }

    private fun recreateTileCache(newCacheId: String) {
        val timingMarker = MapHotPathDiagnostics.begin("mapRenderer.recreateTileCache")
        try {
            runCatching { tileCache.removeObserver(tileCacheObserver) }
            runCatching { tileCache.destroy() }
                .onFailure { Log.w(TAG, "recreateTileCache: failed to destroy previous cache", it) }
            currentTileCacheId = newCacheId
            tileCache = createTileCache(cacheId = currentTileCacheId)
            maybeCleanupPersistentCachesAsync()
        } finally {
            MapHotPathDiagnostics.end(
                marker = timingMarker,
                detail = "cacheId=$newCacheId"
            )
        }
    }

    private fun maybeCleanupPersistentCachesAsync() {
        val cacheRoot = context.externalCacheDir ?: return
        if (!cacheRoot.exists() || !cacheRoot.isDirectory) return

        val now = System.currentTimeMillis()
        val lastCleanupMs = cacheMaintenancePrefs.getLong(KEY_CACHE_LAST_CLEANUP_MS, 0L)
        if ((now - lastCleanupMs) < CACHE_CLEANUP_INTERVAL_MS) return
        if (cacheCleanupInProgress) return

        cacheCleanupInProgress = true
        Thread(
            {
                try {
                    cleanupMapRendererPersistentCacheBuckets(
                        cacheRoot = cacheRoot,
                        nowMs = now,
                        keepIds = setOf(currentTileCacheId)
                    )
                    cacheMaintenancePrefs.edit()
                        .putLong(KEY_CACHE_LAST_CLEANUP_MS, now)
                        .apply()
                } catch (e: Exception) {
                    Log.w(TAG, "Cache cleanup failed", e)
                } finally {
                    cacheCleanupInProgress = false
                }
            },
            "MapCacheCleanup"
        ).start()
    }

    private fun computeDemSignatureOrNull(): String? {
        if (!currentHillShadingEnabled && !currentReliefOverlayEnabled) return null
        return DemSignatureStore.resolveSignature(
            context = context,
            demRootDir = demRootDir,
            maxDepth = DEM_SCAN_MAX_DEPTH
        )
    }

    private fun updateReliefOverlayLayer() {
        if (!currentReliefOverlayEnabled || currentMapPath.isNullOrBlank()) {
            reliefOverlayLayer?.let { existing ->
                mapView.layerManager.layers.remove(existing)
                runCatching { existing.onDestroy() }
            }
            reliefOverlayLayer = null
            publishReliefOverlayState(force = true)
            return
        }

        if (reliefOverlayLayer == null) {
            val cacheNamespace = resolveMapRendererReliefOverlayCacheNamespace(currentDemSignature)
            reliefOverlayLayer = ReliefOverlayLayer(
                demRootDir = demRootDir,
                diskCacheRootDir = reliefOverlayCacheRootDir,
                cacheNamespace = cacheNamespace,
                onProcessingStateChanged = { publishReliefOverlayState() }
            ).also { layer ->
                // Keep above rendered map but below interactive overlays.
                val index = if (mapView.layerManager.layers.size() > 0) 1 else 0
                mapView.layerManager.layers.add(index, layer)
            }
        }
        publishReliefOverlayState(force = true)
    }

    private fun getOrCreateLiveElevationSampler(): ReliefOverlayLayer? {
        if (liveElevationSampler == null) {
            liveElevationSampler = ReliefOverlayLayer(demRootDir = demRootDir)
        }
        return liveElevationSampler
    }

    private fun TileRendererLayer.trySetThreadPriority(priority: Int) {
        runCatching {
            val m = this.javaClass.getMethod("setThreadPriority", Int::class.javaPrimitiveType)
            m.invoke(this, priority)
        }.onFailure {
            Log.d(TAG, "TileRendererLayer.setThreadPriority not available, skipping")
        }
    }

    private fun TileCache.tryPurge() {
        // Some Mapsforge versions have purge(), others don’t. Reflection keeps you safe.
        runCatching {
            val m = this.javaClass.getMethod("purge")
            m.invoke(this)
        }.onFailure {
            // if purge doesn't exist, it's fine (still redraw, but may show cached tiles briefly)
            Log.d(TAG, "TileCache.purge not available, skipping")
        }
    }

    private fun computeReliefOverlayState(): ReliefOverlayState {
        val enabled = currentReliefOverlayEnabled && !currentMapPath.isNullOrBlank()
        if (!enabled) {
            return ReliefOverlayState(
                enabled = false,
                processing = false,
                progressPercent = null
            )
        }
        return ReliefOverlayState(
            enabled = true,
            processing = reliefOverlayLayer?.isProcessing() == true,
            progressPercent = reliefOverlayLayer?.progressPercent()
        )
    }

    private fun publishReliefOverlayState(force: Boolean = false) {
        val listeners = reliefOverlayStateListeners.toList()
        if (listeners.isEmpty()) return

        val state = computeReliefOverlayState()
        if (!force && state == lastPublishedReliefOverlayState) return
        lastPublishedReliefOverlayState = state

        mapView.post {
            listeners.forEach { listener ->
                runCatching { listener(state) }
            }
        }
    }
}
