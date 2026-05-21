package com.glancemap.glancemapwearos.core.service.diagnostics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EnergyDiagnosticsTest {
    @Before
    fun setUp() {
        DebugTelemetry.setEnabled(false)
        DebugTelemetry.clear()
        EnergyDiagnostics.clear()
        DebugTelemetry.setEnabled(true)
    }

    @After
    fun tearDown() {
        DebugTelemetry.setEnabled(false)
        DebugTelemetry.clear()
        EnergyDiagnostics.clear()
    }

    @Test
    fun summaryGroupsSamplesByRuntimeMode() {
        val summary =
            EnergyDiagnostics.summarizeLines(
                listOf(
                    "reason=gps_request_applied mode=BURST level=70 tempC=29.0 curNowUa=-200000",
                    "reason=periodic burst=true tracking=true level=69 tempC=29.5 curNowUa=-100000",
                    "reason=periodic screenState=SCREEN_OFF tracking=false level=68 tempC=28.0 curNowUa=-2000",
                ),
            )
        val burst = checkNotNull(summary.modes["burst"])
        val screenOff = checkNotNull(summary.modes["screen_off"])

        assertEquals(2, burst.sampleCount)
        assertEquals(-150000L, burst.avgCurrentNowUa)
        assertEquals(69, burst.minLevelPct)
        assertEquals(70, burst.maxLevelPct)

        assertEquals(1, screenOff.sampleCount)
        assertEquals(-2000L, screenOff.avgCurrentNowUa)
        assertEquals(68, screenOff.minLevelPct)
        assertEquals(68, screenOff.maxLevelPct)
    }
}
