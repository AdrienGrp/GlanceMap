package com.glancemap.glancemapwearos.presentation.features.navigate.effects

import com.glancemap.glancemapwearos.core.service.location.model.LocationScreenState
import com.glancemap.glancemapwearos.domain.sensors.CompassProviderType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigateCompassEffectsStateTest {

    @Test
    fun compassRunsOnlyWhenResumedInteractiveAndOnline() {
        assertTrue(
            shouldRunNavigateCompass(
                isResumed = true,
                screenState = LocationScreenState.INTERACTIVE,
                isOfflineMode = false
            )
        )
        assertFalse(
            shouldRunNavigateCompass(
                isResumed = true,
                screenState = LocationScreenState.AMBIENT,
                isOfflineMode = false
            )
        )
        assertFalse(
            shouldRunNavigateCompass(
                isResumed = true,
                screenState = LocationScreenState.SCREEN_OFF,
                isOfflineMode = false
            )
        )
        assertFalse(
            shouldRunNavigateCompass(
                isResumed = false,
                screenState = LocationScreenState.INTERACTIVE,
                isOfflineMode = false
            )
        )
        assertFalse(
            shouldRunNavigateCompass(
                isResumed = true,
                screenState = LocationScreenState.INTERACTIVE,
                isOfflineMode = true
            )
        )
    }

    @Test
    fun nonInteractiveAndOfflineStopsAreImmediate() {
        assertTrue(
            shouldStopNavigateCompassImmediately(
                screenState = LocationScreenState.SCREEN_OFF,
                isOfflineMode = false
            )
        )
        assertTrue(
            shouldStopNavigateCompassImmediately(
                screenState = LocationScreenState.AMBIENT,
                isOfflineMode = false
            )
        )
        assertTrue(
            shouldStopNavigateCompassImmediately(
                screenState = LocationScreenState.INTERACTIVE,
                isOfflineMode = true
            )
        )
        assertFalse(
            shouldStopNavigateCompassImmediately(
                screenState = LocationScreenState.INTERACTIVE,
                isOfflineMode = false
            )
        )
    }

    @Test
    fun stopReasonReflectsWhyCompassWasStopped() {
        org.junit.Assert.assertEquals(
            "screen_off",
            resolveNavigateCompassStopReason(
                isResumed = false,
                screenState = LocationScreenState.SCREEN_OFF,
                isOfflineMode = false
            )
        )
        org.junit.Assert.assertEquals(
            "ambient",
            resolveNavigateCompassStopReason(
                isResumed = false,
                screenState = LocationScreenState.AMBIENT,
                isOfflineMode = false
            )
        )
        org.junit.Assert.assertEquals(
            "offline_mode",
            resolveNavigateCompassStopReason(
                isResumed = true,
                screenState = LocationScreenState.INTERACTIVE,
                isOfflineMode = true
            )
        )
        org.junit.Assert.assertEquals(
            "lifecycle_pause",
            resolveNavigateCompassStopReason(
                isResumed = false,
                screenState = LocationScreenState.INTERACTIVE,
                isOfflineMode = false
            )
        )
    }

    @Test
    fun googleFusedGetsTransientStopGraceWhenScreenTurnsOff() {
        assertEquals(
            10_000L,
            resolveNavigateCompassImmediateStopDelayMs(
                compassProviderType = CompassProviderType.GOOGLE_FUSED,
                screenState = LocationScreenState.SCREEN_OFF,
                isOfflineMode = false
            )
        )
        assertEquals(
            10_000L,
            resolveNavigateCompassImmediateStopDelayMs(
                compassProviderType = CompassProviderType.GOOGLE_FUSED,
                screenState = LocationScreenState.AMBIENT,
                isOfflineMode = false
            )
        )
    }

    @Test
    fun customSensorsAndOfflineModeStillStopImmediately() {
        assertEquals(
            0L,
            resolveNavigateCompassImmediateStopDelayMs(
                compassProviderType = CompassProviderType.SENSOR_MANAGER,
                screenState = LocationScreenState.SCREEN_OFF,
                isOfflineMode = false
            )
        )
        assertEquals(
            0L,
            resolveNavigateCompassImmediateStopDelayMs(
                compassProviderType = CompassProviderType.GOOGLE_FUSED,
                screenState = LocationScreenState.SCREEN_OFF,
                isOfflineMode = true
            )
        )
    }
}
