package com.glancemap.glancemapwearos.presentation.features.maps

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.LinkedHashMap
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

internal class ReliefDemRepository(
    private val demRootDir: File,
    private val tag: String
) {
    companion object {
        private const val MIN_DEM_TILE_CACHE_ENTRIES = 12
        private const val MAX_DEM_TILE_CACHE_ENTRIES = 36
    }

    private val maxDemTileCacheEntries: Int = computeMaxDemTileCacheEntries()
    private val demTileCache = object : LinkedHashMap<String, DemTileData?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DemTileData?>?): Boolean {
            return size > maxDemTileCacheEntries
        }
    }

    fun loadDemTileFor(lat: Double, lon: Double): DemTileData? {
        return loadDemTile(floor(lat).toInt(), floor(lon).toInt())
    }

    fun elevationAt(lat: Double, lon: Double): Double? {
        val latTile = floor(lat).toInt()
        val lonTile = floor(lon).toInt()
        val tile = loadDemTile(latTile, lonTile) ?: return null

        val axisLen = tile.axisLen.coerceAtLeast(1)
        val rowLen = tile.rowLen

        val fracLat = (lat - latTile.toDouble()).coerceIn(0.0, 1.0)
        val fracLon = (lon - lonTile.toDouble()).coerceIn(0.0, 1.0)

        val rowF = (1.0 - fracLat) * axisLen
        val colF = fracLon * axisLen

        val r0 = floor(rowF).toInt().coerceIn(0, axisLen)
        val c0 = floor(colF).toInt().coerceIn(0, axisLen)
        val r1 = min(axisLen, r0 + 1)
        val c1 = min(axisLen, c0 + 1)

        val t = (rowF - r0).coerceIn(0.0, 1.0)
        val u = (colF - c0).coerceIn(0.0, 1.0)

        val z00 = tile.samples[r0 * rowLen + c0].toDouble()
        val z01 = tile.samples[r0 * rowLen + c1].toDouble()
        val z10 = tile.samples[r1 * rowLen + c0].toDouble()
        val z11 = tile.samples[r1 * rowLen + c1].toDouble()

        val top = z00 + (z01 - z00) * u
        val bottom = z10 + (z11 - z10) * u
        return top + (bottom - top) * t
    }

    fun clear() {
        synchronized(demTileCache) {
            demTileCache.clear()
        }
    }

    private fun loadDemTile(latTile: Int, lonTile: Int): DemTileData? {
        val tileId = tileId(latTile, lonTile)
        synchronized(demTileCache) {
            if (demTileCache.containsKey(tileId)) {
                return demTileCache[tileId]
            }
        }

        val loaded = runCatching {
            val file = resolveDemFile(tileId) ?: return@runCatching null
            val bytes = readDemBytes(file) ?: return@runCatching null
            decodeDemBytes(bytes)
        }.onFailure { error ->
            Log.w(tag, "Failed to load DEM tile $tileId", error)
        }.getOrNull()

        synchronized(demTileCache) {
            demTileCache[tileId] = loaded
        }
        return loaded
    }

    private fun resolveDemFile(tileId: String): File? {
        val folder = tileId.substring(0, 3)
        val preferred = listOf(
            File(File(demRootDir, folder), "$tileId.hgt.zip"),
            File(File(demRootDir, folder), "$tileId.hgt"),
            File(demRootDir, "$tileId.hgt.zip"),
            File(demRootDir, "$tileId.hgt")
        )
        return preferred.firstOrNull { it.exists() && it.isFile }
    }

    private fun readDemBytes(file: File): ByteArray? {
        return if (file.name.endsWith(".zip", ignoreCase = true)) {
            readZipEntryBytes(file)
        } else {
            file.readBytes()
        }
    }

    private fun readZipEntryBytes(file: File): ByteArray? {
        ZipInputStream(FileInputStream(file).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".hgt", ignoreCase = true)) {
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        if (read > 0) out.write(buffer, 0, read)
                    }
                    return out.toByteArray()
                }
            }
        }
        return null
    }

    private fun decodeDemBytes(bytes: ByteArray): DemTileData? {
        if (bytes.size < 4 || bytes.size % 2 != 0) return null

        val sampleCount = bytes.size / 2
        val rowLen = sqrt(sampleCount.toDouble()).toInt()
        if (rowLen < 2 || rowLen * rowLen != sampleCount) return null
        val axisLen = rowLen - 1

        val samples = ShortArray(sampleCount)
        var cursor = 0
        for (i in 0 until sampleCount) {
            val hi = bytes[cursor].toInt() and 0xFF
            val lo = bytes[cursor + 1].toInt() and 0xFF
            samples[i] = ((hi shl 8) or lo).toShort()
            cursor += 2
        }
        return DemTileData(axisLen = axisLen, rowLen = rowLen, samples = samples)
    }

    private fun tileId(latTile: Int, lonTile: Int): String {
        val latPrefix = if (latTile >= 0) "N" else "S"
        val lonPrefix = if (lonTile >= 0) "E" else "W"
        return String.format(
            Locale.US,
            "%s%02d%s%03d",
            latPrefix,
            abs(latTile),
            lonPrefix,
            abs(lonTile)
        )
    }

    private fun computeMaxDemTileCacheEntries(): Int {
        val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L)
        val adaptive = when {
            maxHeapMb <= 128L -> 12
            maxHeapMb <= 192L -> 16
            maxHeapMb <= 256L -> 20
            maxHeapMb <= 384L -> 28
            else -> 36
        }
        return adaptive.coerceIn(MIN_DEM_TILE_CACHE_ENTRIES, MAX_DEM_TILE_CACHE_ENTRIES)
    }
}
