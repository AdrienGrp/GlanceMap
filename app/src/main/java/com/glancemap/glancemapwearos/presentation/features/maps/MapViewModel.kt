package com.glancemap.glancemapwearos.presentation.features.maps

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glancemap.glancemapwearos.core.cache.AppDerivedCacheCleaner
import com.glancemap.glancemapwearos.core.cache.AppDerivedCacheCleanupResult
import com.glancemap.glancemapwearos.core.maps.Dem3CoverageUtils
import com.glancemap.glancemapwearos.core.routing.RoutingCoverageUtils
import com.glancemap.glancemapwearos.core.routing.isRoutingSegmentFileName
import com.glancemap.glancemapwearos.core.routing.routingSegmentPartFile
import com.glancemap.glancemapwearos.core.routing.routingSegmentsDir
import com.glancemap.glancemapwearos.core.service.diagnostics.MapHotPathDiagnostics
import com.glancemap.glancemapwearos.data.repository.MapRepository
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.data.repository.maps.theme.ThemeRepository
import com.glancemap.glancemapwearos.data.repository.maps.theme.ThemeSelection
import com.glancemap.glancemapwearos.domain.model.maps.theme.mapsforge.MapsforgeThemeCatalog
import com.glancemap.glancemapwearos.presentation.SyncManager
import com.glancemap.glancemapwearos.presentation.features.gpx.GpxTrackDetails
import com.glancemap.glancemapwearos.presentation.features.maps.theme.bundled.BundledAssetThemeComposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import java.io.File

data class MapFileState(
    val name: String,
    val path: String,
    val demCoverageKnown: Boolean = false,
    val demRequiredTiles: Int = 0,
    val demAvailableTiles: Int = 0,
    val demReady: Boolean = false,
    val routingCoverageKnown: Boolean = false,
    val routingRequiredSegments: Int = 0,
    val routingAvailableSegments: Int = 0,
    val routingReady: Boolean = false,
)

data class RoutingPackFileState(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
)

private data class OfflineViewportSnapshot(
    val contextKey: String,
    val center: org.mapsforge.core.model.LatLong,
    val zoomLevel: Int,
)

class MapViewModel(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val mapRepository: MapRepository,
    private val syncManager: SyncManager,
    private val themeRepository: ThemeRepository,
) : ViewModel() {
    companion object {
        private const val MAP_APPEARANCE_APPLY_INDICATOR_MIN_MS = 900L
        private const val INITIAL_MAP_LOAD_INDICATOR_MIN_MS = 1_400L
        private const val MAP_APPEARANCE_VISIBLE_TILE_TIMEOUT_MS = 4_500L
        private const val MAP_APPEARANCE_VISIBLE_TILE_SETTLE_MS = 220L
        private const val MAP_RENDERER_APPLY_DELAY_MS = 16L
        private const val INITIAL_THEME_PREWARM_DELAY_MS = 1500L
    }

    private val _mapFiles = MutableStateFlow<List<MapFileState>>(emptyList())
    val mapFiles: StateFlow<List<MapFileState>> = _mapFiles.asStateFlow()
    private val _reliefOverlayToggleEnabled = MutableStateFlow(false)
    val reliefOverlayToggleEnabled: StateFlow<Boolean> = _reliefOverlayToggleEnabled.asStateFlow()

    private val _routingPackFiles = MutableStateFlow<List<RoutingPackFileState>>(emptyList())
    val routingPackFiles: StateFlow<List<RoutingPackFileState>> = _routingPackFiles.asStateFlow()

    private val _mapAppearanceApplyInProgress = MutableStateFlow(false)
    val mapAppearanceApplyInProgress: StateFlow<Boolean> = _mapAppearanceApplyInProgress.asStateFlow()

    val selectedMapPath: StateFlow<String?> =
        settingsRepository.selectedMapPath
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var mapRenderer: MapRenderer? = null
    private val themeComposer = BundledAssetThemeComposer(context)
    private var latestThemeFile: File? = null
    private var latestMapsforgeThemeName: String? = null
    private var latestBundledThemeId: String = MapsforgeThemeCatalog.ELEVATE_THEME_ID
    private var latestHillShadingEnabled: Boolean = false
    private var latestReliefOverlayEnabled: Boolean = false
    private var latestIsMetric: Boolean = true
    private var themeRenderingDeferred: Boolean = false
    private var pendingThemeSelection: ThemeSelection? = null
    private var pendingThemeSelectionShowsIndicator: Boolean = false
    private var hasConsumedInitialThemeSelection: Boolean = false
    private var rendererConfigApplyPending: Boolean = false
    private var themeApplyJob: Job? = null
    private var themePrewarmJob: Job? = null
    private var rendererWorkJob: Job? = null
    private var rendererWorkGeneration: Long = 0L
    private var pendingMapLayerPath: String? = null
    private var pendingExternalCacheClear: Boolean = false
    private var lastPrewarmedBundledThemeId: String? = null

    private var mapHolder: MapHolder? = null
    private var latestZoomMin: Int? = null
    private var latestZoomMax: Int? = null
    private var initialMapLoadIndicatorPending: Boolean = true
    private var offlineStartCenterContextKey: String? = null
    private var offlineStartCenterApplied: Boolean = false
    private var offlineViewportSnapshot: OfflineViewportSnapshot? = null
    private var lastObservedSelectedMapPath: String? = null
    private var forcedOfflineStartCenterContextKey: String? = null

    init {
        loadMapFiles()
        loadRoutingPackFiles()

        selectedMapPath
            .onEach { newPath ->
                handleSelectedMapPathChanged(newPath)
                requestMapLayerUpdate(newPath)
            }.launchIn(viewModelScope)

        settingsRepository.isMetric
            .distinctUntilChanged()
            .onEach { isMetric ->
                latestIsMetric = isMetric
                if (isMapViewRenderReady()) {
                    mapRenderer?.setElevationLabelUnitsMetric(isMetric)
                } else {
                    rendererConfigApplyPending = true
                }
            }.launchIn(viewModelScope)

        syncManager.mapSyncRequest
            .onEach {
                loadMapFiles()
                loadRoutingPackFiles()
            }.launchIn(viewModelScope)

        viewModelScope.launch {
            themeRepository
                .getThemeSelection()
                .distinctUntilChanged()
                .collectLatest { selection ->
                    val showIndicator = hasConsumedInitialThemeSelection
                    hasConsumedInitialThemeSelection = true
                    if (!showIndicator) {
                        scheduleInitialBundledThemePrewarm(selection)
                    }
                    handleThemeSelection(
                        selection = selection,
                        showIndicator = showIndicator,
                    )
                }
        }
    }

    fun getOrCreateMapHolder(
        context: Context,
        zoomDefault: Int,
        zoomMin: Int,
        zoomMax: Int,
    ): MapHolder {
        latestZoomMin = zoomMin
        latestZoomMax = zoomMax

        mapHolder?.let { existing ->
            applyZoomBounds(
                mapView = existing.mapView,
                zoomMin = zoomMin,
                zoomMax = zoomMax,
                reason = "reuse_holder",
            )

            setMapRenderer(existing.renderer)
            return existing
        }

        val appContext = context.applicationContext
        AndroidGraphicFactory.createInstance(appContext)

        val mv =
            MapView(appContext).apply {
                isClickable = true
                isFocusable = true
                isFocusableInTouchMode = true

                applyZoomBounds(
                    mapView = this,
                    zoomMin = zoomMin,
                    zoomMax = zoomMax,
                    reason = "create_holder",
                )
                val normalizedDefault =
                    zoomDefault.coerceIn(
                        minOf(zoomMin, zoomMax),
                        maxOf(zoomMin, zoomMax),
                    )
                model.mapViewPosition.setZoomLevel(normalizedDefault.toByte(), false)

                setBuiltInZoomControls(false)
                mapScaleBar.isVisible = false
            }

        val renderer = MapRenderer(appContext, mv)
        val holder = MapHolder(mv, renderer)

        mapHolder = holder
        initialMapLoadIndicatorPending = true

        setMapRenderer(renderer)
        requestMapLayerUpdate(selectedMapPath.value)

        return holder
    }

    fun destroyMapHolder() {
        rendererWorkJob?.cancel()
        rendererWorkJob = null
        mapHolder?.renderer?.destroy()
        runCatching { mapHolder?.mapView?.destroyAll() }
        mapHolder = null
        mapRenderer = null
        latestZoomMin = null
        latestZoomMax = null
        initialMapLoadIndicatorPending = true
        resetOfflineStartCenterTracking()
    }

    fun shouldApplyOfflineStartCenter(
        selectedMapPath: String?,
        activeGpxDetails: List<GpxTrackDetails>,
    ): Boolean {
        val contextKey = buildOfflineStartCenterContextKey(selectedMapPath, activeGpxDetails)
        if (offlineStartCenterContextKey != contextKey) {
            offlineStartCenterContextKey = contextKey
            offlineStartCenterApplied = false
            if (selectedMapPath.isNullOrBlank() && activeGpxDetails.isNotEmpty()) {
                forcedOfflineStartCenterContextKey = contextKey
            }
        }
        return !offlineStartCenterApplied
    }

    fun markOfflineStartCenterHandled(
        selectedMapPath: String?,
        activeGpxDetails: List<GpxTrackDetails>,
    ) {
        offlineStartCenterContextKey =
            buildOfflineStartCenterContextKey(selectedMapPath, activeGpxDetails)
        offlineStartCenterApplied = true
    }

    fun resetOfflineStartCenterTracking() {
        offlineStartCenterContextKey = null
        offlineStartCenterApplied = false
        offlineViewportSnapshot = null
    }

    fun shouldForceOfflineStartCenter(
        selectedMapPath: String?,
        activeGpxDetails: List<GpxTrackDetails>,
    ): Boolean {
        val contextKey = buildOfflineStartCenterContextKey(selectedMapPath, activeGpxDetails)
        if (forcedOfflineStartCenterContextKey == contextKey) return true

        // In GPX-only offline mode, a newly active GPX should win over any previously saved viewport.
        if (selectedMapPath.isNullOrBlank() && activeGpxDetails.isNotEmpty()) {
            return offlineStartCenterContextKey != contextKey
        }

        return false
    }

    fun consumeForcedOfflineStartCenter(
        selectedMapPath: String?,
        activeGpxDetails: List<GpxTrackDetails>,
    ) {
        val contextKey = buildOfflineStartCenterContextKey(selectedMapPath, activeGpxDetails)
        if (forcedOfflineStartCenterContextKey == contextKey) {
            forcedOfflineStartCenterContextKey = null
        }
    }

    fun saveOfflineViewport(
        selectedMapPath: String?,
        activeGpxDetails: List<GpxTrackDetails>,
        center: org.mapsforge.core.model.LatLong?,
        zoomLevel: Int,
    ) {
        if (center == null) return
        if (!center.latitude.isFinite() || !center.longitude.isFinite()) return

        offlineViewportSnapshot =
            OfflineViewportSnapshot(
                contextKey = buildOfflineStartCenterContextKey(selectedMapPath, activeGpxDetails),
                center = center,
                zoomLevel = zoomLevel,
            )
    }

    fun restoreOfflineViewport(
        selectedMapPath: String?,
        activeGpxDetails: List<GpxTrackDetails>,
    ): Pair<org.mapsforge.core.model.LatLong, Int>? {
        val snapshot = offlineViewportSnapshot ?: return null
        val contextKey = buildOfflineStartCenterContextKey(selectedMapPath, activeGpxDetails)
        if (snapshot.contextKey != contextKey) return null
        return snapshot.center to snapshot.zoomLevel
    }

    fun getCurrentMapCenter(): org.mapsforge.core.model.LatLong? =
        mapHolder
            ?.mapView
            ?.model
            ?.mapViewPosition
            ?.center
            ?: offlineViewportSnapshot?.center

    private fun applyLatestZoomBounds(reason: String) {
        val zoomMin = latestZoomMin
        val zoomMax = latestZoomMax
        val mapView = mapHolder?.mapView
        if (zoomMin != null && zoomMax != null && mapView != null) {
            applyZoomBounds(
                mapView = mapView,
                zoomMin = zoomMin,
                zoomMax = zoomMax,
                reason = reason,
            )
        }
    }

    private fun applyZoomBounds(
        mapView: MapView,
        zoomMin: Int,
        zoomMax: Int,
        reason: String,
    ) {
        val boundedMin = zoomMin.coerceIn(0, Byte.MAX_VALUE.toInt())
        val boundedMax = zoomMax.coerceIn(0, Byte.MAX_VALUE.toInt())
        val effectiveMin = minOf(boundedMin, boundedMax)
        val effectiveMax = maxOf(boundedMin, boundedMax)
        val position = mapView.model.mapViewPosition
        val beforeMin = position.zoomLevelMin.toInt()
        val beforeMax = position.zoomLevelMax.toInt()
        val beforeZoom = position.zoomLevel.toInt()

        if (effectiveMin > beforeMax) {
            if (beforeMax != effectiveMax) {
                mapView.setZoomLevelMax(effectiveMax.toByte())
            }
            if (beforeMin != effectiveMin) {
                mapView.setZoomLevelMin(effectiveMin.toByte())
            }
        } else {
            if (beforeMin != effectiveMin) {
                mapView.setZoomLevelMin(effectiveMin.toByte())
            }
            if (position.zoomLevelMax.toInt() != effectiveMax) {
                mapView.setZoomLevelMax(effectiveMax.toByte())
            }
        }

        val clampedZoom = beforeZoom.coerceIn(effectiveMin, effectiveMax)
        if (clampedZoom != beforeZoom) {
            position.setZoomLevel(clampedZoom.toByte(), false)
        }

        val afterMin = position.zoomLevelMin.toInt()
        val afterMax = position.zoomLevelMax.toInt()
        if (beforeMin != afterMin || beforeMax != afterMax || beforeZoom != clampedZoom) {
            val beforeRange = "$beforeMin..$beforeMax"
            val afterRange = "$afterMin..$afterMax"
            Log.d(
                "MapZoom",
                "applyBounds reason=$reason requested=$zoomMin..$zoomMax effective=$effectiveMin..$effectiveMax " +
                    "mapViewBefore=$beforeRange zoom=$beforeZoom mapViewAfter=$afterRange zoom=$clampedZoom",
            )
        }
    }

    fun setMapRenderer(renderer: MapRenderer?) {
        mapRenderer = renderer
        applyRendererConfigIfReady()
        schedulePendingRendererWorkIfReady()
    }

    fun setThemeRenderingDeferred(deferred: Boolean) {
        if (themeRenderingDeferred == deferred) return
        themeRenderingDeferred = deferred
        if (!deferred) {
            submitPendingThemeSelectionIfReady()
        }
    }

    fun onMapViewReadyForRendering() {
        submitPendingThemeSelectionIfReady()
        if (pendingThemeSelection == null && themeApplyJob?.isActive != true && rendererConfigApplyPending) {
            applyRendererConfigIfReady()
        }
        schedulePendingRendererWorkIfReady()
    }

    fun loadMapFiles() {
        viewModelScope.launch {
            val files = mapRepository.listMapFiles()
            val states =
                withContext(Dispatchers.IO) {
                    files.map { file ->
                        val coverage = Dem3CoverageUtils.coverageForMap(context, file)
                        val routingCoverage = RoutingCoverageUtils.coverageForMap(context, file)
                        MapFileState(
                            name = file.name,
                            path = file.absolutePath,
                            demCoverageKnown = coverage.isCoverageKnown,
                            demRequiredTiles = coverage.requiredTiles,
                            demAvailableTiles = coverage.availableTiles,
                            demReady = coverage.isReady,
                            routingCoverageKnown = routingCoverage.isCoverageKnown,
                            routingRequiredSegments = routingCoverage.requiredSegments,
                            routingAvailableSegments = routingCoverage.availableSegments,
                            routingReady = routingCoverage.isReady,
                        )
                    }
                }
            _mapFiles.value = states

            val currentPath = selectedMapPath.value
            if (currentPath != null && files.none { it.absolutePath == currentPath }) {
                settingsRepository.setSelectedMapPath(null)
            }

            // Force refresh path/signature checks (handles file replacement with same path).
            requestMapLayerUpdate(selectedMapPath.value)
        }
    }

    fun loadRoutingPackFiles() {
        viewModelScope.launch {
            val states =
                withContext(Dispatchers.IO) {
                    routingSegmentsDir(context)
                        .listFiles()
                        ?.asSequence()
                        ?.filter { it.isFile && isRoutingSegmentFileName(it.name) }
                        ?.sortedBy { it.name.lowercase() }
                        ?.map { file ->
                            RoutingPackFileState(
                                name = file.name,
                                path = file.absolutePath,
                                sizeBytes = file.length(),
                                modifiedAtMillis = file.lastModified(),
                            )
                        }?.toList()
                        .orEmpty()
                }
            _routingPackFiles.value = states
        }
    }

    fun deleteMapFile(path: String) {
        viewModelScope.launch {
            val mapToDelete = File(path)
            val demTilesToDelete =
                withContext(Dispatchers.IO) {
                    val remaining =
                        mapRepository
                            .listMapFiles()
                            .filterNot { it.absolutePath == path }
                    Dem3CoverageUtils.tilesToDeleteForMap(mapToDelete, remaining)
                }

            if (mapRepository.deleteMapFile(path)) {
                withContext(Dispatchers.IO) {
                    Dem3CoverageUtils.deleteTiles(context, demTilesToDelete)
                }

                val removedSelected = selectedMapPath.value == path
                if (removedSelected) {
                    settingsRepository.setSelectedMapPath(null)
                }

                if (removedSelected) {
                    requestMapLayerUpdate(null)
                } else {
                    mapRenderer?.invalidateTileCache()
                }

                loadMapFiles()
            }
        }
    }

    fun deleteRoutingPackFile(path: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val file = File(path)
                if (file.exists() && isRoutingSegmentFileName(file.name)) {
                    file.delete()
                    routingSegmentPartFile(context, file.name).delete()
                }
            }
            RoutingCoverageUtils.clearCaches()
            loadRoutingPackFiles()
            loadMapFiles()
        }
    }

    fun deleteAllRoutingPackFiles() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                routingSegmentsDir(context)
                    .listFiles()
                    ?.filter { it.isFile && isRoutingSegmentFileName(it.name) }
                    ?.forEach { file ->
                        file.delete()
                        routingSegmentPartFile(context, file.name).delete()
                    }
            }
            RoutingCoverageUtils.clearCaches()
            loadRoutingPackFiles()
            loadMapFiles()
        }
    }

    fun renameMapFile(
        filePath: String,
        newName: String,
        onComplete: (Result<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching {
                        mapRepository.renameMapFile(path = filePath, newName = newName)
                    }
                }

            result.onSuccess { renamedFile ->
                val wasSelected = selectedMapPath.value == filePath
                if (wasSelected) {
                    settingsRepository.setSelectedMapPath(renamedFile.absolutePath)
                    requestMapLayerUpdate(renamedFile.absolutePath)
                } else {
                    mapRenderer?.invalidateTileCache()
                }
                loadMapFiles()
            }

            onComplete(result.map { })
        }
    }

    fun selectMapPath(path: String?) {
        viewModelScope.launch {
            settingsRepository.setSelectedMapPath(path)
        }
    }

    fun refreshMapLayer() {
        requestMapLayerUpdate(selectedMapPath.value)
    }

    suspend fun clearDerivedCaches(): AppDerivedCacheCleanupResult {
        val result =
            withContext(Dispatchers.IO) {
                AppDerivedCacheCleaner.clear(context)
            }
        Dem3CoverageUtils.clearCaches()
        RoutingCoverageUtils.clearCaches()
        requestExternalCacheClear()
        lastPrewarmedBundledThemeId = null
        loadMapFiles()
        return result
    }

    private suspend fun applyThemeSelection(
        selection: ThemeSelection,
        awaitVisibleContent: Boolean,
    ) {
        val timingMarker = MapHotPathDiagnostics.begin("mapViewModel.applyThemeSelection")
        var timingStatus = "ok"
        var themeApplyResult = MapRenderer.ThemeApplyResult()
        Log.d(
            "Theme",
            "Selection theme=${selection.themeId} mapsforge=${selection.mapsforgeThemeName} style=${selection.styleId} overlays=${selection.enabledOverlayLayerIds} hillShading=${selection.hillShadingEnabled} reliefOverlay=${selection.reliefOverlayEnabled}",
        )
        try {
            latestHillShadingEnabled = selection.hillShadingEnabled
            latestReliefOverlayEnabled = selection.reliefOverlayEnabled
            _reliefOverlayToggleEnabled.value = selection.reliefOverlayEnabled

            if (selection.mapsforgeThemeName != null) {
                latestThemeFile = null
                latestMapsforgeThemeName = selection.mapsforgeThemeName
                timingStatus = "mapsforge_theme"
            } else {
                val bundledThemeId = selection.themeId.takeIf { MapsforgeThemeCatalog.isBundledAssetTheme(it) }
                if (bundledThemeId == null) {
                    timingStatus = "invalid_bundled_theme"
                    Log.e("Theme", "Invalid bundled theme id in selection: ${selection.themeId}")
                    return
                }
                latestBundledThemeId = bundledThemeId
                latestThemeFile =
                    withContext(Dispatchers.IO) {
                        themeComposer.createDynamicThemeFileOrNull(
                            themeId = latestBundledThemeId,
                            styleId = selection.styleId,
                            enabledOverlayLayerIds = selection.enabledOverlayLayerIds,
                            hillShadingEnabled = selection.hillShadingEnabled,
                        )
                    }
                latestMapsforgeThemeName = null
                timingStatus =
                    if (latestThemeFile == null) {
                        "bundled_default_theme"
                    } else {
                        "bundled_dynamic_theme"
                    }
                Log.d(
                    "Theme",
                    "Theme file=${latestThemeFile?.absolutePath} len=${latestThemeFile?.length()} lm=${latestThemeFile?.lastModified()}",
                )
            }

            val renderer = mapRenderer
            themeApplyResult = applyRendererConfigIfReady()
            applyLatestZoomBounds(reason = "theme_selection")
            if (awaitVisibleContent &&
                themeApplyResult.requiresVisibleTileWait &&
                renderer != null
            ) {
                renderer.awaitTileCacheUpdateAfter(
                    baselineVersion = themeApplyResult.tileUpdateBaselineVersion,
                    timeoutMs = MAP_APPEARANCE_VISIBLE_TILE_TIMEOUT_MS,
                )
                delay(MAP_APPEARANCE_VISIBLE_TILE_SETTLE_MS)
            }
        } finally {
            MapHotPathDiagnostics.end(
                marker = timingMarker,
                status = timingStatus,
                detail =
                    buildString {
                        append("theme=").append(selection.themeId)
                        append(" mapsforge=").append(selection.mapsforgeThemeName != null)
                        append(" overlays=").append(selection.enabledOverlayLayerIds.size)
                        append(" hill=").append(selection.hillShadingEnabled)
                        append(" relief=").append(selection.reliefOverlayEnabled)
                        append(" night=").append(selection.nightModeEnabled)
                        append(" visibleWait=").append(themeApplyResult.requiresVisibleTileWait)
                    },
            )
        }
    }

    private fun handleThemeSelection(
        selection: ThemeSelection,
        showIndicator: Boolean,
    ) {
        if (themeRenderingDeferred) {
            pendingThemeSelection = selection
            pendingThemeSelectionShowsIndicator = showIndicator
            return
        }
        pendingThemeSelection = null
        pendingThemeSelectionShowsIndicator = false
        submitThemeSelection(
            selection = selection,
            showIndicator = showIndicator,
        )
    }

    private fun scheduleInitialBundledThemePrewarm(selection: ThemeSelection) {
        val bundledThemeId =
            selection.themeId
                .takeIf { MapsforgeThemeCatalog.isBundledAssetTheme(it) }
                ?: return
        if (lastPrewarmedBundledThemeId == bundledThemeId) return

        lastPrewarmedBundledThemeId = bundledThemeId
        themePrewarmJob?.cancel()
        themePrewarmJob =
            viewModelScope.launch(Dispatchers.IO) {
                delay(INITIAL_THEME_PREWARM_DELAY_MS)
                runCatching { themeComposer.prewarmThemeAssets(bundledThemeId) }
                    .onFailure { error ->
                        Log.w("Theme", "Failed to prewarm bundled theme assets for $bundledThemeId", error)
                    }
            }
    }

    private fun submitThemeSelection(
        selection: ThemeSelection,
        showIndicator: Boolean,
    ) {
        themeApplyJob?.cancel()
        themeApplyJob =
            viewModelScope.launch {
                val startedAtMs =
                    if (showIndicator) {
                        _mapAppearanceApplyInProgress.value = true
                        SystemClock.elapsedRealtime()
                    } else {
                        0L
                    }
                try {
                    applyThemeSelection(
                        selection = selection,
                        awaitVisibleContent = showIndicator,
                    )
                } finally {
                    if (showIndicator) {
                        val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
                        val remainingMs =
                            (MAP_APPEARANCE_APPLY_INDICATOR_MIN_MS - elapsedMs).coerceAtLeast(0L)
                        if (remainingMs > 0L) {
                            runCatching { delay(remainingMs) }
                        }
                        _mapAppearanceApplyInProgress.value = false
                    }
                }
            }
    }

    private fun submitPendingThemeSelectionIfReady() {
        if (themeRenderingDeferred) return
        if (!isMapViewRenderReady()) return
        val selection = pendingThemeSelection ?: return
        val showIndicator = pendingThemeSelectionShowsIndicator
        pendingThemeSelection = null
        pendingThemeSelectionShowsIndicator = false
        submitThemeSelection(
            selection = selection,
            showIndicator = showIndicator,
        )
    }

    private fun requestMapLayerUpdate(path: String?) {
        pendingMapLayerPath = path?.trim()?.takeIf { it.isNotEmpty() }
        rendererWorkGeneration += 1L
        schedulePendingRendererWorkIfReady()
    }

    private fun requestExternalCacheClear() {
        pendingExternalCacheClear = true
        rendererWorkGeneration += 1L
        schedulePendingRendererWorkIfReady()
    }

    private fun schedulePendingRendererWorkIfReady() {
        val renderer = mapRenderer ?: return
        if (!isMapViewRenderReady()) return

        val generation = rendererWorkGeneration
        rendererWorkJob?.cancel()
        rendererWorkJob =
            viewModelScope.launch {
                // Let the first frame settle before doing renderer work that can block.
                delay(MAP_RENDERER_APPLY_DELAY_MS)
                if (generation != rendererWorkGeneration) return@launch
                if (!isMapViewRenderReady()) return@launch
                if (mapRenderer !== renderer) return@launch
                val showInitialMapLoadIndicator =
                    initialMapLoadIndicatorPending &&
                        !pendingExternalCacheClear &&
                        !pendingMapLayerPath.isNullOrBlank()
                val indicatorStartedAtMs =
                    if (showInitialMapLoadIndicator) {
                        initialMapLoadIndicatorPending = false
                        _mapAppearanceApplyInProgress.value = true
                        SystemClock.elapsedRealtime()
                    } else {
                        0L
                    }

                try {
                    if (pendingExternalCacheClear) {
                        pendingExternalCacheClear = false
                        renderer.onExternalCachesCleared()
                        renderer.updateMapLayer(selectedMapPath.value)
                        applyLatestZoomBounds(reason = "external_cache_clear")
                    } else {
                        val tileBaselineVersion =
                            if (showInitialMapLoadIndicator) {
                                renderer.currentTileCacheUpdateVersion()
                            } else {
                                0L
                            }
                        renderer.updateMapLayer(pendingMapLayerPath)
                        applyLatestZoomBounds(reason = "map_layer_update")
                        if (showInitialMapLoadIndicator) {
                            renderer.awaitTileCacheUpdateAfter(
                                baselineVersion = tileBaselineVersion,
                                timeoutMs = MAP_APPEARANCE_VISIBLE_TILE_TIMEOUT_MS,
                            )
                            delay(MAP_APPEARANCE_VISIBLE_TILE_SETTLE_MS)
                        }
                    }
                } finally {
                    if (showInitialMapLoadIndicator) {
                        val elapsedMs = SystemClock.elapsedRealtime() - indicatorStartedAtMs
                        val remainingMs =
                            (INITIAL_MAP_LOAD_INDICATOR_MIN_MS - elapsedMs).coerceAtLeast(0L)
                        if (remainingMs > 0L) {
                            runCatching { delay(remainingMs) }
                        }
                        _mapAppearanceApplyInProgress.value = false
                    }
                }
            }
    }

    private fun applyRendererConfigIfReady(): MapRenderer.ThemeApplyResult {
        val renderer = mapRenderer
        if (renderer == null) {
            rendererConfigApplyPending = true
            return MapRenderer.ThemeApplyResult()
        }
        if (!isMapViewRenderReady()) {
            rendererConfigApplyPending = true
            return MapRenderer.ThemeApplyResult()
        }

        rendererConfigApplyPending = false
        renderer.setElevationLabelUnitsMetric(latestIsMetric)
        return renderer.setThemeConfig(
            themeFile = latestThemeFile,
            mapsforgeThemeName = latestMapsforgeThemeName,
            bundledThemeId = latestBundledThemeId,
            hillShadingEnabled = latestHillShadingEnabled,
            reliefOverlayEnabled = latestReliefOverlayEnabled,
        )
    }

    private fun isMapViewRenderReady(): Boolean {
        val mapView = mapHolder?.mapView ?: return false
        return mapView.isAttachedToWindow &&
            mapView.width > 0 &&
            mapView.height > 0 &&
            mapView.hasWindowFocus()
    }

    private fun handleSelectedMapPathChanged(newPath: String?) {
        val normalizedPath = newPath?.trim()?.takeIf { it.isNotEmpty() }
        if (lastObservedSelectedMapPath == normalizedPath) return

        val hadMeaningfulMapSelection =
            lastObservedSelectedMapPath != null || normalizedPath != null
        lastObservedSelectedMapPath = normalizedPath

        if (hadMeaningfulMapSelection) {
            resetOfflineStartCenterTracking()
        }
        forcedOfflineStartCenterContextKey = normalizedPath?.let { "map=$it" }
    }

    private fun buildOfflineStartCenterContextKey(
        selectedMapPath: String?,
        activeGpxDetails: List<GpxTrackDetails>,
    ): String {
        val normalizedMapPath = selectedMapPath?.trim().orEmpty()
        if (normalizedMapPath.isNotEmpty()) {
            return "map=$normalizedMapPath"
        }
        val gpxIds =
            activeGpxDetails
                .asSequence()
                .map { it.id }
                .sorted()
                .joinToString(separator = "|")
        return "gpx=$gpxIds"
    }
}
