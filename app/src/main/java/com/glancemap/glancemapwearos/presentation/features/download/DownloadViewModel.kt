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
    val selectedAreaIds: Set<String> = setOf(OamDownloadCatalog.areas.first().id),
    val selection: OamDownloadSelection = OamDownloadSelection(),
    val installedBundles: List<OamInstalledBundle> = emptyList(),
    val isDownloading: Boolean = false,
    val phase: String? = null,
    val detail: String? = null,
    val bytesDone: Long = 0L,
    val totalBytes: Long? = null,
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null

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
                    errorMessage = "Enable Map or POI in Download settings.",
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
        val choice = state.selection.toBundleChoice()
        downloadJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isDownloading = true,
                        phase = "STARTING",
                        detail = "${areas.size} area(s)",
                        bytesDone = 0L,
                        totalBytes = null,
                        statusMessage = "Starting download",
                        errorMessage = null,
                    )
                }
                try {
                    areas.forEachIndexed { index, area ->
                        downloader.downloadBundle(area, choice) { progress ->
                            _uiState.update {
                                it.copy(
                                    phase = progress.phase,
                                    detail = "${index + 1}/${areas.size} ${area.region} - ${progress.detail}",
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
                            statusMessage = if (areas.size == 1) "Bundle installed" else "Bundles installed",
                            errorMessage = null,
                            lastLibraryChangedAtMillis = System.currentTimeMillis(),
                        )
                    }
                } catch (cancelled: CancellationException) {
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            phase = "PAUSED",
                            statusMessage = "Download paused",
                            errorMessage = null,
                        )
                    }
                    throw cancelled
                } catch (error: Throwable) {
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            phase = "FAILED",
                            statusMessage = "Download failed",
                            errorMessage = error.message ?: "Download failed",
                        )
                    }
                }
            }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
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
