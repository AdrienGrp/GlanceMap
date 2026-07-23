package com.glancemap.glancemapwearos.presentation.features.navigate.effects

import com.glancemap.glancemapwearos.domain.sensors.HeadingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigateCompassWakeTelemetryTest {
    @Test
    fun wakeSessionIdsRemainMonotonicAcrossTelemetryOwners() {
        val firstOwnerSessionId = nextCompassWakeSessionId()
        val recreatedOwnerSessionId = nextCompassWakeSessionId()

        assertTrue(recreatedOwnerSessionId > firstOwnerSessionId)
    }

    @Test
    fun startupSnapshotMeasuresVisibleJumpsAndSourceHandoffsAcrossNorth() {
        val metrics = CompassStartupMetrics()
        metrics.start(
            sessionId = 42L,
            nowElapsedMs = 1_000L,
            initialState =
                CompassStartupInitialState(
                    headingDeg = 350f,
                    renderedHeadingDeg = 350f,
                    mapRotationDeg = -350f,
                    source = HeadingSource.ROTATION_VECTOR,
                ),
        )

        metrics.record(
            nowElapsedMs = 1_100L,
            headingDeg = 355f,
            renderedHeadingDeg = 354f,
            mapRotationDeg = -354f,
            source = HeadingSource.ROTATION_VECTOR,
        )
        metrics.record(
            nowElapsedMs = 1_200L,
            headingDeg = 5f,
            renderedHeadingDeg = 2f,
            mapRotationDeg = -2f,
            source = HeadingSource.FUSED_ORIENTATION,
        )

        val snapshot = requireNotNull(metrics.snapshot(nowElapsedMs = 1_200L))
        assertEquals(42L, snapshot.sessionId)
        assertEquals(15f, snapshot.headingSpanDeg, 0.001f)
        assertEquals(10f, snapshot.maxHeadingJumpDeg, 0.001f)
        assertEquals(8f, snapshot.visibleHeadingMaxJumpDeg, 0.001f)
        assertEquals(8f, snapshot.visibleMapRotationMaxJumpDeg, 0.001f)
        assertEquals(1, snapshot.sourceHandoffCount)
        assertEquals(10f, snapshot.sourceHandoffMaxJumpDeg, 0.001f)
    }

    @Test
    fun startupSnapshotReportsFusedAndStabilityLatencies() {
        val metrics = CompassStartupMetrics()
        metrics.start(
            sessionId = 7L,
            nowElapsedMs = 10_000L,
            initialState =
                CompassStartupInitialState(
                    headingDeg = 100f,
                    renderedHeadingDeg = 100f,
                    mapRotationDeg = -100f,
                    source = HeadingSource.NONE,
                ),
        )

        metrics.record(
            nowElapsedMs = 10_010L,
            headingDeg = 100f,
            renderedHeadingDeg = 100f,
            mapRotationDeg = -100f,
            source = HeadingSource.FUSED_ORIENTATION,
        )
        metrics.record(
            nowElapsedMs = 11_010L,
            headingDeg = 102f,
            renderedHeadingDeg = 101f,
            mapRotationDeg = -101f,
            source = HeadingSource.FUSED_ORIENTATION,
        )

        val snapshot = requireNotNull(metrics.snapshot(nowElapsedMs = 11_010L))
        assertEquals(10L, snapshot.fusedReadyMs)
        assertEquals(1_010L, snapshot.stable3Ms)
        assertEquals(1_010L, snapshot.stable5Ms)
        assertEquals(0, snapshot.sourceHandoffCount)
    }

    @Test
    fun startupSnapshotIsUnavailableBeforeStartAndIgnoresLateSamples() {
        val metrics = CompassStartupMetrics()
        assertNull(metrics.snapshot(nowElapsedMs = 1_000L))

        metrics.start(
            sessionId = 1L,
            nowElapsedMs = 1_000L,
            initialState =
                CompassStartupInitialState(
                    headingDeg = 10f,
                    renderedHeadingDeg = 10f,
                    mapRotationDeg = -10f,
                    source = HeadingSource.FUSED_ORIENTATION,
                ),
        )
        metrics.record(
            nowElapsedMs = 6_001L,
            headingDeg = 170f,
            renderedHeadingDeg = 170f,
            mapRotationDeg = -170f,
            source = HeadingSource.ROTATION_VECTOR,
        )

        val snapshot = requireNotNull(metrics.snapshot(nowElapsedMs = 7_000L))
        assertEquals(5_000L, snapshot.windowMs)
        assertEquals(0, snapshot.sampleCount)
        assertEquals(0, snapshot.sourceHandoffCount)
        assertEquals(0f, snapshot.maxHeadingJumpDeg, 0.001f)
    }
}
