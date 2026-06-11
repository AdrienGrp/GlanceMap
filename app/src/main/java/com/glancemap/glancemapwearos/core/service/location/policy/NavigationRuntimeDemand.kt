package com.glancemap.glancemapwearos.core.service.location.policy

import com.glancemap.glancemapwearos.core.service.location.model.LocationScreenState
import com.glancemap.glancemapwearos.core.service.location.model.isNonInteractive

data class NavigationRuntimeDemand(
    val trackingEnabled: Boolean,
    val backgroundGpsEnabled: Boolean,
    val reason: String,
)

fun navigationRuntimeDemand(
    isNavigateScreen: Boolean,
    screenState: LocationScreenState,
    isScreenResumed: Boolean,
    hasLocationPermission: Boolean,
    offlineMode: Boolean,
    generalGpsInAmbient: Boolean,
    recordingActive: Boolean,
    recordingPaused: Boolean,
    turnByTurnActive: Boolean,
    turnByTurnPaused: Boolean,
    turnByTurnGpsInAmbient: Boolean,
): NavigationRuntimeDemand {
    if (!hasLocationPermission) return NavigationRuntimeDemand(false, false, NavigationRuntimeDemandReason.NO_PERMISSION)
    if (offlineMode) return NavigationRuntimeDemand(false, false, NavigationRuntimeDemandReason.OFFLINE)

    val recordingDemand = recordingActive && !recordingPaused
    val guidanceDemand = turnByTurnActive && !turnByTurnPaused
    val guidanceBackgroundDemand = guidanceDemand && turnByTurnGpsInAmbient
    val generalBackgroundDemand = isNavigateScreen && generalGpsInAmbient
    val backgroundGpsEnabled =
        generalBackgroundDemand ||
            guidanceBackgroundDemand ||
            recordingDemand
    val backgroundGpsModeActive = backgroundGpsEnabled && screenState.isNonInteractive
    val navigateVisibleDemand = isNavigateScreen && isScreenResumed
    val guidanceOutsideNavigateDemand = !isNavigateScreen && guidanceBackgroundDemand
    val trackingEnabled =
        navigateVisibleDemand ||
            backgroundGpsModeActive ||
            recordingDemand ||
            guidanceOutsideNavigateDemand

    val reason =
        when {
            recordingDemand -> NavigationRuntimeDemandReason.RECORDING
            guidanceOutsideNavigateDemand -> NavigationRuntimeDemandReason.GUIDANCE_BACKGROUND
            backgroundGpsModeActive && guidanceBackgroundDemand -> NavigationRuntimeDemandReason.GUIDANCE_AMBIENT
            backgroundGpsModeActive && generalBackgroundDemand -> NavigationRuntimeDemandReason.GENERAL_AMBIENT
            navigateVisibleDemand -> NavigationRuntimeDemandReason.NAVIGATE_VISIBLE
            else -> NavigationRuntimeDemandReason.IDLE
        }

    return NavigationRuntimeDemand(
        trackingEnabled = trackingEnabled,
        backgroundGpsEnabled = backgroundGpsEnabled,
        reason = reason,
    )
}

object NavigationRuntimeDemandReason {
    const val NO_PERMISSION = "no_permission"
    const val OFFLINE = "offline"
    const val IDLE = "idle"
    const val NAVIGATE_VISIBLE = "navigate_visible"
    const val GENERAL_AMBIENT = "general_ambient"
    const val GUIDANCE_AMBIENT = "guidance_ambient"
    const val GUIDANCE_BACKGROUND = "guidance_background"
    const val RECORDING = "recording"
}
