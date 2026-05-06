package com.glancemap.glancemapcompanionapp

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.glancemap.glancemapcompanionapp.refuges.GpxWaypointPoiImporter
import com.glancemap.glancemapcompanionapp.refuges.RefugesGeoJsonPoiImporter
import java.io.ByteArrayOutputStream
import java.util.ArrayList

internal data class PreparedUriSelection(
    val uris: List<Uri>,
    val convertedGeoJsonCount: Int,
    val extractedPoiFromGpxCount: Int,
    val extractedPoiFromMixedGpxCount: Int,
)

internal suspend fun prepareSelectedUrisForTransfer(
    context: Context,
    uris: List<Uri>,
    refugesImporter: RefugesGeoJsonPoiImporter,
    gpxWaypointPoiImporter: GpxWaypointPoiImporter,
    maxGeoJsonImportBytes: Long,
    maxGpxWaypointImportBytes: Long,
): PreparedUriSelection {
    if (uris.isEmpty()) {
        return PreparedUriSelection(
            uris = emptyList(),
            convertedGeoJsonCount = 0,
            extractedPoiFromGpxCount = 0,
            extractedPoiFromMixedGpxCount = 0,
        )
    }

    val prepared = ArrayList<Uri>(uris.size)
    var convertedGeoJsonCount = 0
    var extractedPoiFromGpxCount = 0
    var extractedPoiFromMixedGpxCount = 0

    uris.forEach { uri ->
        if (isGeoJsonUri(context, uri)) {
            val geoJsonSize = queryUriSize(context, uri)
            if (geoJsonSize != null && geoJsonSize > maxGeoJsonImportBytes) {
                throw IllegalArgumentException(
                    "GeoJSON is too large (${geoJsonSize / (1024 * 1024)}MB). Please use a smaller file.",
                )
            }
            val geoJsonText = readGeoJsonTextWithLimit(context, uri, maxGeoJsonImportBytes)
            val poiFileName = suggestPoiFileName(context, uri)
            val result =
                refugesImporter.importFromGeoJsonText(
                    geoJsonText = geoJsonText,
                    fileNameInput = poiFileName,
                )
            prepared += result.poiUri
            convertedGeoJsonCount += 1
        } else if (isGpxUri(context, uri)) {
            val gpxOutcome =
                runCatching {
                    val gpxSize = queryUriSize(context, uri)
                    if (gpxSize != null && gpxSize > maxGpxWaypointImportBytes) {
                        return@runCatching null
                    }
                    val gpxText = readGpxTextWithLimit(context, uri, maxGpxWaypointImportBytes)
                    gpxWaypointPoiImporter.importWaypointsFromGpxText(
                        gpxText = gpxText,
                        fileNameInput = suggestPoiFileNameForGpxWaypoints(context, uri),
                        categoryNameInput = suggestPoiCategoryNameForGpx(context, uri),
                    )
                }.getOrElse {
                    Log.w("FileTransferVM", "Failed GPX waypoint extraction for $uri", it)
                    null
                }
            val poiResult = gpxOutcome?.poiResult
            if (poiResult == null) {
                prepared += uri
            } else {
                if (gpxOutcome.hasTrackOrRoutePoints) {
                    prepared += uri
                    extractedPoiFromMixedGpxCount += 1
                }
                prepared += poiResult.poiUri
                extractedPoiFromGpxCount += 1
            }
        } else {
            prepared += uri
        }
    }

    return PreparedUriSelection(
        uris = prepared.distinctBy { it.toString() },
        convertedGeoJsonCount = convertedGeoJsonCount,
        extractedPoiFromGpxCount = extractedPoiFromGpxCount,
        extractedPoiFromMixedGpxCount = extractedPoiFromMixedGpxCount,
    )
}

internal fun buildOsmEnrichTempFileName(basePoiFileName: String): String {
    val base =
        basePoiFileName
            .removeSuffix(".poi")
            .ifBlank { "refuges-info" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "refuges-info" }
    return "${base}__osm_enrich.poi"
}

internal fun suggestPoiFileName(
    context: Context,
    uri: Uri,
): String {
    val raw = resolveUriDisplayName(context, uri)

    val safeBase =
        raw
            .ifBlank { "refuges-local" }
            .replace("\\", "_")
            .replace("/", "_")
            .replace(Regex("\\.[A-Za-z0-9]{1,8}$"), "")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "refuges-local" }

    return "$safeBase.poi"
}

internal fun isGeoJsonUri(
    context: Context,
    uri: Uri,
): Boolean {
    val name = resolveUriDisplayName(context, uri)
    if (isGeoJsonFileName(name)) return true

    val mimeType = context.contentResolver.getType(uri).orEmpty().lowercase()
    return mimeType == "application/geo+json" ||
        mimeType == "application/vnd.geo+json" ||
        (mimeType == "application/json" && name.lowercase().endsWith(".json"))
}

internal fun isGeoJsonFileName(fileName: String): Boolean {
    val lower = fileName.lowercase()
    return lower.endsWith(".geojson") || lower.endsWith(".geo.json")
}

internal fun isGpxUri(
    context: Context,
    uri: Uri,
): Boolean {
    val name = resolveUriDisplayName(context, uri)
    return name.lowercase().endsWith(".gpx")
}

internal fun suggestPoiFileNameForGpxWaypoints(
    context: Context,
    uri: Uri,
): String {
    val base =
        resolveUriDisplayName(context, uri)
            .trim()
            .replace("\\", "_")
            .replace("/", "_")
            .replace(Regex("\\.gpx$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "gpx-waypoints" }
    return "${base}__waypoints.poi"
}

internal fun suggestPoiCategoryNameForGpx(
    context: Context,
    uri: Uri,
): String =
    resolveUriDisplayName(context, uri)
        .trim()
        .replace(Regex("\\.gpx$", RegexOption.IGNORE_CASE), "")
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "Waypoints" }

internal fun resolveUriDisplayName(
    context: Context,
    uri: Uri,
): String {
    val displayName =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx < 0) null else cursor.getString(idx)
            }
        }.getOrNull()

    return displayName
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.trim()
            .orEmpty()
}

internal fun queryUriSize(
    context: Context,
    uri: Uri,
): Long? {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx < 0 || cursor.isNull(idx)) null else cursor.getLong(idx)
        }
    }.getOrNull()
}

internal fun readGeoJsonTextWithLimit(
    context: Context,
    uri: Uri,
    maxBytes: Long,
): String =
    readTextWithLimit(
        context = context,
        uri = uri,
        maxBytes = maxBytes,
        kindLabel = "GeoJSON",
    )

internal fun readGpxTextWithLimit(
    context: Context,
    uri: Uri,
    maxBytes: Long,
): String =
    readTextWithLimit(
        context = context,
        uri = uri,
        maxBytes = maxBytes,
        kindLabel = "GPX",
    )

internal fun readTextWithLimit(
    context: Context,
    uri: Uri,
    maxBytes: Long,
    kindLabel: String,
): String {
    val safeMax = maxBytes.coerceAtLeast(1L)
    val input =
        context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot read selected $kindLabel file.")
    input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read.toLong()
            if (total > safeMax) {
                throw IllegalArgumentException(
                    "$kindLabel is too large (${total / (1024 * 1024)}MB). Please use a smaller file.",
                )
            }
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
