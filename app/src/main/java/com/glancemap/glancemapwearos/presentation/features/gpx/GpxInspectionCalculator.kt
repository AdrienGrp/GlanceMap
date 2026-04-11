package com.glancemap.glancemapwearos.presentation.features.gpx

import kotlin.math.abs

private data class InspectionScalars(
    val dist: Double,
    val asc: Double,
    val desc: Double,
    val etaSec: Double?
)

internal fun buildInspectionAUiState(
    trackTitle: String?,
    profile: TrackProfile,
    pos: TrackPosition,
    etaProjection: GpxEtaProjection?
): InspectionAUiState {
    val scalar = inspectionScalarsAt(profile = profile, pos = pos, etaProjection = etaProjection)
    val totalDist = profile.cumDist.lastOrNull() ?: 0.0
    val totalAsc = profile.cumAscent.lastOrNull() ?: 0.0
    val totalDesc = profile.cumDescent.lastOrNull() ?: 0.0
    val totalEtaSec = etaProjection?.totalSeconds
    val durationToEndSec = if (totalEtaSec != null && scalar.etaSec != null) {
        (totalEtaSec - scalar.etaSec).coerceAtLeast(0.0)
    } else {
        null
    }

    return InspectionAUiState(
        trackTitle = trackTitle,
        a = GpxInspectionAStats(
            distanceFromStart = scalar.dist,
            elevationGainFromStart = scalar.asc,
            elevationLossFromStart = scalar.desc,
            durationFromStartSec = scalar.etaSec,
            distanceToEnd = (totalDist - scalar.dist).coerceAtLeast(0.0),
            elevationGainToEnd = (totalAsc - scalar.asc).coerceAtLeast(0.0),
            elevationLossToEnd = (totalDesc - scalar.desc).coerceAtLeast(0.0),
            durationToEndSec = durationToEndSec
        )
    )
}

internal fun buildInspectionABUiState(
    trackTitle: String?,
    profile: TrackProfile,
    a: TrackPosition,
    b: TrackPosition,
    etaProjection: GpxEtaProjection?
): InspectionABUiState {
    val scalarA = inspectionScalarsAt(profile = profile, pos = a, etaProjection = etaProjection)
    val scalarB = inspectionScalarsAt(profile = profile, pos = b, etaProjection = etaProjection)
    val totalDist = profile.cumDist.lastOrNull() ?: 0.0
    val totalAsc = profile.cumAscent.lastOrNull() ?: 0.0
    val totalDesc = profile.cumDescent.lastOrNull() ?: 0.0
    val totalEtaSec = etaProjection?.totalSeconds

    return InspectionABUiState(
        trackTitle = trackTitle,
        sToA = GpxInspectionLeg(
            distance = scalarA.dist.coerceAtLeast(0.0),
            elevationGain = scalarA.asc.coerceAtLeast(0.0),
            elevationLoss = scalarA.desc.coerceAtLeast(0.0),
            durationSec = scalarA.etaSec
        ),
        aToB = inspectionLegFromTo(scalarA, scalarB),
        bToE = GpxInspectionLeg(
            distance = (totalDist - scalarB.dist).coerceAtLeast(0.0),
            elevationGain = (totalAsc - scalarB.asc).coerceAtLeast(0.0),
            elevationLoss = (totalDesc - scalarB.desc).coerceAtLeast(0.0),
            durationSec = if (totalEtaSec != null && scalarB.etaSec != null) {
                (totalEtaSec - scalarB.etaSec).coerceAtLeast(0.0)
            } else {
                null
            }
        )
    )
}

private fun inspectionScalarsAt(
    profile: TrackProfile,
    pos: TrackPosition,
    etaProjection: GpxEtaProjection?
): InspectionScalars {
    val n = profile.points.size
    if (n < 2) return InspectionScalars(0.0, 0.0, 0.0, null)

    val i = pos.segmentIndex.coerceIn(0, n - 2)
    val t = pos.t.coerceIn(0.0, 1.0)

    val seg = profile.segLen[i]
    val distAt = profile.cumDist[i] + t * seg
    val ascAt = profile.cumAscent[i] + t * (profile.cumAscent[i + 1] - profile.cumAscent[i])
    val descAt = profile.cumDescent[i] + t * (profile.cumDescent[i + 1] - profile.cumDescent[i])
    val etaAt = etaProjection?.secondsAtTrackPosition(pos)

    return InspectionScalars(dist = distAt, asc = ascAt, desc = descAt, etaSec = etaAt)
}

private fun inspectionLegFromTo(from: InspectionScalars, to: InspectionScalars): GpxInspectionLeg {
    val duration = if (from.etaSec != null && to.etaSec != null) {
        abs(to.etaSec - from.etaSec)
    } else {
        null
    }
    return if (to.dist >= from.dist) {
        GpxInspectionLeg(
            distance = (to.dist - from.dist).coerceAtLeast(0.0),
            elevationGain = (to.asc - from.asc).coerceAtLeast(0.0),
            elevationLoss = (to.desc - from.desc).coerceAtLeast(0.0),
            durationSec = duration
        )
    } else {
        GpxInspectionLeg(
            distance = (from.dist - to.dist).coerceAtLeast(0.0),
            elevationGain = (from.desc - to.desc).coerceAtLeast(0.0),
            elevationLoss = (from.asc - to.asc).coerceAtLeast(0.0),
            durationSec = duration
        )
    }
}
