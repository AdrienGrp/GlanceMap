package com.glancemap.glancemapwearos.presentation.features.maps.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ThemeViewModelDemSupportTest {

    @Test
    fun socketTimeoutIsNotMarkedOfflineWhenInternetIsAvailable() {
        val networkUnavailable = classifyDemFailureAsNetworkUnavailable(
            throwable = SocketTimeoutException("Read timed out"),
            internetAvailableNow = true
        )

        assertFalse(networkUnavailable)
    }

    @Test
    fun socketTimeoutIsMarkedOfflineWhenInternetIsUnavailable() {
        val networkUnavailable = classifyDemFailureAsNetworkUnavailable(
            throwable = SocketTimeoutException("Read timed out"),
            internetAvailableNow = false
        )

        assertTrue(networkUnavailable)
    }

    @Test
    fun unknownHostStillCountsAsOffline() {
        val networkUnavailable = classifyDemFailureAsNetworkUnavailable(
            throwable = UnknownHostException("Unable to resolve host"),
            internetAvailableNow = true
        )

        assertTrue(networkUnavailable)
    }

    @Test
    fun timeoutFailureGetsFriendlyRetryMessage() {
        val message = buildDemFailureMessage(
            throwable = SocketTimeoutException("Read timed out"),
            networkUnavailable = false
        )

        assertEquals(
            "DEM download timed out. Retry when the watch internet connection is stable.",
            message
        )
    }

    @Test
    fun timeoutFailuresRemainRetryable() {
        assertTrue(isRetryableDemDownloadFailure(SocketTimeoutException("Read timed out")))
    }
}
