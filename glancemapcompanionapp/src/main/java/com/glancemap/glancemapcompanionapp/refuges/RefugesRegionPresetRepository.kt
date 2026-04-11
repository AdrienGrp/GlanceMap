package com.glancemap.glancemapcompanionapp.refuges

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class RefugesRegionPreset(
    val label: String,
    val bbox: String
)

enum class RefugesRegionPresetMode {
    COMPACT_ZONES,
    DETAILED_MASSIFS
}

class RefugesRegionPresetRepository(
    private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "refuges_region_presets"
        private const val KEY_CACHE_JSON_PREFIX = "cache_json_"
        private const val KEY_CACHE_UPDATED_AT_MS_PREFIX = "cache_updated_at_ms_"
        private const val CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L // 7 days

        private const val API_ENDPOINT = "https://www.refuges.info/api/polygones"
        private const val MAX_RESPONSE_BYTES = 3_000_000
        private const val MAX_LON_SPAN_DEGREES = 20.0
        private const val MAX_LAT_SPAN_DEGREES = 12.0
        private const val MAX_BBOX_AREA_DEGREES = 120.0

        fun defaultPresets(): List<RefugesRegionPreset> = listOf(
            RefugesRegionPreset("Custom", ""),
            RefugesRegionPreset("Andorra", "1.40,42.43,1.79,42.66"),
            RefugesRegionPreset("Pyrenees East", "1.20,42.35,3.20,43.05"),
            RefugesRegionPreset("Pyrenees West", "-2.30,42.60,0.40,43.35"),
            RefugesRegionPreset("Alps West (FR/IT)", "5.30,44.80,8.30,46.70"),
            RefugesRegionPreset("Alps North (CH/DE/AT)", "7.60,46.40,12.40,48.60"),
            RefugesRegionPreset("Alps East (AT/SI)", "12.10,45.80,16.40,48.40"),
            RefugesRegionPreset("Dolomites", "10.80,45.80,12.60,47.10"),
            RefugesRegionPreset("Jura", "5.20,45.70,7.20,47.80"),
            RefugesRegionPreset("Vosges", "6.60,47.40,7.60,49.10"),
            RefugesRegionPreset("Corsica", "8.45,41.30,9.60,43.10"),
            RefugesRegionPreset("Sierra Nevada", "-3.65,36.80,-2.65,37.35")
        )
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    suspend fun loadPresets(
        forceRefresh: Boolean = false,
        mode: RefugesRegionPresetMode = RefugesRegionPresetMode.COMPACT_ZONES
    ): List<RefugesRegionPreset> = withContext(Dispatchers.IO) {
        val cached = readCachedPresets(mode)
        val now = System.currentTimeMillis()
        val isCacheFresh = cached != null && (now - cached.updatedAtMs) <= CACHE_TTL_MS

        if (!forceRefresh && isCacheFresh) {
            return@withContext cached.presets
        }

        val remote = runCatching { fetchRemotePresets(mode) }.getOrNull()
        if (!remote.isNullOrEmpty()) {
            saveCachedPresets(mode, remote, now)
            return@withContext remote
        }

        return@withContext cached?.presets?.takeIf { it.isNotEmpty() } ?: defaultPresets()
    }

    private fun fetchRemotePresets(mode: RefugesRegionPresetMode): List<RefugesRegionPreset> {
        val candidates = when (mode) {
            RefugesRegionPresetMode.COMPACT_ZONES -> listOf(
                "$API_ENDPOINT?type_polygon=11&format=geojson",
                "$API_ENDPOINT?type_polygon=11"
            )
            RefugesRegionPresetMode.DETAILED_MASSIFS -> listOf(
                "$API_ENDPOINT?type_polygon=1&format=geojson",
                "$API_ENDPOINT?type_polygon=1"
            )
        }

        candidates.forEach { endpoint ->
            val parsed = runCatching { fetchAndParse(endpoint) }.getOrNull()
            if (!parsed.isNullOrEmpty()) return parsed
        }

        throw IllegalStateException("No Refuges presets available from API.")
    }

    private fun fetchAndParse(endpoint: String): List<RefugesRegionPreset> {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/geo+json,application/json")
            setRequestProperty("User-Agent", "GlanceMap-Companion")
        }

        try {
            val status = connection.responseCode
            if (status !in 200..299) return emptyList()

            val body = readResponseText(connection.inputStream, MAX_RESPONSE_BYTES)
            return parsePresets(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parsePresets(body: String): List<RefugesRegionPreset> {
        val trimmed = body.trim()
        if (trimmed.isBlank()) return emptyList()

        val rawPresets = when {
            trimmed.startsWith("{") -> parsePresetsFromObject(JSONObject(trimmed))
            trimmed.startsWith("[") -> parsePresetsFromArray(JSONArray(trimmed))
            else -> emptyList()
        }

        val normalized = rawPresets
            .mapNotNull { normalizePreset(it.label, it.bbox) }
            .distinctBy { it.bbox }
            .sortedBy { it.label.lowercase(Locale.ROOT) }

        return if (normalized.isEmpty()) {
            emptyList()
        } else {
            listOf(RefugesRegionPreset("Custom", "")) + normalized
        }
    }

    private fun parsePresetsFromObject(root: JSONObject): List<RawPreset> {
        val groups = listOfNotNull(
            root.optJSONArray("features"),
            root.optJSONArray("polygones"),
            root.optJSONArray("polygons"),
            root.optJSONArray("massifs"),
            root.optJSONArray("results")
        )

        if (groups.isNotEmpty()) {
            return groups.flatMap { parsePresetsFromArray(it) }
        }

        val one = parsePresetFromNode(root)
        return if (one == null) emptyList() else listOf(one)
    }

    private fun parsePresetsFromArray(array: JSONArray): List<RawPreset> {
        val items = mutableListOf<RawPreset>()
        for (i in 0 until array.length()) {
            val node = array.optJSONObject(i) ?: continue
            val parsed = parsePresetFromNode(node) ?: continue
            items += parsed
        }
        return items
    }

    private fun parsePresetFromNode(node: JSONObject): RawPreset? {
        val properties = node.optJSONObject("properties")

        val label = firstNonBlank(
            properties?.optString("nom", "").orEmpty(),
            properties?.optString("name", "").orEmpty(),
            properties?.optString("titre", "").orEmpty(),
            properties?.optString("title", "").orEmpty(),
            properties?.optString("massif", "").orEmpty(),
            properties?.optString("libelle", "").orEmpty(),
            node.optString("nom", ""),
            node.optString("name", ""),
            node.optString("titre", ""),
            node.optString("title", "")
        )
        if (label.isBlank()) return null

        val bbox = parseBBoxValue(node.opt("bbox"))
            ?: parseBBoxValue(properties?.opt("bbox"))
            ?: parseBBoxFromFields(node)
            ?: parseBBoxFromFields(properties)
            ?: computeBBoxFromGeometry(node.optJSONObject("geometry"))
            ?: computeBBoxFromGeometry(properties?.optJSONObject("geometry"))
            ?: return null

        return RawPreset(label = label, bbox = bbox)
    }

    private fun parseBBoxValue(raw: Any?): String? {
        return when (raw) {
            is String -> normalizeBboxString(raw)
            is JSONArray -> {
                if (raw.length() < 4) return null
                val minLon = raw.optDouble(0, Double.NaN)
                val minLat = raw.optDouble(1, Double.NaN)
                val maxLon = raw.optDouble(2, Double.NaN)
                val maxLat = raw.optDouble(3, Double.NaN)
                formatBbox(minLon, minLat, maxLon, maxLat)
            }
            else -> null
        }
    }

    private fun parseBBoxFromFields(node: JSONObject?): String? {
        if (node == null) return null
        val minLon = extractDouble(node, "minLon", "min_lon", "lon_min", "xmin", "west")
        val minLat = extractDouble(node, "minLat", "min_lat", "lat_min", "ymin", "south")
        val maxLon = extractDouble(node, "maxLon", "max_lon", "lon_max", "xmax", "east")
        val maxLat = extractDouble(node, "maxLat", "max_lat", "lat_max", "ymax", "north")
        return formatBbox(minLon, minLat, maxLon, maxLat)
    }

    private fun computeBBoxFromGeometry(geometry: JSONObject?): String? {
        val coordinates = geometry?.optJSONArray("coordinates") ?: return null
        val bounds = MutableBounds()
        scanCoordinates(coordinates, bounds)
        if (!bounds.hasValues) return null
        return formatBbox(bounds.minLon, bounds.minLat, bounds.maxLon, bounds.maxLat)
    }

    private fun scanCoordinates(node: Any?, bounds: MutableBounds) {
        when (node) {
            is JSONArray -> {
                if (node.length() >= 2) {
                    val lon = node.optDouble(0, Double.NaN)
                    val lat = node.optDouble(1, Double.NaN)
                    if (lon.isFinite() && lat.isFinite() && abs(lon) <= 180.0 && abs(lat) <= 90.0) {
                        bounds.add(lon, lat)
                        return
                    }
                }
                for (i in 0 until node.length()) {
                    scanCoordinates(node.opt(i), bounds)
                }
            }
        }
    }

    private fun formatBbox(
        minLon: Double,
        minLat: Double,
        maxLon: Double,
        maxLat: Double
    ): String? {
        if (!minLon.isFinite() || !minLat.isFinite() || !maxLon.isFinite() || !maxLat.isFinite()) return null
        if (minLon >= maxLon || minLat >= maxLat) return null
        if (minLon < -180.0 || maxLon > 180.0 || minLat < -90.0 || maxLat > 90.0) return null

        val lonSpan = maxLon - minLon
        val latSpan = maxLat - minLat
        val area = lonSpan * latSpan
        if (lonSpan > MAX_LON_SPAN_DEGREES || latSpan > MAX_LAT_SPAN_DEGREES || area > MAX_BBOX_AREA_DEGREES) {
            return null
        }

        return String.format(
            Locale.US,
            "%.6f,%.6f,%.6f,%.6f",
            minLon, minLat, maxLon, maxLat
        )
    }

    private fun normalizeBboxString(value: String): String? {
        val parts = value.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size != 4) return null
        val minLon = parts[0].toDoubleOrNull() ?: return null
        val minLat = parts[1].toDoubleOrNull() ?: return null
        val maxLon = parts[2].toDoubleOrNull() ?: return null
        val maxLat = parts[3].toDoubleOrNull() ?: return null
        return formatBbox(minLon, minLat, maxLon, maxLat)
    }

    private fun normalizePreset(label: String, bbox: String): RefugesRegionPreset? {
        val safeLabel = label.trim().ifBlank { return null }
        val safeBbox = normalizeBboxString(bbox) ?: return null
        if (safeLabel.equals("custom", ignoreCase = true)) return null
        return RefugesRegionPreset(label = safeLabel, bbox = safeBbox)
    }

    private fun extractDouble(node: JSONObject, vararg keys: String): Double {
        for (key in keys) {
            if (!node.has(key)) continue
            val raw = node.opt(key)
            val value = when (raw) {
                is Number -> raw.toDouble()
                is String -> raw.trim().toDoubleOrNull()
                else -> null
            }
            if (value != null && value.isFinite()) return value
        }
        return Double.NaN
    }

    private fun firstNonBlank(vararg values: String): String {
        return values.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

    private fun readResponseText(stream: java.io.InputStream?, maxBytes: Int): String {
        if (stream == null) return ""
        stream.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            val output = ByteArray(max(maxBytes, 1))
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (total + read > maxBytes) break
                System.arraycopy(buffer, 0, output, total, read)
                total += read
            }
            return String(output, 0, total, Charsets.UTF_8)
        }
    }

    private fun readCachedPresets(mode: RefugesRegionPresetMode): CachedPresets? {
        val rawJson = prefs.getString(cacheJsonKey(mode), null)?.trim().orEmpty()
        if (rawJson.isBlank()) return null
        val array = runCatching { JSONArray(rawJson) }.getOrNull() ?: return null
        val items = mutableListOf<RefugesRegionPreset>()
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val label = row.optString("label", "").trim()
            val bbox = row.optString("bbox", "").trim()
            val preset = normalizePreset(label, bbox) ?: continue
            items += preset
        }
        val list = if (items.isEmpty()) defaultPresets() else listOf(RefugesRegionPreset("Custom", "")) + items
        val updatedAt = prefs.getLong(cacheUpdatedAtKey(mode), 0L)
        return CachedPresets(presets = list, updatedAtMs = max(updatedAt, 0L))
    }

    private fun saveCachedPresets(
        mode: RefugesRegionPresetMode,
        presets: List<RefugesRegionPreset>,
        nowMs: Long
    ) {
        val items = presets
            .filter { it.bbox.isNotBlank() }
            .distinctBy { it.bbox }
            .sortedBy { it.label.lowercase(Locale.ROOT) }

        val json = JSONArray()
        items.forEach { preset ->
            json.put(
                JSONObject()
                    .put("label", preset.label)
                    .put("bbox", preset.bbox)
            )
        }

        prefs.edit()
            .putString(cacheJsonKey(mode), json.toString())
            .putLong(cacheUpdatedAtKey(mode), nowMs)
            .apply()
    }

    private fun cacheJsonKey(mode: RefugesRegionPresetMode): String {
        return KEY_CACHE_JSON_PREFIX + cacheSuffix(mode)
    }

    private fun cacheUpdatedAtKey(mode: RefugesRegionPresetMode): String {
        return KEY_CACHE_UPDATED_AT_MS_PREFIX + cacheSuffix(mode)
    }

    private fun cacheSuffix(mode: RefugesRegionPresetMode): String {
        return when (mode) {
            RefugesRegionPresetMode.COMPACT_ZONES -> "compact_zones"
            RefugesRegionPresetMode.DETAILED_MASSIFS -> "detailed_massifs"
        }
    }

    private data class RawPreset(
        val label: String,
        val bbox: String
    )

    private data class CachedPresets(
        val presets: List<RefugesRegionPreset>,
        val updatedAtMs: Long
    )

    private class MutableBounds {
        var minLon: Double = Double.POSITIVE_INFINITY
            private set
        var minLat: Double = Double.POSITIVE_INFINITY
            private set
        var maxLon: Double = Double.NEGATIVE_INFINITY
            private set
        var maxLat: Double = Double.NEGATIVE_INFINITY
            private set
        var hasValues: Boolean = false
            private set

        fun add(lon: Double, lat: Double) {
            minLon = min(minLon, lon)
            minLat = min(minLat, lat)
            maxLon = max(maxLon, lon)
            maxLat = max(maxLat, lat)
            hasValues = true
        }
    }
}
