package com.glancemap.glancemapcompanionapp.livetracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoMovementAlertValidationTest {
    @Test
    fun acceptsDisabledAndValuesAtLeastTenMinutes() {
        assertNull(validateNoMovementAlertMinutes("-1"))
        assertNull(validateNoMovementAlertMinutes("10"))
        assertNull(validateNoMovementAlertMinutes("60"))
    }

    @Test
    fun rejectsBlankInvalidAndTooSmallValues() {
        val expected = "No-movement alert must be at least 10 minutes, or disabled."

        assertEquals(expected, validateNoMovementAlertMinutes(""))
        assertEquals(expected, validateNoMovementAlertMinutes("invalid"))
        assertEquals(expected, validateNoMovementAlertMinutes("9"))
        assertEquals(expected, validateNoMovementAlertMinutes("-2"))
    }
}
