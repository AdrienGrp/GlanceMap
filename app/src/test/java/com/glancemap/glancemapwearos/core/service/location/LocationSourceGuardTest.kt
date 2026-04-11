package com.glancemap.glancemapwearos.core.service.location

import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceGuard
import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSourceGuardTest {
    @Test
    fun acceptsMatchingCallbackOrigin() {
        assertTrue(
            LocationSourceGuard.acceptsCallbackOrigin(
                expectedSourceMode = LocationSourceMode.AUTO_FUSED,
                callbackOrigin = LocationSourceMode.AUTO_FUSED,
            ),
        )
        assertTrue(
            LocationSourceGuard.acceptsCallbackOrigin(
                expectedSourceMode = LocationSourceMode.WATCH_GPS,
                callbackOrigin = LocationSourceMode.WATCH_GPS,
            ),
        )
        assertEquals("auto_fused", LocationSourceGuard.expectedOrigin(LocationSourceMode.AUTO_FUSED))
        assertEquals("watch_gps", LocationSourceGuard.expectedOrigin(LocationSourceMode.WATCH_GPS))
    }

    @Test
    fun rejectsMismatchedCallbackOrigin() {
        assertFalse(
            LocationSourceGuard.acceptsCallbackOrigin(
                expectedSourceMode = LocationSourceMode.WATCH_GPS,
                callbackOrigin = LocationSourceMode.AUTO_FUSED,
            ),
        )
        assertFalse(
            LocationSourceGuard.acceptsCallbackOrigin(
                expectedSourceMode = LocationSourceMode.AUTO_FUSED,
                callbackOrigin = LocationSourceMode.WATCH_GPS,
            ),
        )
    }
}
