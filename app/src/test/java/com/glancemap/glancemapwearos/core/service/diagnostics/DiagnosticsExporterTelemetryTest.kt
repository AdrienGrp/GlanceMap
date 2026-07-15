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

    @Test
    fun immediateGuardAndWakeDebounceCountersAreSummarized() {
        val lines =
            listOf(
                "2026-04-20 20:07:12.000 [LocTelemetry] immediateRequest: " +
                    "skipGuard source=ui_startup_fresh_fix_ambient_exit_after_bind " +
                    "reason=tracking_disabled screenState=SCREEN_OFF trackingEnabled=false",
                "2026-04-20 20:07:13.000 [LocTelemetry] immediateRequest: " +
                    "deferWakeBurst source=ui_startup_fresh_fix_ambient_exit_after_bind " +
                    "delayMs=320 screenState=INTERACTIVE trackingEnabled=true",
            )

        val insights =
            deriveTelemetryInsights(
                lines = lines,
                captureWindowEndEpochMs = epochMs("2026-04-20T20:07:29"),
            )

        assertEquals(1, insights.immediateRequestGuardSkipCount)
        assertEquals(1, insights.immediateRequestDeferredWakeBurstCount)
    }

    @Test
    fun passiveExternalGpsSignalSamplesAreSummarized() {
        val lines =
            listOf(
                "2026-04-20 20:07:12.000 [LocTelemetry] requestUpdates applied: " +
                    "priority=105 intervalMs=3000 minDistanceM=0.0 state=ACTIVE " +
                    "bound=false keepOpen=true watchOnly=false burst=false backend=passive_external " +
                    "mode=INTERACTIVE trackingEnabled=true interactive=true screenState=INTERACTIVE " +
                    "finePermission=true coarsePermission=true passivePriority=true",
                "2026-04-20 20:07:13.000 [LocTelemetry] locationBatch: raw=1 normalized=1 " +
                    "accepted=0 fallback=false origin=passive_external duplicatesDropped=0",
                "2026-04-20 20:07:13.000 [LocTelemetry] gpsSignal: sample " +
                    "ageMs=34118 fresh=false maxAgeMs=20000 accuracyM=125.0 " +
                    "sourceMode=passive_external provider=gps accepted=false " +
                    "watchGpsDegraded=false watchGpsDegradedFixStreak=0",
                "2026-04-20 20:07:16.000 [LocTelemetry] gpsSignal: sample " +
                    "ageMs=100 fresh=true maxAgeMs=20000 accuracyM=8.5 " +
                    "sourceMode=passive_external provider=fused accepted=true " +
                    "watchGpsDegraded=false watchGpsDegradedFixStreak=0",
            )

        val insights =
            deriveTelemetryInsights(
                lines = lines,
                captureWindowEndEpochMs = epochMs("2026-04-20T20:07:29"),
            )

        assertEquals(1, insights.requestBackendPassiveExternalCount)
        assertEquals(1, insights.batchOriginPassiveExternalCount)
        assertEquals(2, insights.passiveExternalSignalSampleCount)
        assertEquals(1, insights.passiveExternalFreshSampleCount)
        assertEquals(1, insights.passiveExternalStaleSampleCount)
        assertEquals(1, insights.passiveExternalAcceptedSampleCount)
        assertEquals(1, insights.passiveExternalRejectedSampleCount)
        assertEquals(100L, insights.passiveExternalLastAgeMs)
        assertEquals(100L, insights.passiveExternalMinAgeMs)
        assertEquals(34_118L, insights.passiveExternalMaxAgeMs)
        assertEquals(20_000L, insights.passiveExternalLastMaxAgeMs)
        assertEquals(8.5f, insights.passiveExternalLastAccuracyM)
        assertEquals("fused", insights.passiveExternalLastProvider)
    }

    @Test
    fun watchGpsSelfHealFirstCallbackGraceTelemetryIsSummarized() {
        val lines =
            listOf(
                "2026-04-20 20:07:12.000 [LocTelemetry] watchGpsSelfHeal: skipped " +
                    "phase=burst reason=await_first_callback searchAgeMs=16000 graceMs=120000 " +
                    "fixGapMs=16000 staleThresholdMs=12000 expectedIntervalMs=3000 state=ACTIVE",
                "2026-04-20 20:09:22.000 [LocTelemetry] watchGpsSelfHeal: restarting " +
                    "phase=burst reason=first_callback_grace_expired searchAgeMs=129000 " +
                    "graceMs=120000 fixGapMs=129000 staleThresholdMs=12000 " +
                    "expectedIntervalMs=3000 state=ACTIVE",
            )

        val insights =
            deriveTelemetryInsights(
                lines = lines,
                captureWindowEndEpochMs = epochMs("2026-04-20T20:09:29"),
            )

        assertEquals(1, insights.watchGpsSelfHealSkippedCount)
        assertEquals(1, insights.watchGpsSelfHealRestartCount)
        assertEquals(129_000L, insights.watchGpsSelfHealMaxSearchAgeMs)
    }

    @Test
    fun recordingPointSampleCountFallsBackToObservedRecordingPointTotals() {
        val lines =
            listOf(
                "2026-04-20 20:07:12.000 [TraceRecording] event=point " +
                    "points=1 distanceMeters=0 accuracyMeters=125",
                "2026-04-20 20:07:32.000 [TraceRecording] event=pause " +
                    "points=6 distanceMeters=34 gpsDistanceMeters=34 displayDistanceMeters=34",
                "2026-04-20 20:07:34.000 [TraceRecording] event=save_success " +
                    "points=6 distanceMeters=34 gpsDistanceMeters=34 displayDistanceMeters=34",
                "2026-04-20 20:07:34.100 [TraceRecording] event=saved_gpx_verified " +
                    "writtenPoints=6 parsedPoints=6 summaryPoints=6 summaryDistanceMeters=34",
            )

        val insights =
            deriveTelemetryInsights(
                lines = lines,
                captureWindowEndEpochMs = epochMs("2026-04-20T20:07:39"),
            )

        assertEquals(6, insights.recordingPointSampleCount)
        assertEquals(6, insights.recordingMaxPointCount)
        assertEquals(6, insights.recordingSavedGpxWrittenPoints)
    }

    @Test
    fun compassStartupExperienceTelemetryIsSummarized() {
        val lines =
            listOf(
                "2026-04-20 20:07:12.000 [CompassTelemetry] wake_session stage=startup_summary " +
                    "id=1 windowMs=5000 samples=140 headingSpanDeg=286.0 maxJumpDeg=74.0 " +
                    "cumulativeHeadingRotationDeg=721.0 directionReversals=8 cumulativeMapRotationDeg=610.0 " +
                    "visibleHeadingMaxJumpDeg=68.0 visibleMapRotationMaxJumpDeg=63.0 " +
                    "sourceHandoffs=2 sourceHandoffMaxJumpDeg=52.0 " +
                    "renderErrorAvgDeg=2.4 renderErrorMaxDeg=18.0 stable3Ms=na stable5Ms=4300 fusedReadyMs=450",
                "2026-04-20 20:07:13.000 [CompassTelemetry] google_fused startup_overlap_summary " +
                    "reason=start confirmed=true samples=10 avgDeltaDeg=52.0 maxDeltaDeg=138.0 " +
                    "firstDeltaDeg=20.0 finalDeltaDeg=64.0 previousFinalDeltaDeg=na restartDeltaChangeDeg=na",
                "2026-04-20 20:07:14.000 [CompassTelemetry] wake_session stage=startup_summary " +
                    "id=2 windowMs=5000 samples=150 headingSpanDeg=42.0 maxJumpDeg=12.0 " +
                    "cumulativeHeadingRotationDeg=90.0 directionReversals=2 cumulativeMapRotationDeg=80.0 " +
                    "visibleHeadingMaxJumpDeg=9.0 visibleMapRotationMaxJumpDeg=11.0 " +
                    "sourceHandoffs=1 sourceHandoffMaxJumpDeg=8.0 " +
                    "renderErrorAvgDeg=0.8 renderErrorMaxDeg=3.0 stable3Ms=1600 stable5Ms=1200 fusedReadyMs=420",
                "2026-04-20 20:07:15.000 [CompassTelemetry] google_fused startup_overlap_summary " +
                    "reason=start confirmed=true samples=9 avgDeltaDeg=8.0 maxDeltaDeg=16.0 " +
                    "firstDeltaDeg=12.0 finalDeltaDeg=6.0 previousFinalDeltaDeg=64.0 restartDeltaChangeDeg=-58.0",
                "2026-04-20 20:07:15.100 [CompassTelemetry] google_fused startup_settling_extended " +
                    "reason=start candidateHeading=120.0 bootstrapDeltaDeg=145.0",
                "2026-04-20 20:07:15.500 [CompassTelemetry] google_fused startup_release " +
                    "reason=agreement heading=122.0 bootstrapDeltaDeg=4.0",
                "2026-04-20 20:07:15.510 [CompassTelemetry] map_heading_settling stage=release " +
                    "durationMs=510 fusedHeading=122.0 relativeRotation=38.0 rejectedJumps=3 rejectedMaxDeg=146.0",
                "2026-04-20 20:07:16.000 [CompassTelemetry] user_report heading_looks_wrong " +
                    "source=google_fused heading=220.0 rendered=219.0 mapRotation=-219.0 accuracy=1",
            )

        val insights = deriveCompassTelemetryInsights(lines)

        assertEquals(2, insights.startupSummaryCount)
        assertEquals(286f, insights.startupHeadingSpanMaxDeg)
        assertEquals(74f, insights.startupMaxJumpMaxDeg)
        assertEquals(68f, insights.startupVisibleHeadingJumpMaxDeg)
        assertEquals(63f, insights.startupVisibleMapRotationJumpMaxDeg)
        assertEquals(3, insights.startupSourceHandoffCount)
        assertEquals(52f, insights.startupSourceHandoffMaxJumpDeg)
        assertEquals(1, insights.startupStable3Count)
        assertEquals(2, insights.startupStable5Count)
        assertEquals(2, insights.startupOverlapSummaryCount)
        assertEquals(35f, insights.startupOverlapFinalDeltaAvgDeg)
        assertEquals(1, insights.startupOverlapRestartComparisonCount)
        assertEquals(1, insights.startupOverlapRestartImprovedCount)
        assertEquals(1, insights.startupExtendedSettlingCount)
        assertEquals(0, insights.startupReleaseDirectCount)
        assertEquals(1, insights.startupReleaseAgreementCount)
        assertEquals(0, insights.startupReleaseTimeoutCount)
        assertEquals(38f, insights.startupSettlingRelativeRotationMaxDeg)
        assertEquals(3, insights.startupSettlingRejectedJumpCount)
        assertEquals(146f, insights.startupSettlingRejectedJumpMaxDeg)
        assertEquals(1, insights.headingLooksWrongReportCount)
    }

    @Test
    fun compassRenderTelemetryIncludesMapsforgeRotationThrottleCount() {
        val insights =
            deriveCompassTelemetryInsights(
                listOf(
                    "2026-07-09 09:00:00.000 [CompassTelemetry] compass_render perf " +
                        "windowMs=5000 navMode=COMPASS_FOLLOW frames=250 frameHz=50.0 " +
                        "targetUpdates=120 headingRenders=180 renderHz=36.0 rotationApplied=120 " +
                        "rotationSkipped=40 rotationThrottled=60 markerUpdates=0 redraws=180",
                ),
            )

        assertEquals(1, insights.renderPerfEventCount)
        assertEquals(120, insights.renderPerfRotationAppliedCount)
        assertEquals(40, insights.renderPerfRotationSkippedCount)
        assertEquals(60, insights.renderPerfRotationThrottledCount)
    }

    @Test
    fun compassHeadingSampleCountIsExplicitlyDiagnosticOnly() {
        val insights =
            deriveCompassTelemetryInsights(
                listOf(
                    "2026-07-09 09:00:00.000 [CompassTelemetry] heading raw=1.0 smoothed=1.0",
                    "2026-07-09 09:00:05.000 [CompassTelemetry] google_fused sample heading=2.0",
                    "2026-07-09 09:00:05.000 [CompassTelemetry] google_fused perf " +
                        "windowMs=5000 callbacks=250 confirmed=245 unusable=0 headingPublishes=120",
                    "2026-07-09 09:00:06.000 [CompassTelemetry] google_fused sample_stale " +
                        "ageMs=1500 recoveryAttempted=false",
                    "2026-07-09 09:00:06.010 [CompassTelemetry] google_fused bootstrap activate " +
                        "reason=sample_stale_retry",
                ),
            )

        assertEquals(2, insights.headingSampleCount)
        assertEquals(2, insights.headingDiagnosticSampleCount)
        assertEquals(245, insights.fusedPerfConfirmedCount)
        assertEquals(1, insights.staleSampleCount)
    }

    private fun epochMs(localDateTime: String): Long =
        LocalDateTime
            .parse(localDateTime)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
