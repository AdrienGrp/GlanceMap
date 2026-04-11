package com.glancemap.glancemapwearos.presentation.features.gpx

import com.glancemap.glancemapwearos.presentation.formatting.DurationFormatter
import com.glancemap.glancemapwearos.presentation.formatting.UnitFormatter
import org.mapsforge.core.model.LatLong

data class GpxFileState(
    val name: String,
    val path: String,
    val title: String?,
    val distance: Double,
    val elevationGain: Double,
    val estimatedDurationSec: Double?,
    val isActive: Boolean = false,
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: name

    fun formattedDistance(isMetric: Boolean): Pair<String, String> = UnitFormatter.formatDistance(distance, isMetric)

    fun formattedElevation(isMetric: Boolean): Pair<String, String> = UnitFormatter.formatElevation(elevationGain, isMetric)

    fun formattedEtaShort(): String = DurationFormatter.formatDurationShort(estimatedDurationSec)
}

data class TrackPoint(
    val latLong: LatLong,
    val elevation: Double?,
    val hasTimestamp: Boolean = false,
)

data class GpxTrackDetails(
    val id: String,
    val points: List<LatLong>,
    val title: String?,
    val distance: Double,
    val elevationGain: Double,
    val startPoint: LatLong?,
    val endPoint: LatLong?,
)

data class TrackPosition(
    val trackId: String,
    val segmentIndex: Int,
    val t: Double,
)

data class GpxInspectionAStats(
    val distanceFromStart: Double,
    val elevationGainFromStart: Double,
    val elevationLossFromStart: Double,
    val durationFromStartSec: Double?,
    val distanceToEnd: Double,
    val elevationGainToEnd: Double,
    val elevationLossToEnd: Double,
    val durationToEndSec: Double?,
)

data class GpxInspectionLeg(
    val distance: Double,
    val elevationGain: Double,
    val elevationLoss: Double,
    val durationSec: Double?,
)

data class ElevationSample(
    val distance: Double,
    val elevation: Double,
    val cumulativeAscent: Double,
    val cumulativeDescent: Double,
    val cumulativeDurationSec: Double?,
)

data class GpxElevationProfileUiState(
    val trackPath: String,
    val trackTitle: String,
    val totalDistance: Double,
    val totalAscent: Double,
    val totalDescent: Double,
    val totalDurationSec: Double?,
    val samples: List<ElevationSample>,
    val minElevation: Double?,
    val maxElevation: Double?,
)

sealed interface GpxInspectionUiState {
    val trackTitle: String?
}

data class InspectionAUiState(
    override val trackTitle: String?,
    val a: GpxInspectionAStats,
) : GpxInspectionUiState

data class InspectionABUiState(
    override val trackTitle: String?,
    val sToA: GpxInspectionLeg,
    val aToB: GpxInspectionLeg,
    val bToE: GpxInspectionLeg,
) : GpxInspectionUiState
