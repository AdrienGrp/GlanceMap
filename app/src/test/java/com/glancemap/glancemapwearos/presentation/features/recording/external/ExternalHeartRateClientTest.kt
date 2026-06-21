package com.glancemap.glancemapwearos.presentation.features.recording.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalHeartRateClientTest {
    @Test
    fun decodesEightBitHeartRateMeasurement() {
        assertEquals(
            72,
            ExternalHeartRateClient.decodeHeartRateMeasurement(
                byteArrayOf(0x00, 72),
            ),
        )
    }

    @Test
    fun decodesSixteenBitHeartRateMeasurement() {
        assertEquals(
            180,
            ExternalHeartRateClient.decodeHeartRateMeasurement(
                byteArrayOf(0x01, 0xB4.toByte(), 0x00),
            ),
        )
    }

    @Test
    fun rejectsTruncatedHeartRateMeasurement() {
        assertNull(ExternalHeartRateClient.decodeHeartRateMeasurement(byteArrayOf(0x01, 72)))
    }

    @Test
    fun rejectsHeartRateOutsideSupportedRange() {
        assertNull(
            ExternalHeartRateClient.decodeHeartRateMeasurement(
                byteArrayOf(0x00, 19),
            ),
        )
    }

    @Test
    fun decodesStandardBatteryPercentage() {
        assertEquals(85, ExternalHeartRateClient.decodeBatteryLevel(byteArrayOf(85)))
    }

    @Test
    fun rejectsBatteryPercentageAboveOneHundred() {
        assertNull(ExternalHeartRateClient.decodeBatteryLevel(byteArrayOf(101)))
    }
}
