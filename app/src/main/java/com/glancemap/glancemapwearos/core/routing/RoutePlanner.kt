package com.glancemap.glancemapwearos.core.routing

import org.mapsforge.core.model.LatLong

enum class RoutePlannerPreset {
    BALANCED_HIKE,
    PREFER_TRAILS,
    PREFER_EASIEST
}

data class RoutePlannerRequest(
    val origin: LatLong,
    val destination: LatLong,
    val viaPoints: List<LatLong> = emptyList(),
    val preset: RoutePlannerPreset = RoutePlannerPreset.BALANCED_HIKE,
    val useElevation: Boolean = true,
    val allowFerries: Boolean = false
)

data class RoundTripPlannerRequest(
    val start: LatLong,
    val targetDistanceMeters: Int,
    val targetLabel: String,
    val preset: RoutePlannerPreset = RoutePlannerPreset.BALANCED_HIKE,
    val useElevation: Boolean = true,
    val allowFerries: Boolean = false,
    val allowOutAndBack: Boolean = false,
    val pointCount: Int = 5,
    val variationIndex: Int = 0
)

class LoopRouteSuggestionException(
    val lowerDistanceMeters: Int?,
    val higherDistanceMeters: Int?
) : IllegalStateException(
    when {
        lowerDistanceMeters != null && higherDistanceMeters != null ->
            "No loop found near the selected target. Try a shorter or longer loop."
        lowerDistanceMeters != null ->
            "No loop found near the selected target. Try a shorter loop."
        higherDistanceMeters != null ->
            "No loop found near the selected target. Try a longer loop."
        else ->
            "No loop found near the selected target."
    }
)

data class RouteGeometryPoint(
    val latLong: LatLong,
    val elevation: Double?
)

data class RoutePlannerOutput(
    val fileName: String,
    val title: String,
    val gpxBytes: ByteArray,
    val points: List<RouteGeometryPoint>
)

interface RoutePlanner {
    suspend fun createRoute(request: RoutePlannerRequest): RoutePlannerOutput
    suspend fun createLoop(request: RoundTripPlannerRequest): RoutePlannerOutput
}
