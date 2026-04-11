package com.glancemap.glancemapwearos.presentation.features.routetools

import android.util.Xml
import com.glancemap.glancemapwearos.core.routing.RouteGeometryPoint
import com.glancemap.glancemapwearos.presentation.features.gpx.GpxTrackDetails
import com.glancemap.glancemapwearos.presentation.features.gpx.TrackPoint
import com.glancemap.glancemapwearos.presentation.features.gpx.TrackPosition
import com.glancemap.glancemapwearos.presentation.features.gpx.TrackProfile
import com.glancemap.glancemapwearos.presentation.features.gpx.findClosestTrackPosition
import com.glancemap.glancemapwearos.presentation.features.gpx.totalAscent
import com.glancemap.glancemapwearos.presentation.features.gpx.totalDistance
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.util.MercatorProjection
import org.xmlpull.v1.XmlSerializer
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal data class RouteToolSaveResult(
    val fileName: String,
    val filePath: String,
    val displayTitle: String,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val estimatedDurationSec: Double?,
    val replacedCurrent: Boolean,
    val successMessage: String? = null
) {
    val message: String
        get() = successMessage ?: if (replacedCurrent) {
            "GPX updated"
        } else {
            "New GPX saved"
        }
}

internal data class RouteToolModifyPreview(
    val previewPoints: List<LatLong>
)

internal data class RouteToolCreatePreview(
    val previewPoints: List<LatLong>,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val estimatedDurationSec: Double?,
    val plannedCreation: RouteToolPlannedCreation? = null
)

internal data class RouteToolPlannedCreation(
    val fileName: String,
    val gpxBytes: ByteArray
)

internal data class RouteToolEditOutput(
    val fileName: String,
    val title: String,
    val points: List<TrackPoint>
)

internal data class RouteReshapeHandle(
    val pointIndex: Int,
    val latLong: LatLong,
    val isEndpoint: Boolean
)

internal data class RouteReshapeBounds(
    val startPosition: TrackPosition,
    val endPosition: TrackPosition,
    val startPoint: TrackPoint,
    val endPoint: TrackPoint
)

internal data class RouteToolTrackMatch(
    val position: TrackPosition,
    val latLong: LatLong,
    val distanceMeters: Double
)

private data class RouteRejoinCandidate(
    val position: TrackPosition,
    val distanceMeters: Double
)

private data class RouteReshapeWindow(
    val startPosition: TrackPosition,
    val defaultEndPosition: TrackPosition,
    val anchorDistance: Double,
    val halfWindow: Double
)

private const val ROUTE_TOOL_SNAP_THRESHOLD_METERS = 60.0
private const val ROUTE_RESHAPE_SNAP_THRESHOLD_METERS = 250.0
private const val POSITION_EPSILON = 1e-9
private const val ROUTE_RESHAPE_MAX_HANDLES = 9
private const val ROUTE_RESHAPE_MIN_HALF_WINDOW_METERS = 120.0
private const val ROUTE_RESHAPE_MAX_HALF_WINDOW_METERS = 400.0
private const val ROUTE_RESHAPE_MIN_FORWARD_REJOIN_METERS = 60.0
private const val ROUTE_RESHAPE_MAX_FORWARD_REJOIN_METERS = 1_200.0
private const val ROUTE_RESHAPE_REJOIN_DISTANCE_MARGIN_METERS = 20.0
private const val ROUTE_RESHAPE_REJOIN_CANDIDATE_STEP_METERS = 120.0
private const val ROUTE_RESHAPE_MAX_REJOIN_CANDIDATES = 6
private const val ROUTE_RESHAPE_REJOIN_OVERLAP_THRESHOLD_METERS = 18.0
private const val ROUTE_RESHAPE_REJOIN_ORIGIN_IGNORE_METERS = 30.0
private const val ROUTE_RESHAPE_REJOIN_BACKWARD_TOLERANCE_METERS = 20.0
private const val ROUTE_RESHAPE_MIN_BACKWARD_OVERLAP_METERS = 30.0
private const val ROUTE_RESHAPE_MIN_REVERSE_REJOIN_TRAVEL_METERS = 24.0
private const val ROUTE_RESHAPE_MIN_MEANINGFUL_FORWARD_GAIN_METERS = 120.0
private const val ROUTE_RESHAPE_FORWARD_GAIN_OFFSET_FACTOR = 1.6
private const val ROUTE_RESHAPE_MAX_REJOIN_OVERHEAD_METERS = 70.0
private const val ROUTE_RESHAPE_MIN_REJOIN_PROGRESS_RATIO = 0.5
private const val ROUTE_RESHAPE_MAX_REJOIN_DIRECTNESS_OVERHEAD_METERS = 120.0
private const val ROUTE_RESHAPE_MAX_REJOIN_DIRECTNESS_RATIO = 2.0
private const val ROUTE_RESHAPE_MAX_TOTAL_OVERHEAD_METERS = 120.0
private const val ROUTE_RESHAPE_MIN_TOTAL_REPLACEMENT_RATIO = 0.55

internal fun buildRouteToolEditOutput(
    sourcePath: String,
    sourceFileName: String,
    sourceTitle: String?,
    profile: TrackProfile,
    session: RouteToolSession
): RouteToolEditOutput {
    require(session.options.toolKind == RouteToolKind.MODIFY) {
        "Only modify actions are supported by the GPX editor."
    }

    val sourceTrack = GpxTrackDetails(
        id = sourcePath,
        points = profile.points.map { it.latLong },
        title = sourceTitle,
        distance = profile.totalDistance,
        elevationGain = profile.totalAscent,
        startPoint = profile.points.firstOrNull()?.latLong,
        endPoint = profile.points.lastOrNull()?.latLong
    )

    val editedPoints = when (session.options.modifyMode) {
        RouteModifyMode.RESHAPE_ROUTE -> {
            error("Use buildRouteToolReshapeOutput for reshape edits.")
        }

        RouteModifyMode.REPLACE_SECTION_A_TO_B -> {
            error("Use buildRouteToolReplaceSectionOutput for routed section replacement.")
        }

        RouteModifyMode.KEEP_ONLY_A_TO_B -> {
            val a = session.pointA ?: error("Point A is required.")
            val b = session.pointB ?: error("Point B is required.")
            val posA = snapToTrackPosition(target = a, sourceTrack = sourceTrack, profile = profile)
            val posB = snapToTrackPosition(target = b, sourceTrack = sourceTrack, profile = profile)
            slicePointsBetween(profile.points, posA, posB)
        }

        RouteModifyMode.TRIM_START_TO_HERE -> {
            val a = session.pointA ?: error("Point A is required.")
            val posA = snapToTrackPosition(target = a, sourceTrack = sourceTrack, profile = profile)
            trimTrackStart(profile.points, posA)
        }

        RouteModifyMode.TRIM_END_FROM_HERE -> {
            val b = session.pointB ?: error("Point B is required.")
            val posB = snapToTrackPosition(target = b, sourceTrack = sourceTrack, profile = profile)
            trimTrackEnd(profile.points, posB)
        }

        RouteModifyMode.REVERSE_GPX -> {
            profile.points.reversed()
        }
    }

    require(editedPoints.size >= 2) {
        "The selected edit would produce an empty GPX."
    }

    val titleBase = sourceTitle?.takeIf { it.isNotBlank() }
        ?: sourceFileName.removeSuffix(".gpx")
    val fileName = when (session.options.saveBehavior) {
        RouteSaveBehavior.REPLACE_CURRENT -> sourceFileName
        RouteSaveBehavior.SAVE_AS_NEW -> {
            buildEditedFileName(
                sourceFileName = sourceFileName,
                mode = session.options.modifyMode
            )
        }
    }

    val title = when (session.options.saveBehavior) {
        RouteSaveBehavior.REPLACE_CURRENT -> titleBase
        RouteSaveBehavior.SAVE_AS_NEW -> "$titleBase (edited)"
    }

    return RouteToolEditOutput(
        fileName = fileName,
        title = title,
        points = editedPoints
    )
}

internal fun buildRouteToolEndpointChangeOutput(
    sourceFileName: String,
    sourceTitle: String?,
    profile: TrackProfile,
    session: RouteToolSession,
    snappedPosition: TrackPosition,
    routedPoints: List<RouteGeometryPoint>
): RouteToolEditOutput {
    require(session.options.toolKind == RouteToolKind.MODIFY) {
        "Only modify actions are supported by the GPX editor."
    }
    require(
        session.options.modifyMode == RouteModifyMode.TRIM_START_TO_HERE ||
            session.options.modifyMode == RouteModifyMode.TRIM_END_FROM_HERE
    ) {
        "Only Change start/end are supported here."
    }
    require(routedPoints.size >= 2) {
        "The routed endpoint change is empty."
    }

    val merged = ArrayList<TrackPoint>(profile.points.size + routedPoints.size)
    when (session.options.modifyMode) {
        RouteModifyMode.TRIM_START_TO_HERE -> {
            routedPoints.forEach { point ->
                appendUnique(merged, point.toTrackPoint())
            }
            trimTrackStart(profile.points, snappedPosition).forEach { point ->
                appendUnique(merged, point)
            }
        }

        RouteModifyMode.TRIM_END_FROM_HERE -> {
            trimTrackEnd(profile.points, snappedPosition).forEach { point ->
                appendUnique(merged, point)
            }
            routedPoints.forEach { point ->
                appendUnique(merged, point.toTrackPoint())
            }
        }
    }

    require(merged.size >= 2) {
        "The selected edit would produce an empty GPX."
    }

    val titleBase = sourceTitle?.takeIf { it.isNotBlank() }
        ?: sourceFileName.removeSuffix(".gpx")
    val fileName = when (session.options.saveBehavior) {
        RouteSaveBehavior.REPLACE_CURRENT -> sourceFileName
        RouteSaveBehavior.SAVE_AS_NEW -> {
            buildEditedFileName(
                sourceFileName = sourceFileName,
                mode = session.options.modifyMode
            )
        }
    }
    val title = when (session.options.saveBehavior) {
        RouteSaveBehavior.REPLACE_CURRENT -> titleBase
        RouteSaveBehavior.SAVE_AS_NEW -> "$titleBase (edited)"
    }

    return RouteToolEditOutput(
        fileName = fileName,
        title = title,
        points = merged
    )
}

internal fun buildRouteToolExtensionOutput(
    sourceFileName: String,
    sourceTitle: String?,
    profile: TrackProfile,
    routedPoints: List<RouteGeometryPoint>
): RouteToolEditOutput {
    require(profile.points.size >= 2) {
        "The active GPX does not contain enough points to extend."
    }
    require(routedPoints.size >= 2) {
        "The routed extension is empty."
    }

    val merged = ArrayList<TrackPoint>(profile.points.size + routedPoints.size)
    profile.points.forEach { point ->
        appendUnique(merged, point)
    }
    routedPoints.forEach { point ->
        appendUnique(merged, point.toTrackPoint())
    }

    require(merged.size >= 2) {
        "The routed extension would produce an empty GPX."
    }

    val titleBase = sourceTitle?.takeIf { it.isNotBlank() }
        ?: sourceFileName.removeSuffix(".gpx")

    return RouteToolEditOutput(
        fileName = buildEditedFileName(
            sourceFileName = sourceFileName,
            modeSlug = "extend"
        ),
        title = "$titleBase (extended)",
        points = merged
    )
}

internal fun buildRouteToolReplaceSectionOutput(
    sourcePath: String,
    sourceFileName: String,
    sourceTitle: String?,
    profile: TrackProfile,
    session: RouteToolSession,
    routedPoints: List<RouteGeometryPoint>
): RouteToolEditOutput {
    require(session.options.modifyMode == RouteModifyMode.REPLACE_SECTION_A_TO_B) {
        "Only Replace A-B is supported here."
    }
    require(routedPoints.size >= 2) {
        "The routed replacement is empty."
    }

    val sourceTrack = GpxTrackDetails(
        id = sourcePath,
        points = profile.points.map { it.latLong },
        title = sourceTitle,
        distance = profile.totalDistance,
        elevationGain = profile.totalAscent,
        startPoint = profile.points.firstOrNull()?.latLong,
        endPoint = profile.points.lastOrNull()?.latLong
    )
    val a = session.pointA ?: error("Point A is required.")
    val b = session.pointB ?: error("Point B is required.")
    val posA = snapToTrackPosition(target = a, sourceTrack = sourceTrack, profile = profile)
    val posB = snapToTrackPosition(target = b, sourceTrack = sourceTrack, profile = profile)
    val (start, end) = orderedPositions(posA, posB)

    val merged = ArrayList<TrackPoint>(profile.points.size + routedPoints.size)
    trimTrackEnd(profile.points, start).forEach { point ->
        appendUnique(merged, point)
    }
    routedPoints.forEach { point ->
        appendUnique(merged, point.toTrackPoint())
    }
    trimTrackStart(profile.points, end).forEach { point ->
        appendUnique(merged, point)
    }

    require(merged.size >= 2) {
        "The selected replacement would produce an empty GPX."
    }

    val titleBase = sourceTitle?.takeIf { it.isNotBlank() }
        ?: sourceFileName.removeSuffix(".gpx")
    val fileName = when (session.options.saveBehavior) {
        RouteSaveBehavior.REPLACE_CURRENT -> sourceFileName
        RouteSaveBehavior.SAVE_AS_NEW -> {
            buildEditedFileName(
                sourceFileName = sourceFileName,
                mode = session.options.modifyMode
            )
        }
    }
    val title = when (session.options.saveBehavior) {
        RouteSaveBehavior.REPLACE_CURRENT -> titleBase
        RouteSaveBehavior.SAVE_AS_NEW -> "$titleBase (rerouted)"
    }

    return RouteToolEditOutput(
        fileName = fileName,
        title = title,
        points = merged
    )
}

internal fun buildRouteToolReshapeOutput(
    sourcePath: String,
    sourceFileName: String,
    sourceTitle: String?,
    profile: TrackProfile,
    session: RouteToolSession,
    firstLegPoints: List<RouteGeometryPoint>,
    secondLegPoints: List<RouteGeometryPoint>,
    bounds: RouteReshapeBounds? = null
): RouteToolEditOutput {
    require(session.options.modifyMode == RouteModifyMode.RESHAPE_ROUTE) {
        "Only reshape edits are supported here."
    }
    require(firstLegPoints.size >= 2) {
        "The first routed reshape leg is empty."
    }
    require(secondLegPoints.size >= 2) {
        "The second routed reshape leg is empty."
    }

    val selectedPoint = session.pointA ?: error("Select the route point first.")
    require(session.destination != null) { "Pick the new bend point first." }
    val resolvedBounds = bounds ?: resolveRouteReshapeBounds(
        sourcePath = sourcePath,
        sourceTitle = sourceTitle,
        profile = profile,
        anchor = selectedPoint,
        rejoinHint = session.destination
    )

    val merged = ArrayList<TrackPoint>(profile.points.size + firstLegPoints.size + secondLegPoints.size)
    trimTrackEnd(profile.points, resolvedBounds.startPosition).forEach { point ->
        appendUnique(merged, point)
    }
    firstLegPoints.forEach { point ->
        appendUnique(merged, point.toTrackPoint())
    }
    secondLegPoints.forEach { point ->
        appendUnique(merged, point.toTrackPoint())
    }
    trimTrackStart(profile.points, resolvedBounds.endPosition).forEach { point ->
        appendUnique(merged, point)
    }

    require(merged.size >= 2) {
        "The reshape would produce an empty GPX."
    }

    val titleBase = sourceTitle?.takeIf { it.isNotBlank() }
        ?: sourceFileName.removeSuffix(".gpx")
    val fileName = when (session.options.saveBehavior) {
        RouteSaveBehavior.REPLACE_CURRENT -> sourceFileName
        RouteSaveBehavior.SAVE_AS_NEW -> buildEditedFileName(
            sourceFileName = sourceFileName,
            modeSlug = "reshape"
        )
    }
    val title = when (session.options.saveBehavior) {
        RouteSaveBehavior.REPLACE_CURRENT -> titleBase
        RouteSaveBehavior.SAVE_AS_NEW -> "$titleBase (reshaped)"
    }

    return RouteToolEditOutput(
        fileName = fileName,
        title = title,
        points = merged
    )
}

internal fun buildRouteToolReshapePreview(
    sourcePath: String,
    sourceTitle: String?,
    profile: TrackProfile,
    session: RouteToolSession,
    firstLegPoints: List<RouteGeometryPoint>,
    secondLegPoints: List<RouteGeometryPoint>,
    bounds: RouteReshapeBounds? = null
): RouteToolModifyPreview {
    require(session.options.modifyMode == RouteModifyMode.RESHAPE_ROUTE) {
        "Only reshape previews are supported here."
    }
    require(firstLegPoints.size >= 2) {
        "The first routed reshape leg is empty."
    }
    require(secondLegPoints.size >= 2) {
        "The second routed reshape leg is empty."
    }

    val selectedPoint = session.pointA ?: error("Select the route point first.")
    require(session.destination != null) { "Pick the new bend point first." }
    val resolvedBounds = bounds ?: resolveRouteReshapeBounds(
        sourcePath = sourcePath,
        sourceTitle = sourceTitle,
        profile = profile,
        anchor = selectedPoint,
        rejoinHint = session.destination
    )

    val previewPoints = ArrayList<LatLong>(firstLegPoints.size + secondLegPoints.size + 2)
    appendUniqueLatLong(previewPoints, resolvedBounds.startPoint.latLong)
    firstLegPoints.forEach { point ->
        appendUniqueLatLong(previewPoints, point.latLong)
    }
    secondLegPoints.forEach { point ->
        appendUniqueLatLong(previewPoints, point.latLong)
    }
    appendUniqueLatLong(previewPoints, resolvedBounds.endPoint.latLong)

    return RouteToolModifyPreview(previewPoints = previewPoints)
}

internal fun resolveReplaceSectionEndpoints(
    sourcePath: String,
    sourceTitle: String?,
    profile: TrackProfile,
    pointA: LatLong,
    pointB: LatLong
): Pair<LatLong, LatLong> {
    val sourceTrack = GpxTrackDetails(
        id = sourcePath,
        points = profile.points.map { it.latLong },
        title = sourceTitle,
        distance = profile.totalDistance,
        elevationGain = profile.totalAscent,
        startPoint = profile.points.firstOrNull()?.latLong,
        endPoint = profile.points.lastOrNull()?.latLong
    )
    val posA = snapToTrackPosition(target = pointA, sourceTrack = sourceTrack, profile = profile)
    val posB = snapToTrackPosition(target = pointB, sourceTrack = sourceTrack, profile = profile)
    val (start, end) = orderedPositions(posA, posB)
    return pointAt(profile.points, start).latLong to pointAt(profile.points, end).latLong
}

internal fun resolveRouteToolTrackMatch(
    sourcePath: String,
    sourceTitle: String?,
    profile: TrackProfile,
    target: LatLong
): RouteToolTrackMatch {
    val sourceTrack = GpxTrackDetails(
        id = sourcePath,
        points = profile.points.map { it.latLong },
        title = sourceTitle,
        distance = profile.totalDistance,
        elevationGain = profile.totalAscent,
        startPoint = profile.points.firstOrNull()?.latLong,
        endPoint = profile.points.lastOrNull()?.latLong
    )
    val pick = findClosestTrackPosition(
        press = target,
        tracks = listOf(sourceTrack),
        profileProvider = { profile },
        allowedTrackId = sourceTrack.id
    ) ?: error("Could not match the selected point to the active GPX.")

    return RouteToolTrackMatch(
        position = pick.pos,
        latLong = pointAt(profile.points, pick.pos).latLong,
        distanceMeters = pick.distanceToLineMeters
    )
}

internal fun routeToolSnapThresholdMeters(): Double = ROUTE_TOOL_SNAP_THRESHOLD_METERS

internal fun buildRouteReshapeHandles(points: List<LatLong>): List<RouteReshapeHandle> {
    if (points.isEmpty()) return emptyList()
    val lastIndex = points.lastIndex
    return buildRouteReshapeHandleIndices(points.size).map { pointIndex ->
        RouteReshapeHandle(
            pointIndex = pointIndex,
            latLong = points[pointIndex],
            isEndpoint = pointIndex == 0 || pointIndex == lastIndex
        )
    }
}

internal fun resolveRouteReshapeWaypoint(
    sourcePath: String,
    sourceTitle: String?,
    profile: TrackProfile,
    anchor: LatLong,
    rejoinHint: LatLong? = null,
    direction: RouteReshapeDirection
): LatLong {
    val bounds = resolveRouteReshapeBounds(
        sourcePath = sourcePath,
        sourceTitle = sourceTitle,
        profile = profile,
        anchor = anchor,
        rejoinHint = rejoinHint
    )
    return when (direction) {
        RouteReshapeDirection.START -> bounds.startPoint.latLong
        RouteReshapeDirection.END -> bounds.endPoint.latLong
    }
}

internal fun resolveRouteReshapeCandidateBounds(
    sourcePath: String,
    sourceTitle: String?,
    profile: TrackProfile,
    anchor: LatLong,
    rejoinHint: LatLong? = null
): List<RouteReshapeBounds> {
    val window = resolveRouteReshapeWindow(
        sourcePath = sourcePath,
        sourceTitle = sourceTitle,
        profile = profile,
        anchor = anchor
    )
    val preferredEndPosition = resolvePreferredRejoinPosition(
        profile = profile,
        anchorDistance = window.anchorDistance,
        halfWindow = window.halfWindow,
        defaultEndPosition = window.defaultEndPosition,
        rejoinHint = rejoinHint
    )
    val maxForwardDistance = (window.anchorDistance + reshapeMaxForwardRejoinMeters(window.halfWindow))
        .coerceAtMost(profile.totalDistance)
    val preferredEndDistance = trackDistanceAt(profile, preferredEndPosition)
    val defaultEndDistance = trackDistanceAt(profile, window.defaultEndPosition)
    val candidateStep = ROUTE_RESHAPE_REJOIN_CANDIDATE_STEP_METERS

    val endPositions = ArrayList<TrackPosition>(ROUTE_RESHAPE_MAX_REJOIN_CANDIDATES)
    fun addEndPosition(position: TrackPosition) {
        if (endPositions.any { comparePositions(it, position) == 0 }) return
        require(comparePositions(window.startPosition, position) < 0) {
            "Pick a point farther from the route end to reshape it."
        }
        endPositions += position
    }

    addEndPosition(preferredEndPosition)
    if (
        endPositions.size < ROUTE_RESHAPE_MAX_REJOIN_CANDIDATES &&
        comparePositions(window.defaultEndPosition, preferredEndPosition) > 0
    ) {
        addEndPosition(window.defaultEndPosition)
    }

    var nextDistance = maxOf(
        preferredEndDistance + candidateStep,
        defaultEndDistance + candidateStep * 0.5
    )
    while (
        endPositions.size < ROUTE_RESHAPE_MAX_REJOIN_CANDIDATES - 1 &&
        nextDistance < maxForwardDistance - POSITION_EPSILON
    ) {
        addEndPosition(positionAtDistance(profile, nextDistance))
        nextDistance += candidateStep
    }
    if (endPositions.size < ROUTE_RESHAPE_MAX_REJOIN_CANDIDATES) {
        addEndPosition(positionAtDistance(profile, maxForwardDistance))
    }

    return endPositions.map { endPosition ->
        RouteReshapeBounds(
            startPosition = window.startPosition,
            endPosition = endPosition,
            startPoint = pointAt(profile.points, window.startPosition),
            endPoint = pointAt(profile.points, endPosition)
        )
    }
}

internal fun secondLegRejoinsOriginalPathForward(
    sourcePath: String,
    sourceTitle: String?,
    profile: TrackProfile,
    anchor: LatLong,
    routedPoints: List<RouteGeometryPoint>
): Boolean {
    if (profile.points.size <= 1 || routedPoints.size <= 2) return true

    val sourceTrack = GpxTrackDetails(
        id = sourcePath,
        points = profile.points.map { it.latLong },
        title = sourceTitle,
        distance = profile.totalDistance,
        elevationGain = profile.totalAscent,
        startPoint = profile.points.firstOrNull()?.latLong,
        endPoint = profile.points.lastOrNull()?.latLong
    )
    val anchorPosition = snapToTrackPosition(
        target = anchor,
        sourceTrack = sourceTrack,
        profile = profile,
        thresholdMeters = ROUTE_RESHAPE_SNAP_THRESHOLD_METERS,
        failureMessage = "Move closer to the active GPX to choose where it should bend."
    )
    val minimumAllowedTrackDistance = trackDistanceAt(profile, anchorPosition) -
        ROUTE_RESHAPE_REJOIN_BACKWARD_TOLERANCE_METERS

    var routedDistance = 0.0
    var backwardOverlapDistance = 0.0
    var previousBackwardOverlap = false
    var reverseRejoinDistance = 0.0
    var previousNearTrack = false
    var previousTrackDistance: Double? = null
    var previousPoint = routedPoints.first().latLong

    for (index in 1 until routedPoints.size) {
        val currentPoint = routedPoints[index].latLong
        val segmentDistance = haversineMeters(previousPoint, currentPoint)
        routedDistance += segmentDistance

        val matchedTrackDistance = if (routedDistance < ROUTE_RESHAPE_REJOIN_ORIGIN_IGNORE_METERS) {
            null
        } else {
            val pick = findClosestTrackPosition(
                press = currentPoint,
                tracks = listOf(sourceTrack),
                profileProvider = { profile },
                allowedTrackId = sourceTrack.id
            )
            pick
                ?.takeIf { it.distanceToLineMeters <= ROUTE_RESHAPE_REJOIN_OVERLAP_THRESHOLD_METERS }
                ?.let { trackDistanceAt(profile, it.pos) }
        }
        val backwardOverlap = matchedTrackDistance != null &&
            matchedTrackDistance < minimumAllowedTrackDistance

        backwardOverlapDistance = when {
            backwardOverlap && previousBackwardOverlap -> backwardOverlapDistance + segmentDistance
            backwardOverlap -> segmentDistance
            else -> 0.0
        }
        if (backwardOverlapDistance >= ROUTE_RESHAPE_MIN_BACKWARD_OVERLAP_METERS) {
            return false
        }

        val reverseRejoin = previousNearTrack &&
            matchedTrackDistance != null &&
            previousTrackDistance != null &&
            matchedTrackDistance + ROUTE_RESHAPE_REJOIN_BACKWARD_TOLERANCE_METERS <
            previousTrackDistance

        reverseRejoinDistance = when {
            reverseRejoin -> reverseRejoinDistance + segmentDistance
            else -> 0.0
        }
        if (reverseRejoinDistance >= ROUTE_RESHAPE_MIN_REVERSE_REJOIN_TRAVEL_METERS) {
            return false
        }

        previousBackwardOverlap = backwardOverlap
        previousNearTrack = matchedTrackDistance != null
        previousTrackDistance = matchedTrackDistance
        previousPoint = currentPoint
    }

    return true
}

internal fun reshapeCandidateMatchesUserIntent(
    sourcePath: String,
    sourceTitle: String?,
    profile: TrackProfile,
    anchor: LatLong,
    shapingPoint: LatLong,
    bounds: RouteReshapeBounds,
    firstLegPoints: List<RouteGeometryPoint>,
    secondLegPoints: List<RouteGeometryPoint>
): Boolean {
    if (
        !secondLegRejoinsOriginalPathForward(
            sourcePath = sourcePath,
            sourceTitle = sourceTitle,
            profile = profile,
            anchor = anchor,
            routedPoints = secondLegPoints
        )
    ) {
        return false
    }

    val anchorDistance = resolveRouteReshapeAnchorDistance(
        sourcePath = sourcePath,
        sourceTitle = sourceTitle,
        profile = profile,
        anchor = anchor
    )
    val forwardGain = trackDistanceAt(profile, bounds.endPosition) - anchorDistance
    val minimumForwardGain = maxOf(
        ROUTE_RESHAPE_MIN_MEANINGFUL_FORWARD_GAIN_METERS,
        haversineMeters(anchor, shapingPoint) * ROUTE_RESHAPE_FORWARD_GAIN_OFFSET_FACTOR
    )
    if (forwardGain < minimumForwardGain) {
        return false
    }

    val replacedDistance = trackDistanceAt(profile, bounds.endPosition) -
        trackDistanceAt(profile, bounds.startPosition)
    val firstLegDistance = routeGeometryDistanceMeters(firstLegPoints)
    val secondLegDistance = routeGeometryDistanceMeters(secondLegPoints)
    val totalRerouteDistance = firstLegDistance + secondLegDistance
    val rejoinOverhead = secondLegDistance - forwardGain
    val rejoinProgressRatio = if (secondLegDistance <= POSITION_EPSILON) {
        1.0
    } else {
        forwardGain / secondLegDistance
    }
    if (
        rejoinOverhead > ROUTE_RESHAPE_MAX_REJOIN_OVERHEAD_METERS &&
        rejoinProgressRatio < ROUTE_RESHAPE_MIN_REJOIN_PROGRESS_RATIO
    ) {
        return false
    }

    val directRejoinDistance = haversineMeters(shapingPoint, bounds.endPoint.latLong)
    val rejoinDirectnessRatio = if (directRejoinDistance <= POSITION_EPSILON) {
        1.0
    } else {
        secondLegDistance / directRejoinDistance
    }
    val rejoinDirectnessOverhead = secondLegDistance - directRejoinDistance
    if (
        rejoinDirectnessOverhead > ROUTE_RESHAPE_MAX_REJOIN_DIRECTNESS_OVERHEAD_METERS &&
        rejoinDirectnessRatio > ROUTE_RESHAPE_MAX_REJOIN_DIRECTNESS_RATIO
    ) {
        return false
    }

    val totalOverhead = totalRerouteDistance - replacedDistance
    val replacementRatio = if (totalRerouteDistance <= POSITION_EPSILON) {
        1.0
    } else {
        replacedDistance / totalRerouteDistance
    }
    if (
        totalOverhead > ROUTE_RESHAPE_MAX_TOTAL_OVERHEAD_METERS &&
        replacementRatio < ROUTE_RESHAPE_MIN_TOTAL_REPLACEMENT_RATIO
    ) {
        return false
    }

    return true
}

internal fun encodeTrackAsGpx(
    title: String,
    points: List<TrackPoint>
): ByteArray {
    val writer = StringWriter()
    val serializer: XmlSerializer = Xml.newSerializer().apply {
        setOutput(writer)
        startDocument("UTF-8", true)
        startTag(null, "gpx")
        attribute(null, "version", "1.1")
        attribute(null, "creator", "GlanceMap")
        attribute(null, "xmlns", "http://www.topografix.com/GPX/1/1")

        startTag(null, "metadata")
        startTag(null, "name")
        text(title)
        endTag(null, "name")
        endTag(null, "metadata")

        startTag(null, "trk")
        startTag(null, "name")
        text(title)
        endTag(null, "name")
        startTag(null, "trkseg")
        points.forEach { point ->
            startTag(null, "trkpt")
            attribute(null, "lat", formatCoordinate(point.latLong.latitude))
            attribute(null, "lon", formatCoordinate(point.latLong.longitude))
            point.elevation?.let { elevation ->
                startTag(null, "ele")
                text(formatElevation(elevation))
                endTag(null, "ele")
            }
            endTag(null, "trkpt")
        }
        endTag(null, "trkseg")
        endTag(null, "trk")
        endTag(null, "gpx")
        endDocument()
    }
    serializer.flush()
    return writer.toString().toByteArray(Charsets.UTF_8)
}

private fun snapToTrackPosition(
    target: LatLong,
    sourceTrack: GpxTrackDetails,
    profile: TrackProfile,
    thresholdMeters: Double = ROUTE_TOOL_SNAP_THRESHOLD_METERS,
    failureMessage: String = "Move the crosshair closer to the active GPX before saving."
): TrackPosition {
    val pick = findClosestTrackPosition(
        press = target,
        tracks = listOf(sourceTrack),
        profileProvider = { profile },
        allowedTrackId = sourceTrack.id
    ) ?: error("Could not match the selected point to the active GPX.")

    require(pick.distanceToLineMeters <= thresholdMeters) {
        failureMessage
    }

    return pick.pos
}

private fun slicePointsBetween(
    points: List<TrackPoint>,
    a: TrackPosition,
    b: TrackPosition
): List<TrackPoint> {
    val (start, end) = orderedPositions(a, b)
    val output = ArrayList<TrackPoint>()
    appendUnique(output, pointAt(points, start))
    for (index in start.segmentIndex + 1..end.segmentIndex) {
        appendUnique(output, points[index])
    }
    appendUnique(output, pointAt(points, end))
    return output
}

private fun trimTrackStart(
    points: List<TrackPoint>,
    start: TrackPosition
): List<TrackPoint> {
    val output = ArrayList<TrackPoint>()
    appendUnique(output, pointAt(points, start))
    for (index in start.segmentIndex + 1 until points.size) {
        appendUnique(output, points[index])
    }
    return output
}

private fun trimTrackEnd(
    points: List<TrackPoint>,
    end: TrackPosition
): List<TrackPoint> {
    val output = ArrayList<TrackPoint>()
    for (index in 0..end.segmentIndex) {
        appendUnique(output, points[index])
    }
    appendUnique(output, pointAt(points, end))
    return output
}

private fun pointAt(
    points: List<TrackPoint>,
    position: TrackPosition
): TrackPoint {
    val index = position.segmentIndex.coerceIn(0, points.lastIndex - 1)
    val t = position.t.coerceIn(0.0, 1.0)
    if (t <= POSITION_EPSILON) return points[index]
    if (t >= 1.0 - POSITION_EPSILON) return points[index + 1]

    val start = points[index]
    val end = points[index + 1]
    val elevation = when {
        start.elevation != null && end.elevation != null -> {
            start.elevation + t * (end.elevation - start.elevation)
        }

        start.elevation != null -> start.elevation
        else -> end.elevation
    }
    return TrackPoint(
        latLong = LatLong(
            lerp(start.latLong.latitude, end.latLong.latitude, t),
            lerp(start.latLong.longitude, end.latLong.longitude, t)
        ),
        elevation = elevation,
        hasTimestamp = start.hasTimestamp && end.hasTimestamp
    )
}

private fun comparePositions(
    a: TrackPosition,
    b: TrackPosition
): Int {
    val segmentCompare = a.segmentIndex.compareTo(b.segmentIndex)
    return if (segmentCompare != 0) segmentCompare else a.t.compareTo(b.t)
}

private fun resolveRouteReshapeBounds(
    sourcePath: String,
    sourceTitle: String?,
    profile: TrackProfile,
    anchor: LatLong,
    rejoinHint: LatLong? = null
): RouteReshapeBounds {
    val window = resolveRouteReshapeWindow(
        sourcePath = sourcePath,
        sourceTitle = sourceTitle,
        profile = profile,
        anchor = anchor
    )
    val endPosition = resolvePreferredRejoinPosition(
        profile = profile,
        anchorDistance = window.anchorDistance,
        halfWindow = window.halfWindow,
        defaultEndPosition = window.defaultEndPosition,
        rejoinHint = rejoinHint
    )
    require(comparePositions(window.startPosition, endPosition) < 0) {
        "Pick a point farther from the route end to reshape it."
    }

    return RouteReshapeBounds(
        startPosition = window.startPosition,
        endPosition = endPosition,
        startPoint = pointAt(profile.points, window.startPosition),
        endPoint = pointAt(profile.points, endPosition)
    )
}

private fun reshapeHalfWindowMeters(profile: TrackProfile): Double {
    return (profile.totalDistance * 0.03)
        .coerceIn(
            minimumValue = ROUTE_RESHAPE_MIN_HALF_WINDOW_METERS,
            maximumValue = ROUTE_RESHAPE_MAX_HALF_WINDOW_METERS
        )
}

private fun resolveRouteReshapeWindow(
    sourcePath: String,
    sourceTitle: String?,
    profile: TrackProfile,
    anchor: LatLong
): RouteReshapeWindow {
    val sourceTrack = GpxTrackDetails(
        id = sourcePath,
        points = profile.points.map { it.latLong },
        title = sourceTitle,
        distance = profile.totalDistance,
        elevationGain = profile.totalAscent,
        startPoint = profile.points.firstOrNull()?.latLong,
        endPoint = profile.points.lastOrNull()?.latLong
    )
    val anchorPosition = snapToTrackPosition(
        target = anchor,
        sourceTrack = sourceTrack,
        profile = profile,
        thresholdMeters = ROUTE_RESHAPE_SNAP_THRESHOLD_METERS,
        failureMessage = "Move closer to the active GPX to choose where it should bend."
    )
    val anchorDistance = trackDistanceAt(profile, anchorPosition)
    val halfWindow = reshapeHalfWindowMeters(profile)
    var startDistance = anchorDistance - halfWindow
    var endDistance = anchorDistance + halfWindow
    if (startDistance < 0.0) {
        endDistance = (endDistance - startDistance).coerceAtMost(profile.totalDistance)
        startDistance = 0.0
    }
    if (endDistance > profile.totalDistance) {
        val overflow = endDistance - profile.totalDistance
        startDistance = (startDistance - overflow).coerceAtLeast(0.0)
        endDistance = profile.totalDistance
    }

    return RouteReshapeWindow(
        startPosition = positionAtDistance(profile, startDistance),
        defaultEndPosition = positionAtDistance(profile, endDistance),
        anchorDistance = anchorDistance,
        halfWindow = halfWindow
    )
}

private fun resolveRouteReshapeAnchorDistance(
    sourcePath: String,
    sourceTitle: String?,
    profile: TrackProfile,
    anchor: LatLong
): Double {
    val window = resolveRouteReshapeWindow(
        sourcePath = sourcePath,
        sourceTitle = sourceTitle,
        profile = profile,
        anchor = anchor
    )
    return window.anchorDistance
}

private fun resolvePreferredRejoinPosition(
    profile: TrackProfile,
    anchorDistance: Double,
    halfWindow: Double,
    defaultEndPosition: TrackPosition,
    rejoinHint: LatLong?
): TrackPosition {
    val hint = rejoinHint ?: return defaultEndPosition
    val minForwardDistance = reshapeMinForwardRejoinMeters(halfWindow)
    val maxForwardDistance = reshapeMaxForwardRejoinMeters(halfWindow)
    val searchStart = positionAtDistance(
        profile = profile,
        distanceMeters = (anchorDistance + minForwardDistance).coerceAtMost(profile.totalDistance)
    )
    val searchEnd = positionAtDistance(
        profile = profile,
        distanceMeters = (anchorDistance + maxForwardDistance).coerceAtMost(profile.totalDistance)
    )
    if (comparePositions(searchStart, searchEnd) >= 0) {
        return defaultEndPosition
    }

    return findEarliestForwardRejoinPosition(
        profile = profile,
        hint = hint,
        minPosition = searchStart,
        maxPosition = searchEnd
    ) ?: defaultEndPosition
}

private fun reshapeMinForwardRejoinMeters(halfWindow: Double): Double {
    return maxOf(
        ROUTE_RESHAPE_MIN_FORWARD_REJOIN_METERS,
        halfWindow * 0.5
    )
}

private fun reshapeMaxForwardRejoinMeters(halfWindow: Double): Double {
    return (halfWindow * 6.0).coerceIn(
        minimumValue = reshapeMinForwardRejoinMeters(halfWindow),
        maximumValue = ROUTE_RESHAPE_MAX_FORWARD_REJOIN_METERS
    )
}

private fun findEarliestForwardRejoinPosition(
    profile: TrackProfile,
    hint: LatLong,
    minPosition: TrackPosition,
    maxPosition: TrackPosition
): TrackPosition? {
    if (profile.points.size <= 1) return null

    val zoom: Byte = 20
    val tileSize = 256
    val mapSize: Long = MercatorProjection.getMapSize(zoom, tileSize)

    fun toXY(latLong: LatLong): Pair<Double, Double> {
        val x = MercatorProjection.longitudeToPixelX(latLong.longitude, mapSize)
        val y = MercatorProjection.latitudeToPixelY(latLong.latitude, mapSize)
        return x to y
    }

    fun toLatLong(x: Double, y: Double): LatLong {
        val lon = MercatorProjection.pixelXToLongitude(x, mapSize)
        val lat = MercatorProjection.pixelYToLatitude(y, mapSize)
        return LatLong(lat, lon)
    }

    val (hintX, hintY) = toXY(hint)
    val candidates = ArrayList<RouteRejoinCandidate>()
    val startIndex = minPosition.segmentIndex.coerceIn(0, profile.points.lastIndex - 1)
    val endIndex = maxPosition.segmentIndex.coerceIn(0, profile.points.lastIndex - 1)

    for (segmentIndex in startIndex..endIndex) {
        val minT = if (segmentIndex == startIndex) minPosition.t else 0.0
        val maxT = if (segmentIndex == endIndex) maxPosition.t else 1.0
        if (maxT <= minT + POSITION_EPSILON) continue

        val start = profile.points[segmentIndex].latLong
        val end = profile.points[segmentIndex + 1].latLong
        val (ax, ay) = toXY(start)
        val (bx, by) = toXY(end)
        val abx = bx - ax
        val aby = by - ay
        val abLenSquared = abx * abx + aby * aby
        val rawT = if (abLenSquared > 0.0) {
            ((hintX - ax) * abx + (hintY - ay) * aby) / abLenSquared
        } else {
            minT
        }
        val clampedT = rawT.coerceIn(minT, maxT)
        val projectedX = ax + clampedT * abx
        val projectedY = ay + clampedT * aby
        val projectedLatLong = toLatLong(projectedX, projectedY)
        candidates += RouteRejoinCandidate(
            position = TrackPosition(trackId = "", segmentIndex = segmentIndex, t = clampedT),
            distanceMeters = haversineMeters(projectedLatLong, hint)
        )
    }

    if (candidates.isEmpty()) return null
    val bestDistance = candidates.minOf { it.distanceMeters }
    return candidates.firstOrNull {
        it.distanceMeters <= bestDistance + ROUTE_RESHAPE_REJOIN_DISTANCE_MARGIN_METERS
    }?.position
}

private fun trackDistanceAt(
    profile: TrackProfile,
    position: TrackPosition
): Double {
    if (profile.points.size <= 1) return 0.0
    val segmentIndex = position.segmentIndex.coerceIn(0, profile.segLen.lastIndex)
    val segmentStartDistance = profile.cumDist.getOrElse(segmentIndex) { 0.0 }
    val segmentLength = profile.segLen.getOrElse(segmentIndex) { 0.0 }
    return segmentStartDistance + segmentLength * position.t.coerceIn(0.0, 1.0)
}

private fun routeGeometryDistanceMeters(points: List<RouteGeometryPoint>): Double {
    if (points.size < 2) return 0.0
    var totalDistance = 0.0
    var previous = points.first().latLong
    for (index in 1 until points.size) {
        val current = points[index].latLong
        totalDistance += haversineMeters(previous, current)
        previous = current
    }
    return totalDistance
}

private fun positionAtDistance(
    profile: TrackProfile,
    distanceMeters: Double
): TrackPosition {
    if (profile.points.size <= 1) {
        return TrackPosition(trackId = "", segmentIndex = 0, t = 0.0)
    }
    val target = distanceMeters.coerceIn(0.0, profile.totalDistance)
    if (target <= 0.0) {
        return TrackPosition(trackId = "", segmentIndex = 0, t = 0.0)
    }
    if (target >= profile.totalDistance) {
        return TrackPosition(
            trackId = "",
            segmentIndex = profile.points.lastIndex - 1,
            t = 1.0
        )
    }

    val upperPointIndex = profile.cumDist.indexOfFirst { it >= target }
        .let { if (it < 0) profile.points.lastIndex else it }
    val segmentIndex = (upperPointIndex - 1).coerceIn(0, profile.segLen.lastIndex)
    val segmentStartDistance = profile.cumDist.getOrElse(segmentIndex) { 0.0 }
    val segmentLength = profile.segLen.getOrElse(segmentIndex) { 0.0 }
    val t = if (segmentLength <= POSITION_EPSILON) {
        0.0
    } else {
        ((target - segmentStartDistance) / segmentLength).coerceIn(0.0, 1.0)
    }
    return TrackPosition(trackId = "", segmentIndex = segmentIndex, t = t)
}

private fun orderedPositions(
    a: TrackPosition,
    b: TrackPosition
): Pair<TrackPosition, TrackPosition> {
    return if (comparePositions(a, b) <= 0) a to b else b to a
}

private fun buildRouteReshapeHandleIndices(pointCount: Int): List<Int> {
    if (pointCount <= 0) return emptyList()
    if (pointCount == 1) return listOf(0)

    val lastIndex = pointCount - 1
    val handleCount = minOf(ROUTE_RESHAPE_MAX_HANDLES, pointCount)
    val step = lastIndex.toDouble() / (handleCount - 1).toDouble()
    return buildSet {
        add(0)
        add(lastIndex)
        for (index in 1 until handleCount - 1) {
            add((index * step).toInt().coerceIn(1, lastIndex - 1))
        }
    }.toList().sorted()
}

private fun positionForPointIndex(
    points: List<TrackPoint>,
    pointIndex: Int
): TrackPosition {
    if (points.size <= 1) {
        return TrackPosition(trackId = "", segmentIndex = 0, t = 0.0)
    }
    return when {
        pointIndex <= 0 -> TrackPosition(trackId = "", segmentIndex = 0, t = 0.0)
        pointIndex >= points.lastIndex -> TrackPosition(
            trackId = "",
            segmentIndex = points.lastIndex - 1,
            t = 1.0
        )

        else -> TrackPosition(
            trackId = "",
            segmentIndex = pointIndex - 1,
            t = 1.0
        )
    }
}

private fun appendUnique(
    target: MutableList<TrackPoint>,
    point: TrackPoint
) {
    val last = target.lastOrNull()
    if (last != null && sameLocation(last.latLong, point.latLong)) {
        return
    }
    target += point
}

private fun appendUniqueLatLong(
    target: MutableList<LatLong>,
    point: LatLong
) {
    val last = target.lastOrNull()
    if (last != null && sameLocation(last, point)) {
        return
    }
    target += point
}

private fun sameLocation(
    a: LatLong,
    b: LatLong
): Boolean {
    return abs(a.latitude - b.latitude) < 1e-9 &&
        abs(a.longitude - b.longitude) < 1e-9
}

private fun haversineMeters(
    a: LatLong,
    b: LatLong
): Double {
    val r = 6_371_000.0
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val s = sin(dLat / 2.0)
    val t = sin(dLon / 2.0)
    val h = s * s + cos(lat1) * cos(lat2) * t * t
    return 2 * r * asin(min(1.0, sqrt(h)))
}

private fun buildEditedFileName(
    sourceFileName: String,
    mode: RouteModifyMode
): String {
    return buildEditedFileName(
        sourceFileName = sourceFileName,
        modeSlug = when (mode) {
            RouteModifyMode.RESHAPE_ROUTE -> "reshape"
            RouteModifyMode.REPLACE_SECTION_A_TO_B -> "replace-ab"
            RouteModifyMode.KEEP_ONLY_A_TO_B -> "keep-ab"
            RouteModifyMode.TRIM_START_TO_HERE -> "trim-start"
            RouteModifyMode.TRIM_END_FROM_HERE -> "trim-end"
            RouteModifyMode.REVERSE_GPX -> "reverse"
        }
    )
}

private fun buildEditedFileName(
    sourceFileName: String,
    modeSlug: String
): String {
    val base = sourceFileName.removeSuffix(".gpx")
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    return "${sanitizeFileStem(base)}-$modeSlug-$stamp.gpx"
}

internal fun buildRenamedGpxFileName(title: String): String {
    return "${sanitizeFileStem(title)}.gpx"
}

internal fun sanitizeFileStem(input: String): String {
    return input
        .replace(Regex("\\.gpx$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-')
        .ifBlank { "gpx-edit" }
}

private fun formatCoordinate(value: Double): String = String.format("%.8f", value)

private fun formatElevation(value: Double): String = String.format("%.1f", value)

private fun lerp(
    start: Double,
    end: Double,
    t: Double
): Double = start + t * (end - start)

private fun RouteGeometryPoint.toTrackPoint(): TrackPoint {
    return TrackPoint(
        latLong = latLong,
        elevation = elevation
    )
}

internal enum class RouteReshapeDirection {
    START,
    END
}
