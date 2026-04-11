package com.glancemap.glancemapwearos.presentation.features.routetools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.LatLong

class RouteToolPlannerMappingTest {

    @Test
    fun loopRequestKeepsDistanceTargetWhenDistanceModeIsSelected() {
        val request = RouteToolSession(
            options = RouteToolOptions(
                toolKind = RouteToolKind.CREATE,
                createMode = RouteCreateMode.LOOP_AROUND_HERE,
                loopTargetMode = LoopTargetMode.DISTANCE,
                loopDistanceKm = 12
            )
        ).toRoundTripPlannerRequest(currentLocation = LatLong(42.5, 1.6))

        assertEquals(12_000, request.targetDistanceMeters)
        assertEquals("12 km", request.targetLabel)
    }

    @Test
    fun loopRequestConvertsTimeTargetIntoEstimatedDistance() {
        val request = RouteToolSession(
            options = RouteToolOptions(
                toolKind = RouteToolKind.CREATE,
                createMode = RouteCreateMode.LOOP_AROUND_HERE,
                loopTargetMode = LoopTargetMode.TIME,
                loopDurationMinutes = 90,
                routeStyle = RouteStylePreset.BALANCED_HIKE,
                useElevation = true
            )
        ).toRoundTripPlannerRequest(currentLocation = LatLong(42.5, 1.6))

        assertEquals(6_000, request.targetDistanceMeters)
        assertEquals("1 h 30", request.targetLabel)
    }

    @Test
    fun loopRequestDefaultsToCircuitPreference() {
        val request = RouteToolSession(
            options = RouteToolOptions(
                toolKind = RouteToolKind.CREATE,
                createMode = RouteCreateMode.LOOP_AROUND_HERE
            )
        ).toRoundTripPlannerRequest(currentLocation = LatLong(42.5, 1.6))

        assertFalse(request.allowOutAndBack)
        assertEquals(5, request.pointCount)
    }

    @Test
    fun loopRequestCanAllowOutAndBack() {
        val request = RouteToolSession(
            options = RouteToolOptions(
                toolKind = RouteToolKind.CREATE,
                createMode = RouteCreateMode.LOOP_AROUND_HERE,
                loopShapeMode = LoopShapeMode.ALLOW_OUT_AND_BACK
            )
        ).toRoundTripPlannerRequest(currentLocation = LatLong(42.5, 1.6))

        assertTrue(request.allowOutAndBack)
        assertEquals(5, request.pointCount)
    }

    @Test
    fun multiPointRequestUsesMiddlePointsAsViaPoints() {
        val request = RouteToolSession(
            options = RouteToolOptions(
                toolKind = RouteToolKind.CREATE,
                createMode = RouteCreateMode.MULTI_POINT_CHAIN
            ),
            chainPoints = listOf(
                LatLong(42.50, 1.50),
                LatLong(42.55, 1.55),
                LatLong(42.60, 1.60),
                LatLong(42.65, 1.65)
            )
        ).toRoutePlannerRequest(currentLocation = null)

        assertEquals(LatLong(42.50, 1.50), request.origin)
        assertEquals(LatLong(42.65, 1.65), request.destination)
        assertEquals(
            listOf(LatLong(42.55, 1.55), LatLong(42.60, 1.60)),
            request.viaPoints
        )
    }
}
