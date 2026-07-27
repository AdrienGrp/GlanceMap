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
    fun observedMedianRejectsSingleLongGap() {
        val cadence = MarkerPredictionCadence(configuredIntervalMs = 3_000L)
        listOf(3_000L, 3_200L, 2_800L, 30_000L, 3_100L).forEach { gapMs ->
            cadence.recordAcceptedFixGap(gapMs = gapMs, sourceChanged = false)
        }

        assertEquals(3_100L, cadence.expectedIntervalMs())
    }

    @Test
    fun observedCadenceRequiresThreeValidSameSourceGaps() {
        val cadence = MarkerPredictionCadence(configuredIntervalMs = 3_000L)

        cadence.recordAcceptedFixGap(gapMs = 5_000L, sourceChanged = false)
        cadence.recordAcceptedFixGap(gapMs = 5_200L, sourceChanged = false)
        assertEquals(3_000L, cadence.expectedIntervalMs())

        cadence.recordAcceptedFixGap(gapMs = 4_800L, sourceChanged = false)
        assertEquals(5_000L, cadence.expectedIntervalMs())
    }

    @Test
    fun configuredIntervalChangeImmediatelyClearsObservedCadence() {
        val cadence = MarkerPredictionCadence(configuredIntervalMs = 3_000L)
        repeat(3) {
            cadence.recordAcceptedFixGap(gapMs = 5_000L, sourceChanged = false)
        }
        assertEquals(5_000L, cadence.expectedIntervalMs())

        cadence.updateConfiguredInterval(intervalMs = 8_000L)

        assertEquals(8_000L, cadence.expectedIntervalMs())
    }

    @Test
    fun observedCadenceIsBoundedAroundConfiguredRequest() {
        val cadence = MarkerPredictionCadence(configuredIntervalMs = 4_000L)

        repeat(3) {
            cadence.recordAcceptedFixGap(gapMs = 700L, sourceChanged = false)
        }
        assertEquals(2_000L, cadence.expectedIntervalMs())

        cadence.resetObservedIntervals()
        repeat(3) {
            cadence.recordAcceptedFixGap(gapMs = 20_000L, sourceChanged = false)
        }
        assertEquals(8_000L, cadence.expectedIntervalMs())
    }

    @Test
    fun sourceChangeResetsObservedCadence() {
        val cadence = MarkerPredictionCadence(configuredIntervalMs = 3_000L)
        repeat(3) {
            cadence.recordAcceptedFixGap(gapMs = 5_000L, sourceChanged = false)
        }
        assertEquals(5_000L, cadence.expectedIntervalMs())

        cadence.recordAcceptedFixGap(gapMs = 2_000L, sourceChanged = true)

        assertEquals(3_000L, cadence.expectedIntervalMs())
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
