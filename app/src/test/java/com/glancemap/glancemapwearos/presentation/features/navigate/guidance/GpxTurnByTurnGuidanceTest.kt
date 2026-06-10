package com.glancemap.glancemapwearos.presentation.features.navigate.guidance

import com.glancemap.glancemapwearos.presentation.features.gpx.TrackPoint
import com.glancemap.glancemapwearos.presentation.features.gpx.GpxGuidanceHint
import com.glancemap.glancemapwearos.presentation.features.gpx.GpxGuidanceHintSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.LatLong

class GpxTurnByTurnGuidanceTest {
    @Test
    fun deriveInstructionsDetectsLeftTurnFromGpxGeometry() {
        val session =
            buildGpxGuidanceSession(
                trackId = "left.gpx",
                trackTitle = "Left route",
                trackPoints =
                    listOf(
                        point(0.0, 0.0),
                        point(0.0, 0.001),
                        point(0.001, 0.001),
                    ),
                startReached = true,
            )

        assertEquals(RouteInstructionCommand.LEFT, session.instructions.first().command)
        assertEquals(RouteInstructionCommand.FINISH, session.instructions.last().command)
    }

    @Test
    fun hintedInstructionsArePreferredOverGpxGeometry() {
        val session =
            buildGpxGuidanceSession(
                trackId = "hinted.gpx",
                trackTitle = "Hinted route",
                trackPoints =
                    listOf(
                        point(0.0, 0.0),
                        point(
                            lat = 0.0,
                            lon = 0.001,
                            guidanceHint =
                                GpxGuidanceHint(
                                    commandCode = "TR",
                                    message = "right",
                                    source = GpxGuidanceHintSource.BROUTER,
                                ),
                        ),
                        point(0.001, 0.001),
                    ),
                startReached = true,
            )

        assertEquals(RouteInstructionSource.BROUTER_HINT, session.instructions.first().source)
        assertEquals(RouteInstructionCommand.RIGHT, session.instructions.first().command)
        assertEquals("Right", session.instructions.first().message)
        assertEquals(RouteInstructionCommand.FINISH, session.instructions.last().command)
    }

    @Test
    fun reversedGuidanceInvertsDirectionSpecificHints() {
        val session =
            buildGpxGuidanceSession(
                trackId = "hinted-reverse.gpx",
                trackTitle = "Hinted reverse route",
                trackPoints =
                    listOf(
                        point(0.001, 0.001),
                        point(
                            lat = 0.0,
                            lon = 0.001,
                            guidanceHint =
                                GpxGuidanceHint(
                                    commandCode = "TR",
                                    message = "right",
                                    source = GpxGuidanceHintSource.BROUTER,
                                ),
                        ),
                        point(0.0, 0.0),
                    ),
                startReached = true,
                reversed = true,
            )

        assertEquals(RouteInstructionSource.BROUTER_HINT, session.instructions.first().source)
        assertEquals(RouteInstructionCommand.LEFT, session.instructions.first().command)
        assertEquals("Left", session.instructions.first().message)
    }

    @Test
    fun reversedHintDerivationKeepsNonDirectionalHints() {
        val instructions =
            deriveHintedRouteInstructions(
                trackPoints =
                    listOf(
                        point(0.0, 0.0),
                        point(
                            lat = 0.0,
                            lon = 0.001,
                            guidanceHint =
                                GpxGuidanceHint(
                                    commandCode = "C",
                                    message = "continue",
                                    source = GpxGuidanceHintSource.BROUTER,
                                ),
                        ),
                        point(0.0, 0.002),
                    ),
                reverseDirection = true,
            )

        assertEquals(RouteInstructionCommand.CONTINUE, instructions.first().command)
        assertEquals("Continue", instructions.first().message)
    }

    @Test
    fun guidanceStartsByPointingToGpxStartWhenStartNotReached() {
        val session =
            buildGpxGuidanceSession(
                trackId = "route.gpx",
                trackTitle = "Route",
                trackPoints =
                    listOf(
                        point(45.0, 6.0),
                        point(45.0, 6.001),
                    ),
                startReached = false,
            )

        val state =
            computeTurnByTurnGuidanceState(
                session = session,
                currentLocation = LatLong(45.0, 6.01),
            )

        assertEquals(GuidanceMode.TO_START, state.mode)
        assertTrue((state.distanceToStartMeters ?: 0.0) > 700.0)
        assertNotNull(state.bearingToStartDegrees)
    }

    @Test
    fun guidanceFollowsRouteAfterStartIsReached() {
        val session =
            buildGpxGuidanceSession(
                trackId = "route.gpx",
                trackTitle = "Route",
                trackPoints =
                    listOf(
                        point(45.0, 6.0),
                        point(45.0, 6.001),
                        point(45.001, 6.001),
                    ),
                startReached = true,
            )

        val state =
            computeTurnByTurnGuidanceState(
                session = session,
                currentLocation = LatLong(45.0, 6.0002),
            )

        assertEquals(GuidanceMode.FOLLOW_ROUTE, state.mode)
        assertEquals(RouteInstructionCommand.LEFT, state.nextInstruction?.command)
        assertTrue((state.distanceRemainingMeters ?: 0.0) > 0.0)
        assertTrue((state.routeProgressFraction ?: 0f) > 0f)
        assertTrue((state.routeProgressFraction ?: 1f) < 1f)
    }

    @Test
    fun guidanceFinishesWhenNearRouteEnd() {
        val session =
            buildGpxGuidanceSession(
                trackId = "route.gpx",
                trackTitle = "Route",
                trackPoints =
                    listOf(
                        point(45.0, 6.0),
                        point(45.0, 6.001),
                    ),
                startReached = true,
            )

        val state =
            computeTurnByTurnGuidanceState(
                session = session,
                currentLocation = LatLong(45.0, 6.001),
            )

        assertEquals(GuidanceMode.FINISHED, state.mode)
        assertEquals(RouteInstructionCommand.FINISH, state.nextInstruction?.command)
    }

    @Test
    fun guidanceDoesNotFinishWhenProjectedPastEndButFarFromRoute() {
        val session =
            buildGpxGuidanceSession(
                trackId = "route.gpx",
                trackTitle = "Route",
                trackPoints =
                    listOf(
                        point(45.0, 6.0),
                        point(45.0, 6.001),
                    ),
                startReached = true,
            )

        val state =
            computeTurnByTurnGuidanceState(
                session = session,
                currentLocation = LatLong(45.002, 6.0012),
            )

        assertEquals(GuidanceMode.FOLLOW_ROUTE, state.mode)
        assertEquals(RouteInstructionCommand.FINISH, state.nextInstruction?.command)
        assertTrue(state.offRoute)
        assertTrue((state.distanceToRouteMeters ?: 0.0) > 100.0)
    }

    @Test
    fun projectionTracksDistanceAlongRoute() {
        val points =
            listOf(
                LatLong(45.0, 6.0),
                LatLong(45.0, 6.002),
            )
        val projection =
            projectLocationToRoute(
                points = points,
                location = LatLong(45.0, 6.001),
            )

        assertNotNull(projection)
        assertEquals(0, projection?.segmentIndex)
        assertEquals(0.5, projection?.t ?: 0.0, 0.05)
    }

    private fun point(
        lat: Double,
        lon: Double,
        guidanceHint: GpxGuidanceHint? = null,
    ): TrackPoint =
        TrackPoint(
            latLong = LatLong(lat, lon),
            elevation = null,
            guidanceHint = guidanceHint,
        )
}
