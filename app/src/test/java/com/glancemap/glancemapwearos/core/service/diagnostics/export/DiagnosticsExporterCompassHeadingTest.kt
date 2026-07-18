package com.glancemap.glancemapwearos.core.service.diagnostics.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsExporterCompassHeadingTest {
    @Test
    fun aggregatesHeadingWindowsTransitionsAndReferenceMarkers() {
        val summary = deriveCompassHeadingTelemetrySummary(headingTelemetryLines())

        assertAggregatedSummary(summary)

        val output = StringBuilder().also { it.writeCompassHeadingTelemetrySummary(summary) }.toString()
        assertTrue(output.contains("Compass Heading Engine Summary"))
        assertTrue(output.contains("lastNorthBasis=google_automatic"))
    }

    private fun assertAggregatedSummary(summary: CompassHeadingTelemetrySummary) {
        assertEquals(2, summary.windowCount)
        assertEquals(150, summary.sampleCount)
        assertEquals(13.3f, summary.liveErrorAverageDeg!!, 0.1f)
        assertEquals(30f, summary.liveErrorMaximumDeg!!, 0.01f)
        assertEquals(46.7f, summary.magneticAverageUt!!, 0.1f)
        assertEquals(25f, summary.magneticMinimumUt!!, 0.01f)
        assertEquals(80f, summary.magneticMaximumUt!!, 0.01f)
        assertEquals(1, summary.transitionCounts["acquiring"])
        assertEquals(1, summary.transitionCounts["degraded"])
        assertEquals(1, summary.degradationReasonCounts["magnetic_interference"])
        assertEquals(1, summary.quarantineStartCount)
        assertEquals(1, summary.quarantineEndCount)
        assertEquals(1, summary.recoveryStartCount)
        assertEquals(1, summary.recoveryEndCount)
        assertEquals(85L, summary.firstRenderableLatencyMaxMs)
        assertEquals(380L, summary.firstTrustedLatencyMaxMs)
        assertEquals(1, summary.referenceMarkerCount)
        assertEquals(12f, summary.referenceTargetErrorAverageDeg!!, 0.01f)
    }

    private fun headingTelemetryLines(): List<String> =
        listOf(
            "2026-07-18 10:00:00.000 [CompassTelemetry] heading_engine transition " +
                "fromState=none fromReason=none toState=acquiring toReason=startup " +
                "northBasis=google_automatic",
            "2026-07-18 10:00:01.000 [CompassTelemetry] heading_engine transition " +
                "fromState=acquiring fromReason=startup toState=degraded " +
                "toReason=magnetic_interference northBasis=google_automatic",
            "2026-07-18 10:00:01.010 [CompassTelemetry] heading_engine quarantine " +
                "stage=start reason=magnetic_interference",
            engineWindow(
                samples = 100,
                liveAverage = 10f,
                liveMaximum = 20f,
                liveSamples = 100,
                magneticAverage = 50f,
                magneticMinimum = 35f,
                magneticMaximum = 80f,
                magneticSamples = 100,
            ),
            engineWindow(
                samples = 50,
                liveAverage = 20f,
                liveMaximum = 30f,
                liveSamples = 50,
                magneticAverage = 40f,
                magneticMinimum = 25f,
                magneticMaximum = 60f,
                magneticSamples = 50,
            ),
            "2026-07-18 10:00:02.000 [CompassTelemetry] heading_engine recovery stage=start",
            "2026-07-18 10:00:03.000 [CompassTelemetry] heading_engine recovery stage=end",
            "2026-07-18 10:00:03.010 [CompassTelemetry] heading_engine quarantine stage=end",
            "2026-07-18 10:00:04.000 [CompassTelemetry] wake_session " +
                "stage=first_renderable id=1 latencyMs=85",
            "2026-07-18 10:00:04.100 [CompassTelemetry] wake_session " +
                "stage=first_trusted id=1 latencyMs=380",
            "2026-07-18 10:00:05.000 [CompassTelemetry] user_report heading_reference " +
                "referenceDeg=90.0 targetSignedErrorDeg=-12.0",
        )

    @Suppress("LongParameterList")
    private fun engineWindow(
        samples: Int,
        liveAverage: Float,
        liveMaximum: Float,
        liveSamples: Int,
        magneticAverage: Float,
        magneticMinimum: Float,
        magneticMaximum: Float,
        magneticSamples: Int,
    ): String =
        "2026-07-18 10:00:10.000 [CompassTelemetry] heading_engine window " +
            "reason=periodic windowMs=10000 samples=$samples provider=google_fused " +
            "northBasis=google_automatic lastReason=stable " +
            "liveErrorAvgDeg=$liveAverage liveErrorMaxDeg=$liveMaximum liveErrorSamples=$liveSamples " +
            "conservativeErrorAvgDeg=25.0 conservativeErrorMaxDeg=30.0 conservativeErrorSamples=$samples " +
            "magneticAvgUt=$magneticAverage magneticMinUt=$magneticMinimum " +
            "magneticMaxUt=$magneticMaximum magneticSamples=$magneticSamples " +
            "disagreementAvgDeg=3.0 disagreementMaxDeg=7.0 disagreementSamples=$samples " +
            "acquiringSamples=0 trackingSamples=$samples degradedSamples=0 " +
            "magGoodSamples=$samples magInterferenceSamples=0"
}
