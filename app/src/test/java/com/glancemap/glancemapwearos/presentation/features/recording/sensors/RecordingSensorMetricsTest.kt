package com.glancemap.glancemapwearos.presentation.features.recording.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingSensorMetricsTest {
    @Test
    fun unavailableRunPodClearsOnlyMetricsOwnedByExternalSensor() {
        val metrics =
            RecordingSensorMetrics(
                cadenceSpm = 174,
                cadenceUpdatedAtMillis = 1_000L,
                cadenceFromBluetooth = true,
                externalSpeedMps = 3.2f,
                externalSpeedUpdatedAtMillis = 1_000L,
                externalDistanceRawUnits = 100L,
                externalDistanceMeters = 10.0,
                externalDistanceUpdatedAtMillis = 1_000L,
                externalPowerWatts = 245,
                externalPowerUpdatedAtMillis = 1_000L,
                externalBatteryLevelPercent = 80,
            ).withExternalRunPodUnavailable(clearCadence = true, clearPower = true)

        assertNull(metrics.cadenceSpm)
        assertNull(metrics.externalSpeedMps)
        assertNull(metrics.externalDistanceRawUnits)
        assertNull(metrics.externalDistanceMeters)
        assertNull(metrics.externalPowerWatts)
        assertEquals(80, metrics.externalBatteryLevelPercent)
    }

    @Test
    fun unavailableRunPodPreservesWatchCadenceAndUnselectedPower() {
        val metrics =
            RecordingSensorMetrics(
                cadenceSpm = 168,
                cadenceUpdatedAtMillis = 2_000L,
                cadenceFromBluetooth = false,
                externalPowerWatts = 230,
                externalPowerUpdatedAtMillis = 2_000L,
                externalSpeedMps = 4f,
            ).withExternalRunPodUnavailable(clearCadence = false, clearPower = false)

        assertEquals(168, metrics.cadenceSpm)
        assertEquals(2_000L, metrics.cadenceUpdatedAtMillis)
        assertEquals(false, metrics.cadenceFromBluetooth)
        assertEquals(230, metrics.externalPowerWatts)
        assertEquals(2_000L, metrics.externalPowerUpdatedAtMillis)
        assertNull(metrics.externalSpeedMps)
    }
}
