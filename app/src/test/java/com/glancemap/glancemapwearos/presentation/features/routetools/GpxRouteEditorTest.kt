package com.glancemap.glancemapwearos.presentation.features.routetools

import com.glancemap.glancemapwearos.core.routing.RouteGeometryPoint
import com.glancemap.glancemapwearos.presentation.features.gpx.FileSig
import com.glancemap.glancemapwearos.presentation.features.gpx.TrackPoint
import com.glancemap.glancemapwearos.presentation.features.gpx.buildProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.LatLong
import kotlin.math.abs

class GpxRouteEditorTest {

    @Test
    fun extensionOutputAppendsRoutedSegmentWithoutDuplicatingJoint() {
        val profile = buildTestProfile(
            trackPoint(0.0, 0.0),
            trackPoint(0.0, 0.001),
            trackPoint(0.0, 0.002),
            trackPoint(0.0, 0.003)
        )

        val output = buildRouteToolExtensionOutput(
            sourceFileName = "ridge.gpx",
            sourceTitle = "Ridge",
            profile = profile,
            routedPoints = listOf(
                routeGeometryPoint(0.0, 0.003),
                routeGeometryPoint(0.0, 0.004),
                routeGeometryPoint(0.0, 0.005)
            )
        )

        assertTrue(output.fileName.startsWith("ridge-extend-"))
        assertTrue(output.fileName.endsWith(".gpx"))
        assertEquals("Ridge (extended)", output.title)
        assertEquals(6, output.points.size)
        assertEquals(LatLong(0.0, 0.003), output.points[3].latLong)
        assertEquals(LatLong(0.0, 0.005), output.points.last().latLong)
    }

    @Test
    fun replaceSectionOutputKeepsPrefixAndSuffixAroundReroutedSegment() {
        val profile = buildTestProfile(
            trackPoint(0.0, 0.0),
            trackPoint(0.0, 0.001),
            trackPoint(0.0, 0.002),
            trackPoint(0.0, 0.003),
            trackPoint(0.0, 0.004)
        )

        val output = buildRouteToolReplaceSectionOutput(
            sourcePath = "/tmp/ridge.gpx",
            sourceFileName = "ridge.gpx",
            sourceTitle = "Ridge",
            profile = profile,
            session = replaceSectionSession(
                pointA = LatLong(0.0, 0.001),
                pointB = LatLong(0.0, 0.003)
            ),
            routedPoints = listOf(
                routeGeometryPoint(0.0, 0.001),
                routeGeometryPoint(0.001, 0.002),
                routeGeometryPoint(0.0, 0.003)
            )
        )

        assertTrue(output.fileName.startsWith("ridge-replace-ab-"))
        assertEquals("Ridge (rerouted)", output.title)
        assertEquals(
            listOf(
                LatLong(0.0, 0.0),
                LatLong(0.0, 0.001),
                LatLong(0.001, 0.002),
                LatLong(0.0, 0.003),
                LatLong(0.0, 0.004)
            ),
            output.points.map { it.latLong }
        )
    }

    @Test
    fun replaceSectionOutputOrdersReversedABSelectionsOnTheTrack() {
        val profile = buildTestProfile(
            trackPoint(0.0, 0.0),
            trackPoint(0.0, 0.001),
            trackPoint(0.0, 0.002),
            trackPoint(0.0, 0.003),
            trackPoint(0.0, 0.004)
        )

        val output = buildRouteToolReplaceSectionOutput(
            sourcePath = "/tmp/ridge.gpx",
            sourceFileName = "ridge.gpx",
            sourceTitle = "Ridge",
            profile = profile,
            session = replaceSectionSession(
                pointA = LatLong(0.0, 0.003),
                pointB = LatLong(0.0, 0.001)
            ),
            routedPoints = listOf(
                routeGeometryPoint(0.0, 0.001),
                routeGeometryPoint(-0.001, 0.002),
                routeGeometryPoint(0.0, 0.003)
            )
        )

        assertEquals(
            listOf(
                LatLong(0.0, 0.0),
                LatLong(0.0, 0.001),
                LatLong(-0.001, 0.002),
                LatLong(0.0, 0.003),
                LatLong(0.0, 0.004)
            ),
            output.points.map { it.latLong }
        )
    }

    @Test
    fun reshapeWaypointUsesLocalWindowAroundAnchor() {
        val points = (0..30).map { index ->
            trackPoint(0.0, index * 0.001)
        }
        val profile = buildTestProfile(*points.toTypedArray())

        val start = resolveRouteReshapeWaypoint(
            sourcePath = "/tmp/local-reshape.gpx",
            sourceTitle = "Local reshape",
            profile = profile,
            anchor = LatLong(0.0, 0.015),
            direction = RouteReshapeDirection.START
        )
        val end = resolveRouteReshapeWaypoint(
            sourcePath = "/tmp/local-reshape.gpx",
            sourceTitle = "Local reshape",
            profile = profile,
            anchor = LatLong(0.0, 0.015),
            direction = RouteReshapeDirection.END
        )

        assertTrue(abs(start.longitude - 0.0139) < 0.0015)
        assertTrue(abs(end.longitude - 0.0161) < 0.0015)
    }

    @Test
    fun reshapeWaypointAllowsMeaningfulOffsetFromTrack() {
        val profile = buildTestProfile(
            trackPoint(0.0, 0.0),
            trackPoint(0.0, 0.001),
            trackPoint(0.0, 0.002),
            trackPoint(0.0, 0.003),
            trackPoint(0.0, 0.004)
        )

        val waypoint = resolveRouteReshapeWaypoint(
            sourcePath = "/tmp/offset-reshape.gpx",
            sourceTitle = "Offset reshape",
            profile = profile,
            anchor = LatLong(0.0015, 0.002),
            direction = RouteReshapeDirection.START
        )

        assertTrue(waypoint.longitude < 0.002)
    }

    @Test
    fun reshapeWaypointCanReconnectFartherAheadWhenHintMatchesFutureTrack() {
        val points = (0..20).map { index ->
            trackPoint(0.0, index * 0.001)
        }
        val profile = buildTestProfile(*points.toTypedArray())

        val defaultEnd = resolveRouteReshapeWaypoint(
            sourcePath = "/tmp/reshape-default.gpx",
            sourceTitle = "Reshape default",
            profile = profile,
            anchor = LatLong(0.0, 0.010),
            direction = RouteReshapeDirection.END
        )
        val hintedEnd = resolveRouteReshapeWaypoint(
            sourcePath = "/tmp/reshape-hinted.gpx",
            sourceTitle = "Reshape hinted",
            profile = profile,
            anchor = LatLong(0.0, 0.010),
            rejoinHint = LatLong(0.0015, 0.014),
            direction = RouteReshapeDirection.END
        )

        assertTrue(hintedEnd.longitude > defaultEnd.longitude)
        assertTrue(abs(hintedEnd.longitude - 0.014) < 0.0015)
    }

    @Test
    fun reshapeWaypointStaysForwardWhenReplacementPointSitsNearPastTrack() {
        val points = (0..20).map { index ->
            trackPoint(0.0, index * 0.001)
        }
        val profile = buildTestProfile(*points.toTypedArray())

        val defaultEnd = resolveRouteReshapeWaypoint(
            sourcePath = "/tmp/reshape-forward-default.gpx",
            sourceTitle = "Reshape forward default",
            profile = profile,
            anchor = LatLong(0.0, 0.010),
            direction = RouteReshapeDirection.END
        )
        val hintedEnd = resolveRouteReshapeWaypoint(
            sourcePath = "/tmp/reshape-forward-hint.gpx",
            sourceTitle = "Reshape forward hint",
            profile = profile,
            anchor = LatLong(0.0, 0.010),
            rejoinHint = LatLong(0.0015, 0.008),
            direction = RouteReshapeDirection.END
        )

        assertTrue(hintedEnd.longitude > 0.0104)
        assertTrue(hintedEnd.longitude < defaultEnd.longitude)
    }

    @Test
    fun reshapeCandidateBoundsKeepPushingRejoinFartherForward() {
        val points = (0..20).map { index ->
            trackPoint(0.0, index * 0.001)
        }
        val profile = buildTestProfile(*points.toTypedArray())

        val bounds = resolveRouteReshapeCandidateBounds(
            sourcePath = "/tmp/reshape-candidates.gpx",
            sourceTitle = "Reshape candidates",
            profile = profile,
            anchor = LatLong(0.0, 0.010),
            rejoinHint = LatLong(0.0015, 0.014)
        )

        assertTrue(bounds.size >= 2)
        assertTrue(abs(bounds.first().endPoint.latLong.longitude - 0.014) < 0.0015)
        bounds.zipWithNext { first, second ->
            assertTrue(second.endPoint.latLong.longitude >= first.endPoint.latLong.longitude)
        }
    }

    @Test
    fun reshapeSecondLegRejectsBackwardReuseOfOriginalTrack() {
        val points = (0..20).map { index ->
            trackPoint(0.0, index * 0.001)
        }
        val profile = buildTestProfile(*points.toTypedArray())

        val isForwardOnly = secondLegRejoinsOriginalPathForward(
            sourcePath = "/tmp/reshape-backward-check.gpx",
            sourceTitle = "Reshape backward check",
            profile = profile,
            anchor = LatLong(0.0, 0.010),
            routedPoints = listOf(
                routeGeometryPoint(0.0015, 0.0140),
                routeGeometryPoint(0.0008, 0.0120),
                routeGeometryPoint(0.0, 0.0096),
                routeGeometryPoint(0.0, 0.0092),
                routeGeometryPoint(0.0, 0.0088),
                routeGeometryPoint(0.0, 0.0140)
            )
        )

        assertFalse(isForwardOnly)
    }

    @Test
    fun reshapeSecondLegAllowsForwardRejoinOntoOriginalTrack() {
        val points = (0..20).map { index ->
            trackPoint(0.0, index * 0.001)
        }
        val profile = buildTestProfile(*points.toTypedArray())

        val isForwardOnly = secondLegRejoinsOriginalPathForward(
            sourcePath = "/tmp/reshape-forward-check.gpx",
            sourceTitle = "Reshape forward check",
            profile = profile,
            anchor = LatLong(0.0, 0.010),
            routedPoints = listOf(
                routeGeometryPoint(0.0015, 0.0140),
                routeGeometryPoint(0.0010, 0.0143),
                routeGeometryPoint(0.0005, 0.0142),
                routeGeometryPoint(0.0, 0.0141),
                routeGeometryPoint(0.0, 0.0140)
            )
        )

        assertTrue(isForwardOnly)
    }

    @Test
    fun reshapeSecondLegRejectsReverseTravelAfterRejoiningAhead() {
        val points = (0..20).map { index ->
            trackPoint(0.0, index * 0.001)
        }
        val profile = buildTestProfile(*points.toTypedArray())

        val isForwardOnly = secondLegRejoinsOriginalPathForward(
            sourcePath = "/tmp/reshape-reverse-after-rejoin.gpx",
            sourceTitle = "Reshape reverse after rejoin",
            profile = profile,
            anchor = LatLong(0.0, 0.010),
            routedPoints = listOf(
                routeGeometryPoint(0.0015, 0.0150),
                routeGeometryPoint(0.0, 0.0149),
                routeGeometryPoint(0.0, 0.0145),
                routeGeometryPoint(0.0, 0.0140),
                routeGeometryPoint(0.0, 0.0136),
                routeGeometryPoint(0.0, 0.0132),
                routeGeometryPoint(0.0, 0.0150)
            )
        )

        assertFalse(isForwardOnly)
    }

    @Test
    fun reshapeIntentRejectsBalloonDetourThatBarelyAdvancesTheRoute() {
        val points = (0..20).map { index ->
            trackPoint(0.0, index * 0.001)
        }
        val profile = buildTestProfile(*points.toTypedArray())
        val shapingPoint = LatLong(0.0012, 0.0122)
        val bounds = resolveRouteReshapeCandidateBounds(
            sourcePath = "/tmp/reshape-intent-balloon.gpx",
            sourceTitle = "Reshape intent balloon",
            profile = profile,
            anchor = LatLong(0.0, 0.010),
            rejoinHint = LatLong(0.0015, 0.014)
        ).first()

        val matchesIntent = reshapeCandidateMatchesUserIntent(
            sourcePath = "/tmp/reshape-intent-balloon.gpx",
            sourceTitle = "Reshape intent balloon",
            profile = profile,
            anchor = LatLong(0.0, 0.010),
            shapingPoint = shapingPoint,
            bounds = bounds,
            firstLegPoints = listOf(
                routeGeometryPoint(bounds.startPoint.latLong.latitude, bounds.startPoint.latLong.longitude),
                routeGeometryPoint(shapingPoint.latitude, shapingPoint.longitude)
            ),
            secondLegPoints = listOf(
                routeGeometryPoint(shapingPoint.latitude, shapingPoint.longitude),
                routeGeometryPoint(0.0027, 0.0126),
                routeGeometryPoint(0.0029, 0.0135),
                routeGeometryPoint(0.0018, 0.0142),
                routeGeometryPoint(0.0010, 0.0131),
                routeGeometryPoint(0.0004, 0.0136),
                routeGeometryPoint(0.0, bounds.endPoint.latLong.longitude)
            )
        )

        assertFalse(matchesIntent)
    }

    @Test
    fun reshapeIntentAllowsForwardReplacementThatHeadsCleanlyToRejoin() {
        val points = (0..20).map { index ->
            trackPoint(0.0, index * 0.001)
        }
        val profile = buildTestProfile(*points.toTypedArray())
        val shapingPoint = LatLong(0.0012, 0.0122)
        val bounds = resolveRouteReshapeCandidateBounds(
            sourcePath = "/tmp/reshape-intent-forward.gpx",
            sourceTitle = "Reshape intent forward",
            profile = profile,
            anchor = LatLong(0.0, 0.010),
            rejoinHint = LatLong(0.0015, 0.015)
        ).first()

        val matchesIntent = reshapeCandidateMatchesUserIntent(
            sourcePath = "/tmp/reshape-intent-forward.gpx",
            sourceTitle = "Reshape intent forward",
            profile = profile,
            anchor = LatLong(0.0, 0.010),
            shapingPoint = shapingPoint,
            bounds = bounds,
            firstLegPoints = listOf(
                routeGeometryPoint(bounds.startPoint.latLong.latitude, bounds.startPoint.latLong.longitude),
                routeGeometryPoint(shapingPoint.latitude, shapingPoint.longitude)
            ),
            secondLegPoints = listOf(
                routeGeometryPoint(shapingPoint.latitude, shapingPoint.longitude),
                routeGeometryPoint(0.0010, 0.0128),
                routeGeometryPoint(0.0006, 0.0134),
                routeGeometryPoint(0.0002, 0.0138),
                routeGeometryPoint(0.0, bounds.endPoint.latLong.longitude)
            )
        )

        assertTrue(matchesIntent)
    }

    @Test
    fun reshapePreviewUsesReplacementPointWhenChoosingForwardRejoin() {
        val points = (0..20).map { index ->
            trackPoint(0.0, index * 0.001)
        }
        val profile = buildTestProfile(*points.toTypedArray())
        val session = RouteToolSession(
            options = RouteToolOptions(
                toolKind = RouteToolKind.MODIFY,
                modifyMode = RouteModifyMode.RESHAPE_ROUTE,
                saveBehavior = RouteSaveBehavior.SAVE_AS_NEW
            ),
            pointA = LatLong(0.0, 0.010),
            destination = LatLong(0.0015, 0.014)
        )

        val defaultEnd = resolveRouteReshapeWaypoint(
            sourcePath = "/tmp/reshape-preview-default.gpx",
            sourceTitle = "Reshape preview default",
            profile = profile,
            anchor = LatLong(0.0, 0.010),
            direction = RouteReshapeDirection.END
        )
        val preview = buildRouteToolReshapePreview(
            sourcePath = "/tmp/reshape-preview.gpx",
            sourceTitle = "Reshape preview",
            profile = profile,
            session = session,
            firstLegPoints = listOf(
                routeGeometryPoint(0.0, 0.009),
                routeGeometryPoint(0.0015, 0.014)
            ),
            secondLegPoints = listOf(
                routeGeometryPoint(0.0015, 0.014),
                routeGeometryPoint(0.0006, 0.0134)
            )
        )

        assertTrue(preview.previewPoints.last().longitude > defaultEnd.longitude + 0.001)
        assertTrue(abs(preview.previewPoints.last().longitude - 0.014) < 0.0015)
    }

    @Test
    fun changeStartOutputPrependsRoutedSegmentBeforeRemainingTrack() {
        val profile = buildTestProfile(
            trackPoint(0.0, 0.0),
            trackPoint(0.0, 0.001),
            trackPoint(0.0, 0.002),
            trackPoint(0.0, 0.003)
        )

        val snapped = resolveRouteToolTrackMatch(
            sourcePath = "/tmp/ridge.gpx",
            sourceTitle = "Ridge",
            profile = profile,
            target = LatLong(0.0, 0.0012)
        )

        val output = buildRouteToolEndpointChangeOutput(
            sourceFileName = "ridge.gpx",
            sourceTitle = "Ridge",
            profile = profile,
            session = RouteToolSession(
                options = RouteToolOptions(
                    toolKind = RouteToolKind.MODIFY,
                    modifyMode = RouteModifyMode.TRIM_START_TO_HERE,
                    saveBehavior = RouteSaveBehavior.SAVE_AS_NEW
                ),
                pointA = LatLong(0.001, 0.0002)
            ),
            snappedPosition = snapped.position,
            routedPoints = listOf(
                routeGeometryPoint(0.001, 0.0002),
                routeGeometryPoint(0.0004, 0.0008),
                routeGeometryPoint(snapped.latLong.latitude, snapped.latLong.longitude)
            )
        )

        assertTrue(output.fileName.startsWith("ridge-trim-start-"))
        assertEquals("Ridge (edited)", output.title)
        assertEquals(LatLong(0.001, 0.0002), output.points.first().latLong)
        assertEquals(LatLong(0.0, 0.003), output.points.last().latLong)
        assertEquals(snapped.latLong, output.points[2].latLong)
    }

    @Test
    fun changeEndOutputAppendsRoutedSegmentAfterRemainingTrack() {
        val profile = buildTestProfile(
            trackPoint(0.0, 0.0),
            trackPoint(0.0, 0.001),
            trackPoint(0.0, 0.002),
            trackPoint(0.0, 0.003)
        )

        val snapped = resolveRouteToolTrackMatch(
            sourcePath = "/tmp/ridge.gpx",
            sourceTitle = "Ridge",
            profile = profile,
            target = LatLong(0.0, 0.0018)
        )

        val output = buildRouteToolEndpointChangeOutput(
            sourceFileName = "ridge.gpx",
            sourceTitle = "Ridge",
            profile = profile,
            session = RouteToolSession(
                options = RouteToolOptions(
                    toolKind = RouteToolKind.MODIFY,
                    modifyMode = RouteModifyMode.TRIM_END_FROM_HERE,
                    saveBehavior = RouteSaveBehavior.SAVE_AS_NEW
                ),
                pointB = LatLong(0.001, 0.0038)
            ),
            snappedPosition = snapped.position,
            routedPoints = listOf(
                routeGeometryPoint(snapped.latLong.latitude, snapped.latLong.longitude),
                routeGeometryPoint(0.0005, 0.0031),
                routeGeometryPoint(0.001, 0.0038)
            )
        )

        assertTrue(output.fileName.startsWith("ridge-trim-end-"))
        assertEquals("Ridge (edited)", output.title)
        assertEquals(LatLong(0.0, 0.0), output.points.first().latLong)
        assertEquals(LatLong(0.001, 0.0038), output.points.last().latLong)
        assertEquals(snapped.latLong, output.points[2].latLong)
    }

    @Test
    fun reverseOutputReversesTrackOrderWithoutRouting() {
        val profile = buildTestProfile(
            trackPoint(0.0, 0.0, 10.0),
            trackPoint(0.0, 0.001, 20.0),
            trackPoint(0.0, 0.002, 30.0),
            trackPoint(0.0, 0.003, 40.0)
        )

        val output = buildRouteToolEditOutput(
            sourcePath = "/tmp/ridge.gpx",
            sourceFileName = "ridge.gpx",
            sourceTitle = "Ridge",
            profile = profile,
            session = RouteToolSession(
                options = RouteToolOptions(
                    toolKind = RouteToolKind.MODIFY,
                    modifyMode = RouteModifyMode.REVERSE_GPX,
                    saveBehavior = RouteSaveBehavior.SAVE_AS_NEW
                )
            )
        )

        assertTrue(output.fileName.startsWith("ridge-reverse-"))
        assertEquals("Ridge (edited)", output.title)
        assertEquals(
            listOf(
                LatLong(0.0, 0.003),
                LatLong(0.0, 0.002),
                LatLong(0.0, 0.001),
                LatLong(0.0, 0.0)
            ),
            output.points.map { it.latLong }
        )
        assertEquals(40.0, output.points.first().elevation ?: 0.0, 0.0)
        assertEquals(10.0, output.points.last().elevation ?: 0.0, 0.0)
    }

    private fun replaceSectionSession(
        pointA: LatLong,
        pointB: LatLong
    ): RouteToolSession {
        return RouteToolSession(
            options = RouteToolOptions(
                toolKind = RouteToolKind.MODIFY,
                modifyMode = RouteModifyMode.REPLACE_SECTION_A_TO_B,
                saveBehavior = RouteSaveBehavior.SAVE_AS_NEW
            ),
            pointA = pointA,
            pointB = pointB
        )
    }

    private fun buildTestProfile(vararg points: TrackPoint) = buildProfile(
        sig = FileSig(lastModified = 0L, length = points.size.toLong()),
        pts = points.toList()
    )

    private fun trackPoint(
        latitude: Double,
        longitude: Double,
        elevation: Double? = null
    ) = TrackPoint(
        latLong = LatLong(latitude, longitude),
        elevation = elevation
    )

    private fun routeGeometryPoint(
        latitude: Double,
        longitude: Double,
        elevation: Double? = null
    ) = RouteGeometryPoint(
        latLong = LatLong(latitude, longitude),
        elevation = elevation
    )
}
