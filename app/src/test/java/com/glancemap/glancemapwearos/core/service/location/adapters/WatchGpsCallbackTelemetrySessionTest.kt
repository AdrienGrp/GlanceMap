package com.glancemap.glancemapwearos.core.service.location.adapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchGpsCallbackTelemetrySessionTest {
    @Test
    fun summaryIncludesRawCallbacksBeforeDeduplication() {
        val session = WatchGpsCallbackTelemetrySession()

        session.start(nowElapsedMs = 1_000L)

        assertEquals(250L, session.recordRawCallback(nowElapsedMs = 1_250L, rawLocationCount = 2))
        assertNull(session.recordRawCallback(nowElapsedMs = 1_500L, rawLocationCount = 1))
        session.recordDuplicatesDropped(2)

        val summary = requireNotNull(session.finish(nowElapsedMs = 2_000L, reason = "remove_requested"))

        assertEquals("remove_requested", summary.reason)
        assertEquals(1_000L, summary.runtimeMs)
        assertEquals(2, summary.rawCallbackCount)
        assertEquals(3, summary.rawLocationCount)
        assertEquals(2, summary.duplicatesDropped)
        assertEquals(250L, summary.firstRawCallbackDelayMs)
        assertNull(session.finish(nowElapsedMs = 2_500L, reason = "second_finish"))
    }
}
