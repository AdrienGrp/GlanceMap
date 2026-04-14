package com.glancemap.glancemapwearos.presentation.features.navigate.effects

import com.glancemap.glancemapwearos.core.service.location.model.LocationScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigateLocationEffectsStaleRefreshTest {
    @Test
    fun interactiveStaleRefreshTriggersAfterPredictionEndsAndMotionStalls() {
        val decision =
            resolveInteractiveStaleRefreshDecision(
                input =
                    InteractiveStaleRefreshInput(
                        shouldTrackLocation = true,
                        screenState = LocationScreenState.INTERACTIVE,
                        holdMarkerUntilFreshFix = false,
                        postWakePredictionHoldActive = false,
                        activeWakeSessionId = 0L,
                        lastFixAtElapsedMs = 10_000L,
                        lastFixFreshMaxAgeMs = 6_000L,
                        lastVisualUpdateAtElapsedMs = 14_500L,
                        lastMotionAdvanceAtElapsedMs = 14_500L,
                        lastRefreshRequestAtElapsedMs = Long.MIN_VALUE,
                        predictionFreshnessMaxAgeMs = 4_500L,
                        nowElapsedMs = 15_800L,
                    ),
            )

        assertTrue(decision.shouldRequest)
        assertEquals("motion_stalled", decision.reason)
        assertEquals(5_800L, decision.fixAgeMs)
        assertEquals(1_300L, decision.visualAgeMs)
        assertEquals(1_300L, decision.motionIdleMs)
    }

    @Test
    fun interactiveStaleRefreshSkipsDuringWakeRecovery() {
        val decision =
            resolveInteractiveStaleRefreshDecision(
                input =
                    InteractiveStaleRefreshInput(
                        shouldTrackLocation = true,
                        screenState = LocationScreenState.INTERACTIVE,
                        holdMarkerUntilFreshFix = true,
                        postWakePredictionHoldActive = false,
                        activeWakeSessionId = 7L,
                        lastFixAtElapsedMs = 10_000L,
                        lastFixFreshMaxAgeMs = 6_000L,
                        lastVisualUpdateAtElapsedMs = 10_000L,
                        lastMotionAdvanceAtElapsedMs = 10_000L,
                        lastRefreshRequestAtElapsedMs = Long.MIN_VALUE,
                        predictionFreshnessMaxAgeMs = 4_500L,
                        nowElapsedMs = 17_000L,
                    ),
            )

        assertFalse(decision.shouldRequest)
        assertEquals("wake_recovery_active", decision.reason)
    }

    @Test
    fun interactiveStaleRefreshHonorsCooldown() {
        val decision =
            resolveInteractiveStaleRefreshDecision(
                input =
                    InteractiveStaleRefreshInput(
                        shouldTrackLocation = true,
                        screenState = LocationScreenState.INTERACTIVE,
                        holdMarkerUntilFreshFix = false,
                        postWakePredictionHoldActive = false,
                        activeWakeSessionId = 0L,
                        lastFixAtElapsedMs = 10_000L,
                        lastFixFreshMaxAgeMs = 6_000L,
                        lastVisualUpdateAtElapsedMs = 16_000L,
                        lastMotionAdvanceAtElapsedMs = 16_000L,
                        lastRefreshRequestAtElapsedMs = 15_000L,
                        predictionFreshnessMaxAgeMs = 4_500L,
                        nowElapsedMs = 20_000L,
                    ),
            )

        assertFalse(decision.shouldRequest)
        assertEquals("cooldown", decision.reason)
    }

    @Test
    fun interactiveStaleRefreshWaitsWhilePredictionWindowIsStillActive() {
        val decision =
            resolveInteractiveStaleRefreshDecision(
                input =
                    InteractiveStaleRefreshInput(
                        shouldTrackLocation = true,
                        screenState = LocationScreenState.INTERACTIVE,
                        holdMarkerUntilFreshFix = false,
                        postWakePredictionHoldActive = false,
                        activeWakeSessionId = 0L,
                        lastFixAtElapsedMs = 10_000L,
                        lastFixFreshMaxAgeMs = 6_000L,
                        lastVisualUpdateAtElapsedMs = 13_900L,
                        lastMotionAdvanceAtElapsedMs = 13_900L,
                        lastRefreshRequestAtElapsedMs = Long.MIN_VALUE,
                        predictionFreshnessMaxAgeMs = 4_500L,
                        nowElapsedMs = 15_000L,
                    ),
            )

        assertFalse(decision.shouldRequest)
        assertEquals("prediction_active", decision.reason)
    }

    @Test
    fun interactiveStaleRefreshWaitsWhenMarkerStoppedOnlyMomentarily() {
        val decision =
            resolveInteractiveStaleRefreshDecision(
                input =
                    InteractiveStaleRefreshInput(
                        shouldTrackLocation = true,
                        screenState = LocationScreenState.INTERACTIVE,
                        holdMarkerUntilFreshFix = false,
                        postWakePredictionHoldActive = false,
                        activeWakeSessionId = 0L,
                        lastFixAtElapsedMs = 10_000L,
                        lastFixFreshMaxAgeMs = 6_000L,
                        lastVisualUpdateAtElapsedMs = 15_100L,
                        lastMotionAdvanceAtElapsedMs = 15_100L,
                        lastRefreshRequestAtElapsedMs = Long.MIN_VALUE,
                        predictionFreshnessMaxAgeMs = 4_500L,
                        nowElapsedMs = 15_800L,
                    ),
            )

        assertFalse(decision.shouldRequest)
        assertEquals("motion_recent", decision.reason)
    }
}
