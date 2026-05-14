package com.glancemap.glancemapwearos.core.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteDataFilesTest {
    @Test
    fun routingSegmentBoundsParsesEasternNorthernTile() {
        val bounds = routingSegmentBounds("E0_N45.rd5")

        requireNotNull(bounds)
        assertEquals(45.0, bounds.minLat, 0.0)
        assertEquals(50.0, bounds.maxLat, 0.0)
        assertEquals(0.0, bounds.minLon, 0.0)
        assertEquals(5.0, bounds.maxLon, 0.0)
    }

    @Test
    fun routingSegmentBoundsParsesWesternSouthernTile() {
        val bounds = routingSegmentBounds("W5_S10.rd5")

        requireNotNull(bounds)
        assertEquals(-10.0, bounds.minLat, 0.0)
        assertEquals(-5.0, bounds.maxLat, 0.0)
        assertEquals(-5.0, bounds.minLon, 0.0)
        assertEquals(0.0, bounds.maxLon, 0.0)
    }

    @Test
    fun routingSegmentBoundsRejectsUnknownNames() {
        assertNull(routingSegmentBounds("routing-pack.rd5"))
        assertNull(routingSegmentBounds("E0_N45.zip"))
    }
}
