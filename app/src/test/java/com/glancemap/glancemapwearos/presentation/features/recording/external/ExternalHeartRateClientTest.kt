package com.glancemap.glancemapwearos.presentation.features.recording.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalHeartRateClientTest {
    @Test
    fun decodesStandardBatteryPercentage() {
        assertEquals(85, ExternalHeartRateClient.decodeBatteryLevel(byteArrayOf(85)))
    }

    @Test
    fun rejectsBatteryPercentageAboveOneHundred() {
        assertNull(ExternalHeartRateClient.decodeBatteryLevel(byteArrayOf(101)))
    }
}
