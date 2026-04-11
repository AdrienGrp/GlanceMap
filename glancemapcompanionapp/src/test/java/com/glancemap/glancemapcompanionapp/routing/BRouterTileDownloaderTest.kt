package com.glancemap.glancemapcompanionapp.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BRouterTileDownloaderTest {

    @Test
    fun `computes overall routing progress within download range`() {
        assertEquals(0, overallRoutingDownloadProgress(stepIndex = 0, totalSteps = 1, stepFraction = 0.0))
        assertEquals(43, overallRoutingDownloadProgress(stepIndex = 0, totalSteps = 1, stepFraction = 0.5))
        assertEquals(85, overallRoutingDownloadProgress(stepIndex = 0, totalSteps = 1, stepFraction = 1.0))
    }

    @Test
    fun `marks transient routing statuses as retriable`() {
        assertTrue(isRetriableRoutingStatus(504))
        assertTrue(isRetriableRoutingStatus(429))
        assertFalse(isRetriableRoutingStatus(404))
    }
}
