package com.glancemap.glancemapwearos.core.service.location.model

import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsSignalTrackerTest {
    @Test
    fun doesNotEnterDegradedStateForKnownWatchGpsAccuracyFloor() {
        val tracker = GpsSignalTracker()

        repeat(6) { index ->
            tracker.onGpsSignalSample(
                nowElapsedMs = 10_000L + index,
                ageMs = 100L,
                accuracyM = 125f,
                freshnessMaxAgeMs = 6_000L,
                sourceMode = LocationSourceMode.WATCH_GPS,
            )
        }

        assertTrue(tracker.snapshot.watchGpsOnlyActive)
        assertFalse(tracker.snapshot.watchGpsDegraded)
        assertTrue(tracker.snapshot.watchGpsDegradedFixStreak == 0)
        assertTrue(tracker.snapshot.watchGpsDegradedSinceElapsedMs == 0L)
    }

    @Test
    fun entersDegradedStateAfterConsecutivePoorWatchGpsFixesOutsideFloorWindow() {
        val tracker = GpsSignalTracker()

        repeat(4) { index ->
            tracker.onGpsSignalSample(
                nowElapsedMs = 12_000L + index,
                ageMs = 120L,
                accuracyM = 110f,
                freshnessMaxAgeMs = 6_000L,
                sourceMode = LocationSourceMode.WATCH_GPS,
            )
        }

        assertTrue(tracker.snapshot.watchGpsOnlyActive)
        assertTrue(tracker.snapshot.watchGpsDegraded)
        assertTrue(tracker.snapshot.watchGpsDegradedFixStreak >= 4)
        assertTrue(tracker.snapshot.watchGpsDegradedSinceElapsedMs > 0L)
    }

    @Test
    fun clearsDegradedStateWhenLeavingWatchGpsMode() {
        val tracker = GpsSignalTracker()

        repeat(4) { index ->
            tracker.onGpsSignalSample(
                nowElapsedMs = 20_000L + index,
                ageMs = 90L,
                accuracyM = 110f,
                freshnessMaxAgeMs = 6_000L,
                sourceMode = LocationSourceMode.WATCH_GPS,
            )
        }
        assertTrue(tracker.snapshot.watchGpsDegraded)

        tracker.onSourceModeChanged(LocationSourceMode.AUTO_FUSED)

        assertFalse(tracker.snapshot.watchGpsOnlyActive)
        assertFalse(tracker.snapshot.watchGpsDegraded)
        assertTrue(tracker.snapshot.watchGpsDegradedFixStreak == 0)
        assertTrue(tracker.snapshot.watchGpsDegradedSinceElapsedMs == 0L)
    }
}
