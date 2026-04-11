package com.glancemap.glancemapwearos.presentation.features.gpx

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glancemap.glancemapwearos.core.gpx.GpxElevationFilterConfig
import com.glancemap.glancemapwearos.core.gpx.GpxElevationFilterDefaults
import com.glancemap.glancemapwearos.core.routing.RoutePlanner
import com.glancemap.glancemapwearos.data.repository.GpxRepository
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.SyncManager
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolCreatePreview
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolKind
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolModifyPreview
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolSaveResult
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mapsforge.core.model.LatLong
import java.io.File

class GpxViewModel(
    private val gpxRepository: GpxRepository,
    private val syncManager: SyncManager,
    private val settingsRepository: SettingsRepository,
    private val routePlanner: RoutePlanner,
) : ViewModel() {
    private val _gpxFiles = MutableStateFlow<List<GpxFileState>>(emptyList())
    val gpxFiles: StateFlow<List<GpxFileState>> = _gpxFiles.asStateFlow()

    private val _activeGpxDetails = MutableStateFlow<List<GpxTrackDetails>>(emptyList())
    val activeGpxDetails: StateFlow<List<GpxTrackDetails>> = _activeGpxDetails.asStateFlow()

    private val _inspectionUiState = MutableStateFlow<GpxInspectionUiState?>(null)
    val inspectionUiState: StateFlow<GpxInspectionUiState?> = _inspectionUiState.asStateFlow()

    private val _selectedPointA = MutableStateFlow<LatLong?>(null)
    val selectedPointA: StateFlow<LatLong?> = _selectedPointA.asStateFlow()

    private val _selectedPointB = MutableStateFlow<LatLong?>(null)
    val selectedPointB: StateFlow<LatLong?> = _selectedPointB.asStateFlow()

    private val _elevationProfileUiState = MutableStateFlow<GpxElevationProfileUiState?>(null)
    val elevationProfileUiState: StateFlow<GpxElevationProfileUiState?> =
        _elevationProfileUiState.asStateFlow()

    private val _showLongPressTip = MutableStateFlow(false)
    val showLongPressTip: StateFlow<Boolean> = _showLongPressTip.asStateFlow()

    // ----------------------------
    // Internal inspection session state
    // ----------------------------
    private var aPos: TrackPosition? = null
    private var bPos: TrackPosition? = null
    private var selectingB: Boolean = false
    private var selectBTimeoutJob: Job? = null

    // ✅ Delay popup so user sees the yellow dot first
    private var popupDelayJob: Job? = null
    private val popupDelayMs = 1_000L

    // ----------------------------
    // CACHES
    // ----------------------------
    private data class CachedMeta(
        val sig: FileSig,
        val title: String?,
        val distance: Double,
        val elevationGain: Double,
        val elevationLoss: Double,
    )

    private data class CachedEta(
        val sig: FileSig,
        val modelConfig: GpxEtaModelConfig,
        val projection: GpxEtaProjection?,
    )

    private val metaCache = LinkedHashMap<String, CachedMeta>(64, 0.75f, true)
    private val profileCache = LinkedHashMap<String, TrackProfile>(16, 0.75f, true)
    private val etaCache = LinkedHashMap<String, CachedEta>(16, 0.75f, true)

    private val maxMetaCacheEntries = 128
    private val maxProfileCacheEntries = 24
    private var etaModelConfig =
        GpxEtaModelConfig(
            flatSpeedMps = SettingsRepository.DEFAULT_GPX_FLAT_SPEED_MPS.toDouble(),
            advancedVerticalRateEnabled = SettingsRepository.DEFAULT_GPX_ADVANCED_ETA_ENABLED,
            uphillVerticalMetersPerHour = SettingsRepository.DEFAULT_GPX_UPHILL_VERTICAL_METERS_PER_HOUR.toDouble(),
            downhillVerticalMetersPerHour = SettingsRepository.DEFAULT_GPX_DOWNHILL_VERTICAL_METERS_PER_HOUR.toDouble(),
        )
    private var elevationFilterConfig = GpxElevationFilterDefaults.defaultConfig()
    private val routeToolOperations =
        GpxRouteToolOperations(
            gpxRepository = gpxRepository,
            routePlanner = routePlanner,
            activeGpxFiles = { _gpxFiles.value },
            elevationFilterConfig = { elevationFilterConfig },
            etaModelConfig = { etaModelConfig },
        )

    // Require press to be close to route
    private val pressThresholdMeters = 30.0

    init {
        viewModelScope.launch {
            _showLongPressTip.value = !settingsRepository.gpxLongPressTipShown.first()
        }

        combine(
            settingsRepository.gpxFlatSpeedMps,
            settingsRepository.gpxAdvancedEtaEnabled,
            settingsRepository.gpxUphillVerticalMetersPerHour,
            settingsRepository.gpxDownhillVerticalMetersPerHour,
        ) { flatSpeedMps, advancedEnabled, uphillMetersPerHour, downhillMetersPerHour ->
            GpxEtaModelConfig(
                flatSpeedMps =
                    flatSpeedMps
                        .toDouble()
                        .coerceIn(0.0, SettingsRepository.MAX_GPX_FLAT_SPEED_MPS.toDouble()),
                advancedVerticalRateEnabled = advancedEnabled,
                uphillVerticalMetersPerHour =
                    uphillMetersPerHour
                        .toDouble()
                        .coerceIn(
                            SettingsRepository.MIN_GPX_VERTICAL_METERS_PER_HOUR.toDouble(),
                            SettingsRepository.MAX_GPX_UPHILL_VERTICAL_METERS_PER_HOUR.toDouble(),
                        ),
                downhillVerticalMetersPerHour =
                    downhillMetersPerHour
                        .toDouble()
                        .coerceIn(
                            SettingsRepository.MIN_GPX_VERTICAL_METERS_PER_HOUR.toDouble(),
                            SettingsRepository.MAX_GPX_DOWNHILL_VERTICAL_METERS_PER_HOUR.toDouble(),
                        ),
            )
        }.onEach { config ->
            if (config == etaModelConfig) return@onEach

            etaModelConfig = config
            etaCache.clear()

            reloadFromDisk()
            refreshOpenEtaUi()
        }.launchIn(viewModelScope)

        combine(
            settingsRepository.gpxElevationSmoothingDistanceMeters,
            settingsRepository.gpxElevationNeutralDiffThresholdMeters,
            settingsRepository.gpxElevationTrendActivationThresholdMeters,
            settingsRepository.gpxElevationAutoAdjustPerGpx,
        ) { smoothingDistanceMeters, neutralDiffThresholdMeters, trendActivationThresholdMeters, autoAdjustPerGpx ->
            GpxElevationFilterDefaults.sanitize(
                GpxElevationFilterConfig(
                    smoothingDistanceMeters = smoothingDistanceMeters,
                    neutralDiffThresholdMeters = neutralDiffThresholdMeters,
                    trendActivationThresholdMeters = trendActivationThresholdMeters,
                    autoAdjustPerGpx = autoAdjustPerGpx,
                ),
            )
        }.onEach { config ->
            if (config == elevationFilterConfig) return@onEach

            elevationFilterConfig = config
            profileCache.clear()
            metaCache.clear()

            reloadFromDisk()
            refreshOpenEtaUi()
        }.launchIn(viewModelScope)

        viewModelScope.launch { reloadFromDisk() }

        gpxRepository
            .getActiveGpxFiles()
            .onEach { activePaths ->
                val files = gpxRepository.listGpxFiles()
                loadAndProcessGpxFiles(files, activePaths)
            }.launchIn(viewModelScope)

        syncManager.gpxSyncRequest
            .onEach { reloadFromDisk() }
            .launchIn(viewModelScope)
    }

    fun loadGpxFiles() {
        viewModelScope.launch { reloadFromDisk() }
    }

    suspend fun clearDerivedCaches() {
        metaCache.clear()
        profileCache.clear()
        etaCache.clear()
        reloadFromDisk()
        refreshOpenEtaUi()
    }

    private suspend fun reloadFromDisk() {
        val files = gpxRepository.listGpxFiles()
        val activePaths = gpxRepository.getActiveGpxFiles().first()
        loadAndProcessGpxFiles(files, activePaths)
    }

    private suspend fun loadAndProcessGpxFiles(
        files: List<File>,
        activePaths: Set<String>,
    ) {
        val existingPaths = files.asSequence().map { it.absolutePath }.toSet()
        metaCache.keys.retainAll(existingPaths)
        profileCache.keys.retainAll(existingPaths)
        etaCache.keys.retainAll(existingPaths)

        val fileStates =
            withContext(Dispatchers.IO) {
                files.map { file ->
                    val path = file.absolutePath
                    val sig = sigOf(file)

                    val cachedMeta = metaCache[path]?.takeIf { it.sig == sig }
                    val cachedProfile =
                        profileCache[path]?.takeIf {
                            it.sig == sig && it.elevationFilterConfig == elevationFilterConfig
                        }

                    val parsed =
                        if (cachedMeta != null && cachedProfile != null) {
                            null
                        } else {
                            parseGpxData(file)
                        }
                    val profile =
                        cachedProfile ?: buildProfile(
                            sig = sig,
                            pts = parsed?.points ?: emptyList(),
                            elevationFilterConfig = elevationFilterConfig,
                        ).also { created ->
                            profileCache[path] = created
                            profileCache.trimTo(maxProfileCacheEntries)
                        }
                    val canonicalMeta =
                        CachedMeta(
                            sig = sig,
                            title = cachedMeta?.title ?: parsed?.title,
                            distance =
                                profile.totalDistance.takeIf { it > 0.0 }
                                    ?: parsed?.totalDistance
                                    ?: 0.0,
                            elevationGain = profile.totalAscent,
                            elevationLoss = profile.totalDescent,
                        )
                    val meta =
                        if (cachedMeta == canonicalMeta) {
                            cachedMeta
                        } else {
                            canonicalMeta.also { created ->
                                metaCache[path] = created
                                metaCache.trimTo(maxMetaCacheEntries)
                            }
                        }

                    val etaSeconds =
                        getOrBuildEtaProjection(
                            path = path,
                            sig = sig,
                            profile = profile,
                        )?.totalSeconds

                    GpxFileState(
                        name =
                            normalizeUserFacingGpxText(file.nameWithoutExtension)
                                ?: file.nameWithoutExtension,
                        path = path,
                        title = meta.title,
                        distance = meta.distance,
                        elevationGain = meta.elevationGain,
                        estimatedDurationSec = etaSeconds,
                        isActive = path in activePaths,
                    )
                }
            }

        _gpxFiles.value = fileStates
        updateActiveGpxDetails(fileStates.filter { it.isActive })

        val aTrack = aPos?.trackId
        if (aTrack != null && aTrack !in existingPaths) {
            dismissInspection()
        }
        val elevationTrack = _elevationProfileUiState.value?.trackPath
        if (elevationTrack != null && elevationTrack !in existingPaths) {
            dismissElevationProfile()
        }
    }

    fun toggleGpxFile(path: String) {
        viewModelScope.launch {
            val currentActive = gpxRepository.getActiveGpxFiles().first()
            val newActive =
                if (currentActive.contains(path)) currentActive - path else currentActive + path
            gpxRepository.setActiveGpxFiles(newActive)
        }
    }

    fun deleteGpxFile(path: String) {
        viewModelScope.launch {
            gpxRepository.deleteGpxFile(path)
            metaCache.remove(path)
            profileCache.remove(path)
            etaCache.remove(path)
            if (aPos?.trackId == path) dismissInspection()
            if (_elevationProfileUiState.value?.trackPath == path) dismissElevationProfile()
            reloadFromDisk()
        }
    }

    fun renameGpxFile(
        filePath: String,
        newName: String,
        onComplete: (Result<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching {
                        routeToolOperations.renameGpxFileOnDisk(
                            filePath = filePath,
                            newName = newName,
                        )
                    }
                }
            if (result.isSuccess) {
                metaCache.clear()
                profileCache.clear()
                etaCache.clear()
                if (aPos?.trackId == filePath) dismissInspection()
                if (_elevationProfileUiState.value?.trackPath == filePath) dismissElevationProfile()
                reloadFromDisk()
            }
            onComplete(result.map { })
        }
    }

    fun showElevationProfile(path: String) {
        viewModelScope.launch {
            val uiState =
                withContext(Dispatchers.IO) {
                    val file = File(path)
                    if (!file.exists()) return@withContext null

                    val profile = getOrBuildProfile(path = path, file = file, sig = sigOf(file))
                    val totalDistance = profile.totalDistance
                    val totalAscent = profile.totalAscent
                    val totalDescent = profile.totalDescent
                    val etaProjection = getOrBuildEtaProjection(path = path, sig = profile.sig, profile = profile)
                    val totalDurationSec = etaProjection?.totalSeconds
                    val rawSamples =
                        profile.points.mapIndexedNotNull { index, point ->
                            val elevation = point.elevation ?: return@mapIndexedNotNull null
                            val distance = profile.cumDist.getOrElse(index) { totalDistance }
                            ElevationSample(
                                distance = distance,
                                elevation = elevation,
                                cumulativeAscent = profile.cumAscent.getOrElse(index) { totalAscent },
                                cumulativeDescent = profile.cumDescent.getOrElse(index) { totalDescent },
                                cumulativeDurationSec = etaProjection?.secondsAtPointIndex(index),
                            )
                        }
                    val samples = downsampleElevationSamples(rawSamples, maxSamples = 120)
                    val trackTitle =
                        _gpxFiles.value.firstOrNull { it.path == path }?.displayTitle
                            ?: file.nameWithoutExtension

                    GpxElevationProfileUiState(
                        trackPath = path,
                        trackTitle = trackTitle,
                        totalDistance = totalDistance,
                        totalAscent = totalAscent,
                        totalDescent = totalDescent,
                        totalDurationSec = totalDurationSec,
                        samples = samples,
                        minElevation = samples.minOfOrNull { it.elevation },
                        maxElevation = samples.maxOfOrNull { it.elevation },
                    )
                }
            _elevationProfileUiState.value = uiState
        }
    }

    fun dismissElevationProfile() {
        _elevationProfileUiState.value = null
    }

    fun dismissLongPressTipForever() {
        viewModelScope.launch {
            settingsRepository.setGpxLongPressTipShown(true)
            _showLongPressTip.value = false
        }
    }

    private suspend fun updateActiveGpxDetails(activeFiles: List<GpxFileState>) {
        val details =
            withContext(Dispatchers.IO) {
                activeFiles.mapNotNull { fileState ->
                    val file = File(fileState.path)
                    if (!file.exists()) return@mapNotNull null

                    val path = file.absolutePath
                    val profile = getOrBuildProfile(path = path, file = file, sig = sigOf(file))

                    val start = profile.points.firstOrNull()?.latLong
                    val end = profile.points.lastOrNull()?.latLong

                    GpxTrackDetails(
                        id = path,
                        points = profile.points.map { it.latLong },
                        title = fileState.title,
                        distance = profile.totalDistance,
                        elevationGain = profile.totalAscent,
                        startPoint = start,
                        endPoint = end,
                    )
                }
            }

        _activeGpxDetails.value = details
    }

    private fun getOrBuildProfile(
        path: String,
        file: File,
        sig: FileSig,
    ): TrackProfile {
        val cached = profileCache[path]
        if (
            cached != null &&
            cached.sig == sig &&
            cached.elevationFilterConfig == elevationFilterConfig
        ) {
            return cached
        }

        return buildProfile(
            sig = sig,
            pts = parseGpxPoints(file),
            elevationFilterConfig = elevationFilterConfig,
        ).also { profile ->
            profileCache[path] = profile
            profileCache.trimTo(maxProfileCacheEntries)
        }
    }

    private fun getOrBuildEtaProjection(
        path: String,
        sig: FileSig,
        profile: TrackProfile,
    ): GpxEtaProjection? {
        val cached = etaCache[path]
        if (
            cached != null &&
            cached.sig == sig &&
            cached.modelConfig == etaModelConfig
        ) {
            return cached.projection
        }

        val projection = buildEtaProjection(profile, etaModelConfig)
        etaCache[path] =
            CachedEta(
                sig = sig,
                modelConfig = etaModelConfig,
                projection = projection,
            )
        etaCache.trimTo(maxProfileCacheEntries)
        return projection
    }

    private suspend fun refreshOpenEtaUi() {
        val a = aPos
        if (a != null) {
            val b = bPos
            if (b != null && b.trackId == a.trackId) {
                publishAB(a.trackId, a, b)
            } else {
                publishA(a.trackId, a)
            }
        }

        val openProfilePath = _elevationProfileUiState.value?.trackPath
        if (openProfilePath != null) {
            showElevationProfile(openProfilePath)
        }
    }

    private fun downsampleElevationSamples(
        samples: List<ElevationSample>,
        maxSamples: Int,
    ): List<ElevationSample> {
        if (samples.size <= maxSamples || maxSamples <= 1) return samples

        val lastIndex = samples.lastIndex
        val step = lastIndex.toDouble() / (maxSamples - 1).toDouble()
        return List(maxSamples) { sampleIndex ->
            val pointIndex = (sampleIndex * step).toInt().coerceIn(0, lastIndex)
            samples[pointIndex]
        }
    }

    // -------------------------------------------------------------------------
    // Inspection API
    // -------------------------------------------------------------------------

    fun onMapLongPress(press: LatLong) {
        viewModelScope.launch(Dispatchers.Default) {
            val tracks = activeGpxDetails.value
            if (tracks.isEmpty()) return@launch

            val allowedTrackId = if (selectingB) aPos?.trackId else null

            val found =
                findClosestTrackPosition(
                    press = press,
                    tracks = tracks,
                    profileProvider = { id -> profileCache[id] },
                    allowedTrackId = allowedTrackId,
                ) ?: return@launch

            val pos = found.pos
            val snapped = found.snapped
            val distToLineMeters = found.distanceToLineMeters

            if (distToLineMeters > pressThresholdMeters) return@launch

            // Cancel any pending delayed popup (A or AB)
            popupDelayJob?.cancel()

            if (!selectingB) {
                aPos = pos
                bPos = null
                selectingB = false
                selectBTimeoutJob?.cancel()

                _selectedPointB.value = null
                _selectedPointA.value = snapped

                popupDelayJob =
                    viewModelScope.launch(Dispatchers.Default) {
                        delay(popupDelayMs)
                        publishA(pos.trackId, pos)
                    }
            } else {
                val a = aPos ?: return@launch
                selectingB = false
                selectBTimeoutJob?.cancel()
                bPos = pos

                _selectedPointB.value = snapped

                popupDelayJob =
                    viewModelScope.launch(Dispatchers.Default) {
                        delay(popupDelayMs)
                        publishAB(a.trackId, a, pos)
                    }
            }
        }
    }

    fun startSelectingB() {
        val a = aPos ?: return
        selectingB = true
        bPos = null

        // Hide popup for B selection
        _inspectionUiState.value = null

        // Keep A visible; clear B while selecting
        _selectedPointB.value = null

        // Cancel any pending popup (A delayed)
        popupDelayJob?.cancel()

        selectBTimeoutJob?.cancel()
        selectBTimeoutJob =
            viewModelScope.launch {
                delay(15_000L)
                if (selectingB && aPos == a) {
                    selectingB = false
                    publishA(a.trackId, a)
                }
            }
    }

    fun dismissInspection() {
        selectingB = false
        aPos = null
        bPos = null
        selectBTimeoutJob?.cancel()
        popupDelayJob?.cancel()

        _inspectionUiState.value = null
        _selectedPointA.value = null
        _selectedPointB.value = null
    }

    internal fun applyRouteToolModification(
        session: RouteToolSession,
        onProgress: (String) -> Unit = {},
        onComplete: (Result<RouteToolSaveResult>) -> Unit,
    ) {
        if (session.options.toolKind != RouteToolKind.MODIFY) {
            onComplete(Result.failure(IllegalArgumentException("Only GPX modify actions are supported here.")))
            return
        }

        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching { routeToolOperations.applyModification(session, onProgress) }
                }
            if (result.isSuccess) {
                metaCache.clear()
                profileCache.clear()
                etaCache.clear()
                dismissInspection()
                onComplete(result)
                launch { reloadFromDisk() }
            } else {
                onComplete(result)
            }
        }
    }

    internal fun previewRouteToolModification(
        session: RouteToolSession,
        onComplete: (Result<RouteToolModifyPreview>) -> Unit,
    ) {
        if (session.options.toolKind != RouteToolKind.MODIFY) {
            onComplete(Result.failure(IllegalArgumentException("Only GPX modify actions are supported here.")))
            return
        }

        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching { routeToolOperations.previewModification(session) }
                }
            onComplete(result)
        }
    }

    internal fun previewRouteToolCreation(
        session: RouteToolSession,
        currentLocation: LatLong?,
        onComplete: (Result<RouteToolCreatePreview>) -> Unit,
    ) {
        if (session.options.toolKind != RouteToolKind.CREATE) {
            onComplete(Result.failure(IllegalArgumentException("Only GPX create actions are supported here.")))
            return
        }

        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching { routeToolOperations.previewCreation(session, currentLocation) }
                }
            onComplete(result)
        }
    }

    internal fun applyRouteToolCreation(
        session: RouteToolSession,
        currentLocation: LatLong?,
        preview: RouteToolCreatePreview? = null,
        onProgress: (String) -> Unit = {},
        onComplete: (Result<RouteToolSaveResult>) -> Unit,
    ) {
        if (session.options.toolKind != RouteToolKind.CREATE) {
            onComplete(Result.failure(IllegalArgumentException("Only GPX create actions are supported here.")))
            return
        }

        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching {
                        routeToolOperations.applyCreation(
                            session = session,
                            currentLocation = currentLocation,
                            preview = preview,
                            onProgress = onProgress,
                        )
                    }
                }
            if (result.isSuccess) {
                metaCache.clear()
                profileCache.clear()
                etaCache.clear()
                dismissInspection()
                onComplete(result)
                launch { reloadFromDisk() }
            } else {
                onComplete(result)
            }
        }
    }

    internal fun renameRouteToolResult(
        filePath: String,
        newName: String,
        onComplete: (Result<RouteToolSaveResult>) -> Unit,
    ) {
        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching { routeToolOperations.renameSavedRoute(filePath, newName) }
                }
            if (result.isSuccess) {
                metaCache.clear()
                profileCache.clear()
                etaCache.clear()
                if (aPos?.trackId == filePath) dismissInspection()
                if (_elevationProfileUiState.value?.trackPath == filePath) dismissElevationProfile()
                reloadFromDisk()
            }
            onComplete(result)
        }
    }

    // -------------------------------------------------------------------------
    // Publishing UI state
    // -------------------------------------------------------------------------

    private fun publishA(
        trackId: String,
        pos: TrackPosition,
    ) {
        val profile = profileCache[trackId] ?: return
        val title = activeGpxDetails.value.firstOrNull { it.id == trackId }?.title
        val etaProjection = getOrBuildEtaProjection(path = trackId, sig = profile.sig, profile = profile)

        _inspectionUiState.value =
            buildInspectionAUiState(
                trackTitle = title,
                profile = profile,
                pos = pos,
                etaProjection = etaProjection,
            )
    }

    private fun publishAB(
        trackId: String,
        a: TrackPosition,
        b: TrackPosition,
    ) {
        if (a.trackId != b.trackId) return
        val profile = profileCache[trackId] ?: return
        val title = activeGpxDetails.value.firstOrNull { it.id == trackId }?.title
        val etaProjection = getOrBuildEtaProjection(path = trackId, sig = profile.sig, profile = profile)

        _inspectionUiState.value =
            buildInspectionABUiState(
                trackTitle = title,
                profile = profile,
                a = a,
                b = b,
                etaProjection = etaProjection,
            )
    }
}
