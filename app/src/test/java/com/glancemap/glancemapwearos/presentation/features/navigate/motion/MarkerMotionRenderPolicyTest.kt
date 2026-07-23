package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.LatLong

class MarkerMotionRenderPolicyTest {
    @Test
    fun movingFreshFollowModeUsesTenHertzCadence() {
        val decision =
            markerMotionRenderDecision(
                activeFollowInputs,
            )

        assertEquals(100L, decision.tickIntervalMs)
    }

    @Test
    fun movingFreeMapUsesLowerCadence() {
        val decision =
            markerMotionRenderDecision(
                activeFollowInputs.copy(isFollowingPosition = false),
            )

        assertEquals(250L, decision.tickIntervalMs)
    }

    @Test
    fun inactiveStatesUseEventDrivenRendering() {
        assertNull(markerMotionRenderDecision(activeFollowInputs.copy(isInteractive = false)).tickIntervalMs)
        assertNull(markerMotionRenderDecision(activeFollowInputs.copy(isMoving = false)).tickIntervalMs)
        assertNull(markerMotionRenderDecision(activeFollowInputs.copy(isFresh = false)).tickIntervalMs)
    }

    @Test
    fun pendingCorrectionKeepsSmoothCadenceAfterMovementStops() {
        val decision =
            markerMotionRenderDecision(
                activeFollowInputs.copy(
                    isMoving = false,
                    hasPendingVisualCorrection = true,
                ),
            )

        assertEquals(100L, decision.tickIntervalMs)
    }

    @Test
    fun zoomAwareThresholdRemainsFourTenthsOfAPixel() {
        val latitude = 48.8566
        val closeMetersPerPixel = requireNotNull(markerMotionMetersPerPixel(latitude, 18, TILE_SIZE))
        val farMetersPerPixel = requireNotNull(markerMotionMetersPerPixel(latitude, 15, TILE_SIZE))
        val closeThreshold = markerMotionRenderThresholdMeters(closeMetersPerPixel)
        val farThreshold = markerMotionRenderThresholdMeters(farMetersPerPixel)

        assertEquals(TARGET_RENDER_DISPLACEMENT_PX, closeThreshold / closeMetersPerPixel, 0.0001)
        assertEquals(TARGET_RENDER_DISPLACEMENT_PX, farThreshold / farMetersPerPixel, 0.0001)
        assertEquals(8f, farThreshold / closeThreshold, 0.01f)
    }

    @Test
    fun scaleAwareRenderDecisionUsesPixelDisplacement() {
        val base = LatLong(48.8566, 2.3522)
        val metersPerPixel = requireNotNull(markerMotionMetersPerPixel(base.latitude, 18, TILE_SIZE))
        val decision =
            markerMotionRenderDecision(
                activeFollowInputs.copy(
                    metersPerPixel = metersPerPixel,
                ),
            )

        assertFalse(
            shouldRenderMarkerMotion(
                previous = base,
                candidate = moveNorth(base, metersPerPixel * 0.35),
                decision = decision,
            ),
        )
        assertTrue(
            shouldRenderMarkerMotion(
                previous = base,
                candidate = moveNorth(base, metersPerPixel * 0.45),
                decision = decision,
            ),
        )
    }

    @Test
    fun invalidScaleRetainsLegacySafeThreshold() {
        assertEquals(MARKER_MOTION_RENDER_THRESHOLD_M, markerMotionRenderThresholdMeters(null))
        assertEquals(MARKER_MOTION_RENDER_THRESHOLD_M, markerMotionRenderThresholdMeters(Double.NaN))
        assertEquals(MARKER_MOTION_RENDER_THRESHOLD_M, markerMotionRenderThresholdMeters(0.0))
    }

    private val activeFollowInputs =
        MarkerMotionRenderInputs(
            isInteractive = true,
            isFollowingPosition = true,
            isMoving = true,
            isFresh = true,
        )

    private fun moveNorth(
        origin: LatLong,
        distanceM: Double,
    ): LatLong = LatLong(origin.latitude + distanceM / METERS_PER_LATITUDE_DEGREE, origin.longitude)

    private companion object {
        const val TILE_SIZE = 256
        const val METERS_PER_LATITUDE_DEGREE = 111_320.0
    }
}
