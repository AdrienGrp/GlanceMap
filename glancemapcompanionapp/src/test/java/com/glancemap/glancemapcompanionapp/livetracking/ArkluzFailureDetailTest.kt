package com.glancemap.glancemapcompanionapp.livetracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ArkluzFailureDetailTest {
    @Test
    fun unknownHostUsesRetryableConnectionMessage() {
        val detail =
            UnknownHostException("Unable to resolve host \"arkluz.com\": No address associated with hostname")
                .toArkluzFailureDetail()

        assertEquals(
            "Arkluz is temporarily unreachable. Check your internet connection and try again.",
            detail,
        )
    }

    @Test
    fun timeoutUsesShortRetryMessage() {
        val detail = SocketTimeoutException("timeout").toArkluzFailureDetail()

        assertEquals("Arkluz did not respond in time. Try again.", detail)
    }

    @Test
    fun networkIoDoesNotLeakRawTransportText() {
        val detail = IOException("stream was reset").toArkluzFailureDetail()

        assertTrue(detail.contains("Network connection to Arkluz"))
    }
}
