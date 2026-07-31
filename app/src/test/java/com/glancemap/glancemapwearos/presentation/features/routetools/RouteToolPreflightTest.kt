package com.glancemap.glancemapwearos.presentation.features.routetools

import com.glancemap.glancemapwearos.core.service.location.model.GpsSignalSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.LatLong

class RouteToolPreflightTest {
    @Test
    fun routeToolAcceptsRecentOriginWhenProviderTemporarilyReportsUnavailable() {
        val usable =
            hasUsableRouteToolCurrentLocation(
                currentLocation = LatLong(9.03, 38.74),
                gpsSignalSnapshot =
                    GpsSignalSnapshot(
                        lastFixElapsedRealtimeMs = 90_000L,
                        isLocationAvailable = false,
                        lastFixFresh = false,
                    ),
                nowElapsedMs = 100_000L,
            )

        assertTrue(usable)
    }

    @Test
    fun routeToolRejectsOriginOlderThanTenSeconds() {
        val usable =
            hasUsableRouteToolCurrentLocation(
                currentLocation = LatLong(9.03, 38.74),
                gpsSignalSnapshot = GpsSignalSnapshot(lastFixElapsedRealtimeMs = 90_000L),
                nowElapsedMs = 100_001L,
            )

        assertFalse(usable)
    }

    @Test
    fun routeToolRequiresAnAcceptedLocation() {
        val usable =
            hasUsableRouteToolCurrentLocation(
                currentLocation = null,
                gpsSignalSnapshot = GpsSignalSnapshot(lastFixElapsedRealtimeMs = 100_000L),
                nowElapsedMs = 100_000L,
            )

        assertFalse(usable)
    }
}
