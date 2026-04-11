package com.glancemap.glancemapwearos.core.service.location

import com.glancemap.glancemapwearos.core.service.location.adapters.WatchGpsAvailabilityReason
import com.glancemap.glancemapwearos.core.service.location.adapters.resolveWatchGpsAvailabilityReason
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchGpsCapabilityTest {
    @Test
    fun reportsHardwareMissingBeforeProviderChecks() {
        val reason =
            resolveWatchGpsAvailabilityReason(
                hasGpsHardwareFeature = false,
                isGpsProviderPresent = true,
                isGpsProviderEnabled = true,
            )

        assertEquals(WatchGpsAvailabilityReason.HARDWARE_MISSING, reason)
    }

    @Test
    fun reportsProviderMissingWhenHardwareExistsButProviderDoesNot() {
        val reason =
            resolveWatchGpsAvailabilityReason(
                hasGpsHardwareFeature = true,
                isGpsProviderPresent = false,
                isGpsProviderEnabled = false,
            )

        assertEquals(WatchGpsAvailabilityReason.PROVIDER_MISSING, reason)
    }

    @Test
    fun reportsProviderDisabledWhenHardwareExistsButGpsIsOff() {
        val reason =
            resolveWatchGpsAvailabilityReason(
                hasGpsHardwareFeature = true,
                isGpsProviderPresent = true,
                isGpsProviderEnabled = false,
            )

        assertEquals(WatchGpsAvailabilityReason.PROVIDER_DISABLED, reason)
    }

    @Test
    fun reportsAvailableWhenHardwareAndProviderAreReady() {
        val reason =
            resolveWatchGpsAvailabilityReason(
                hasGpsHardwareFeature = true,
                isGpsProviderPresent = true,
                isGpsProviderEnabled = true,
            )

        assertEquals(WatchGpsAvailabilityReason.AVAILABLE, reason)
    }
}
