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

    @Test
    fun wakeBurstSkipsOnlyForARecentAccurateFixAfterAShortScreenOff() {
        val decision =
            evaluateWakeBurstSkipCandidate(
                fixAgeMs = 1_500L,
                accuracyM = 12f,
                screenOffMs = 5_000L,
            )

        assertTrue(decision.wouldSkip)
    }

    @Test
    fun wakeBurstDoesNotSkipForAnOldOrWeakFix() {
        assertFalse(
            evaluateWakeBurstSkipCandidate(
                fixAgeMs = 2_500L,
                accuracyM = 12f,
                screenOffMs = 5_000L,
            ).wouldSkip,
        )
        assertFalse(
            evaluateWakeBurstSkipCandidate(
                fixAgeMs = 1_500L,
                accuracyM = 36f,
                screenOffMs = 5_000L,
            ).wouldSkip,
        )
    }
}
