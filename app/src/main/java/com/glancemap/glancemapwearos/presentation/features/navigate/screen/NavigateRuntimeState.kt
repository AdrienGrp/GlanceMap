package com.glancemap.glancemapwearos.presentation.features.navigate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import com.glancemap.glancemapwearos.core.service.location.model.LocationScreenState
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
    recordingGpsEnabled: Boolean,
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
    val runtimeDemand =
        navigationRuntimeDemand(
            isNavigateScreen = true,
            screenState = screenState,
            isScreenResumed = isScreenResumed,
            hasLocationPermission = hasLocationPermission,
            offlineMode = offlineMode,
            generalGpsInAmbient = generalGpsInAmbient,
            recordingActive = traceRecordingState.active,
            recordingPaused = traceRecordingState.paused,
            recordingGpsEnabled = recordingGpsEnabled,
            turnByTurnActive = turnByTurnActive,
            turnByTurnPaused = turnByTurnPaused,
            turnByTurnGpsInAmbient = turnByTurnGpsInAmbient,
        )
    val disposeRuntimeDemand by rememberUpdatedState(
        navigationRuntimeDemand(
            isNavigateScreen = false,
            screenState = LocationScreenState.INTERACTIVE,
            isScreenResumed = true,
            hasLocationPermission = hasLocationPermission,
            offlineMode = offlineMode,
            generalGpsInAmbient = generalGpsInAmbient,
            recordingActive = traceRecordingState.active,
            recordingPaused = traceRecordingState.paused,
            recordingGpsEnabled = recordingGpsEnabled,
            turnByTurnActive = turnByTurnActive,
            turnByTurnPaused = turnByTurnPaused,
            turnByTurnGpsInAmbient = turnByTurnGpsInAmbient,
        ),
    )

    NavigateRuntimeEffects(
        screenState = screenState,
        runtimeDemand = runtimeDemand,
        disposeRuntimeDemand = disposeRuntimeDemand,
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
    disposeRuntimeDemand: NavigationRuntimeDemand,
    isScreenResumed: Boolean,
    hasLocationPermission: Boolean,
    offlineMode: Boolean,
    traceRecordingState: TraceRecordingUiState,
    recordingGpsEnabled: Boolean,
    locationViewModel: LocationViewModel,
    traceRecordingViewModel: TraceRecordingViewModel,
) {
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

    DisposableEffect(locationViewModel) {
        onDispose {
            locationViewModel.syncRuntimeState(
                screenState = LocationScreenState.INTERACTIVE,
                trackingEnabled = disposeRuntimeDemand.trackingEnabled,
                backgroundGpsEnabled = disposeRuntimeDemand.backgroundGpsEnabled,
                runtimeReason = disposeRuntimeDemand.reason,
            )
        }
    }
}
