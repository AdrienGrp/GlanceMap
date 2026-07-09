package com.glancemap.glancemapwearos.presentation.features.navigate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.glancemap.glancemapwearos.core.service.location.model.LocationScreenState
import com.glancemap.glancemapwearos.core.service.location.model.isNonInteractive
import com.glancemap.glancemapwearos.core.service.location.model.resolveLocationScreenState
import com.glancemap.glancemapwearos.core.service.location.policy.NavigationRuntimeDemand
import com.glancemap.glancemapwearos.core.service.location.policy.navigationRuntimeDemand
import com.glancemap.glancemapwearos.presentation.features.recording.TraceRecordingUiState
import com.glancemap.glancemapwearos.presentation.features.recording.TraceRecordingViewModel

internal data class NavigateRuntimeState(
    val screenState: LocationScreenState,
    val shouldTrackLocation: Boolean,
    val backgroundGpsEnabled: Boolean,
    val reason: String,
)

@Composable
internal fun rememberNavigateRuntimeState(
    isAmbient: Boolean,
    isDeviceInteractive: Boolean,
    isScreenResumed: Boolean,
    hasLocationPermission: Boolean,
    offlineMode: Boolean,
    generalGpsInAmbient: Boolean,
    traceRecordingState: TraceRecordingUiState,
    recordingScreenOnGpsEnabled: Boolean,
    recordingScreenOffGpsEnabled: Boolean,
    turnByTurnScreenOnGpsEnabled: Boolean,
    turnByTurnScreenOffGpsEnabled: Boolean,
    turnByTurnActive: Boolean,
    turnByTurnPaused: Boolean,
    turnByTurnGpsInAmbient: Boolean,
    locationViewModel: LocationViewModel,
    traceRecordingViewModel: TraceRecordingViewModel,
): NavigateRuntimeState {
    val screenState =
        remember(isAmbient, isDeviceInteractive) {
            resolveLocationScreenState(
                isAmbient = isAmbient,
                isDeviceInteractive = isDeviceInteractive,
            )
        }
    val recordingGpsEnabled =
        if (screenState.isNonInteractive) {
            recordingScreenOffGpsEnabled
        } else {
            recordingScreenOnGpsEnabled
        }
    val turnByTurnGpsEnabled =
        if (screenState.isNonInteractive) {
            turnByTurnScreenOffGpsEnabled
        } else {
            turnByTurnScreenOnGpsEnabled
        }
    val recordingRuntimePaused = traceRecordingState.paused && !traceRecordingState.autoPaused
    val runtimeDemand =
        navigationRuntimeDemand(
            isNavigateScreen = true,
            screenState = screenState,
            isScreenResumed = isScreenResumed,
            hasLocationPermission = hasLocationPermission,
            offlineMode = offlineMode,
            generalGpsInAmbient = generalGpsInAmbient,
            recordingActive = traceRecordingState.active,
            recordingPaused = recordingRuntimePaused,
            recordingAutoPaused = traceRecordingState.autoPaused,
            recordingGpsEnabled = recordingGpsEnabled,
            turnByTurnActive = turnByTurnActive,
            turnByTurnPaused = turnByTurnPaused,
            turnByTurnGpsEnabled = turnByTurnGpsEnabled,
            turnByTurnGpsInAmbient = turnByTurnGpsInAmbient,
        )
    NavigateRuntimeEffects(
        screenState = screenState,
        runtimeDemand = runtimeDemand,
        isScreenResumed = isScreenResumed,
        hasLocationPermission = hasLocationPermission,
        offlineMode = offlineMode,
        traceRecordingState = traceRecordingState,
        recordingGpsEnabled = recordingGpsEnabled,
        locationViewModel = locationViewModel,
        traceRecordingViewModel = traceRecordingViewModel,
    )

    return NavigateRuntimeState(
        screenState = screenState,
        shouldTrackLocation = runtimeDemand.trackingEnabled,
        backgroundGpsEnabled = runtimeDemand.backgroundGpsEnabled,
        reason = runtimeDemand.reason,
    )
}

@Composable
private fun NavigateRuntimeEffects(
    screenState: LocationScreenState,
    runtimeDemand: NavigationRuntimeDemand,
    isScreenResumed: Boolean,
    hasLocationPermission: Boolean,
    offlineMode: Boolean,
    traceRecordingState: TraceRecordingUiState,
    recordingGpsEnabled: Boolean,
    locationViewModel: LocationViewModel,
    traceRecordingViewModel: TraceRecordingViewModel,
) {
    val recordingRuntimePaused = traceRecordingState.paused && !traceRecordingState.autoPaused

    LaunchedEffect(
        screenState,
        runtimeDemand.trackingEnabled,
        runtimeDemand.backgroundGpsEnabled,
        runtimeDemand.reason,
    ) {
        locationViewModel.syncRuntimeState(
            screenState = screenState,
            trackingEnabled = runtimeDemand.trackingEnabled,
            backgroundGpsEnabled = runtimeDemand.backgroundGpsEnabled,
            runtimeReason = runtimeDemand.reason,
        )
    }

    LaunchedEffect(screenState, isScreenResumed, offlineMode, hasLocationPermission) {
        if (
            isScreenResumed &&
            screenState == LocationScreenState.INTERACTIVE &&
            !offlineMode &&
            hasLocationPermission
        ) {
            locationViewModel.requestImmediateLocation(
                source = NAVIGATE_WAKE_REACQUIRE_AMBIENT_EXIT_SOURCE,
            )
        }
    }

    LaunchedEffect(
        screenState,
        isScreenResumed,
        offlineMode,
        hasLocationPermission,
        traceRecordingState.active,
        traceRecordingState.paused,
        traceRecordingState.autoPaused,
        recordingRuntimePaused,
        traceRecordingState.saving,
        recordingGpsEnabled,
    ) {
        if (
            isScreenResumed &&
            screenState == LocationScreenState.INTERACTIVE &&
            !offlineMode &&
            hasLocationPermission &&
            traceRecordingState.active &&
            !traceRecordingState.paused &&
            !traceRecordingState.saving &&
            recordingGpsEnabled
        ) {
            traceRecordingViewModel.onWakeRefreshRequested(UI_RECORDING_WAKE_REFRESH_SOURCE)
            locationViewModel.requestImmediateLocation(source = UI_RECORDING_WAKE_REFRESH_SOURCE)
        }
    }
}
