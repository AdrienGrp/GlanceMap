package com.glancemap.glancemapcompanionapp.livetracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ArkluzSmsSupportTest {
    @Test
    fun recognizesTheSupportedAndUnsupportedResponses() {
        assertEquals(ArkluzSmsSupport.SUPPORTED, "OK\n".toArkluzSmsSupport())
        assertEquals(ArkluzSmsSupport.UNSUPPORTED, " forbidden\n".toArkluzSmsSupport())
    }

    @Test
    fun rejectsUnexpectedResponses() {
        val error = runCatching { "pending".toArkluzSmsSupport() }.exceptionOrNull()

        assertTrue(error is IOException)
    }

    @Test
    fun buildsTheSmsSupportRequestWithAnEncodedPhoneNumber() {
        val url = buildArkluzSmsSupportUrl("https://arkluz.com/trk", "+33 6 12 34 56 78")

        assertEquals("sms", url.queryParameter("q"))
        assertEquals("+33 6 12 34 56 78", url.queryParameter("sms"))
    }
}
