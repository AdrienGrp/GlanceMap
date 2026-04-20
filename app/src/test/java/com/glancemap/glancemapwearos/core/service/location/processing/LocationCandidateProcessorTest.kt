package com.glancemap.glancemapwearos.core.service.location.processing

import com.glancemap.glancemapwearos.core.service.location.activity.LocationActivityTracker
import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import com.glancemap.glancemapwearos.core.service.location.telemetry.LocationServiceTelemetry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationCandidateProcessorTest {
    private val processor =
        LocationCandidateProcessor(
            activityTracker = LocationActivityTracker(movementHistoryDurationMs = 35_000L),
            telemetry =
                LocationServiceTelemetry(
                    tag = "LocationCandidateProcessorTest",
                    summaryIntervalMs = 60_000L,
                ),
            jitterThresholdMoving = 2f,
            jitterThresholdStationary = 8f,
            burstEarlyStopAccuracyM = 20f,
            watchGpsBurstEarlyStopAccuracyM = 130f,
        )

    @Test
    fun autoFusedBurstUsesStrictEarlyStopAccuracy() {
        assertFalse(
            processor.shouldEndBurstEarly(
                isInHighAccuracyBurst = true,
                sourceMode = LocationSourceMode.AUTO_FUSED,
                accuracyM = 125f,
                ageMs = 10L,
                burstEarlyStopMaxAgeMs = 6_000L,
            ),
        )
    }

    @Test
    fun watchGpsBurstAllowsKnownOnePlusAccuracyFloor() {
        assertTrue(
            processor.shouldEndBurstEarly(
                isInHighAccuracyBurst = true,
                sourceMode = LocationSourceMode.WATCH_GPS,
                accuracyM = 125f,
                ageMs = 10L,
                burstEarlyStopMaxAgeMs = 6_000L,
            ),
        )
    }

    @Test
    fun watchGpsBurstStillRejectsTooInaccurateFixForEarlyStop() {
        assertFalse(
            processor.shouldEndBurstEarly(
                isInHighAccuracyBurst = true,
                sourceMode = LocationSourceMode.WATCH_GPS,
                accuracyM = 131f,
                ageMs = 10L,
                burstEarlyStopMaxAgeMs = 6_000L,
            ),
        )
    }
}
