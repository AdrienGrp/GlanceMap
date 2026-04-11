package com.glancemap.glancemapcompanionapp.refuges

import org.json.JSONObject
import java.io.InputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

private val REFUGES_INTEGER_REGEX = Regex("-?\\d+")
private const val REFUGES_API_TYPE_POINTS_ALL = "all"
private const val REFUGES_MAX_SHORT_DESCRIPTION_CHARS = 420
private const val REFUGES_MAX_BBOX_AREA_DEGREES = 120.0
private const val REFUGES_MAX_LON_SPAN_DEGREES = 20.0
private const val REFUGES_MAX_LAT_SPAN_DEGREES = 12.0

internal fun buildTagData(point: ResolvedPoint): String {
    val tags = linkedMapOf<String, String>()
    tags["name"] = point.name
    tags[point.poiKey] = point.poiValue
    tags["source"] = "refuges.info"
    point.sourceId?.let { tags["refuges_info:id"] = it.toString() }
    if (point.typeLabel.isNotBlank()) tags["refuges_info:type"] = point.typeLabel
    if (point.typeSym.isNotBlank()) tags["refuges_info:sym"] = point.typeSym
    if (point.icon.isNotBlank()) tags["refuges_info:icon"] = point.icon
    if (!point.website.isNullOrBlank()) tags["website"] = point.website
    point.elevation?.takeIf { it > 0 }?.let { tags["ele"] = it.toString() }
    point.sleepingPlaces?.takeIf { it >= 0 }?.let {
        tags["capacity"] = it.toString()
        tags["refuges_info:places"] = it.toString()
    }
    point.state?.takeIf { it.isNotBlank() }?.let { tags["refuges_info:state"] = it }
    point.shortDescription
        ?.takeIf { it.isNotBlank() }
        ?.let { tags["refuges_info:description"] = it }

    return tags.entries.joinToString("\r") { (k, v) ->
        "${sanitizeTagKey(k)}=${sanitizeTagValue(v)}"
    }
}

internal fun defaultCategoryName(
    key: String,
    value: String,
): String =
    when ("$key=$value") {
        "tourism=alpine_hut" -> "Alpine Huts"
        "tourism=camp_site" -> "Camping"
        "natural=peak" -> "Peaks"
        "amenity=drinking_water" -> "Water"
        "tourism=viewpoint" -> "Viewpoints"
        "amenity=toilets" -> "Toilets"
        "amenity=parking" -> "Parking"
        "amenity=restaurant" -> "Food"
        else -> "Other"
    }

internal fun sanitizeTagKey(key: String): String =
    key
        .trim()
        .replace(Regex("[^A-Za-z0-9:_-]"), "_")
        .ifBlank { "tag" }

internal fun sanitizeTagValue(value: String): String =
    value
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace('\u0000', ' ')
        .trim()

internal fun parseShortDescription(props: JSONObject): String? {
    val raw =
        parseStringValue(
            props.opt("description_courte"),
            props.opt("short_description"),
            props.opt("description_short"),
            props.opt("resume"),
            props.opt("description"),
            props.opt("desc"),
            props.opt("remarque"),
            props.opt("commentaire"),
            props.opt("comment"),
            props.opt("acces"),
            props.opt("infos_comp"),
            parseObjectStringField(props.opt("description"), "texte"),
            parseObjectStringField(props.opt("description"), "text"),
            parseObjectStringField(props.opt("description"), "description"),
            parseObjectStringField(props.opt("description"), "resume"),
            parseObjectStringField(props.opt("resume"), "texte"),
            parseObjectStringField(props.opt("resume"), "text"),
            parseObjectStringField(props.opt("acces"), "texte"),
            parseObjectStringField(props.opt("remarque"), "texte"),
            parseObjectStringField(props.opt("infos_comp"), "texte"),
        ) ?: return null

    return sanitizeShortDescription(raw)
}

internal fun sanitizeShortDescription(raw: String): String? {
    val cleaned =
        raw
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("(?i)<p[^>]*>"), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(REFUGES_MAX_SHORT_DESCRIPTION_CHARS)
    return cleaned.takeIf { it.isNotBlank() }
}

internal fun parseObjectLongField(
    raw: Any?,
    key: String,
): Long? {
    val obj = raw as? JSONObject ?: return null
    return parseLongValue(obj.opt(key))
}

internal fun parseObjectStringField(
    raw: Any?,
    key: String,
): String? {
    val obj = raw as? JSONObject ?: return null
    return parseStringValue(obj.opt(key))
}

internal fun parseStringValue(vararg values: Any?): String? {
    values.forEach { value ->
        when (value) {
            is String -> {
                val cleaned = value.trim()
                if (cleaned.isNotBlank()) return cleaned
            }
            is Number -> {
                return value.toString()
            }
            is JSONObject -> {
                val nested =
                    firstNonBlank(
                        value.optString("valeur", ""),
                        value.optString("value", ""),
                        value.optString("name", ""),
                        value.optString("label", ""),
                    )
                if (nested.isNotBlank()) return nested
            }
        }
    }
    return null
}

internal fun parseLongValue(vararg values: Any?): Long? {
    values.forEach { value ->
        when (value) {
            is Number -> return value.toLong()
            is String -> {
                val cleaned = value.trim()
                cleaned.toLongOrNull()?.let { return it }
                cleaned.toDoubleOrNull()?.let { return it.toLong() }
                REFUGES_INTEGER_REGEX
                    .find(cleaned)
                    ?.value
                    ?.toLongOrNull()
                    ?.let { return it }
            }
        }
    }
    return null
}

internal fun normalizeFileName(input: String): String {
    val base =
        input
            .trim()
            .ifBlank { "refuges-info.poi" }
            .replace("\\", "_")
            .replace("/", "_")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "refuges-info.poi" }
    return if (base.lowercase(Locale.ROOT).endsWith(".poi")) base else "$base.poi"
}

internal fun parseStoredTypePointIds(raw: String?): Set<Int> {
    val value = raw?.trim().orEmpty()
    if (
        value.isBlank() ||
        value.equals(REFUGES_API_TYPE_POINTS_ALL, ignoreCase = true) ||
        value.equals("tous", ignoreCase = true)
    ) {
        return RefugesGeoJsonPoiImporter.defaultPointTypeIds()
    }
    val parsed =
        value
            .split(',')
            .asSequence()
            .map { it.trim().toIntOrNull() }
            .filterNotNull()
            .filter { it in RefugesGeoJsonPoiImporter.defaultPointTypeIds() }
            .toSet()
    return if (parsed.isEmpty()) RefugesGeoJsonPoiImporter.defaultPointTypeIds() else parsed
}

internal fun normalizeTypePointIds(typePointIds: Set<Int>): Set<Int> {
    if (typePointIds.isEmpty()) return RefugesGeoJsonPoiImporter.defaultPointTypeIds()
    val normalized =
        typePointIds
            .filter { it in RefugesGeoJsonPoiImporter.defaultPointTypeIds() }
            .toSet()
    return if (normalized.isEmpty()) RefugesGeoJsonPoiImporter.defaultPointTypeIds() else normalized
}

internal fun toTypePointQueryValue(typePointIds: Set<Int>): String {
    val normalized = normalizeTypePointIds(typePointIds)
    return if (normalized.size == RefugesGeoJsonPoiImporter.defaultPointTypeIds().size) {
        REFUGES_API_TYPE_POINTS_ALL
    } else {
        normalized.sorted().joinToString(",")
    }
}

internal fun parseBbox(input: String): BBox {
    val normalized = input.trim()
    val parts =
        normalized
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    if (parts.size != 4) {
        throw IllegalArgumentException("BBox must be: minLon,minLat,maxLon,maxLat")
    }
    val minLon =
        parts[0].toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid minLon in bbox.")
    val minLat =
        parts[1].toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid minLat in bbox.")
    val maxLon =
        parts[2].toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid maxLon in bbox.")
    val maxLat =
        parts[3].toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid maxLat in bbox.")
    if (minLon >= maxLon || minLat >= maxLat) {
        throw IllegalArgumentException("BBox min values must be smaller than max values.")
    }
    if (minLon < -180.0 || maxLon > 180.0 || minLat < -90.0 || maxLat > 90.0) {
        throw IllegalArgumentException("BBox coordinates are out of valid range.")
    }

    val lonSpan = maxLon - minLon
    val latSpan = maxLat - minLat
    val area = lonSpan * latSpan
    if (lonSpan > REFUGES_MAX_LON_SPAN_DEGREES ||
        latSpan > REFUGES_MAX_LAT_SPAN_DEGREES ||
        area > REFUGES_MAX_BBOX_AREA_DEGREES
    ) {
        throw IllegalArgumentException(
            "Selected area is too large. Please choose a smaller region.",
        )
    }

    return BBox(minLon = minLon, minLat = minLat, maxLon = maxLon, maxLat = maxLat)
}

internal fun readResponseText(
    stream: InputStream?,
    maxBytes: Int,
): String {
    if (stream == null) return ""
    stream.use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val out = ByteArray(maxBytes.coerceAtLeast(1))
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (total + read > maxBytes) {
                throw IllegalStateException(
                    "Refuges response is too large. Please use a smaller area.",
                )
            }
            System.arraycopy(buffer, 0, out, total, read)
            total += read
        }
        return String(out, 0, total, Charsets.UTF_8)
    }
}

internal fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

internal fun firstNonBlank(vararg values: String): String = values.firstOrNull { it.isNotBlank() }?.trim().orEmpty()

internal data class TypeDescriptor(
    val id: Int?,
    val label: String,
    val sym: String,
)

internal data class ParsedPoint(
    val sourceId: Long?,
    val lat: Double,
    val lon: Double,
    val name: String,
    val typeId: Int?,
    val typeLabel: String,
    val typeSym: String,
    val icon: String,
    val website: String?,
    val elevation: Int?,
    val sleepingPlaces: Int?,
    val state: String?,
    val shortDescription: String?,
    val poiKey: String = "",
    val poiValue: String = "",
)

internal data class ResolvedPoint(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val name: String,
    val typeLabel: String,
    val typeSym: String,
    val icon: String,
    val website: String?,
    val elevation: Int?,
    val sleepingPlaces: Int?,
    val state: String?,
    val shortDescription: String?,
    val poiKey: String,
    val poiValue: String,
    val categoryId: Int,
    val sourceId: Long?,
)

internal data class BBox(
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double,
) {
    fun asQueryParam(): String = "$minLon,$minLat,$maxLon,$maxLat"
}
