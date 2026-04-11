package com.glancemap.glancemapwearos.presentation.features.maps.theme

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glancemap.glancemapwearos.core.maps.Dem3CoverageUtils
import com.glancemap.glancemapwearos.core.maps.DemSignatureStore
import com.glancemap.glancemapwearos.data.repository.maps.theme.ThemeRepository
import com.glancemap.glancemapwearos.data.repository.maps.theme.ThemeRepositoryImpl
import com.glancemap.glancemapwearos.domain.model.maps.theme.ThemeListItem
import com.glancemap.glancemapwearos.domain.model.maps.theme.mapsforge.MapsforgeThemeCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale

private enum class OverlayPreset {
    DEFAULT,
    MINIMAL,
    ALL_ON,
}

data class DemDownloadUiState(
    val isDownloading: Boolean = false,
    val activeMapPath: String? = null,
    val totalTiles: Int = 0,
    val processedTiles: Int = 0,
    val downloadedTiles: Int = 0,
    val skippedTiles: Int = 0,
    val failedTiles: Int = 0,
    val networkUnavailable: Boolean = false,
    val statusMessage: String = "",
    val lastCompletedAtMillis: Long = 0L,
)

private data class DemDownloadResult(
    val totalTiles: Int,
    val processedTiles: Int,
    val downloadedTiles: Int,
    val skippedTiles: Int,
    val failedTiles: Int,
    val networkUnavailable: Boolean,
    val statusMessage: String,
)

private data class DemTileDownloadOutcome(
    val success: Boolean,
    val networkUnavailable: Boolean,
)

class ThemeViewModel(
    private val themeRepository: ThemeRepository,
    private val context: Context,
) : ViewModel() {
    companion object {
        private const val DEM3_BASE_URL = "https://download.mapsforge.org/maps/dem/dem3"
        private const val DEM3_USER_AGENT = "GlanceMap-DEM3/1.0"
        private const val TAG = "ThemeViewModel"
        private const val DEM_TILE_MAX_ATTEMPTS = 3
        private const val DEM_TILE_RETRY_BASE_DELAY_MS = 1_500L
        private const val DEM_INTERNET_WAIT_TIMEOUT_MS = 8_000L
        private const val DEM_INTERNET_RECHECK_MS = 500L
    }

    private val appContext: Context = context.applicationContext

    private val _demDownloadUiState = MutableStateFlow(DemDownloadUiState())
    val demDownloadUiState: StateFlow<DemDownloadUiState> = _demDownloadUiState.asStateFlow()

    val themeItems: StateFlow<List<ThemeListItem>> =
        themeRepository
            .getThemeItems()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun setTheme(themeId: String) {
        viewModelScope.launch {
            Log.d("Theme", "setTheme called with: $themeId")
            themeRepository.setTheme(themeId)
        }
    }

    fun setMapStyle(styleId: String) {
        viewModelScope.launch {
            Log.d("Theme", "setMapStyle called with: $styleId")
            themeRepository.setMapStyle(styleId)
        }
    }

    fun toggleOverlay(overlayId: String) {
        viewModelScope.launch {
            // overlayId is the concrete overlay layer id for the selected bundled theme/style.
            Log.d("Theme", "toggleOverlay called with: $overlayId")

            val snapshot = themeItems.first()
            val currentStyleId =
                snapshot
                    .filterIsInstance<ThemeListItem.Style>()
                    .firstOrNull { it.selected }
                    ?.id
                    ?: run {
                        Log.w("Theme", "toggleOverlay: no selected style found, ignoring")
                        return@launch
                    }

            val bundledThemeSelected =
                snapshot
                    .filterIsInstance<ThemeListItem.ThemeOption>()
                    .firstOrNull { it.selected }
                    ?.id
                    .let { MapsforgeThemeCatalog.isBundledAssetTheme(it) }
            if (!bundledThemeSelected) {
                Log.d("Theme", "toggleOverlay ignored because bundled asset theme is not selected")
                return@launch
            }

            Log.d("Theme", "toggleOverlay: currentStyleId=$currentStyleId overlayId=$overlayId")
            themeRepository.toggleOverlay(currentStyleId, overlayId)
        }
    }

    fun applyOverlayPresetDefault() {
        applyOverlayPreset(OverlayPreset.DEFAULT)
    }

    fun applyOverlayPresetMinimal() {
        applyOverlayPreset(OverlayPreset.MINIMAL)
    }

    fun applyOverlayPresetAllOn() {
        applyOverlayPreset(OverlayPreset.ALL_ON)
    }

    fun resetCurrentStyleOverlaysToDefault() {
        applyOverlayPreset(OverlayPreset.DEFAULT)
    }

    private fun applyOverlayPreset(preset: OverlayPreset) {
        viewModelScope.launch {
            val snapshot = themeItems.first()

            val selectedThemeId =
                snapshot
                    .filterIsInstance<ThemeListItem.ThemeOption>()
                    .firstOrNull { it.selected }
                    ?.id
            if (!MapsforgeThemeCatalog.isBundledAssetTheme(selectedThemeId)) return@launch

            val currentStyleId =
                snapshot
                    .filterIsInstance<ThemeListItem.Style>()
                    .firstOrNull { it.selected }
                    ?.id
                    ?: ThemeRepositoryImpl.DEFAULT_STYLE_ID

            val overlays = snapshot.filterIsInstance<ThemeListItem.Overlay>()
            if (overlays.isEmpty()) return@launch

            val enabledOverlayIds =
                when (preset) {
                    OverlayPreset.DEFAULT ->
                        overlays
                            .asSequence()
                            .filter { it.defaultEnabled }
                            .map { it.layerId }
                            .toSet()

                    OverlayPreset.ALL_ON ->
                        overlays
                            .asSequence()
                            .map { it.layerId }
                            .toSet()

                    OverlayPreset.MINIMAL ->
                        overlays
                            .asSequence()
                            .filter { isMinimalOverlay(it.layerId) }
                            .map { it.layerId }
                            .toSet()
                }

            themeRepository.setOverlaysForStyle(
                styleId = currentStyleId,
                enabledOverlayLayerIds = enabledOverlayIds,
            )
        }
    }

    private fun isMinimalOverlay(layerId: String): Boolean {
        val id = layerId.lowercase(Locale.ROOT)
        return id.contains("routes") ||
            id.contains("waymarks") ||
            id.contains("winter_reference") ||
            id.contains("winter_symbol") ||
            id.contains("skipiste") ||
            id.contains("skitour") ||
            id.contains("skiloipe") ||
            id.contains("schneeschuh") ||
            id.contains("rodeln") ||
            id.contains("hundeschlitten") ||
            id.contains("eislaufen") ||
            id.contains("schneepark")
    }

    fun setGlobalToggle(
        toggleId: String,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            when (toggleId) {
                ThemeRepositoryImpl.GLOBAL_HILL_SHADING_ID -> {
                    Log.d("Theme", "setGlobalToggle: hillShading=$enabled")
                    themeRepository.setHillShadingEnabled(enabled)
                }
                ThemeRepositoryImpl.GLOBAL_RELIEF_OVERLAY_ID -> {
                    Log.d("Theme", "setGlobalToggle: reliefOverlay=$enabled")
                    themeRepository.setReliefOverlayEnabled(enabled)
                }
            }
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            Log.d("Theme", "resetToDefaults called")
            themeRepository.resetToDefaults()
        }
    }

    fun downloadDemForSelectedMap(selectedMapPath: String?) {
        downloadDemForMap(selectedMapPath)
    }

    fun downloadDemForMap(mapPath: String?) {
        if (_demDownloadUiState.value.isDownloading) return

        viewModelScope.launch {
            if (mapPath.isNullOrBlank()) {
                _demDownloadUiState.value =
                    DemDownloadUiState(
                        activeMapPath = null,
                        statusMessage = "No map selected. Select a .map first.",
                        lastCompletedAtMillis = System.currentTimeMillis(),
                    )
                return@launch
            }

            val selectedMapFile = File(mapPath)
            if (!selectedMapFile.exists()) {
                _demDownloadUiState.value =
                    DemDownloadUiState(
                        activeMapPath = null,
                        statusMessage = "Selected map not found on watch.",
                        lastCompletedAtMillis = System.currentTimeMillis(),
                    )
                return@launch
            }

            _demDownloadUiState.value =
                DemDownloadUiState(
                    isDownloading = true,
                    activeMapPath = mapPath,
                    statusMessage = "Checking watch internet...",
                )

            if (!waitForWatchInternetConnection()) {
                _demDownloadUiState.value =
                    DemDownloadUiState(
                        isDownloading = false,
                        activeMapPath = null,
                        networkUnavailable = true,
                        statusMessage = DEM_NO_INTERNET_MESSAGE,
                        lastCompletedAtMillis = System.currentTimeMillis(),
                    )
                return@launch
            }

            _demDownloadUiState.value =
                _demDownloadUiState.value.copy(
                    statusMessage = "Reading map area...",
                )

            runCatching {
                withContext(Dispatchers.IO) {
                    downloadDemForMapInternal(selectedMapFile)
                }
            }.onSuccess { result ->
                _demDownloadUiState.value =
                    DemDownloadUiState(
                        isDownloading = false,
                        activeMapPath = null,
                        totalTiles = result.totalTiles,
                        processedTiles = result.processedTiles,
                        downloadedTiles = result.downloadedTiles,
                        skippedTiles = result.skippedTiles,
                        failedTiles = result.failedTiles,
                        networkUnavailable = result.networkUnavailable,
                        statusMessage = result.statusMessage,
                        lastCompletedAtMillis = System.currentTimeMillis(),
                    )
            }.onFailure { error ->
                Log.e(TAG, "DEM download failed", error)
                val networkUnavailable =
                    classifyDemFailureAsNetworkUnavailable(
                        throwable = error,
                        internetAvailableNow = hasWatchInternetConnection(),
                    )
                _demDownloadUiState.value =
                    DemDownloadUiState(
                        isDownloading = false,
                        activeMapPath = null,
                        networkUnavailable = networkUnavailable,
                        statusMessage = buildDemFailureMessage(error, networkUnavailable),
                        lastCompletedAtMillis = System.currentTimeMillis(),
                    )
            }
        }
    }

    suspend fun isDemReadyForMap(mapPath: String?): Boolean =
        withContext(Dispatchers.IO) {
            Dem3CoverageUtils.isReadyForMap(appContext, mapPath)
        }

    private suspend fun downloadDemForMapInternal(selectedMapFile: File): DemDownloadResult {
        val tileIds =
            Dem3CoverageUtils
                .requiredTileIdsForMap(selectedMapFile)
                ?.sorted()
                ?: return DemDownloadResult(
                    totalTiles = 0,
                    processedTiles = 0,
                    downloadedTiles = 0,
                    skippedTiles = 0,
                    failedTiles = 0,
                    networkUnavailable = false,
                    statusMessage = "Failed reading selected map area.",
                )

        if (tileIds.isEmpty()) {
            return DemDownloadResult(
                totalTiles = 0,
                processedTiles = 0,
                downloadedTiles = 0,
                skippedTiles = 0,
                failedTiles = 0,
                networkUnavailable = false,
                statusMessage = "No DEM tiles required for this map.",
            )
        }

        val outputRoot = getDemOutputRoot()
        outputRoot.mkdirs()

        var downloaded = 0
        var skipped = 0
        var failed = 0
        var processedTiles = 0
        var networkUnavailable = false

        for ((index, tileId) in tileIds.withIndex()) {
            val processed = index + 1
            _demDownloadUiState.value =
                _demDownloadUiState.value.copy(
                    isDownloading = true,
                    activeMapPath = selectedMapFile.absolutePath,
                    totalTiles = tileIds.size,
                    processedTiles = processed,
                    downloadedTiles = downloaded,
                    skippedTiles = skipped,
                    failedTiles = failed,
                    statusMessage = "Downloading...",
                )

            val (folder, fileName) = tilePathSegments(tileId)
            val localDir = File(outputRoot, folder).apply { mkdirs() }
            val localFile = File(localDir, fileName)
            if (localFile.exists()) {
                skipped += 1
                processedTiles = processed
                continue
            }

            val url = "$DEM3_BASE_URL/$folder/$fileName"
            val outcome =
                downloadTileWithRetries(
                    url = url,
                    target = localFile,
                    processed = processed,
                    total = tileIds.size,
                )

            processedTiles = processed
            if (outcome.success) {
                downloaded += 1
            } else {
                failed += 1
                if (outcome.networkUnavailable) {
                    networkUnavailable = true
                    break
                }
            }
        }

        if (downloaded > 0) {
            DemSignatureStore.markDirty(appContext)
        }

        val remaining = (tileIds.size - processedTiles).coerceAtLeast(0)
        val summary =
            buildDemSummaryMessage(
                downloaded = downloaded,
                skipped = skipped,
                failed = failed,
                remaining = remaining,
            )
        val finalMessage =
            when {
                networkUnavailable && remaining > 0 ->
                    "No internet on watch. $downloaded downloaded, $skipped already on watch, $remaining remaining. Reconnect and retry to finish DEM."
                networkUnavailable ->
                    DEM_NO_INTERNET_MESSAGE
                else -> summary
            }
        return DemDownloadResult(
            totalTiles = tileIds.size,
            processedTiles = processedTiles,
            downloadedTiles = downloaded,
            skippedTiles = skipped,
            failedTiles = failed,
            networkUnavailable = networkUnavailable,
            statusMessage = finalMessage,
        )
    }

    private fun buildDemSummaryMessage(
        downloaded: Int,
        skipped: Int,
        failed: Int,
        remaining: Int,
    ): String =
        when {
            failed == 0 && downloaded > 0 && skipped == 0 ->
                "DEM ready: $downloaded tile(s) downloaded."
            failed == 0 && downloaded > 0 && skipped > 0 ->
                "DEM ready: $downloaded downloaded, $skipped already on watch."
            failed == 0 && downloaded == 0 && skipped > 0 ->
                "DEM already available ($skipped tile(s) already on watch)."
            downloaded == 0 && skipped == 0 && failed > 0 ->
                "DEM download failed ($failed tile(s))."
            remaining > 0 ->
                "DEM partial: $downloaded downloaded, $skipped on watch, $failed failed, $remaining remaining."
            else ->
                "DEM partial: $downloaded downloaded, $skipped on watch, $failed failed."
        }

    private fun getDemOutputRoot(): File = Dem3CoverageUtils.demRootDir(appContext)

    private fun downloadFile(
        url: String,
        target: File,
    ) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        if (tmp.exists()) tmp.delete()

        val connection =
            (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", DEM3_USER_AGENT)
            }

        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                throw FileNotFoundException("HTTP 404 for $url")
            }
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code for $url")
            }

            connection.inputStream.use { input ->
                tmp.outputStream().use { out -> input.copyTo(out) }
            }

            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                throw IllegalStateException("Failed moving temp DEM file to ${target.absolutePath}")
            }
        } finally {
            connection.disconnect()
            if (tmp.exists() && !target.exists()) tmp.delete()
        }
    }

    private suspend fun downloadTileWithRetries(
        url: String,
        target: File,
        processed: Int,
        total: Int,
    ): DemTileDownloadOutcome {
        var lastError: Throwable? = null

        repeat(DEM_TILE_MAX_ATTEMPTS) { attemptIndex ->
            val attemptNumber = attemptIndex + 1
            if (attemptNumber > 1) {
                _demDownloadUiState.value =
                    _demDownloadUiState.value.copy(
                        statusMessage = "Retrying download (attempt $attemptNumber/$DEM_TILE_MAX_ATTEMPTS)...",
                    )
            }

            if (!hasWatchInternetConnection()) {
                return DemTileDownloadOutcome(
                    success = false,
                    networkUnavailable = true,
                )
            }

            val success =
                runCatching {
                    downloadFile(url = url, target = target)
                    true
                }.getOrElse { error ->
                    lastError = error
                    false
                }

            if (success) {
                return DemTileDownloadOutcome(
                    success = true,
                    networkUnavailable = false,
                )
            }

            val error = lastError
            if (error != null && error !is FileNotFoundException) {
                Log.w(
                    TAG,
                    "Failed downloading DEM tile $processed/$total from $url on attempt $attemptNumber/$DEM_TILE_MAX_ATTEMPTS",
                    error,
                )
            }

            if (attemptNumber >= DEM_TILE_MAX_ATTEMPTS || !isRetryableDemDownloadFailure(error)) {
                return DemTileDownloadOutcome(
                    success = false,
                    networkUnavailable =
                        error?.let {
                            classifyDemFailureAsNetworkUnavailable(
                                throwable = it,
                                internetAvailableNow = hasWatchInternetConnection(),
                            )
                        } == true,
                )
            }

            delay(DEM_TILE_RETRY_BASE_DELAY_MS * attemptNumber)
        }

        return DemTileDownloadOutcome(
            success = false,
            networkUnavailable =
                lastError?.let {
                    classifyDemFailureAsNetworkUnavailable(
                        throwable = it,
                        internetAvailableNow = hasWatchInternetConnection(),
                    )
                } == true,
        )
    }

    private fun tilePathSegments(tileId: String): Pair<String, String> {
        val upper = tileId.uppercase(Locale.ROOT)
        val folder = upper.substring(0, 3)
        val file = "$upper.hgt.zip"
        return folder to file
    }

    private suspend fun waitForWatchInternetConnection(): Boolean {
        val deadline = SystemClock.elapsedRealtime() + DEM_INTERNET_WAIT_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasWatchInternetConnection()) {
                return true
            }
            delay(DEM_INTERNET_RECHECK_MS)
        }
        return hasWatchInternetConnection()
    }

    private fun hasWatchInternetConnection(): Boolean {
        val connectivityManager =
            appContext.getSystemService(ConnectivityManager::class.java)
                ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
