package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MarkerMotionTelemetryTest {
    @Before
    fun setUp() {
        MarkerMotionTelemetry.setCollectionEnabledForTests(true)
    }

    @After
    fun tearDown() {
        MarkerMotionTelemetry.clear()
        MarkerMotionTelemetry.setCollectionEnabledForTests(false)
    }

    @Test
    fun aggregatesPredictionCorrectionRenderAndFilterMetrics() {
        MarkerMotionTelemetry.clear()

        MarkerMotionTelemetry.recordNextFixPredictionResidual(
            residualDistanceM = 0.5f,
            alongTrackErrorM = -1f,
            crossTrackErrorM = 0.5f,
        )
        MarkerMotionTelemetry.recordNextFixPredictionResidual(
            residualDistanceM = 2f,
            alongTrackErrorM = 3f,
            crossTrackErrorM = -1.5f,
        )
        MarkerMotionTelemetry.recordNextFixPredictionResidual(residualDistanceM = 6f)
        MarkerMotionTelemetry.recordNextFixPredictionResidual(residualDistanceM = 10f)
        MarkerMotionTelemetry.recordRawVsFilteredOffsets(
            positionOffsetM = 3f,
            speedOffsetMps = -0.5f,
            bearingOffsetDeg = -15f,
        )
        MarkerMotionTelemetry.recordMotionRendered(1_000L, displacementM = 0.4f, displacementPx = 0.5f)
        MarkerMotionTelemetry.recordMotionRendered(1_100L, displacementM = 0.8f, displacementPx = 1f)
        MarkerMotionTelemetry.recordMotionRendered(1_225L, displacementM = 1.2f, displacementPx = 1.5f)
        MarkerMotionTelemetry.recordMotionRendered(1_475L, displacementM = 1.6f, displacementPx = 2f)
        MarkerMotionTelemetry.recordCorrectionSettled(reason = "source_switch", durationMs = 2_400L)
        MarkerMotionTelemetry.recordCorrectionInterrupted(reason = "gps_correction", durationMs = 700L)

        val summary = MarkerMotionTelemetry.summary()
        assertEquals(4, summary.nextFixPredictionResidualM.samples)
        assertEquals(4.625f, summary.nextFixPredictionResidualM.mean ?: 0f, 0.001f)
        assertEquals(2f, summary.nextFixPredictionResidualM.p50 ?: 0f, 0.001f)
        assertEquals(13f, summary.nextFixPredictionResidualM.p95 ?: 0f, 0.001f)
        assertEquals(10f, summary.nextFixPredictionResidualM.max ?: 0f, 0.001f)
        assertEquals(2, summary.correctionComponentSamples)
        assertEquals(1f, summary.correctionAlongTrackMeanM ?: 0f, 0.001f)
        assertEquals(-0.5f, summary.correctionCrossTrackMeanM ?: 0f, 0.001f)
        assertEquals(3f, summary.correctionAlongTrackAbsM.max ?: 0f, 0.001f)
        assertEquals(1.5f, summary.correctionCrossTrackAbsM.max ?: 0f, 0.001f)
        assertEquals(4, summary.renderDisplacementM.samples)
        assertEquals(4, summary.renderDisplacementPx.samples)
        assertEquals(125L, summary.activeRenderIntervalP50Ms)
        assertEquals(250L, summary.activeRenderIntervalP95Ms)
        assertEquals(3f, summary.rawFilteredPositionOffsetM.max ?: 0f, 0.001f)
        assertEquals(0.5f, summary.rawFilteredSpeedOffsetMps.max ?: 0f, 0.001f)
        assertEquals(15f, summary.rawFilteredBearingOffsetDeg.max ?: 0f, 0.001f)
        assertEquals(2_400f, summary.correctionSettleDurationMs.max ?: 0f, 0.001f)
        assertEquals(1, summary.correctionInterruptedCount)
    }

    @Test
    fun tracksModeDwellWithoutRetainingFrameSamples() {
        MarkerMotionTelemetry.clear()

        MarkerMotionTelemetry.recordModeObserved(MarkerMotionMode.FIXED, 0L)
        MarkerMotionTelemetry.recordModeObserved(MarkerMotionMode.FIXED, 100L)
        MarkerMotionTelemetry.recordModeObserved(MarkerMotionMode.PREDICT, 300L)
        MarkerMotionTelemetry.recordModeObserved(MarkerMotionMode.PREDICT, 500L)
        MarkerMotionTelemetry.recordModeObserved(MarkerMotionMode.IDLE, 550L)

        val dwell = MarkerMotionTelemetry.summary(observedAtElapsedMs = 750L).modeDwellMs
        assertEquals(300L, dwell[MarkerMotionMode.FIXED])
        assertEquals(250L, dwell[MarkerMotionMode.PREDICT])
        assertEquals(200L, dwell[MarkerMotionMode.IDLE])
        assertNull(dwell[MarkerMotionMode.BLEND])
    }

    @Test
    fun ignoresInvalidOptionalMetrics() {
        MarkerMotionTelemetry.clear()

        MarkerMotionTelemetry.recordNextFixPredictionResidual(Float.NaN)
        MarkerMotionTelemetry.recordMotionRendered(
            nowElapsedMs = 1_000L,
            displacementM = -1f,
            displacementPx = Float.POSITIVE_INFINITY,
        )
        MarkerMotionTelemetry.recordRawVsFilteredOffsets(
            positionOffsetM = Float.NaN,
            speedOffsetMps = Float.NEGATIVE_INFINITY,
        )

        val summary = MarkerMotionTelemetry.summary()
        assertEquals(0, summary.nextFixPredictionResidualM.samples)
        assertEquals(0, summary.renderDisplacementM.samples)
        assertEquals(0, summary.renderDisplacementPx.samples)
        assertEquals(0, summary.rawFilteredPositionOffsetM.samples)
        assertEquals(0, summary.rawFilteredSpeedOffsetMps.samples)
    }
}
