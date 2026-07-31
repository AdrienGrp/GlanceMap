package com.glancemap.glancemapwearos.presentation.features.routetools

import com.glancemap.glancemapwearos.presentation.features.gpx.GpxTrackDetails
import com.glancemap.glancemapwearos.presentation.features.gpx.TrackPosition
import com.glancemap.glancemapwearos.presentation.features.gpx.TrackProfile
import com.glancemap.glancemapwearos.presentation.features.gpx.findClosestTrackPosition
import com.glancemap.glancemapwearos.presentation.features.gpx.totalAscent
import com.glancemap.glancemapwearos.presentation.features.gpx.totalDistance
import org.mapsforge.core.model.LatLong

private const val ROUTE_TOOL_SNAP_THRESHOLD_METERS = 60.0

internal fun resolveReplaceSectionEndpoints(
    sourcePath: String,
    sourceTitle: String?,
    profile: TrackProfile,
    pointA: LatLong,
    pointB: LatLong,
): Pair<LatLong, LatLong> {
    val sourceTrack =
        GpxTrackDetails(
            id = sourcePath,
            points = profile.points.map { it.latLong },
            title = sourceTitle,
            distance = profile.totalDistance,
            elevationGain = profile.totalAscent,
            startPoint = profile.points.firstOrNull()?.latLong,
            endPoint = profile.points.lastOrNull()?.latLong,
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
    target: LatLong,
): RouteToolTrackMatch {
    val sourceTrack =
        GpxTrackDetails(
            id = sourcePath,
            points = profile.points.map { it.latLong },
            title = sourceTitle,
            distance = profile.totalDistance,
            elevationGain = profile.totalAscent,
            startPoint = profile.points.firstOrNull()?.latLong,
            endPoint = profile.points.lastOrNull()?.latLong,
        )
    val pick =
        findClosestTrackPosition(
            press = target,
            tracks = listOf(sourceTrack),
            profileProvider = { profile },
            allowedTrackId = sourceTrack.id,
        ) ?: error("Could not match the selected point to the active GPX.")

    return RouteToolTrackMatch(
        position = pick.pos,
        latLong = pointAt(profile.points, pick.pos).latLong,
        distanceMeters = pick.distanceToLineMeters,
    )
}

internal fun routeToolSnapThresholdMeters(): Double = ROUTE_TOOL_SNAP_THRESHOLD_METERS

internal fun snapToTrackPosition(
    target: LatLong,
    sourceTrack: GpxTrackDetails,
    profile: TrackProfile,
    thresholdMeters: Double = ROUTE_TOOL_SNAP_THRESHOLD_METERS,
    failureMessage: String = "Move the crosshair closer to the active GPX before saving.",
): TrackPosition {
    val pick =
        findClosestTrackPosition(
            press = target,
            tracks = listOf(sourceTrack),
            profileProvider = { profile },
            allowedTrackId = sourceTrack.id,
        ) ?: error("Could not match the selected point to the active GPX.")

    require(pick.distanceToLineMeters <= thresholdMeters) {
        failureMessage
    }

    return pick.pos
}
