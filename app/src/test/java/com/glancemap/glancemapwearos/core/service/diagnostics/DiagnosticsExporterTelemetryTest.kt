package com.glancemap.glancemapwearos.core.service.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DiagnosticsExporterTelemetryTest {
    @Test
    fun requestClearedLineStopsModeAndBackendDurations() {
        val lines =
            listOf(
                "2026-04-20 20:07:12.000 [LocTelemetry] requestUpdates applied: " +
                    "priority=100 intervalMs=1000 minDistanceM=1.0 state=ACTIVE " +
                    "bound=false keepOpen=true watchOnly=true burst=true backend=watch_gps " +
                    "mode=BURST trackingEnabled=true interactive=true screenState=INTERACTIVE " +
                    "finePermission=true coarsePermission=true passivePriority=false",
                "2026-04-20 20:07:14.000 [LocTelemetry] requestUpdates cleared: " +
                    "reason=tracking_disabled bound=false keepOpen=true trackingEnabled=false " +
                    "screenState=SCREEN_OFF backgroundGpsEnabled=false",
                "2026-04-20 20:07:24.000 [LocTelemetry] requestUpdates applied: " +
                    "priority=100 intervalMs=3000 minDistanceM=1.0 state=ACTIVE " +
                    "bound=false keepOpen=true watchOnly=true burst=false backend=watch_gps " +
                    "mode=INTERACTIVE trackingEnabled=true interactive=true screenState=INTERACTIVE " +
                    "finePermission=true coarsePermission=true passivePriority=false",
            )

        val insights =
            deriveTelemetryInsights(
                lines = lines,
                captureWindowEndEpochMs = epochMs("2026-04-20T20:07:29"),
            )

        assertEquals(2_000L, insights.requestModeBurstDurationMs)
        assertEquals(5_000L, insights.requestModeOtherwiseDurationMs)
        assertEquals(7_000L, insights.requestBackendWatchGpsDurationMs)
        assertEquals(7_000L, insights.requestBackendDurationCoverageMs)
    }

    @Test
    fun legacyTrackingDisabledLineStopsActiveRequestDuration() {
        val lines =
            listOf(
                "2026-04-20 20:07:12.000 [LocTelemetry] requestUpdates applied: " +
                    "priority=100 intervalMs=1000 minDistanceM=1.0 state=ACTIVE " +
                    "bound=false keepOpen=true watchOnly=true burst=true backend=watch_gps " +
                    "mode=BURST trackingEnabled=true interactive=true screenState=INTERACTIVE " +
                    "finePermission=true coarsePermission=true passivePriority=false",
                "2026-04-20 20:07:14.500 [LocTelemetry] tracking: enabled=false",
                "2026-04-20 20:07:24.000 [LocTelemetry] requestUpdates applied: " +
                    "priority=100 intervalMs=3000 minDistanceM=1.0 state=ACTIVE " +
                    "bound=false keepOpen=true watchOnly=true burst=false backend=watch_gps " +
                    "mode=INTERACTIVE trackingEnabled=true interactive=true screenState=INTERACTIVE " +
                    "finePermission=true coarsePermission=true passivePriority=false",
            )

        val insights =
            deriveTelemetryInsights(
                lines = lines,
                captureWindowEndEpochMs = epochMs("2026-04-20T20:07:29"),
            )

        assertEquals(2_500L, insights.requestModeBurstDurationMs)
        assertEquals(5_000L, insights.requestModeOtherwiseDurationMs)
        assertEquals(7_500L, insights.requestBackendWatchGpsDurationMs)
    }

    @Test
    fun requestClearedLineUpdatesLastObservedTrackingState() {
        val lines =
            listOf(
                "2026-04-20 20:07:12.000 [LocTelemetry] requestUpdates applied: " +
                    "priority=100 intervalMs=1000 minDistanceM=1.0 state=ACTIVE " +
                    "bound=false keepOpen=true watchOnly=true burst=true backend=watch_gps " +
                    "mode=BURST trackingEnabled=true interactive=true screenState=INTERACTIVE " +
                    "finePermission=true coarsePermission=true passivePriority=false",
                "2026-04-20 20:07:14.000 [LocTelemetry] requestUpdates cleared: " +
                    "reason=tracking_disabled bound=false keepOpen=true trackingEnabled=false " +
                    "screenState=INTERACTIVE backgroundGpsEnabled=false",
            )

        val insights =
            deriveTelemetryInsights(
                lines = lines,
                captureWindowEndEpochMs = epochMs("2026-04-20T20:07:29"),
            )

        assertEquals(false, insights.lastObservedTrackingEnabled)
        assertEquals(2_000L, insights.requestModeBurstDurationMs)
        assertEquals(2_000L, insights.requestBackendWatchGpsDurationMs)
    }

    private fun epochMs(localDateTime: String): Long =
        LocalDateTime
            .parse(localDateTime)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
