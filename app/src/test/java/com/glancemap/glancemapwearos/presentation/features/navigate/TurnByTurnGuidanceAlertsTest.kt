package com.glancemap.glancemapwearos.presentation.features.navigate

import org.junit.Assert.assertEquals
import org.junit.Test

class TurnByTurnGuidanceAlertsTest {
    @Test
    fun walkingVoiceAlertKeepsUsefulPreparationDistance() {
        assertEquals(35.0, turnAlertDistanceMeters(1.4f), 0.01)
    }

    @Test
    fun walkingHapticOpensSlightlyBeforeTheTurn() {
        assertEquals(10.0, turnHapticDistanceMeters(1.4f), 0.01)
    }

    @Test
    fun hapticWindowExpandsForSlowerGpsDelivery() {
        val frequentGps = turnHapticDistanceMeters(speedMps = 1.4f, gpsDeliveryIntervalMs = 1_000L)
        val slowerGps = turnHapticDistanceMeters(speedMps = 1.4f, gpsDeliveryIntervalMs = 10_000L)

        assertEquals(10.0, frequentGps, 0.01)
        assertEquals(12.6, slowerGps, 0.01)
    }

    @Test
    fun hapticWindowStillAdaptsWhenGpsSpeedIsUnavailable() {
        val defaultGps = turnHapticDistanceMeters(speedMps = null, gpsDeliveryIntervalMs = 3_000L)
        val slowerGps = turnHapticDistanceMeters(speedMps = null, gpsDeliveryIntervalMs = 10_000L)

        assertEquals(12.0, defaultGps, 0.01)
        assertEquals(17.25, slowerGps, 0.01)
    }
}
