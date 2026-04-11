package com.glancemap.glancemapcompanionapp.refuges

import kotlin.math.ceil
import kotlin.math.min

private const val OSM_OVERPASS_TILE_MAX_LON_SPAN_DEGREES = 2.0
private const val OSM_OVERPASS_TILE_MAX_LAT_SPAN_DEGREES = 1.5
private const val OSM_OVERPASS_TILE_MAX_AREA_DEGREES = 3.0
private const val OSM_OVERPASS_MIN_SUBDIVIDE_LON_SPAN_DEGREES = 0.10
private const val OSM_OVERPASS_MIN_SUBDIVIDE_LAT_SPAN_DEGREES = 0.10
internal const val OSM_OVERPASS_TOO_LARGE_MESSAGE =
    "OSM Overpass response is too large. Please use a smaller area."
private val HTML_TITLE_REGEX = Regex("(?is)<title[^>]*>(.*?)</title>")
private val OVERPASS_RATE_LIMIT_REGEX = Regex("""Rate limit:\s*(\d+)""", RegexOption.IGNORE_CASE)
private val OVERPASS_SLOTS_AVAILABLE_REGEX =
    Regex("""(\d+)\s+slots?\s+available\s+now""", RegexOption.IGNORE_CASE)
private val OVERPASS_SLOT_WAIT_SECONDS_REGEX =
    Regex("""slot available after.*?(\d+)\s+seconds?""", RegexOption.IGNORE_CASE)
private val OVERPASS_IN_SECONDS_REGEX = Regex("""in\s+(\d+)\s+seconds?""", RegexOption.IGNORE_CASE)

internal fun splitBboxForOverpass(
    bbox: BBox,
    maxLonSpanDegrees: Double = OSM_OVERPASS_TILE_MAX_LON_SPAN_DEGREES,
    maxLatSpanDegrees: Double = OSM_OVERPASS_TILE_MAX_LAT_SPAN_DEGREES,
    maxAreaDegrees: Double = OSM_OVERPASS_TILE_MAX_AREA_DEGREES,
): List<BBox> {
    val lonSpan = bbox.maxLon - bbox.minLon
    val latSpan = bbox.maxLat - bbox.minLat
    val area = lonSpan * latSpan
    if (
        lonSpan <= maxLonSpanDegrees &&
        latSpan <= maxLatSpanDegrees &&
        area <= maxAreaDegrees
    ) {
        return listOf(bbox)
    }

    val columns = ceil(lonSpan / maxLonSpanDegrees).toInt().coerceAtLeast(1)
    val rows = ceil(latSpan / maxLatSpanDegrees).toInt().coerceAtLeast(1)
    val tileLonSpan = lonSpan / columns.toDouble()
    val tileLatSpan = latSpan / rows.toDouble()

    val tiles = ArrayList<BBox>(rows * columns)
    for (row in 0 until rows) {
        val tileMinLat = bbox.minLat + (row * tileLatSpan)
        val tileMaxLat = if (row == rows - 1) bbox.maxLat else min(bbox.maxLat, tileMinLat + tileLatSpan)
        for (column in 0 until columns) {
            val tileMinLon = bbox.minLon + (column * tileLonSpan)
            val tileMaxLon = if (column == columns - 1) bbox.maxLon else min(bbox.maxLon, tileMinLon + tileLonSpan)
            tiles +=
                BBox(
                    minLon = tileMinLon,
                    minLat = tileMinLat,
                    maxLon = tileMaxLon,
                    maxLat = tileMaxLat,
                )
        }
    }
    return tiles
}

internal fun isRetriableOsmOverpassStatus(code: Int): Boolean = code == 408 || code == 429 || code in 500..599

internal fun splitBboxForOverpassOverflow(
    bbox: BBox,
    minLonSpanDegrees: Double = OSM_OVERPASS_MIN_SUBDIVIDE_LON_SPAN_DEGREES,
    minLatSpanDegrees: Double = OSM_OVERPASS_MIN_SUBDIVIDE_LAT_SPAN_DEGREES,
): List<BBox> {
    val lonSpan = bbox.maxLon - bbox.minLon
    val latSpan = bbox.maxLat - bbox.minLat
    val canSplitLon = lonSpan > minLonSpanDegrees
    val canSplitLat = latSpan > minLatSpanDegrees
    if (!canSplitLon && !canSplitLat) {
        return listOf(bbox)
    }

    val lonStops =
        if (canSplitLon) {
            listOf(bbox.minLon, bbox.minLon + (lonSpan / 2.0), bbox.maxLon)
        } else {
            listOf(bbox.minLon, bbox.maxLon)
        }
    val latStops =
        if (canSplitLat) {
            listOf(bbox.minLat, bbox.minLat + (latSpan / 2.0), bbox.maxLat)
        } else {
            listOf(bbox.minLat, bbox.maxLat)
        }

    val tiles = ArrayList<BBox>((lonStops.size - 1) * (latStops.size - 1))
    for (row in 0 until latStops.lastIndex) {
        for (column in 0 until lonStops.lastIndex) {
            tiles +=
                BBox(
                    minLon = lonStops[column],
                    minLat = latStops[row],
                    maxLon = lonStops[column + 1],
                    maxLat = latStops[row + 1],
                )
        }
    }
    return tiles
}

internal fun isOverpassResponseTooLarge(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        if (current.message.orEmpty().contains(OSM_OVERPASS_TOO_LARGE_MESSAGE, ignoreCase = true)) {
            return true
        }
        current = current.cause
    }
    return false
}

internal fun summarizeOsmOverpassFailure(
    code: Int,
    body: String,
): String {
    val detail = sanitizeRemoteHttpDetail(body)
    val fallback =
        when (code) {
            408 -> "OpenStreetMap request timed out. Please try again."
            429 -> "OpenStreetMap rate limit reached. Please wait a moment and retry."
            502 -> "OpenStreetMap gateway returned an invalid response. Please try again."
            503 -> "OpenStreetMap server is temporarily unavailable. Please try again."
            504 -> "OpenStreetMap server timed out. Please try again."
            else -> "Please try again."
        }
    return if (detail.isBlank()) {
        "OSM Overpass API failed ($code). $fallback"
    } else {
        "OSM Overpass API failed ($code). $detail"
    }
}

internal fun sanitizeRemoteHttpDetail(body: String): String {
    val trimmed = body.trim()
    if (trimmed.isBlank()) return ""

    if (trimmed.contains("<!doctype", ignoreCase = true) || trimmed.contains("<html", ignoreCase = true)) {
        val title =
            HTML_TITLE_REGEX
                .find(trimmed)
                ?.groupValues
                ?.getOrNull(1)
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                .orEmpty()
        if (title.isNotBlank()) return title.take(160)

        return when {
            trimmed.contains("504") -> "Remote server timed out."
            trimmed.contains("503") -> "Remote server is temporarily unavailable."
            trimmed.contains("502") -> "Remote server returned an invalid gateway response."
            trimmed.contains("429") -> "Remote server rate limit reached."
            else -> ""
        }
    }

    return trimmed
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(180)
}

internal data class OverpassStatusSummary(
    val rateLimit: Int?,
    val slotsAvailable: Int?,
    val nextSlotSeconds: Int?,
    val message: String,
)

internal fun parseOverpassStatusSummary(body: String): OverpassStatusSummary {
    val lines =
        body
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

    val rateLimit =
        lines.firstNotNullOfOrNull { line ->
            OVERPASS_RATE_LIMIT_REGEX
                .find(line)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        }
    val slotsAvailable =
        lines.firstNotNullOfOrNull { line ->
            OVERPASS_SLOTS_AVAILABLE_REGEX
                .find(line)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        }
    val nextSlotSeconds =
        lines.firstNotNullOfOrNull { line ->
            OVERPASS_SLOT_WAIT_SECONDS_REGEX
                .find(line)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: if (line.contains("slot available after", ignoreCase = true)) {
                    OVERPASS_IN_SECONDS_REGEX
                        .find(line)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                } else {
                    null
                }
        }

    val message =
        when {
            slotsAvailable != null && slotsAvailable > 0 -> {
                "$slotsAvailable slot${if (slotsAvailable == 1) "" else "s"} available now"
            }
            nextSlotSeconds != null -> "next slot in ${nextSlotSeconds}s"
            rateLimit != null -> "rate limit $rateLimit"
            else -> lines.take(2).joinToString(" | ").take(160)
        }.ifBlank { "status unavailable" }

    return OverpassStatusSummary(
        rateLimit = rateLimit,
        slotsAvailable = slotsAvailable,
        nextSlotSeconds = nextSlotSeconds,
        message = message,
    )
}
