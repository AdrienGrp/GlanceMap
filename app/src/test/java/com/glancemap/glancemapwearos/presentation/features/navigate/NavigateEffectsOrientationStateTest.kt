package com.glancemap.glancemapwearos.presentation.features.navigate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigateEffectsOrientationStateTest {
    @Test
    fun compassModeUsesFixedCenterMarkerState() {
        val state =
            markerRenderStateForMode(
                navMode = NavMode.COMPASS_FOLLOW,
                displayedHeadingDeg = 123f,
                displayedMapRotationDeg = -57f,
                frozenMapRotationDeg = -57f,
                showRealMarkerInCompassMode = false,
            )

        assertFalse(state.isVisible)
        assertEquals(0f, state.headingDeg, 0f)
    }

    @Test
    fun compassModeCanShowRealMarkerWhenRequested() {
        val state =
            markerRenderStateForMode(
                navMode = NavMode.COMPASS_FOLLOW,
                displayedHeadingDeg = 222f,
                displayedMapRotationDeg = -222f,
                frozenMapRotationDeg = -222f,
                showRealMarkerInCompassMode = true,
            )

        assertTrue(state.isVisible)
        assertEquals(0f, state.headingDeg, 0f)
    }

    @Test
    fun northUpModeCompensatesCurrentMapRotation() {
        val state =
            markerRenderStateForMode(
                navMode = NavMode.NORTH_UP_FOLLOW,
                displayedHeadingDeg = 15f,
                displayedMapRotationDeg = -90f,
                frozenMapRotationDeg = -90f,
                showRealMarkerInCompassMode = false,
            )

        assertTrue(state.isVisible)
        assertEquals(285f, state.headingDeg, 0.0001f)
    }

    @Test
    fun northUpMarkerHeadingChangesWhenOnlyMapRotationChanges() {
        val heading = 42f
        val stateWithRotA =
            markerRenderStateForMode(
                navMode = NavMode.NORTH_UP_FOLLOW,
                displayedHeadingDeg = heading,
                displayedMapRotationDeg = -90f,
                frozenMapRotationDeg = -90f,
                showRealMarkerInCompassMode = false,
            )
        val stateWithRotB =
            markerRenderStateForMode(
                navMode = NavMode.NORTH_UP_FOLLOW,
                displayedHeadingDeg = heading,
                displayedMapRotationDeg = -45f,
                frozenMapRotationDeg = -45f,
                showRealMarkerInCompassMode = false,
            )

        assertNotEquals(stateWithRotA.headingDeg, stateWithRotB.headingDeg)
        assertEquals(312f, stateWithRotA.headingDeg, 0.0001f)
        assertEquals(357f, stateWithRotB.headingDeg, 0.0001f)
    }

    @Test
    fun panningModeUsesFrozenRotationNotCurrentMapRotation() {
        val state =
            markerRenderStateForMode(
                navMode = NavMode.PANNING,
                displayedHeadingDeg = 30f,
                displayedMapRotationDeg = -10f,
                frozenMapRotationDeg = -80f,
                showRealMarkerInCompassMode = false,
            )

        assertTrue(state.isVisible)
        assertEquals(310f, state.headingDeg, 0.0001f)
    }
}
