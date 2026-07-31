package com.glancemap.glancemapwearos.core.service.location.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFixPolicyTest {
    @Test
    fun acceptsFiniteCoordinatesInsideEarthBounds() {
        assertTrue(LocationFixPolicy.hasValidCoordinates(latitude = 48.8566, longitude = 2.3522))
        assertTrue(LocationFixPolicy.hasValidCoordinates(latitude = -90.0, longitude = -180.0))
        assertTrue(LocationFixPolicy.hasValidCoordinates(latitude = 90.0, longitude = 180.0))
    }

    @Test
    fun rejectsNonFiniteCoordinates() {
        assertFalse(LocationFixPolicy.hasValidCoordinates(latitude = Double.NaN, longitude = 2.3522))
        assertFalse(LocationFixPolicy.hasValidCoordinates(latitude = 48.8566, longitude = Double.NaN))
        assertFalse(
            LocationFixPolicy.hasValidCoordinates(
                latitude = Double.POSITIVE_INFINITY,
                longitude = 2.3522,
            ),
        )
    }

    @Test
    fun rejectsCoordinatesOutsideEarthBounds() {
        assertFalse(LocationFixPolicy.hasValidCoordinates(latitude = 90.0001, longitude = 2.3522))
        assertFalse(LocationFixPolicy.hasValidCoordinates(latitude = 48.8566, longitude = 180.0001))
        assertFalse(LocationFixPolicy.hasValidCoordinates(latitude = -90.0001, longitude = 2.3522))
        assertFalse(LocationFixPolicy.hasValidCoordinates(latitude = 48.8566, longitude = -180.0001))
    }
}
