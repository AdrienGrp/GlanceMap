package com.glancemap.glancemapwearos.core.service.location

import com.glancemap.glancemapwearos.core.service.location.activity.LocationActivityClassifier
import com.glancemap.glancemapwearos.core.service.location.activity.LocationActivityState
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationActivityClassifierTest {
    @Test
    fun startsInActiveState() {
        val classifier = LocationActivityClassifier()
        assertEquals(LocationActivityState.ACTIVE, classifier.state)
    }

    @Test
    fun entersStationaryOnlyAfterConfirmationWindow() {
        val classifier = LocationActivityClassifier()
        classifier.reset(LocationActivityState.ACTIVE)

        assertEquals(
            LocationActivityState.ACTIVE,
            classifier.evaluate(
                nowElapsedMs = 1_000L,
                speedMps = 0.1f,
                hasEnterWindowHistory = true,
                enterDisplacementMeters = 2f,
                hasExitWindowHistory = false,
                exitDisplacementMeters = 0f,
            ),
        )

        assertEquals(
            LocationActivityState.ACTIVE,
            classifier.evaluate(
                nowElapsedMs = 8_999L,
                speedMps = 0.1f,
                hasEnterWindowHistory = true,
                enterDisplacementMeters = 2f,
                hasExitWindowHistory = false,
                exitDisplacementMeters = 0f,
            ),
        )

        assertEquals(
            LocationActivityState.STATIONARY,
            classifier.evaluate(
                nowElapsedMs = 9_000L,
                speedMps = 0.1f,
                hasEnterWindowHistory = true,
                enterDisplacementMeters = 2f,
                hasExitWindowHistory = false,
                exitDisplacementMeters = 0f,
            ),
        )
    }

    @Test
    fun enterStationaryCandidateResetsWhenConditionBreaks() {
        val classifier = LocationActivityClassifier()
        classifier.reset(LocationActivityState.ACTIVE)

        classifier.evaluate(
            nowElapsedMs = 1_000L,
            speedMps = 0.1f,
            hasEnterWindowHistory = true,
            enterDisplacementMeters = 2f,
            hasExitWindowHistory = false,
            exitDisplacementMeters = 0f,
        )

        // Break enter condition with large displacement.
        classifier.evaluate(
            nowElapsedMs = 5_000L,
            speedMps = 0.1f,
            hasEnterWindowHistory = true,
            enterDisplacementMeters = 40f,
            hasExitWindowHistory = false,
            exitDisplacementMeters = 0f,
        )

        assertEquals(
            LocationActivityState.ACTIVE,
            classifier.evaluate(
                nowElapsedMs = 6_000L,
                speedMps = 0.1f,
                hasEnterWindowHistory = true,
                enterDisplacementMeters = 2f,
                hasExitWindowHistory = false,
                exitDisplacementMeters = 0f,
            ),
        )

        assertEquals(
            LocationActivityState.ACTIVE,
            classifier.evaluate(
                nowElapsedMs = 13_999L,
                speedMps = 0.1f,
                hasEnterWindowHistory = true,
                enterDisplacementMeters = 2f,
                hasExitWindowHistory = false,
                exitDisplacementMeters = 0f,
            ),
        )

        assertEquals(
            LocationActivityState.STATIONARY,
            classifier.evaluate(
                nowElapsedMs = 14_000L,
                speedMps = 0.1f,
                hasEnterWindowHistory = true,
                enterDisplacementMeters = 2f,
                hasExitWindowHistory = false,
                exitDisplacementMeters = 0f,
            ),
        )
    }

    @Test
    fun exitsStationaryToActiveOnlyAfterConfirmationWindow() {
        val classifier = LocationActivityClassifier()
        classifier.reset(LocationActivityState.STATIONARY)

        assertEquals(
            LocationActivityState.STATIONARY,
            classifier.evaluate(
                nowElapsedMs = 1_000L,
                speedMps = 0.8f,
                hasEnterWindowHistory = true,
                enterDisplacementMeters = 1f,
                hasExitWindowHistory = true,
                exitDisplacementMeters = 25f,
            ),
        )

        assertEquals(
            LocationActivityState.STATIONARY,
            classifier.evaluate(
                nowElapsedMs = 4_999L,
                speedMps = 0.8f,
                hasEnterWindowHistory = true,
                enterDisplacementMeters = 1f,
                hasExitWindowHistory = true,
                exitDisplacementMeters = 25f,
            ),
        )

        assertEquals(
            LocationActivityState.ACTIVE,
            classifier.evaluate(
                nowElapsedMs = 5_000L,
                speedMps = 0.8f,
                hasEnterWindowHistory = true,
                enterDisplacementMeters = 1f,
                hasExitWindowHistory = true,
                exitDisplacementMeters = 25f,
            ),
        )
    }

    @Test
    fun normalUsageScenario_walkStopWalk_transitionsAsExpected() {
        val classifier = LocationActivityClassifier()
        classifier.reset(LocationActivityState.ACTIVE)

        // Moving phase: should remain ACTIVE.
        assertEquals(
            LocationActivityState.ACTIVE,
            classifier.evaluate(
                nowElapsedMs = 1_000L,
                speedMps = 1.2f,
                hasEnterWindowHistory = true,
                enterDisplacementMeters = 40f,
                hasExitWindowHistory = true,
                exitDisplacementMeters = 40f,
            ),
        )

        // User slows/stops: stationary candidate starts but not confirmed yet.
        assertEquals(
            LocationActivityState.ACTIVE,
            classifier.evaluate(
                nowElapsedMs = 10_000L,
                speedMps = 0.1f,
                hasEnterWindowHistory = true,
                enterDisplacementMeters = 5f,
                hasExitWindowHistory = true,
                exitDisplacementMeters = 3f,
            ),
        )
        assertEquals(
            LocationActivityState.ACTIVE,
            classifier.evaluate(
                nowElapsedMs = 17_999L,
                speedMps = 0.1f,
                hasEnterWindowHistory = true,
                enterDisplacementMeters = 5f,
                hasExitWindowHistory = true,
                exitDisplacementMeters = 3f,
            ),
        )

        // After confirm window, should enter STATIONARY.
        assertEquals(
            LocationActivityState.STATIONARY,
            classifier.evaluate(
                nowElapsedMs = 18_000L,
                speedMps = 0.1f,
                hasEnterWindowHistory = true,
                enterDisplacementMeters = 5f,
                hasExitWindowHistory = true,
                exitDisplacementMeters = 3f,
            ),
        )

        // Starts moving again: active candidate starts, still STATIONARY before confirmation.
        assertEquals(
            LocationActivityState.STATIONARY,
            classifier.evaluate(
                nowElapsedMs = 20_000L,
                speedMps = 0.95f,
                hasEnterWindowHistory = true,
                enterDisplacementMeters = 20f,
                hasExitWindowHistory = true,
                exitDisplacementMeters = 28f,
            ),
        )
        assertEquals(
            LocationActivityState.STATIONARY,
            classifier.evaluate(
                nowElapsedMs = 23_999L,
                speedMps = 0.95f,
                hasEnterWindowHistory = true,
                enterDisplacementMeters = 20f,
                hasExitWindowHistory = true,
                exitDisplacementMeters = 28f,
            ),
        )

        // After confirm window, should return to ACTIVE.
        assertEquals(
            LocationActivityState.ACTIVE,
            classifier.evaluate(
                nowElapsedMs = 24_000L,
                speedMps = 0.95f,
                hasEnterWindowHistory = true,
                enterDisplacementMeters = 20f,
                hasExitWindowHistory = true,
                exitDisplacementMeters = 28f,
            ),
        )
    }
}
