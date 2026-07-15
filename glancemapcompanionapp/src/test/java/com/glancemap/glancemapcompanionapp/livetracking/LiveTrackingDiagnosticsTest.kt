package com.glancemap.glancemapcompanionapp.livetracking

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTrackingDiagnosticsTest {
    @After
    fun clearDiagnostics() {
        LiveTrackingDiagnostics.clear()
    }

    @Test
    fun displaysOnlyRedactedRequestMetadata() {
        val event =
            LiveTrackingDiagnosticEvent(
                timestampEpochMs = 0L,
                request =
                    LiveTrackingDiagnosticRequest(
                        operation = LiveTrackingDiagnosticOperation.LOCATION_UPDATE,
                        alarmMinutes = 10,
                        notificationEmailCount = 1,
                        alertEmailCount = 2,
                        alertSmsCount = 1,
                        includesRecipientSummary = true,
                        start = true,
                    ),
                result = LiveTrackingDiagnosticResult.SUCCESS,
                httpCode = 200,
                durationMs = 125,
            )

        val displayText = event.toDisplayText()

        assertTrue(displayText.contains("GPS update"))
        assertTrue(displayText.contains("HTTP 200"))
        assertTrue(displayText.contains("alarm 10m"))
        assertTrue(displayText.contains("notify 1"))
        assertTrue(displayText.contains("alerts 2 email/1 SMS"))
        assertTrue(displayText.contains("start"))
    }

    @Test
    fun keepsOnlyTheMostRecentOneHundredEvents() {
        repeat(105) { index ->
            LiveTrackingDiagnostics.record(
                request =
                    LiveTrackingDiagnosticRequest(
                        operation = LiveTrackingDiagnosticOperation.LOCATION_UPDATE,
                    ),
                result = LiveTrackingDiagnosticResult.SUCCESS,
                durationMs = index.toLong(),
            )
        }

        val events = LiveTrackingDiagnostics.events.value

        assertEquals(100, events.size)
        assertEquals(5L, events.first().durationMs)
        assertEquals(104L, events.last().durationMs)
    }
}
