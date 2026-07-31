package com.glancemap.glancemapwearos.presentation.features.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElevationLabelThemeCallbackTest {
    @Test
    fun `metric keeps original ele label`() {
        assertEquals("2301", convertMapEleValueToDisplayText("2301", isMetric = true))
    }

    @Test
    fun `imperial converts meters to rounded feet`() {
        assertEquals("7549", convertMapEleValueToDisplayText("2301", isMetric = false))
    }

    @Test
    fun `invalid elevation text is ignored`() {
        assertNull(convertMapEleValueToDisplayText("not-a-number", isMetric = false))
    }
}
