package com.glancemap.glancemapwearos.presentation.features.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val statusMessage: String? = null,
    val errorMessage: String? = null,
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null
    private var stopRequest: DownloadStopRequest? = null

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
            )
        }
    }

    fun downloadSelectedBundle() {
        if (downloadJob?.isActive == true) return
        val state = _uiState.value
        if (!state.selection.canDownload) {
            _uiState.update {
                it.copy(
                    statusMessage = "Nothing selected",
                    errorMessage = "Enable Maps, POI, or Routing in Download settings.",
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
                )
            }
            return
        }
        val selection = state.selection
        stopRequest = null
        downloadJob =
            viewModelScope.launch {
                notificationController.showProgress(
                    title = "Downloading maps",
                    detail = "${areas.size} area(s)",
                    bytesDone = 0L,
                    totalBytes = null,
                )
                _uiState.update {
                    it.copy(
                        isDownloading = true,
                        phase = "STARTING",
                        detail = "${areas.size} area(s)",
                        bytesDone = 0L,
                        totalBytes = null,
                        isPausedDownload = false,
                        statusMessage = "Starting download",
                        errorMessage = null,
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
                                    statusMessage = progress.phase.lowercase().replaceFirstChar { char -> char.uppercase() },
                                    errorMessage = null,
                                )
                            }
                        }
                    }
                    val installed = downloader.installedBundles()
                    _uiState.update {
                        it.copy(
                            installedBundles = installed,
                            isDownloading = false,
                            phase = "READY",
                            detail = "${areas.size} area(s)",
                            bytesDone = 0L,
                            totalBytes = null,
                            isPausedDownload = false,
                            statusMessage = if (areas.size == 1) "Bundle installed" else "Bundles installed",
                            errorMessage = null,
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
                            )
                        } else {
                            it.copy(
                                isDownloading = false,
                                phase = "PAUSED",
                                isPausedDownload = true,
                                statusMessage = "Download paused",
                                errorMessage = null,
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
                        )
                    }
                    notificationController.showError(error.message ?: "Download failed")
                } finally {
                    downloadJob = null
                    stopRequest = null
                }
            }
    }

    fun pauseDownload() {
        stopRequest = DownloadStopRequest.PAUSE
        downloader.abortActiveDownloads()
        downloadJob?.cancel()
    }

    fun cancelDownload() {
        stopRequest = DownloadStopRequest.CANCEL
        downloader.abortActiveDownloads()
        downloadJob?.cancel()
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
                        lastLibraryChangedAtMillis = System.currentTimeMillis(),
                    )
                }
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        statusMessage = "Delete failed",
                        errorMessage = error.message ?: "Delete failed",
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
}

private enum class DownloadStopRequest {
    PAUSE,
    CANCEL,
}
