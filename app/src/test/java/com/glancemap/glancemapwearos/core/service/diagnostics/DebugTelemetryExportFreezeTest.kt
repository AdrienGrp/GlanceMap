package com.glancemap.glancemapwearos.core.service.diagnostics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DebugTelemetryExportFreezeTest {
    @Before
    fun setUp() {
        DebugTelemetry.setTransitionMarkersEnabledForTests(false)
        DebugTelemetry.setEnabledFromLocationService(false)
        DebugTelemetry.clear()
    }

    @After
    fun tearDown() {
        DebugTelemetry.setEnabledFromLocationService(false)
        DebugTelemetry.clear()
        DebugTelemetry.setTransitionMarkersEnabledForTests(true)
    }

    @Test
    fun exportFreezeIgnoresStaleEnableUntilPersistedDisableArrives() {
        DebugTelemetry.setEnabledFromLocationService(true)
        val firstSession = DebugTelemetry.captureSessionSnapshot()

        DebugTelemetry.freezeForExport()
        DebugTelemetry.setEnabledFromLocationService(true)

        val frozenSession = DebugTelemetry.captureSessionSnapshot()
        assertFalse(frozenSession.active)
        assertEquals(firstSession.sessionId, frozenSession.sessionId)

        DebugTelemetry.setEnabledFromLocationService(false)
        DebugTelemetry.setEnabledFromLocationService(true)

        val nextSession = DebugTelemetry.captureSessionSnapshot()
        assertTrue(nextSession.active)
        assertEquals(firstSession.sessionId + 1L, nextSession.sessionId)
    }
}
