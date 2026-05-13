package com.glancemap.glancemapcompanionapp.livetracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveTrackingUiState(
    val isTracking: Boolean = false,
    val status: String = "Not started",
    val lastSuccessfulUpdateEpochMs: Long? = null,
    val lastError: String? = null,
)

object LiveTrackingSessionStore {
    private val _state = MutableStateFlow(LiveTrackingUiState())
    val state = _state.asStateFlow()

    fun setStarting() {
        _state.value =
            _state.value.copy(
                isTracking = true,
                status = "Starting",
                lastError = null,
            )
    }

    fun setStatus(status: String) {
        _state.value =
            _state.value.copy(
                isTracking = true,
                status = status,
                lastError = null,
            )
    }

    fun setSent(message: String = "Position sent") {
        _state.value =
            _state.value.copy(
                isTracking = true,
                status = message,
                lastSuccessfulUpdateEpochMs = System.currentTimeMillis(),
                lastError = null,
            )
    }

    fun setError(message: String) {
        _state.value =
            _state.value.copy(
                status = "Tracking with errors",
                lastError = message,
            )
    }

    fun setStoppedWithError(
        status: String,
        message: String,
    ) {
        _state.value =
            _state.value.copy(
                isTracking = false,
                status = status,
                lastError = message,
            )
    }

    fun setStopped(status: String = "Stopped") {
        _state.value =
            _state.value.copy(
                isTracking = false,
                status = status,
            )
    }
}
