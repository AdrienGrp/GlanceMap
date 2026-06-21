package com.glancemap.glancemapwearos.presentation.features.recording.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalRunPodClientTest {
    @Test
    fun decodesBasicRunningSpeedAndCadenceMeasurement() {
        val measurement =
            ExternalRunPodClient.decodeRscMeasurement(
                byteArrayOf(
                    0x00,
                    0x80.toByte(),
                    0x02,
                    164.toByte(),
                ),
            )

        requireNotNull(measurement)
        assertEquals(2.5f, measurement.speedMps)
        assertEquals(164, measurement.cadenceSpm)
        assertNull(measurement.rawTotalDistanceUnits)
        assertNull(measurement.totalDistanceMeters)
    }

    @Test
    fun decodesRunningMeasurementWithStrideAndDistance() {
        val measurement =
            ExternalRunPodClient.decodeRscMeasurement(
                byteArrayOf(
                    0x03,
                    0x00,
                    0x03,
                    180.toByte(),
                    0x78,
                    0x00,
                    0x39,
                    0x30,
                    0x00,
                    0x00,
                ),
            )

        requireNotNull(measurement)
        assertEquals(3.0f, measurement.speedMps)
        assertEquals(180, measurement.cadenceSpm)
        assertEquals(12_345L, measurement.rawTotalDistanceUnits)
        assertEquals(1_234.5, measurement.totalDistanceMeters)
    }

    @Test
    fun rejectsTruncatedOptionalRunningMeasurementFields() {
        assertNull(
            ExternalRunPodClient.decodeRscMeasurement(
                byteArrayOf(
                    0x02,
                    0x00,
                    0x03,
                    180.toByte(),
                    0x39,
                    0x30,
                ),
            ),
        )
    }

    @Test
    fun preservesMeasurementWhileRejectingImplausibleRunningSpeed() {
        val measurement =
            ExternalRunPodClient.decodeRscMeasurement(
                byteArrayOf(
                    0x00,
                    0x00,
                    0x0D,
                    180.toByte(),
                ),
            )

        requireNotNull(measurement)
        assertNull(measurement.speedMps)
        assertEquals(180, measurement.cadenceSpm)
    }

    @Test
    fun decodesCyclingPowerMeasurement() {
        val measurement =
            ExternalRunPodClient.decodeCyclingPowerMeasurement(
                byteArrayOf(
                    0x00,
                    0x00,
                    0x2C,
                    0x01,
                ),
            )

        requireNotNull(measurement)
        assertEquals(300, measurement.powerWatts)
    }

    @Test
    fun rejectsTruncatedCyclingPowerMeasurement() {
        assertNull(
            ExternalRunPodClient.decodeCyclingPowerMeasurement(
                byteArrayOf(0x00, 0x00, 0x2C),
            ),
        )
    }

    @Test
    fun preservesCyclingPowerMeasurementWhileRejectingImplausiblePower() {
        val measurement =
            ExternalRunPodClient.decodeCyclingPowerMeasurement(
                byteArrayOf(
                    0x00,
                    0x00,
                    0xB8.toByte(),
                    0x0B,
                ),
            )

        requireNotNull(measurement)
        assertNull(measurement.powerWatts)
    }
}
