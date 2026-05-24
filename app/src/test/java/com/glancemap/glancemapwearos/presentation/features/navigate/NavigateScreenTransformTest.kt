package com.glancemap.glancemapwearos.presentation.features.navigate

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigateScreenTransformTest {
    @Test
    fun rotateMapSpaceToScreenUsesCustomPivot() {
        val point = ScreenAnchor(10.0, 0.0)
        val pivot = ScreenAnchor(10.0, 10.0)

        val rotated =
            rotateMapSpaceToScreen(
                point = point,
                mapWidth = 100.0,
                mapHeight = 100.0,
                mapRotationDeg = 90.0,
                pivot = pivot,
            )

        assertEquals(20.0, rotated.x, 0.001)
        assertEquals(10.0, rotated.y, 0.001)
    }

    @Test
    fun unrotateTouchToMapSpaceReversesCustomPivotRotation() {
        val original = ScreenAnchor(10.0, 0.0)
        val pivot = ScreenAnchor(10.0, 10.0)
        val rotated =
            rotateMapSpaceToScreen(
                point = original,
                mapWidth = 100.0,
                mapHeight = 100.0,
                mapRotationDeg = 90.0,
                pivot = pivot,
            )

        val unrotated =
            unrotateTouchToMapSpace(
                point = rotated,
                mapWidth = 100.0,
                mapHeight = 100.0,
                mapRotationDeg = 90.0,
                pivot = pivot,
            )

        assertEquals(original.x, unrotated.x, 0.001)
        assertEquals(original.y, unrotated.y, 0.001)
    }
}
