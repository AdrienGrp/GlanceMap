package com.glancemap.glancemapwearos.core.service.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsFixQualityTest {

    @Test
    fun deriveAcceptedFixSummariesSeparatesWatchGpsReportedAccuracy() {
        val lines = listOf(
            "2026-03-18 13:39:06.328 [LocTelemetry] fixAccepted: source=callback detail=callback_candidate ageMs=9 accuracyM=125.0 origin=watch_gps provider=gps",
            "2026-03-18 13:39:07.396 [LocTelemetry] fixAccepted: source=callback detail=callback_candidate ageMs=49 accuracyM=125.0 origin=watch_gps provider=gps",
            "2026-03-18 13:39:10.091 [LocTelemetry] fixAccepted: source=immediate detail=ui_startup_fresh_fix_after_bind ageMs=28 accuracyM=125.0 origin=watch_gps provider=gps",
            "2026-03-18 13:41:24.272 [LocTelemetry] fixAccepted: source=callback detail=callback_candidate ageMs=8 accuracyM=12.3 origin=auto_fused provider=fused"
        )

        val summaries = DiagnosticsExporter.deriveAcceptedFixSummaries(lines)

        assertEquals(4, summaries.overall.acceptedFixCount)
        assertEquals(3, summaries.watchGps.acceptedFixCount)
        assertEquals(1, summaries.autoFused.acceptedFixCount)
        assertEquals(125.0f, summaries.watchGps.reportedAccuracyMedianM)
        assertEquals(1, summaries.watchGps.reportedAccuracyDistinctCount)
        assertTrue(summaries.watchGps.reportedAccuracyAllSame)
    }

    @Test
    fun inferObservedFixQualityFlagsConstantWatchGpsAccuracyAsSuspectButGood() {
        val summary = DiagnosticsExporter.AcceptedFixSummary(
            acceptedFixCount = 8,
            callbackFixCount = 7,
            immediateFixCount = 1,
            providerGpsCount = 8,
            providerFusedCount = 0,
            reportedAccuracyMedianM = 125.0f,
            reportedAccuracyP90M = 125.0f,
            reportedAccuracyMinM = 125.0f,
            reportedAccuracyMaxM = 125.0f,
            reportedAccuracyDistinctCount = 1,
            reportedAccuracyAllSame = true,
            ageMedianMs = 18L,
            ageP90Ms = 28L,
            ageMaxMs = 49L
        )
        val gnss = DiagnosticsExporter.GnssInsights(
            statusSampleCount = 10,
            firstFixCount = 4,
            usedInFixAvg = 22.0,
            usedInFixMax = 39,
            cn0AvgDbHz = 24.5,
            cn0MaxDbHz = 37.0f
        )

        val quality = DiagnosticsExporter.inferObservedFixQuality(
            summary = summary,
            origin = "watch_gps",
            gnssInsights = gnss
        )

        assertEquals("suspect_constant_watch_gps", quality.reportedAccuracyReliability)
        assertEquals("good", quality.quality)
        assertEquals("high", quality.confidence)
        assertTrue(quality.reason.contains("despite constant reported accuracy"))
    }
}
