package com.glancemap.glancemapwearos.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityRuntimeOwnershipTest {
    @Test
    fun navigateOwnsRuntimeOnlyWhileItsInteractiveUiIsComposed() {
        assertFalse(
            activityOwnsNavigationRuntime(
                isNavigateScreen = true,
                isAmbient = false,
            ),
        )
        assertTrue(
            activityOwnsNavigationRuntime(
                isNavigateScreen = true,
                isAmbient = true,
            ),
        )
    }

    @Test
    fun activityOwnsRuntimeOutsideNavigateInBothDisplayModes() {
        assertTrue(
            activityOwnsNavigationRuntime(
                isNavigateScreen = false,
                isAmbient = false,
            ),
        )
        assertTrue(
            activityOwnsNavigationRuntime(
                isNavigateScreen = false,
                isAmbient = true,
            ),
        )
    }
}
