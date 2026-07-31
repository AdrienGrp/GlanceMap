package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkerPredictionCadenceTest {
    @Test
    fun configuredCadenceDefinesInitialPredictionWindow() {
        val cadence = MarkerPredictionCadence(configuredIntervalMs = 3_000L)

        val window =
            cadence.predictionWindow(
                configuredFreshnessMaxAgeMs = 6_000L,
                serviceFreshnessMaxAgeMs = 4_500L,
                startDelayMs = 50L,
            )

        assertEquals(3_000L, cadence.expectedIntervalMs())
        assertEquals(50L, window.startDelayMs)
        assertEquals(3_000L, window.fullSpeedUntilMs)
        assertEquals(4_500L, window.stopAtMs)
    }

    @Test
    fun configuredCadenceChangesThePredictionCadenceImmediately() {
        val cadence = MarkerPredictionCadence(configuredIntervalMs = 3_000L)
        assertEquals(3_000L, cadence.expectedIntervalMs())

        cadence.updateConfiguredInterval(intervalMs = 8_000L)

        assertEquals(8_000L, cadence.expectedIntervalMs())
    }

    @Test
    fun serviceFreshnessCapsPredictionHorizonAndKeepsEasingWindow() {
        val cadence = MarkerPredictionCadence(configuredIntervalMs = 5_000L)

        val window =
            cadence.predictionWindow(
                configuredFreshnessMaxAgeMs = 12_000L,
                serviceFreshnessMaxAgeMs = 4_000L,
                startDelayMs = 50L,
            )

        assertEquals(3_600L, window.fullSpeedUntilMs)
        assertEquals(4_000L, window.stopAtMs)
    }
}
