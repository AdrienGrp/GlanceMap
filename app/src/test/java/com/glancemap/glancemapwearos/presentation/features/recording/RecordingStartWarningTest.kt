package com.glancemap.glancemapwearos.presentation.features.recording

import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingStartWarningTest {
    @Test
    fun `internal sources do not require a warning`() {
        assertNull(
            resolveRecordingStartWarning(
                heartRateSource = SettingsRepository.RECORDING_HEART_RATE_SOURCE_WATCH,
                cadenceSource = SettingsRepository.RECORDING_SENSOR_SOURCE_WATCH_GPS,
                speedSource = SettingsRepository.RECORDING_SENSOR_SOURCE_WATCH_GPS,
                distanceSource = SettingsRepository.RECORDING_SENSOR_SOURCE_WATCH_GPS,
                externalHeartRateAddress = null,
                externalRunPodAddress = null,
            ),
        )
    }

    @Test
    fun `linked external sources are reported as not connected yet`() {
        val warning =
            resolveRecordingStartWarning(
                heartRateSource = SettingsRepository.RECORDING_HEART_RATE_SOURCE_STRAP,
                cadenceSource = SettingsRepository.RECORDING_SENSOR_SOURCE_POD,
                speedSource = SettingsRepository.RECORDING_SENSOR_SOURCE_WATCH_GPS,
                distanceSource = SettingsRepository.RECORDING_SENSOR_SOURCE_POD,
                externalHeartRateAddress = "AA:BB:CC:DD:EE:01",
                externalRunPodAddress = "AA:BB:CC:DD:EE:02",
            )

        requireNotNull(warning)
        assertTrue(warning.unlinkedDevices.isEmpty())
        assertEquals(listOf("heart-rate strap", "run pod"), warning.disconnectedDevices)
        assertTrue(warning.message.contains("Recording will try to connect"))
    }

    @Test
    fun `connected external sources do not require a warning`() {
        assertNull(
            resolveRecordingStartWarning(
                heartRateSource = SettingsRepository.RECORDING_HEART_RATE_SOURCE_STRAP,
                cadenceSource = SettingsRepository.RECORDING_SENSOR_SOURCE_POD,
                speedSource = SettingsRepository.RECORDING_SENSOR_SOURCE_WATCH_GPS,
                distanceSource = SettingsRepository.RECORDING_SENSOR_SOURCE_POD,
                externalHeartRateAddress = "aa:bb:cc:dd:ee:01",
                externalRunPodAddress = "aa:bb:cc:dd:ee:02",
                connectedExternalAddresses =
                    setOf(
                        "AA:BB:CC:DD:EE:01",
                        "AA:BB:CC:DD:EE:02",
                    ),
            ),
        )
    }

    @Test
    fun `selected external source without a linked device is explicit`() {
        val warning =
            resolveRecordingStartWarning(
                heartRateSource = SettingsRepository.RECORDING_HEART_RATE_SOURCE_STRAP,
                cadenceSource = SettingsRepository.RECORDING_SENSOR_SOURCE_WATCH_GPS,
                speedSource = SettingsRepository.RECORDING_SENSOR_SOURCE_WATCH_GPS,
                distanceSource = SettingsRepository.RECORDING_SENSOR_SOURCE_WATCH_GPS,
                externalHeartRateAddress = null,
                externalRunPodAddress = null,
            )

        requireNotNull(warning)
        assertEquals(listOf("heart-rate strap"), warning.unlinkedDevices)
        assertTrue(warning.disconnectedDevices.isEmpty())
        assertTrue(warning.message.contains("No linked device"))
    }
}
