package com.glancemap.glancemapwearos.core.routing

import android.content.Context
import android.util.Log
import org.mapsforge.map.reader.MapFile
import java.io.File
import java.util.LinkedHashMap
import kotlin.math.floor

data class RoutingCoverageSummary(
    val requiredSegments: Int,
    val availableSegments: Int,
    val isCoverageKnown: Boolean,
) {
    val isReady: Boolean
        get() = isCoverageKnown && requiredSegments == availableSegments

    val isPartial: Boolean
        get() = isCoverageKnown && availableSegments in 1 until requiredSegments
}

object RoutingCoverageUtils {
    private const val TAG = "RoutingCoverageUtils"
    private const val ROUTING_TILE_DEGREES = 5
    private const val MAX_REQUIRED_SEGMENT_CACHE_ENTRIES = 64
    private const val MAX_COVERAGE_CACHE_ENTRIES = 128

    private data class CoverageCacheKey(
        val mapSignature: String,
        val routingSignature: String,
    )

    private val requiredSegmentNamesCache =
        LinkedHashMap<String, Set<String>>(
            MAX_REQUIRED_SEGMENT_CACHE_ENTRIES,
            0.75f,
            true,
        )
    private val coverageCache =
        LinkedHashMap<CoverageCacheKey, RoutingCoverageSummary>(
            MAX_COVERAGE_CACHE_ENTRIES,
            0.75f,
            true,
        )

    fun coverageForMap(
        context: Context,
        mapFile: File,
    ): RoutingCoverageSummary {
        val mapSignature =
            mapSignatureOf(mapFile)
                ?: return RoutingCoverageSummary(0, 0, isCoverageKnown = false)
        val requiredSegmentNames =
            requiredSegmentNamesForMap(mapFile, mapSignature)
                ?: return RoutingCoverageSummary(0, 0, isCoverageKnown = false)
        if (requiredSegmentNames.isEmpty()) {
            return RoutingCoverageSummary(0, 0, isCoverageKnown = true)
        }

        val segmentDir = routingSegmentsDir(context)
        val routingSignature = routingSignatureOf(segmentDir)
        val cacheKey =
            CoverageCacheKey(
                mapSignature = mapSignature,
                routingSignature = routingSignature,
            )

        synchronized(coverageCache) {
            coverageCache[cacheKey]?.let { return it }
        }

        val installedSegments =
            segmentDir
                .listFiles()
                ?.asSequence()
                ?.filter { it.isFile && isRoutingSegmentFileName(it.name) }
                ?.map { it.name }
                ?.toSet()
                ?: emptySet()

        return RoutingCoverageSummary(
            requiredSegments = requiredSegmentNames.size,
            availableSegments = requiredSegmentNames.count { it in installedSegments },
            isCoverageKnown = true,
        ).also { summary ->
            synchronized(coverageCache) {
                coverageCache[cacheKey] = summary
                coverageCache.trimTo(MAX_COVERAGE_CACHE_ENTRIES)
            }
        }
    }

    fun clearCaches() {
        synchronized(requiredSegmentNamesCache) {
            requiredSegmentNamesCache.clear()
        }
        synchronized(coverageCache) {
            coverageCache.clear()
        }
    }

    fun requiredSegmentNamesForMapFile(mapFile: File): Set<String>? {
        val mapSignature = mapSignatureOf(mapFile) ?: return null
        return requiredSegmentNamesForMap(
            mapFile = mapFile,
            mapSignature = mapSignature,
        )
    }

    private fun requiredSegmentNamesForMap(
        mapFile: File,
        mapSignature: String,
    ): Set<String>? {
        synchronized(requiredSegmentNamesCache) {
            requiredSegmentNamesCache[mapSignature]?.let { return it }
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

                segmentNamesFromBbox(
                    minLat = bbox.minLatitude,
                    minLon = bbox.minLongitude,
                    maxLat = bbox.maxLatitude,
                    maxLon = bbox.maxLongitude,
                )
            }.onFailure { error ->
                Log.w(TAG, "Failed reading map bounds for ${mapFile.absolutePath}", error)
            }.getOrNull() ?: return null

        synchronized(requiredSegmentNamesCache) {
            requiredSegmentNamesCache[mapSignature] = computed
            requiredSegmentNamesCache.trimTo(MAX_REQUIRED_SEGMENT_CACHE_ENTRIES)
        }
        return computed
    }

    private fun segmentNamesFromBbox(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
    ): Set<String> {
        val adjustedMaxLat = if (maxLat <= minLat) minLat + 1e-9 else maxLat
        val adjustedMaxLon = if (maxLon <= minLon) minLon + 1e-9 else maxLon

        val latStart = routingTileOrigin(minLat)
        val lonStart = routingTileOrigin(minLon)
        val latEnd = routingTileOrigin(Math.nextDown(adjustedMaxLat))
        val lonEnd = routingTileOrigin(Math.nextDown(adjustedMaxLon))

        val result = linkedSetOf<String>()
        var lat = latStart
        while (lat <= latEnd) {
            var lon = lonStart
            while (lon <= lonEnd) {
                result += routingTileFileName(swLat = lat, swLon = lon)
                lon += ROUTING_TILE_DEGREES
            }
            lat += ROUTING_TILE_DEGREES
        }
        return result
    }

    private fun routingTileOrigin(coordinate: Double): Int = floor(coordinate / ROUTING_TILE_DEGREES.toDouble()).toInt() * ROUTING_TILE_DEGREES

    private fun routingTileFileName(
        swLat: Int,
        swLon: Int,
    ): String =
        "${formatRoutingTileCoord(swLon, positivePrefix = 'E', negativePrefix = 'W')}" +
            "_${formatRoutingTileCoord(swLat, positivePrefix = 'N', negativePrefix = 'S')}.rd5"

    private fun formatRoutingTileCoord(
        value: Int,
        positivePrefix: Char,
        negativePrefix: Char,
    ): String {
        val prefix = if (value < 0) negativePrefix else positivePrefix
        return "$prefix${kotlin.math.abs(value)}"
    }

    private fun routingSignatureOf(segmentDir: File): String {
        if (!segmentDir.exists() || !segmentDir.isDirectory) return "ROUTING:NONE"
        val parts =
            segmentDir
                .listFiles()
                ?.asSequence()
                ?.filter { it.isFile && isRoutingSegmentFileName(it.name) }
                ?.sortedBy { it.name }
                ?.map { "${it.name}|${it.length()}|${it.lastModified()}" }
                ?.toList()
                ?: emptyList()
        if (parts.isEmpty()) return "ROUTING:NONE"
        return "ROUTING:${parts.joinToString(";")}"
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
