package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.data.repository.PoiType
import com.glancemap.glancemapwearos.presentation.features.poi.PoiOverlayMarker
import com.glancemap.glancemapwearos.presentation.formatting.UnitFormatter
import org.mapsforge.core.model.LatLong
import java.net.URI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal fun unrotateTouchToMapSpace(
    x: Double,
    y: Double,
    mapWidth: Double,
    mapHeight: Double,
    mapRotationDeg: Double,
): Pair<Double, Double> {
    if (mapWidth <= 0.0 || mapHeight <= 0.0) return x to y
    if (abs(mapRotationDeg) < 0.001) return x to y

    val cx = mapWidth / 2.0
    val cy = mapHeight / 2.0

    // Convert screen point back into unrotated map content coordinates.
    val rad = Math.toRadians(-mapRotationDeg)
    val c = cos(rad)
    val s = sin(rad)

    val dx = x - cx
    val dy = y - cy

    val rx = dx * c - dy * s
    val ry = dx * s + dy * c

    return (cx + rx) to (cy + ry)
}

internal fun findTappedPoiMarker(
    tap: LatLong,
    zoomLevel: Int,
    markers: List<PoiOverlayMarker>,
): PoiOverlayMarker? {
    if (markers.isEmpty()) return null
    val thresholdMeters = tapToleranceMetersForZoom(zoomLevel)
    return markers
        .asSequence()
        .map { marker ->
            val distance =
                haversineMeters(
                    lat1 = tap.latitude,
                    lon1 = tap.longitude,
                    lat2 = marker.lat,
                    lon2 = marker.lon,
                )
            marker to distance
        }.filter { (_, distance) -> distance <= thresholdMeters }
        .minByOrNull { (_, distance) -> distance }
        ?.first
}

private fun tapToleranceMetersForZoom(zoomLevel: Int): Double =
    when {
        zoomLevel >= 17 -> 45.0
        zoomLevel >= 16 -> 65.0
        zoomLevel >= 15 -> 90.0
        zoomLevel >= 14 -> 130.0
        else -> 180.0
    }

private const val COMPACT_DESCRIPTION_MAX_CHARS = 120

internal data class PoiTapPopupContent(
    val compactText: String,
    val expandedText: String? = null,
) {
    val canExpand: Boolean
        get() = !expandedText.isNullOrBlank() && expandedText != compactText
}

internal fun buildPoiTapPopupContent(
    marker: PoiOverlayMarker,
    isMetric: Boolean,
): PoiTapPopupContent {
    val name = marker.label?.trim().orEmpty()
    val details = marker.details
    val typeLabel = details?.typeLabel?.trim().orEmpty()
    val displayType = if (typeLabel.isNotBlank()) typeLabel else marker.type.displayName

    val title =
        if (name.isBlank()) {
            displayType
        } else if (name.equals(displayType, ignoreCase = true)) {
            name
        } else {
            "$name ($displayType)"
        }

    val detailBits = mutableListOf<String>()
    details?.elevationMeters?.takeIf { it > 0 }?.let { meters ->
        val (value, unit) = UnitFormatter.formatElevation(meters.toDouble(), isMetric)
        detailBits += "$value $unit"
    }
    if (marker.type == PoiType.HUT || marker.type == PoiType.CAMP) {
        details?.sleepingPlaces?.takeIf { it >= 0 }?.let { places ->
            detailBits += if (places == 1) "1 place" else "$places places"
        }
    }
    details
        ?.state
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { detailBits += it }

    val description =
        details
            ?.shortDescription
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    val sourceBits = mutableListOf<String>()
    details
        ?.source
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { sourceBits += it }
    details
        ?.website
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { websiteHost(it) }
        ?.let { sourceBits += it }
    val uniqueSourceBits =
        sourceBits
            .fold(mutableListOf<String>()) { acc, value ->
                if (acc.none { it.equals(value, ignoreCase = true) }) {
                    acc += value
                }
                acc
            }

    val compactLines = mutableListOf(title)
    if (detailBits.isNotEmpty()) compactLines += detailBits.joinToString(" • ")
    description?.let { compactLines += truncateText(it, COMPACT_DESCRIPTION_MAX_CHARS) }
    if (uniqueSourceBits.isNotEmpty()) compactLines += uniqueSourceBits.joinToString(" • ")
    val compactText = compactLines.joinToString("\n")

    val expandedLines = mutableListOf(title)
    if (detailBits.isNotEmpty()) expandedLines += detailBits.joinToString(" • ")
    description?.let { expandedLines += it }
    if (uniqueSourceBits.isNotEmpty()) expandedLines += uniqueSourceBits.joinToString(" • ")
    val expandedText =
        expandedLines
            .joinToString("\n")
            .takeIf { it != compactText }

    return PoiTapPopupContent(
        compactText = compactText,
        expandedText = expandedText,
    )
}

private fun truncateText(
    value: String,
    maxChars: Int,
): String {
    if (maxChars <= 0 || value.length <= maxChars) return value
    val safePrefix = value.take(maxChars).trimEnd()
    if (safePrefix.isBlank()) return value.take(maxChars)
    val cutAtWordBoundary = safePrefix.substringBeforeLast(' ', safePrefix)
    val base = cutAtWordBoundary.ifBlank { safePrefix }
    return "${base.trimEnd('.')}..."
}

private fun websiteHost(url: String): String? {
    val normalized = if ("://" in url) url else "https://$url"
    val host =
        runCatching { URI(normalized).host }
            .getOrNull()
            ?.removePrefix("www.")
            .orEmpty()
    return host.takeIf { it.isNotBlank() }
}

private val PoiType.displayName: String
    get() =
        when (this) {
            PoiType.PEAK -> "Peak"
            PoiType.WATER -> "Water"
            PoiType.HUT -> "Hut"
            PoiType.CAMP -> "Camp"
            PoiType.FOOD -> "Food"
            PoiType.TOILET -> "Toilets"
            PoiType.TRANSPORT -> "Transport"
            PoiType.BIKE -> "Bike"
            PoiType.VIEWPOINT -> "Viewpoint"
            PoiType.PARKING -> "Parking"
            PoiType.SHOP -> "Shop"
            PoiType.GENERIC -> "POI"
            PoiType.CUSTOM -> "My creation"
        }

private fun haversineMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
        sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2.0) * sin(dLon / 2.0)
    val c = 2.0 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1.0 - a))
    return earthRadius * c
}
