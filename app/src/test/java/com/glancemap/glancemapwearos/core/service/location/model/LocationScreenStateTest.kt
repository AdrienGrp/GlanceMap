package com.glancemap.glancemapwearos.core.service.location.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationScreenStateTest {

    @Test
    fun resolvesAmbientWhenAmbientLifecycleIsActive() {
        val screenState = resolveLocationScreenState(
            isAmbient = true,
            isDeviceInteractive = false
        )

        assertEquals(LocationScreenState.AMBIENT, screenState)
    }

    @Test
    fun resolvesScreenOffWhenDeviceIsNotInteractiveOutsideAmbient() {
        val screenState = resolveLocationScreenState(
            isAmbient = false,
            isDeviceInteractive = false
        )

        assertEquals(LocationScreenState.SCREEN_OFF, screenState)
    }

    @Test
    fun interactiveHelpersMatchState() {
        assertTrue(LocationScreenState.INTERACTIVE.isInteractive)
        assertFalse(LocationScreenState.INTERACTIVE.isNonInteractive)
        assertTrue(LocationScreenState.AMBIENT.isNonInteractive)
        assertTrue(LocationScreenState.SCREEN_OFF.isNonInteractive)
    }
}
