package com.glancemap.glancemapcompanionapp.livetracking

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ArkluzClientTokenInterceptorTest {
    @Test
    fun addsTokenOnlyAsAHeaderForArkluzHttpsRequests() {
        val request = request("https://arkluz.com/trk?q=sms&sms=%2B33612345678")

        val authenticatedRequest = request.withArkluzClientToken(DUMMY_TOKEN)

        assertEquals(DUMMY_TOKEN, authenticatedRequest.header(ARKLUZ_CLIENT_TOKEN_HEADER))
        assertFalse(authenticatedRequest.url.toString().contains(DUMMY_TOKEN))
    }

    @Test
    fun omitsTheHeaderWhenDebugTokenIsBlank() {
        val request = request("https://arkluz.com/trk")

        val authenticatedRequest = request.withArkluzClientToken("")

        assertNull(authenticatedRequest.header(ARKLUZ_CLIENT_TOKEN_HEADER))
    }

    @Test
    fun neverSendsTheTokenOverPlainHttp() {
        val request = request("http://arkluz.com/trk")

        val authenticatedRequest = request.withArkluzClientToken(DUMMY_TOKEN)

        assertNull(authenticatedRequest.header(ARKLUZ_CLIENT_TOKEN_HEADER))
    }

    @Test
    fun removesTheTokenFromRequestsToOtherHosts() {
        val redirectedRequest =
            request("https://example.com/trk")
                .newBuilder()
                .header(ARKLUZ_CLIENT_TOKEN_HEADER, DUMMY_TOKEN)
                .build()

        val authenticatedRequest = redirectedRequest.withArkluzClientToken(DUMMY_TOKEN)

        assertNull(authenticatedRequest.header(ARKLUZ_CLIENT_TOKEN_HEADER))
    }

    private fun request(url: String): Request =
        Request
            .Builder()
            .url(url)
            .build()

    private companion object {
        const val DUMMY_TOKEN = "dummy_mobile_client_token_for_tests_1234567890"
    }
}
