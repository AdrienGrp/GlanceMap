package com.glancemap.glancemapwearos.presentation.features.navigate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationViewModelSupportTest {
    @Test
    fun startupFreshFixRequestAlwaysBypassesFreshnessSkip() {
        assertTrue(shouldForceUiImmediateLocationRequest("ui_startup_fresh_fix"))
    }

    @Test
    fun wakeTimeoutRequestAlwaysBypassesFreshnessSkip() {
        assertTrue(shouldForceUiImmediateLocationRequest(UI_WAKE_REACQUIRE_TIMEOUT_SOURCE))
    }

    @Test
    fun ordinaryUiRequestStillUsesFreshnessSkip() {
        assertFalse(shouldForceUiImmediateLocationRequest("ui_unknown"))
    }
}
