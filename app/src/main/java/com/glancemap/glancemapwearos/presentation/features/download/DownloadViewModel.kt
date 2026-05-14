@file:Suppress(
    "CyclomaticComplexMethod",
    "LargeClass",
    "LongMethod",
    "ReturnCount",
    "TooGenericExceptionCaught",
    "TooManyFunctions",
)

package com.glancemap.glancemapwearos.presentation.features.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DownloadUiState(
    val areas: List<OamDownloadArea> = OamDownloadCatalog.areas,
    val selectedAreaIds: Set<String> = emptySet(),
    val selection: OamDownloadSelection = OamDownloadSelection(),
    val installedBundles: List<OamInstalledBundle> = emptyList(),
    val isDownloading: Boolean = false,
    val phase: String? = null,
    val detail: String? = null,
    val bytesDone: Long = 0L,
    val totalBytes: Long? = null,
    val isPausedDownload: Boolean = false,
    val isCheckingUpdates: Boolean = false,
    val selectedRefreshBundleIds: Set<String> = emptySet(),
    val refreshPrompt: OamBundleUpdateCheck? = null,
    val refreshSummaryPrompt: OamBundleRefreshSummary? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val networkWarningMessage: String? = null,
    val lastLibraryChangedAtMillis: Long = 0L,
) {
    val selectedAreas: List<OamDownloadArea>
        get() = areas.filter { it.id in selectedAreaIds }

    val selectedBundle: OamBundleChoice
        get() = selection.toBundleChoice()
}

class DownloadViewModel(
    private val downloader: OamBundleDownloader,
    private val notificationController: OamDownloadNotificationController,
    private val networkMonitor: OamDownloadNetworkMonitor,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null
    private var stopRequest: DownloadStopRequest? = null
    private var pendingNonWifiRefreshBundles: List<OamInstalledBundle> = emptyList()

    init {
        refreshInstalledBundles()
    }

    fun toggleArea(areaId: String) {
        if (_uiState.value.isDownloading) return
        _uiState.update { state ->
            val nextIds =
                if (areaId in state.selectedAreaIds) {
                    state.selectedAreaIds - areaId
                } else {
                    state.selectedAreaIds + areaId
                }
            state.copy(
                selectedAreaIds = nextIds,
                isPausedDownload = false,
                statusMessage = null,
                errorMessage = null,
                networkWarningMessage = null,
            )
        }
    }

    fun setIncludeMap(includeMap: Boolean) {
        if (_uiState.value.isDownloading) return
        _uiState.update { state ->
            state.copy(
                selection = state.selection.copy(includeMap = includeMap),
                isPausedDownload = false,
                statusMessage = null,
                errorMessage = null,
                networkWarningMessage = null,
            )
        }
    }

    fun setIncludePoi(includePoi: Boolean) {
        if (_uiState.value.isDownloading) return
        _uiState.update { state ->
            state.copy(
                selection = state.selection.copy(includePoi = includePoi),
                isPausedDownload = false,
                statusMessage = null,
                errorMessage = null,
                networkWarningMessage = null,
            )
        }
    }

    fun setIncludeRouting(includeRouting: Boolean) {
        if (_uiState.value.isDownloading) return
        _uiState.update { state ->
            state.copy(
                selection = state.selection.copy(includeRouting = includeRouting),
                isPausedDownload = false,
                statusMessage = null,
                errorMessage = null,
                networkWarningMessage = null,
            )
        }
    }

    fun setIncludeDem(includeDem: Boolean) {
        if (_uiState.value.isDownloading) return
        _uiState.update { state ->
            state.copy(
                selection = state.selection.copy(includeDem = includeDem),
                isPausedDownload = false,
                statusMessage = null,
                errorMessage = null,
                networkWarningMessage = null,
            )
        }
    }

    fun downloadSelectedBundle() {
        val state = _uiState.value
        DebugTelemetry.log(
            OAM_DOWNLOAD_TELEMETRY_TAG,
            "event=${if (state.isPausedDownload) "user_resume_request" else "user_download_request"} " +
                networkMonitor.currentState().telemetryFields,
        )
        downloadSelectedBundleInternal(allowNonWifi = false)
    }

    fun continueDownloadWithoutWifi() {
        DebugTelemetry.log(
            OAM_DOWNLOAD_TELEMETRY_TAG,
            "event=user_continue_without_wifi ${networkMonitor.currentState().telemetryFields}",
        )
        val refreshBundles = pendingNonWifiRefreshBundles
        pendingNonWifiRefreshBundles = emptyList()
        if (refreshBundles.isNotEmpty()) {
            refreshBundlesInternal(refreshBundles, allowNonWifi = true)
        } else {
            downloadSelectedBundleInternal(allowNonWifi = true)
        }
    }

    fun dismissNetworkWarning() {
        pendingNonWifiRefreshBundles = emptyList()
        _uiState.update { it.copy(networkWarningMessage = null) }
    }

    private fun downloadSelectedBundleInternal(allowNonWifi: Boolean) {
        if (downloadJob?.isActive == true) return
        val state = _uiState.value
        if (!state.selection.canDownload) {
            _uiState.update {
                it.copy(
                    statusMessage = "Nothing selected",
                    errorMessage = "Enable Maps, POI, Routing, or DEM in Download settings.",
                    networkWarningMessage = null,
                )
            }
            return
        }
        val areas = state.selectedAreas
        if (areas.isEmpty()) {
            _uiState.update {
                it.copy(
                    statusMessage = "No area selected",
                    errorMessage = "Select at least one area before downloading.",
                    networkWarningMessage = null,
                )
            }
            return
        }
        val selection = state.selection
        val networkState = networkMonitor.currentState()
        DebugTelemetry.log(
            OAM_DOWNLOAD_TELEMETRY_TAG,
            "event=download_request allowNonWifi=$allowNonWifi " +
                "areas=${areas.size} bundle=${selection.toBundleChoice().name} ${networkState.telemetryFields}",
        )
        if (!allowNonWifi && !networkState.isValidatedWifi) {
            _uiState.update {
                it.copy(
                    statusMessage = "Wi-Fi recommended",
                    errorMessage = null,
                    networkWarningMessage =
                        "${networkState.userLabel}. Connect the watch to Wi-Fi for large downloads, " +
                            "or continue and the app will reconnect automatically if Wi-Fi appears.",
                )
            }
            DebugTelemetry.log(
                OAM_DOWNLOAD_TELEMETRY_TAG,
                "event=wifi_preflight_blocked state=${networkState.userLabel.telemetryValue()} " +
                    networkState.telemetryFields,
            )
            return
        }
        stopRequest = null
        downloadJob =
            viewModelScope.launch {
                var wifiReconnectHandle: AutoCloseable? = null
                var didReconnectOnWifi = false
                if (!networkState.isValidatedWifi) {
                    wifiReconnectHandle =
                        networkMonitor.watchForValidatedWifi {
                            if (!didReconnectOnWifi) {
                                didReconnectOnWifi = true
                                DebugTelemetry.log(
                                    OAM_DOWNLOAD_TELEMETRY_TAG,
                                    "event=auto_reconnect_request reason=wifi_available " +
                                        networkMonitor.currentState().telemetryFields,
                                )
                                downloader.abortActiveDownloads(reason = "wifi_available")
                            }
                        }
                }
                notificationController.showProgress(
                    title = "Downloading maps",
                    detail = "${areas.size} area(s)",
                    bytesDone = 0L,
                    totalBytes = null,
                )
                _uiState.update {
                    it.copy(
                        isDownloading = true,
                        refreshPrompt = null,
                        refreshSummaryPrompt = null,
                        phase = "STARTING",
                        detail = "${areas.size} area(s)",
                        bytesDone = 0L,
                        totalBytes = null,
                        isPausedDownload = false,
                        statusMessage = "Starting download",
                        errorMessage = null,
                        networkWarningMessage = null,
                    )
                }
                try {
                    areas.forEachIndexed { index, area ->
                        downloader.downloadBundle(area, selection) { progress ->
                            val detail = "${index + 1}/${areas.size} ${area.region} - ${progress.detail}"
                            notificationController.showProgress(
                                title = "Downloading offline bundle",
                                detail = detail,
                                bytesDone = progress.bytesDone,
                                totalBytes = progress.totalBytes,
                            )
                            _uiState.update {
                                it.copy(
                                    phase = progress.phase,
                                    detail = detail,
                                    bytesDone = progress.bytesDone,
                                    totalBytes = progress.totalBytes,
                                    statusMessage =
                                        progress.phase
                                            .lowercase()
                                            .replaceFirstChar { char -> char.uppercase() },
                                    errorMessage = null,
                                )
                            }
                        }
                    }
                    val installed = downloader.installedBundles()
                    _uiState.update {
                        it.copy(
                            installedBundles = installed,
                            selectedAreaIds = emptySet(),
                            isDownloading = false,
                            phase = "READY",
                            detail = "${areas.size} area(s)",
                            bytesDone = 0L,
                            totalBytes = null,
                            isPausedDownload = false,
                            statusMessage = if (areas.size == 1) "Bundle installed" else "Bundles installed",
                            errorMessage = null,
                            networkWarningMessage = null,
                            lastLibraryChangedAtMillis = System.currentTimeMillis(),
                        )
                    }
                    notificationController.showComplete(
                        if (areas.size == 1) {
                            "${areas.first().region} installed"
                        } else {
                            "${areas.size} bundles installed"
                        },
                    )
                } catch (cancelled: CancellationException) {
                    val request = stopRequest ?: DownloadStopRequest.PAUSE
                    if (request == DownloadStopRequest.CANCEL) {
                        downloader.deletePartialDownloads(areas, selection)
                    }
                    if (request == DownloadStopRequest.CANCEL) {
                        notificationController.clear()
                    } else {
                        notificationController.showPaused("${areas.size} area(s)")
                    }
                    _uiState.update {
                        if (request == DownloadStopRequest.CANCEL) {
                            it.copy(
                                isDownloading = false,
                                phase = "CANCELED",
                                detail = "${areas.size} area(s)",
                                bytesDone = 0L,
                                totalBytes = null,
                                isPausedDownload = false,
                                statusMessage = "Download canceled",
                                errorMessage = null,
                                networkWarningMessage = null,
                            )
                        } else {
                            it.copy(
                                isDownloading = false,
                                phase = "PAUSED",
                                isPausedDownload = true,
                                statusMessage = "Download paused",
                                errorMessage = null,
                                networkWarningMessage = null,
                            )
                        }
                    }
                    throw cancelled
                } catch (error: Throwable) {
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            phase = "FAILED",
                            statusMessage = "Download failed",
                            isPausedDownload = false,
                            errorMessage = error.message ?: "Download failed",
                            networkWarningMessage = null,
                        )
                    }
                    notificationController.showError(error.message ?: "Download failed")
                } finally {
                    wifiReconnectHandle?.close()
                    downloadJob = null
                    stopRequest = null
                }
            }
    }

    fun pauseDownload() {
        DebugTelemetry.log(
            OAM_DOWNLOAD_TELEMETRY_TAG,
            "event=user_pause_request ${networkMonitor.currentState().telemetryFields}",
        )
        stopRequest = DownloadStopRequest.PAUSE
        downloader.abortActiveDownloads(reason = "user_pause")
        downloadJob?.cancel()
    }

    fun cancelDownload() {
        DebugTelemetry.log(
            OAM_DOWNLOAD_TELEMETRY_TAG,
            "event=user_cancel_request ${networkMonitor.currentState().telemetryFields}",
        )
        stopRequest = DownloadStopRequest.CANCEL
        downloader.abortActiveDownloads(reason = "user_cancel")
        downloadJob?.cancel()
    }

    fun checkBundleForRefresh(bundle: OamInstalledBundle) {
        if (_uiState.value.isDownloading || _uiState.value.isCheckingUpdates) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCheckingUpdates = true,
                    refreshPrompt = null,
                    statusMessage = "Checking updates",
                    errorMessage = null,
                    networkWarningMessage = null,
                )
            }
            val check =
                runCatching { downloader.checkBundleUpdates(bundle) }
                    .getOrElse { error ->
                        OamBundleUpdateCheck(
                            bundle = bundle,
                            status = OamBundleUpdateStatus.UNKNOWN,
                            checkedFileCount = 0,
                            unknownFileNames = listOf(error.message ?: "Update check failed"),
                        )
                    }
            _uiState.update {
                when (check.status) {
                    OamBundleUpdateStatus.UP_TO_DATE ->
                        it.copy(
                            isCheckingUpdates = false,
                            statusMessage = "${bundle.areaLabel} is up to date",
                            errorMessage = null,
                            refreshPrompt = check,
                            networkWarningMessage = null,
                        )
                    OamBundleUpdateStatus.UPDATE_AVAILABLE,
                    OamBundleUpdateStatus.UNKNOWN,
                    ->
                        it.copy(
                            isCheckingUpdates = false,
                            statusMessage =
                                if (check.status == OamBundleUpdateStatus.UPDATE_AVAILABLE) {
                                    "Update available"
                                } else {
                                    "Update check incomplete"
                                },
                            errorMessage = null,
                            refreshPrompt = check,
                            networkWarningMessage = null,
                        )
                }
            }
        }
    }

    fun dismissRefreshPrompt() {
        _uiState.update { it.copy(refreshPrompt = null, refreshSummaryPrompt = null) }
    }

    fun confirmRefreshBundle() {
        val bundle = _uiState.value.refreshPrompt?.bundle ?: return
        _uiState.update { it.copy(refreshPrompt = null) }
        refreshBundlesInternal(listOf(bundle), allowNonWifi = false)
    }

    fun toggleRefreshBundleSelection(areaId: String) {
        if (_uiState.value.isDownloading || _uiState.value.isCheckingUpdates) return
        _uiState.update { state ->
            val nextSelection =
                if (areaId in state.selectedRefreshBundleIds) {
                    state.selectedRefreshBundleIds - areaId
                } else {
                    state.selectedRefreshBundleIds + areaId
                }
            state.copy(
                selectedRefreshBundleIds = nextSelection,
                refreshPrompt = null,
                refreshSummaryPrompt = null,
                statusMessage = null,
                errorMessage = null,
                networkWarningMessage = null,
            )
        }
    }

    fun clearRefreshBundleSelection() {
        if (_uiState.value.isDownloading || _uiState.value.isCheckingUpdates) return
        _uiState.update {
            it.copy(
                selectedRefreshBundleIds = emptySet(),
                refreshPrompt = null,
                refreshSummaryPrompt = null,
            )
        }
    }

    fun checkSelectedBundlesForRefresh() {
        val state = _uiState.value
        if (state.isDownloading || state.isCheckingUpdates) return
        val bundles = state.installedBundles.filter { it.areaId in state.selectedRefreshBundleIds }
        if (bundles.isEmpty()) {
            _uiState.update {
                it.copy(
                    statusMessage = "No bundle selected",
                    errorMessage = "Select at least one installed bundle.",
                    refreshSummaryPrompt = null,
                    networkWarningMessage = null,
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCheckingUpdates = true,
                    refreshPrompt = null,
                    refreshSummaryPrompt = null,
                    statusMessage = "Checking ${bundles.size} bundle(s)",
                    errorMessage = null,
                    networkWarningMessage = null,
                )
            }
            val checks =
                bundles.map { bundle ->
                    runCatching { downloader.checkBundleUpdates(bundle) }
                        .getOrElse { error ->
                            OamBundleUpdateCheck(
                                bundle = bundle,
                                status = OamBundleUpdateStatus.UNKNOWN,
                                checkedFileCount = 0,
                                unknownFileNames = listOf(error.message ?: "Update check failed"),
                            )
                        }
                }
            val summary = OamBundleRefreshSummary(checks)
            _uiState.update {
                it.copy(
                    isCheckingUpdates = false,
                    refreshSummaryPrompt = summary,
                    statusMessage =
                        if (summary.bundlesToRefresh.isEmpty()) {
                            "Selected bundles are up to date"
                        } else {
                            "${summary.bundlesToRefresh.size} bundle(s) need refresh"
                        },
                    errorMessage = null,
                    networkWarningMessage = null,
                )
            }
        }
    }

    fun dismissRefreshSummary() {
        _uiState.update { it.copy(refreshSummaryPrompt = null) }
    }

    fun confirmRefreshSelectedBundles() {
        val bundles =
            _uiState.value
                .refreshSummaryPrompt
                ?.bundlesToRefresh
                .orEmpty()
        _uiState.update {
            it.copy(
                refreshSummaryPrompt = null,
                selectedRefreshBundleIds = emptySet(),
            )
        }
        if (bundles.isNotEmpty()) {
            refreshBundlesInternal(bundles, allowNonWifi = false)
        }
    }

    fun deleteBundle(bundle: OamInstalledBundle) {
        if (_uiState.value.isDownloading) return
        viewModelScope.launch {
            try {
                downloader.deleteBundle(bundle)
                val installed = downloader.installedBundles()
                _uiState.update {
                    it.copy(
                        installedBundles = installed,
                        statusMessage = "Bundle deleted",
                        errorMessage = null,
                        networkWarningMessage = null,
                        lastLibraryChangedAtMillis = System.currentTimeMillis(),
                    )
                }
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        statusMessage = "Delete failed",
                        errorMessage = error.message ?: "Delete failed",
                        networkWarningMessage = null,
                    )
                }
            }
        }
    }

    fun refreshInstalledBundles() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(installedBundles = downloader.installedBundles())
            }
        }
    }

    private fun refreshBundlesInternal(
        bundles: List<OamInstalledBundle>,
        allowNonWifi: Boolean,
    ) {
        if (downloadJob?.isActive == true) return
        val targets =
            bundles.mapNotNull { bundle ->
                _uiState.value.areas.firstOrNull { it.id == bundle.areaId }?.let { area ->
                    RefreshTarget(
                        bundle = bundle,
                        area = area,
                        selection = bundle.toDownloadSelection(),
                    )
                }
            }
        if (targets.size != bundles.size || targets.isEmpty()) {
            _uiState.update {
                it.copy(
                    statusMessage = "Refresh failed",
                    errorMessage = "One or more bundle areas are unknown.",
                    networkWarningMessage = null,
                )
            }
            return
        }
        val networkState = networkMonitor.currentState()
        DebugTelemetry.log(
            OAM_DOWNLOAD_TELEMETRY_TAG,
            "event=refresh_request allowNonWifi=$allowNonWifi bundles=${targets.size} " +
                networkState.telemetryFields,
        )
        if (!allowNonWifi && !networkState.isValidatedWifi) {
            pendingNonWifiRefreshBundles = bundles
            _uiState.update {
                it.copy(
                    statusMessage = "Wi-Fi recommended",
                    errorMessage = null,
                    networkWarningMessage =
                        "${networkState.userLabel}. Connect the watch to Wi-Fi for large downloads, " +
                            "or continue and the app will reconnect automatically if Wi-Fi appears.",
                )
            }
            DebugTelemetry.log(
                OAM_DOWNLOAD_TELEMETRY_TAG,
                "event=wifi_preflight_blocked state=${networkState.userLabel.telemetryValue()} " +
                    networkState.telemetryFields,
            )
            return
        }
        stopRequest = null
        downloadJob =
            viewModelScope.launch {
                notificationController.showProgress(
                    title = "Refreshing bundles",
                    detail = "${targets.size} bundle(s)",
                    bytesDone = 0L,
                    totalBytes = null,
                )
                _uiState.update {
                    it.copy(
                        isDownloading = true,
                        phase = "STARTING",
                        detail = "${targets.size} bundle(s)",
                        bytesDone = 0L,
                        totalBytes = null,
                        isPausedDownload = false,
                        statusMessage = "Refreshing bundles",
                        errorMessage = null,
                        networkWarningMessage = null,
                    )
                }
                try {
                    targets.forEachIndexed { index, target ->
                        downloader.downloadBundle(
                            area = target.area,
                            selection = target.selection,
                            forceMapAndPoi = true,
                            forceRoutingSegments = true,
                            forceDemTiles = true,
                        ) { progress ->
                            val detail = "${index + 1}/${targets.size} ${target.area.region} - ${progress.detail}"
                            notificationController.showProgress(
                                title = "Refreshing offline bundle",
                                detail = detail,
                                bytesDone = progress.bytesDone,
                                totalBytes = progress.totalBytes,
                            )
                            _uiState.update {
                                it.copy(
                                    phase = progress.phase,
                                    detail = detail,
                                    bytesDone = progress.bytesDone,
                                    totalBytes = progress.totalBytes,
                                    statusMessage =
                                        progress.phase
                                            .lowercase()
                                            .replaceFirstChar { char -> char.uppercase() },
                                    errorMessage = null,
                                )
                            }
                        }
                    }
                    val installed = downloader.installedBundles()
                    _uiState.update {
                        it.copy(
                            installedBundles = installed,
                            selectedAreaIds = emptySet(),
                            selectedRefreshBundleIds = emptySet(),
                            isDownloading = false,
                            phase = "READY",
                            detail = "${targets.size} bundle(s)",
                            bytesDone = 0L,
                            totalBytes = null,
                            isPausedDownload = false,
                            statusMessage = if (targets.size == 1) "Bundle refreshed" else "Bundles refreshed",
                            errorMessage = null,
                            networkWarningMessage = null,
                            lastLibraryChangedAtMillis = System.currentTimeMillis(),
                        )
                    }
                    notificationController.showComplete(
                        if (targets.size == 1) {
                            "${targets.first().area.region} refreshed"
                        } else {
                            "${targets.size} bundles refreshed"
                        },
                    )
                } catch (cancelled: CancellationException) {
                    val request = stopRequest ?: DownloadStopRequest.PAUSE
                    if (request == DownloadStopRequest.CANCEL) {
                        targets.forEach { target ->
                            downloader.deletePartialDownloads(listOf(target.area), target.selection)
                        }
                        notificationController.clear()
                    } else {
                        notificationController.showPaused("${targets.size} bundle(s)")
                    }
                    _uiState.update {
                        if (request == DownloadStopRequest.CANCEL) {
                            it.copy(
                                isDownloading = false,
                                phase = "CANCELED",
                                detail = "${targets.size} bundle(s)",
                                bytesDone = 0L,
                                totalBytes = null,
                                isPausedDownload = false,
                                statusMessage = "Refresh canceled",
                                errorMessage = null,
                                networkWarningMessage = null,
                            )
                        } else {
                            it.copy(
                                isDownloading = false,
                                phase = "PAUSED",
                                isPausedDownload = true,
                                statusMessage = "Refresh paused",
                                errorMessage = null,
                                networkWarningMessage = null,
                            )
                        }
                    }
                    throw cancelled
                } catch (error: Throwable) {
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            phase = "FAILED",
                            statusMessage = "Refresh failed",
                            isPausedDownload = false,
                            errorMessage = error.message ?: "Refresh failed",
                            networkWarningMessage = null,
                        )
                    }
                    notificationController.showError(error.message ?: "Refresh failed")
                } finally {
                    downloadJob = null
                    stopRequest = null
                }
            }
    }

    private companion object {
        private const val OAM_DOWNLOAD_TELEMETRY_TAG = "OamDownload"
    }
}

private fun String.telemetryValue(): String = replace(' ', '_')

private enum class DownloadStopRequest {
    PAUSE,
    CANCEL,
}

private data class RefreshTarget(
    val bundle: OamInstalledBundle,
    val area: OamDownloadArea,
    val selection: OamDownloadSelection,
)

private fun OamInstalledBundle.toDownloadSelection(): OamDownloadSelection =
    OamDownloadSelection(
        includeMap = mapFileName != null,
        includePoi = poiFileName != null,
        includeRouting = routingFileNames.isNotEmpty(),
        includeDem = demTileIds.isNotEmpty(),
    )
