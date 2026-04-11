package com.glancemap.glancemapcompanionapp.refuges

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

object MapsforgeMapBoundsParser {
    private const val MAGIC = "mapsforge binary OSM"
    private val MAGIC_BYTES = MAGIC.toByteArray(Charsets.UTF_8)
    private const val HEADER_MIN_BYTES = 70
    private const val HEADER_MAX_BYTES = 1_000_000

    suspend fun readBboxString(
        context: Context,
        uri: Uri
    ): String = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open map file stream.")

        stream.use { input ->
            val magicAndHeaderSize = readExactly(input, MAGIC_BYTES.size + 4)
            val magic = String(magicAndHeaderSize, 0, MAGIC_BYTES.size, Charsets.UTF_8)
            if (magic != MAGIC) {
                throw IllegalArgumentException("Selected file is not a mapsforge .map file.")
            }

            val remainingHeaderSize = ByteBuffer
                .wrap(magicAndHeaderSize, MAGIC_BYTES.size, 4)
                .order(ByteOrder.BIG_ENDIAN)
                .int
            if (remainingHeaderSize < HEADER_MIN_BYTES || remainingHeaderSize > HEADER_MAX_BYTES) {
                throw IllegalArgumentException("Invalid map header size.")
            }

            val header = readExactly(input, remainingHeaderSize)
            val bb = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)

            val fileVersion = bb.int
            if (fileVersion < 3 || fileVersion > 5) {
                throw IllegalArgumentException("Unsupported mapsforge file version: $fileVersion")
            }

            // file size and map date exist in header; skip them here.
            bb.long
            bb.long

            val minLat = bb.int / 1_000_000.0
            val minLon = bb.int / 1_000_000.0
            val maxLat = bb.int / 1_000_000.0
            val maxLon = bb.int / 1_000_000.0

            validateBounds(minLat, minLon, maxLat, maxLon)
            formatBbox(minLon, minLat, maxLon, maxLat)
        }
    }

    private fun readExactly(input: InputStream, size: Int): ByteArray {
        val out = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = input.read(out, offset, size - offset)
            if (read <= 0) throw EOFException("Unexpected end of file while reading map header.")
            offset += read
        }
        return out
    }

    private fun validateBounds(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double
    ) {
        if (!minLat.isFinite() || !minLon.isFinite() || !maxLat.isFinite() || !maxLon.isFinite()) {
            throw IllegalArgumentException("Map bounding box is invalid.")
        }
        if (minLat >= maxLat || minLon >= maxLon) {
            throw IllegalArgumentException("Map bounding box min values must be < max values.")
        }
        if (minLat < -90.0 || maxLat > 90.0 || minLon < -180.0 || maxLon > 180.0) {
            throw IllegalArgumentException("Map bounding box is outside valid lat/lon range.")
        }
    }

    private fun formatBbox(
        minLon: Double,
        minLat: Double,
        maxLon: Double,
        maxLat: Double
    ): String {
        return String.format(
            Locale.US,
            "%.6f,%.6f,%.6f,%.6f",
            minLon, minLat, maxLon, maxLat
        )
    }
}
