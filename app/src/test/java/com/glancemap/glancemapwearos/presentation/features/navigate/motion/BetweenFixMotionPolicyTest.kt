package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import com.glancemap.glancemapwearos.presentation.features.navigate.CompassMarkerQuality
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BetweenFixMotionPolicyTest {
    @Test
    fun allowsPredictionInsideUiFreshnessGate() {
        val allowed =
            BetweenFixMotionPolicy.allowPrediction(
                inputs =
                    BetweenFixMotionInputs(
                        compassQuality = CompassMarkerQuality.GOOD,
                        gpsAccuracyM = 12f,
                        gpsFixAgeMs = 4_200L,
                        gpsFreshMaxAgeMs = 6_000L,
                        predictionFreshnessMaxAgeMs = 4_500L,
                        watchGpsDegraded = false,
                        allowLowQualityCompassPrediction = false,
                    ),
            )

        assertTrue(allowed)
    }

    @Test
    fun blocksPredictionOutsideUiFreshnessGateEvenWhenServiceFreshnessIsLooser() {
        val allowed =
            BetweenFixMotionPolicy.allowPrediction(
                inputs =
                    BetweenFixMotionInputs(
                        compassQuality = CompassMarkerQuality.GOOD,
                        gpsAccuracyM = 12f,
                        gpsFixAgeMs = 5_100L,
                        gpsFreshMaxAgeMs = 6_000L,
                        predictionFreshnessMaxAgeMs = 4_500L,
                        watchGpsDegraded = false,
                        allowLowQualityCompassPrediction = false,
                    ),
            )

        assertFalse(allowed)
    }

    @Test
    fun allowsLowCompassPredictionWhenOverrideIsActiveAndGpsIsStrong() {
        val allowed =
            BetweenFixMotionPolicy.allowPrediction(
                inputs =
                    BetweenFixMotionInputs(
                        compassQuality = CompassMarkerQuality.LOW,
                        gpsAccuracyM = 10f,
                        gpsFixAgeMs = 2_000L,
                        gpsFreshMaxAgeMs = 6_000L,
                        predictionFreshnessMaxAgeMs = 4_500L,
                        watchGpsDegraded = false,
                        allowLowQualityCompassPrediction = true,
                    ),
            )

        assertTrue(allowed)
    }

    @Test
    fun blocksLowCompassPredictionWhenOverrideIsInactiveOrGpsIsWeak() {
        val overrideInactive =
            BetweenFixMotionPolicy.allowPrediction(
                inputs =
                    BetweenFixMotionInputs(
                        compassQuality = CompassMarkerQuality.LOW,
                        gpsAccuracyM = 10f,
                        gpsFixAgeMs = 2_000L,
                        gpsFreshMaxAgeMs = 6_000L,
                        predictionFreshnessMaxAgeMs = 4_500L,
                        watchGpsDegraded = false,
                        allowLowQualityCompassPrediction = false,
                    ),
            )
        val weakGps =
            BetweenFixMotionPolicy.allowPrediction(
                inputs =
                    BetweenFixMotionInputs(
                        compassQuality = CompassMarkerQuality.LOW,
                        gpsAccuracyM = 24f,
                        gpsFixAgeMs = 2_000L,
                        gpsFreshMaxAgeMs = 6_000L,
                        predictionFreshnessMaxAgeMs = 4_500L,
                        watchGpsDegraded = false,
                        allowLowQualityCompassPrediction = true,
                    ),
            )

        assertFalse(overrideInactive)
        assertFalse(weakGps)
    }
}
