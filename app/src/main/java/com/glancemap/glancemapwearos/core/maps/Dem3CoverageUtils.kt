package com.glancemap.glancemapwearos.core.maps

import android.content.Context
import android.util.Log
import org.mapsforge.map.reader.MapFile
import java.io.File
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

data class DemCoverageSummary(
    val requiredTiles: Int,
    val availableTiles: Int,
    val isCoverageKnown: Boolean,
) {
    val isReady: Boolean
        get() = isCoverageKnown && requiredTiles == availableTiles
}

object Dem3CoverageUtils {
    private const val TAG = "Dem3CoverageUtils"
    private const val DEM3_DIR_NAME = "dem3"
    private const val DEM_SCAN_MAX_DEPTH = 6
    private const val DEM_SIGNATURE_NONE = "DEM:NONE"
    private const val MAX_REQUIRED_TILE_CACHE_ENTRIES = 64
    private const val MAX_COVERAGE_CACHE_ENTRIES = 128

    private data class CoverageCacheKey(
        val mapSignature: String,
        val demSignature: String,
    )

    private val requiredTileIdsCache =
        LinkedHashMap<String, Set<String>>(
            MAX_REQUIRED_TILE_CACHE_ENTRIES,
            0.75f,
            true,
        )
    private val coverageCache =
        LinkedHashMap<CoverageCacheKey, DemCoverageSummary>(
            MAX_COVERAGE_CACHE_ENTRIES,
            0.75f,
            true,
        )

    fun demRootDir(context: Context): File =
        context.getExternalFilesDir(DEM3_DIR_NAME)
            ?: File(context.getDir("maps", Context.MODE_PRIVATE), DEM3_DIR_NAME)

    fun coverageForMap(
        context: Context,
        mapFile: File,
    ): DemCoverageSummary {
        val mapSignature =
            mapSignatureOf(mapFile)
                ?: return DemCoverageSummary(0, 0, isCoverageKnown = false)
        val requiredTileIds =
            requiredTileIdsForMap(mapFile, mapSignature)
                ?: return DemCoverageSummary(0, 0, isCoverageKnown = false)
        if (requiredTileIds.isEmpty()) {
            return DemCoverageSummary(0, 0, isCoverageKnown = true)
        }

        val demRoot = demRootDir(context)
        val demSignature =
            DemSignatureStore.resolveSignature(
                context = context,
                demRootDir = demRoot,
                maxDepth = DEM_SCAN_MAX_DEPTH,
            ) ?: DEM_SIGNATURE_NONE
        val cacheKey =
            CoverageCacheKey(
                mapSignature = mapSignature,
                demSignature = demSignature,
            )

        synchronized(coverageCache) {
            coverageCache[cacheKey]?.let { return it }
        }

        val available =
            if (demSignature == DEM_SIGNATURE_NONE) {
                0
            } else {
                requiredTileIds.count { tileId ->
                    tileFileCandidates(demRoot, tileId).any { it.exists() && it.isFile }
                }
            }

        return DemCoverageSummary(
            requiredTiles = requiredTileIds.size,
            availableTiles = available,
            isCoverageKnown = true,
        ).also { summary ->
            synchronized(coverageCache) {
                coverageCache[cacheKey] = summary
                coverageCache.trimTo(MAX_COVERAGE_CACHE_ENTRIES)
            }
        }
    }

    fun isReadyForMap(
        context: Context,
        mapPath: String?,
    ): Boolean {
        val file = mapPath?.takeIf { it.isNotBlank() }?.let(::File) ?: return false
        if (!file.exists() || !file.isFile) return false
        return coverageForMap(context, file).isReady
    }

    fun requiredTileIdsForMap(mapFile: File): Set<String>? {
        val mapSignature = mapSignatureOf(mapFile) ?: return null
        return requiredTileIdsForMap(mapFile, mapSignature)
    }

    fun tilesToDeleteForMap(
        deletedMapFile: File,
        remainingMapFiles: List<File>,
    ): Set<String> {
        val deletedTiles = requiredTileIdsForMap(deletedMapFile) ?: return emptySet()
        if (deletedTiles.isEmpty()) return emptySet()

        val keepTiles = linkedSetOf<String>()
        remainingMapFiles.forEach { file ->
            requiredTileIdsForMap(file)?.let { keepTiles.addAll(it) }
        }
        return deletedTiles - keepTiles
    }

    fun deleteTiles(
        context: Context,
        tileIds: Set<String>,
    ): Int {
        if (tileIds.isEmpty()) return 0

        val demRoot = demRootDir(context)
        var deletedFiles = 0
        var demChanged = false

        tileIds.forEach { tileId ->
            tileFileCandidates(demRoot, tileId)
                .toSet()
                .forEach { target ->
                    if (target.exists() && target.isFile && target.delete()) {
                        deletedFiles += 1
                        demChanged = true
                    }
                    val part = File(target.parentFile ?: demRoot, ".${target.name}.part")
                    if (part.exists() && part.delete()) {
                        demChanged = true
                    }
                }
        }

        if (demChanged) {
            DemSignatureStore.markDirty(context)
        }
        return deletedFiles
    }

    fun clearCaches() {
        synchronized(requiredTileIdsCache) {
            requiredTileIdsCache.clear()
        }
        synchronized(coverageCache) {
            coverageCache.clear()
        }
    }

    private fun requiredTileIdsForMap(
        mapFile: File,
        mapSignature: String,
    ): Set<String>? {
        synchronized(requiredTileIdsCache) {
            requiredTileIdsCache[mapSignature]?.let { return it }
        }

        val computed =
            runCatching {
                val map = MapFile(mapFile)
                val bbox =
                    try {
                        map.boundingBox()
                    } finally {
                        runCatching { map.close() }
                    }
                tilesFromBbox(
                    minLat = bbox.minLatitude,
                    minLon = bbox.minLongitude,
                    maxLat = bbox.maxLatitude,
                    maxLon = bbox.maxLongitude,
                )
            }.onFailure { error ->
                Log.w(TAG, "Failed reading map bounds for ${mapFile.absolutePath}", error)
            }.getOrNull() ?: return null

        synchronized(requiredTileIdsCache) {
            requiredTileIdsCache[mapSignature] = computed
            requiredTileIdsCache.trimTo(MAX_REQUIRED_TILE_CACHE_ENTRIES)
        }
        return computed
    }

    private fun tileFileCandidates(
        demRoot: File,
        tileId: String,
    ): List<File> {
        val upperTileId = tileId.uppercase(Locale.ROOT)
        val folder = upperTileId.substring(0, 3)
        return listOf(
            File(File(demRoot, folder), "$upperTileId.hgt.zip"),
            File(File(demRoot, folder), "$upperTileId.hgt"),
            File(demRoot, "$upperTileId.hgt.zip"),
            File(demRoot, "$upperTileId.hgt"),
        )
    }

    private fun tilesFromBbox(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
    ): Set<String> {
        val adjustedMaxLat = if (maxLat <= minLat) minLat + 1e-9 else maxLat
        val adjustedMaxLon = if (maxLon <= minLon) minLon + 1e-9 else maxLon

        val startLat = floor(minLat).toInt()
        val startLon = floor(minLon).toInt()
        val endLat = floor(Math.nextDown(adjustedMaxLat)).toInt()
        val endLon = floor(Math.nextDown(adjustedMaxLon)).toInt()

        val result = linkedSetOf<String>()
        for (lat in startLat..endLat) {
            for (lon in startLon..endLon) {
                result += tileId(lat, lon)
            }
        }
        return result
    }

    private fun tileId(
        lat: Int,
        lon: Int,
    ): String {
        val latPrefix = if (lat >= 0) "N" else "S"
        val lonPrefix = if (lon >= 0) "E" else "W"
        return String.format(
            Locale.US,
            "%s%02d%s%03d",
            latPrefix,
            abs(lat),
            lonPrefix,
            abs(lon),
        )
    }

    private fun mapSignatureOf(mapFile: File): String? {
        if (!mapFile.exists() || !mapFile.isFile) return null
        val lastModified = runCatching { mapFile.lastModified() }.getOrDefault(0L)
        val length = runCatching { mapFile.length() }.getOrDefault(0L)
        return "FILE:${mapFile.absolutePath}|$lastModified|$length"
    }

    private fun <K, V> LinkedHashMap<K, V>.trimTo(max: Int) {
        while (size > max) {
            val iterator = entries.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
    }
}
