package com.glancemap.glancemapwearos.presentation.features.navigate.effects

import com.glancemap.glancemapwearos.domain.sensors.CompassProviderType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigateCalibrationEffectsSupportTest {
    @Test
    fun calibrationPromptsSupportedForSensorManagerOnly() {
        assertTrue(supportsCompassCalibrationPrompts(CompassProviderType.SENSOR_MANAGER))
        assertFalse(supportsCompassCalibrationPrompts(CompassProviderType.GOOGLE_FUSED))
    }
}
