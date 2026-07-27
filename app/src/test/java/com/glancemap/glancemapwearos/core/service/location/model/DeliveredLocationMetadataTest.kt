package com.glancemap.glancemapwearos.core.service.location.model

import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveredLocationMetadataTest {
    @Test
    fun parsesKnownSourceMode() {
        assertEquals(
            LocationSourceMode.WATCH_GPS,
            parseDeliveredSourceMode(LocationSourceMode.WATCH_GPS.name),
        )
    }

    @Test
    fun ignoresUnknownOrMissingSourceMode() {
        assertNull(parseDeliveredSourceMode("future_source"))
        assertNull(parseDeliveredSourceMode(null))
    }
}
