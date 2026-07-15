package com.glancemap.glancemapcompanionapp.livetracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun buildsTheSmsSupportRequestWithEncodedPhoneNumberAndApiKey() {
        val url =
            buildArkluzSmsSupportUrl(
                trackingUrl = "https://arkluz.com/dev/trk",
                phoneNumber = "+33 6 12 34 56 78",
                apiKey = DUMMY_API_KEY,
            )

        assertEquals("sms", url.queryParameter("q"))
        assertEquals("+33 6 12 34 56 78", url.queryParameter("sms"))
        assertEquals(DUMMY_API_KEY, url.queryParameter("key"))
        assertTrue(url.toString().contains("key=testSmsApiKey%2Bwith%2FstandardBase64"))
    }

    @Test
    fun omitsTheApiKeyWhenDebugConfigurationIsBlank() {
        val url =
            buildArkluzSmsSupportUrl(
                trackingUrl = "https://arkluz.com/trk",
                phoneNumber = "+33612345678",
                apiKey = "",
            )

        assertNull(url.queryParameter("key"))
    }

    @Test
    fun neverSendsTheApiKeyOutsideArkluzHttps() {
        val plainHttpUrl =
            buildArkluzSmsSupportUrl(
                trackingUrl = "http://arkluz.com/trk",
                phoneNumber = "+33612345678",
                apiKey = DUMMY_API_KEY,
            )
        val otherHostUrl =
            buildArkluzSmsSupportUrl(
                trackingUrl = "https://example.com/trk",
                phoneNumber = "+33612345678",
                apiKey = DUMMY_API_KEY,
            )

        assertNull(plainHttpUrl.queryParameter("key"))
        assertNull(otherHostUrl.queryParameter("key"))
    }

    private companion object {
        const val DUMMY_API_KEY = "testSmsApiKey+with/standardBase64"
    }
}
