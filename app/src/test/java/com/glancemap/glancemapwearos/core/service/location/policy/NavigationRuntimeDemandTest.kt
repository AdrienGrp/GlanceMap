package com.glancemap.glancemapwearos.core.service.location.policy

import com.glancemap.glancemapwearos.core.service.location.model.LocationScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRuntimeDemandTest {
    @Test
    fun normalNavigateVisibleTracksWithoutBackgroundGpsDemand() {
        val demand =
            demand(
                isNavigateScreen = true,
                screenState = LocationScreenState.INTERACTIVE,
                isScreenResumed = true,
            )

        assertTrue(demand.trackingEnabled)
        assertFalse(demand.backgroundGpsEnabled)
        assertEquals(NavigationRuntimeDemandReason.NAVIGATE_VISIBLE, demand.reason)
    }

    @Test
    fun normalNonNavigateDoesNotTrack() {
        val demand =
            demand(
                isNavigateScreen = false,
                screenState = LocationScreenState.INTERACTIVE,
                isScreenResumed = true,
            )

        assertFalse(demand.trackingEnabled)
        assertFalse(demand.backgroundGpsEnabled)
        assertEquals(NavigationRuntimeDemandReason.IDLE, demand.reason)
    }

    @Test
    fun recordingActiveForcesTrackingAndBackgroundGps() {
        val demand =
            demand(
                isNavigateScreen = false,
                screenState = LocationScreenState.SCREEN_OFF,
                isScreenResumed = false,
                recordingActive = true,
                recordingPaused = false,
            )

        assertTrue(demand.trackingEnabled)
        assertTrue(demand.backgroundGpsEnabled)
        assertEquals(NavigationRuntimeDemandReason.RECORDING, demand.reason)
    }

    @Test
    fun recordingPausedFallsBackToDefault() {
        val demand =
            demand(
                isNavigateScreen = false,
                screenState = LocationScreenState.SCREEN_OFF,
                isScreenResumed = false,
                recordingActive = true,
                recordingPaused = true,
            )

        assertFalse(demand.trackingEnabled)
        assertFalse(demand.backgroundGpsEnabled)
        assertEquals(NavigationRuntimeDemandReason.IDLE, demand.reason)
    }

    @Test
    fun generalAmbientGpsDoesNotKeepGpsAliveOutsideNavigate() {
        val demand =
            demand(
                isNavigateScreen = false,
                screenState = LocationScreenState.SCREEN_OFF,
                isScreenResumed = false,
                generalGpsInAmbient = true,
            )

        assertFalse(demand.trackingEnabled)
        assertFalse(demand.backgroundGpsEnabled)
        assertEquals(NavigationRuntimeDemandReason.IDLE, demand.reason)
    }

    @Test
    fun turnByTurnVisibleNavigateTracksWithoutAmbientSetting() {
        val demand =
            demand(
                isNavigateScreen = true,
                screenState = LocationScreenState.INTERACTIVE,
                isScreenResumed = true,
                turnByTurnActive = true,
                turnByTurnGpsInAmbient = false,
            )

        assertTrue(demand.trackingEnabled)
        assertFalse(demand.backgroundGpsEnabled)
        assertEquals(NavigationRuntimeDemandReason.NAVIGATE_VISIBLE, demand.reason)
    }

    @Test
    fun turnByTurnScreenOffRequiresGuidanceAmbientSetting() {
        val demand =
            demand(
                isNavigateScreen = true,
                screenState = LocationScreenState.SCREEN_OFF,
                isScreenResumed = false,
                turnByTurnActive = true,
                turnByTurnGpsInAmbient = false,
            )

        assertFalse(demand.trackingEnabled)
        assertFalse(demand.backgroundGpsEnabled)
        assertEquals(NavigationRuntimeDemandReason.IDLE, demand.reason)
    }

    @Test
    fun turnByTurnGuidanceAmbientKeepsGpsInScreenOff() {
        val demand =
            demand(
                isNavigateScreen = true,
                screenState = LocationScreenState.SCREEN_OFF,
                isScreenResumed = false,
                turnByTurnActive = true,
                turnByTurnGpsInAmbient = true,
            )

        assertTrue(demand.trackingEnabled)
        assertTrue(demand.backgroundGpsEnabled)
        assertEquals(NavigationRuntimeDemandReason.GUIDANCE_AMBIENT, demand.reason)
    }

    @Test
    fun turnByTurnOutsideNavigateRequiresGuidanceAmbientSetting() {
        val withoutSetting =
            demand(
                isNavigateScreen = false,
                screenState = LocationScreenState.INTERACTIVE,
                isScreenResumed = true,
                turnByTurnActive = true,
                turnByTurnGpsInAmbient = false,
            )
        val withSetting =
            demand(
                isNavigateScreen = false,
                screenState = LocationScreenState.INTERACTIVE,
                isScreenResumed = true,
                turnByTurnActive = true,
                turnByTurnGpsInAmbient = true,
            )

        assertFalse(withoutSetting.trackingEnabled)
        assertFalse(withoutSetting.backgroundGpsEnabled)
        assertTrue(withSetting.trackingEnabled)
        assertTrue(withSetting.backgroundGpsEnabled)
        assertEquals(NavigationRuntimeDemandReason.GUIDANCE_BACKGROUND, withSetting.reason)
    }

    @Test
    fun keepPermissionAndOfflineGatesAllDemand() {
        assertFalse(demand(hasLocationPermission = false, recordingActive = true).trackingEnabled)
        assertFalse(demand(offlineMode = true, recordingActive = true).trackingEnabled)
    }

    private fun demand(
        isNavigateScreen: Boolean = true,
        screenState: LocationScreenState = LocationScreenState.INTERACTIVE,
        isScreenResumed: Boolean = true,
        hasLocationPermission: Boolean = true,
        offlineMode: Boolean = false,
        generalGpsInAmbient: Boolean = false,
        recordingActive: Boolean = false,
        recordingPaused: Boolean = false,
        turnByTurnActive: Boolean = false,
        turnByTurnPaused: Boolean = false,
        turnByTurnGpsInAmbient: Boolean = false,
    ): NavigationRuntimeDemand =
        navigationRuntimeDemand(
            isNavigateScreen = isNavigateScreen,
            screenState = screenState,
            isScreenResumed = isScreenResumed,
            hasLocationPermission = hasLocationPermission,
            offlineMode = offlineMode,
            generalGpsInAmbient = generalGpsInAmbient,
            recordingActive = recordingActive,
            recordingPaused = recordingPaused,
            turnByTurnActive = turnByTurnActive,
            turnByTurnPaused = turnByTurnPaused,
            turnByTurnGpsInAmbient = turnByTurnGpsInAmbient,
        )
}
