package com.glancemap.glancemapwearos.presentation.features.navigate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.LatLong

class NavigateDoubleTapPanningTest {
    @Test
    fun doubleTapDoesNotEnterPanningWhenMarkerRemainsCentered() {
        val marker = LatLong(45.0, 6.0)
        val center = LatLong(45.00002, 6.0)

        assertFalse(shouldEnterPanningAfterDoubleTap(center = center, marker = marker))
    }

    @Test
    fun doubleTapEntersPanningWhenMarkerIsNoLongerCentered() {
        val marker = LatLong(45.0, 6.0)
        val center = LatLong(45.00008, 6.0)

        assertTrue(shouldEnterPanningAfterDoubleTap(center = center, marker = marker))
    }

    @Test
    fun doubleTapDoesNotEnterPanningWithoutMarker() {
        assertFalse(
            shouldEnterPanningAfterDoubleTap(
                center = LatLong(45.00008, 6.0),
                marker = null,
            ),
        )
    }
}
