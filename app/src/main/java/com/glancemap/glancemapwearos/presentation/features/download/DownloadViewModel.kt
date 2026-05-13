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
    val selectedAreaId: String = OamDownloadCatalog.areas.first().id,
    val selectedBundle: OamBundleChoice = OamBundleChoice.MAP_AND_POI,
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
    val selectedArea: OamDownloadArea
        get() = areas.firstOrNull { it.id == selectedAreaId } ?: areas.first()
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

    fun selectArea(areaId: String) {
        if (_uiState.value.isDownloading) return
        _uiState.update { state ->
            state.copy(
                selectedAreaId = areaId,
                statusMessage = null,
                errorMessage = null,
            )
        }
    }

    fun selectBundle(choice: OamBundleChoice) {
        if (_uiState.value.isDownloading) return
        _uiState.update { state ->
            state.copy(
                selectedBundle = choice,
                statusMessage = null,
                errorMessage = null,
            )
        }
    }

    fun downloadSelectedBundle() {
        if (downloadJob?.isActive == true) return
        val state = _uiState.value
        val area = state.selectedArea
        val choice = state.selectedBundle
        downloadJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isDownloading = true,
                        phase = "STARTING",
                        detail = area.region,
                        bytesDone = 0L,
                        totalBytes = null,
                        statusMessage = "Starting download",
                        errorMessage = null,
                    )
                }
                try {
                    downloader.downloadBundle(area, choice) { progress ->
                        _uiState.update {
                            it.copy(
                                phase = progress.phase,
                                detail = progress.detail,
                                bytesDone = progress.bytesDone,
                                totalBytes = progress.totalBytes,
                                statusMessage = progress.phase.lowercase().replaceFirstChar { char -> char.uppercase() },
                                errorMessage = null,
                            )
                        }
                    }
                    val installed = downloader.installedBundles()
                    _uiState.update {
                        it.copy(
                            installedBundles = installed,
                            isDownloading = false,
                            phase = "READY",
                            detail = area.region,
                            bytesDone = 0L,
                            totalBytes = null,
                            statusMessage = "Bundle installed",
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
