package com.glancemap.glancemapwearos.presentation.features.navigate

import org.junit.Assert.assertEquals
import org.junit.Test

class TurnByTurnGuidanceAlertsTest {
    @Test
    fun walkingVoiceAlertKeepsUsefulPreparationDistance() {
        assertEquals(35.0, turnAlertDistanceMeters(1.4f), 0.01)
    }

    @Test
    fun walkingHapticOccursAtTheTurn() {
        assertEquals(8.0, turnHapticDistanceMeters(1.4f), 0.01)
    }
}
