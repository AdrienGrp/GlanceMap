package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkerMotionReplayMetricsTest {
    @Test
    fun evaluatesAccuracyCadenceAndVisualReversals() {
        val summary =
            MarkerMotionReplayMetrics.evaluate(
                listOf(
                    sample(elapsedMs = 0L, renderedEastM = 0.0, referenceEastM = 0.0),
                    sample(elapsedMs = 100L, renderedEastM = 1.0, referenceEastM = 1.0),
                    sample(elapsedMs = 200L, renderedEastM = 2.0, referenceEastM = 2.0),
                    sample(elapsedMs = 300L, renderedEastM = 1.5, referenceEastM = 3.0),
                ),
            )

        assertEquals(4, summary.samples)
        assertEquals(0.375, summary.positionErrorMeanM ?: 0.0, 0.001)
        assertEquals(1.5, summary.positionErrorP95M ?: 0.0, 0.001)
        assertEquals(1.5, summary.positionErrorMaxM ?: 0.0, 0.001)
        assertEquals(5.0 / 6.0, summary.renderStepMeanM ?: 0.0, 0.001)
        assertEquals(1.0, summary.renderStepP95M ?: 0.0, 0.001)
        assertEquals(100L, summary.renderIntervalP50Ms)
        assertEquals(100L, summary.renderIntervalP95Ms)
        assertEquals(100L, summary.renderIntervalMaxMs)
        assertEquals(1, summary.directionReversals)
    }

    @Test
    fun emptyReplayProducesNoDerivedMetrics() {
        val summary = MarkerMotionReplayMetrics.evaluate(emptyList())

        assertEquals(0, summary.samples)
        assertNull(summary.positionErrorMeanM)
        assertNull(summary.renderStepMeanM)
        assertNull(summary.renderIntervalP50Ms)
        assertEquals(0, summary.directionReversals)
    }

    @Test
    fun deterministicControllerReplayMeasuresRealPredictionPath() {
        val fixes =
            listOf(
                replayFix(elapsedMs = 0L, eastM = 0.0),
                replayFix(elapsedMs = 3_000L, eastM = 3.0),
                replayFix(elapsedMs = 6_000L, eastM = 6.0),
            )

        val summary =
            MarkerMotionControllerReplay.evaluate(
                fixes = fixes,
                config =
                    MarkerMotionReplayConfig(
                        expectedGpsIntervalMs = 3_000L,
                        predictionFreshnessMaxAgeMs = 4_500L,
                        maxAcceptedFixAgeMs = 6_000L,
                        minimumRenderDisplacementM = 0f,
                    ),
            )

        assertEquals(true, summary.samples > fixes.size)
        assertEquals(100L, summary.renderIntervalP50Ms)
        assertEquals(0, summary.directionReversals)
    }

    private fun sample(
        elapsedMs: Long,
        renderedEastM: Double,
        referenceEastM: Double,
    ): MarkerMotionReplaySample =
        MarkerMotionReplaySample(
            elapsedMs = elapsedMs,
            renderedEastM = renderedEastM,
            renderedNorthM = 0.0,
            referenceEastM = referenceEastM,
            referenceNorthM = 0.0,
        )

    private fun replayFix(
        elapsedMs: Long,
        eastM: Double,
    ): MarkerMotionReplayFix =
        MarkerMotionReplayFix(
            elapsedMs = elapsedMs,
            providerEastM = eastM,
            providerNorthM = 0.0,
            accuracyM = 5f,
            speedMps = 1f,
            bearingDeg = 90f,
        )
}
