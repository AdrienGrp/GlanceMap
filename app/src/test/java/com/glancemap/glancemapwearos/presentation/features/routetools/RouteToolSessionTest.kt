package com.glancemap.glancemapwearos.presentation.features.routetools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.LatLong

class RouteToolSessionTest {
    @Test
    fun reshapeRouteUsesSelectThenReplaceFlow() {
        val initial =
            RouteToolSession(
                options =
                    RouteToolOptions(
                        toolKind = RouteToolKind.MODIFY,
                        modifyMode = RouteModifyMode.RESHAPE_ROUTE,
                    ),
            )

        assertEquals(RouteSelectionTarget.RESHAPE_POINT, initial.currentSelectionTarget)
        assertEquals("Place route point, then check.", initial.instructionText)
        assertTrue(initial.usesCrosshair)

        val selectedPoint = initial.captureSelection(LatLong(0.0, 1.0))
        assertEquals(RouteSelectionTarget.DESTINATION, selectedPoint.currentSelectionTarget)
        assertEquals("Place replacement point, then check.", selectedPoint.instructionText)
        assertEquals(LatLong(0.0, 1.0), selectedPoint.pointA)

        val completed = selectedPoint.captureSelection(LatLong(0.5, 1.5))
        assertNull(completed.currentSelectionTarget)
        assertEquals(LatLong(0.5, 1.5), completed.destination)
    }

    @Test
    fun multiPointCreateAppendsPointsWithoutCompletingSession() {
        val initial =
            RouteToolSession(
                options =
                    RouteToolOptions(
                        toolKind = RouteToolKind.CREATE,
                        createMode = RouteCreateMode.MULTI_POINT_CHAIN,
                    ),
            )

        assertEquals(RouteSelectionTarget.DESTINATION, initial.currentSelectionTarget)
        assertEquals("Place start, then check.", initial.instructionText)

        val firstPoint = initial.captureSelection(LatLong(42.5, 1.5))
        assertEquals(1, firstPoint.chainPoints.size)
        assertEquals("Place next point, then check.", firstPoint.instructionText)
        assertTrue(!firstPoint.isComplete)

        val secondPoint = firstPoint.captureSelection(LatLong(42.6, 1.6))
        assertEquals(2, secondPoint.chainPoints.size)
        assertEquals("Add point, then check.", secondPoint.instructionText)
        assertTrue(!secondPoint.isComplete)

        val undone = secondPoint.removeLastChainPoint()
        assertEquals(1, undone.chainPoints.size)
        assertEquals("Place next point, then check.", undone.instructionText)
    }

    @Test
    fun multiPointDraftConnectorDisappearsWhenPreviewMatchesChain() {
        val session =
            multiPointSession(
                LatLong(42.5, 1.5),
                LatLong(42.6, 1.6),
            )
        val preview = routeToolCreatePreview(chainPointCount = 2)

        val visiblePreview =
            visibleRouteToolCreatePreview(
                session = session,
                createPreview = preview,
                createPreviewInProgress = false,
            )
        val draftConnectors =
            routeToolMultiPointDraftConnectorPoints(
                session = session,
                visibleCreatePreview = visiblePreview,
                createPreviewInProgress = false,
            )

        assertEquals(preview, visiblePreview)
        assertTrue(draftConnectors.isEmpty())
    }

    @Test
    fun multiPointDraftConnectorKeepsOnlyPendingLegWhilePreviewUpdates() {
        val second = LatLong(42.6, 1.6)
        val third = LatLong(42.7, 1.7)
        val session =
            multiPointSession(
                LatLong(42.5, 1.5),
                second,
                third,
            )
        val previousPreview = routeToolCreatePreview(chainPointCount = 2)

        val visiblePreview =
            visibleRouteToolCreatePreview(
                session = session,
                createPreview = previousPreview,
                createPreviewInProgress = true,
            )
        val draftConnectors =
            routeToolMultiPointDraftConnectorPoints(
                session = session,
                visibleCreatePreview = visiblePreview,
                createPreviewInProgress = true,
            )

        assertEquals(previousPreview, visiblePreview)
        assertEquals(listOf(second, third), draftConnectors)
    }

    @Test
    fun multiPointPreviewHidesStalePreviewFromLongerChain() {
        val session =
            multiPointSession(
                LatLong(42.5, 1.5),
                LatLong(42.6, 1.6),
            )
        val stalePreview = routeToolCreatePreview(chainPointCount = 3)

        assertNull(
            visibleRouteToolCreatePreview(
                session = session,
                createPreview = stalePreview,
                createPreviewInProgress = true,
            ),
        )
    }

    @Test
    fun reverseGpxCompletesImmediatelyWithoutPointSelection() {
        val session =
            RouteToolSession(
                options =
                    RouteToolOptions(
                        toolKind = RouteToolKind.MODIFY,
                        modifyMode = RouteModifyMode.REVERSE_GPX,
                    ),
            )

        assertNull(session.currentSelectionTarget)
        assertTrue(session.isComplete)
        assertEquals("Ready.", session.instructionText)
    }

    @Test
    fun loopRetryAdvancesVariationCounter() {
        val session =
            RouteToolSession(
                options =
                    RouteToolOptions(
                        toolKind = RouteToolKind.CREATE,
                        createMode = RouteCreateMode.LOOP_AROUND_HERE,
                    ),
                pointA = LatLong(42.5, 1.5),
            )

        val retried = session.advanceLoopVariation()

        assertEquals(0, session.loopVariationIndex)
        assertEquals(1, retried.loopVariationIndex)
        assertEquals(session.pointA, retried.pointA)
    }

    private fun multiPointSession(vararg points: LatLong): RouteToolSession =
        RouteToolSession(
            options =
                RouteToolOptions(
                    toolKind = RouteToolKind.CREATE,
                    createMode = RouteCreateMode.MULTI_POINT_CHAIN,
                ),
            chainPoints = points.toList(),
        )

    private fun routeToolCreatePreview(chainPointCount: Int): RouteToolCreatePreview =
        RouteToolCreatePreview(
            previewPoints = emptyList(),
            distanceMeters = 0.0,
            elevationGainMeters = 0.0,
            elevationLossMeters = 0.0,
            estimatedDurationSec = null,
            multiPointChainPointCount = chainPointCount,
        )
}
