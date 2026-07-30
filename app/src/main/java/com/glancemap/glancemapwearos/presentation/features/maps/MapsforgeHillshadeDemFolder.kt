package com.glancemap.glancemapwearos.presentation.features.maps

import org.mapsforge.map.layer.hills.DemFile
import org.mapsforge.map.layer.hills.DemFileFS
import org.mapsforge.map.layer.hills.DemFolder
import org.mapsforge.map.layer.hills.HgtFileInfo
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

internal class MapsforgeHillshadeDemFolder(
    private val demRootDirs: List<File>,
    requiredTileIds: Set<String>? = null,
) : DemFolder {
    private val normalizedRequiredTileIds =
        requiredTileIds?.mapTo(linkedSetOf()) { tileId -> tileId.uppercase(Locale.ROOT) }

    override fun files(): Iterable<DemFile> =
        selectedDemFiles()
            .mapNotNull(::toHillshadeDemFile)
            .distinctBy { demFile -> demFile.name.uppercase(Locale.ROOT) }
            .toList()

    override fun subs(): Iterable<DemFolder> = emptyList()

    private fun selectedDemFiles(): Sequence<File> {
        val requiredTiles = normalizedRequiredTileIds
        return if (requiredTiles == null) {
            demRootDirs
                .asSequence()
                .filter { it.exists() && it.isDirectory }
                .flatMap { root -> root.walkTopDown().filter { it.isFile && it.length() > 0L } }
        } else {
            demRootDirs
                .asSequence()
                .filter { it.exists() && it.isDirectory }
                .flatMap { root ->
                    requiredTiles
                        .asSequence()
                        .flatMap(root::hillshadeDemTileCandidates)
                        .filter { candidate -> candidate.isFile && candidate.length() > 0L }
                }
        }
    }

    private fun toHillshadeDemFile(file: File): DemFile? {
        val lowerName = file.name.lowercase(Locale.ROOT)
        val demFile =
            when {
                lowerName.endsWith(".hgt") -> DemFileFS(file)
                lowerName.endsWith(".hgt.zip") -> ZipHgtDemFile(file)
                lowerName.endsWith(".hgt.gz") -> GzipHgtDemFile(file)
                else -> null
            }
        return demFile?.let(::limitHillshadeDemFileInput)
    }
}

/**
 * Makes high-resolution DEMs affordable for the watch before Mapsforge reads them into memory.
 *
 * Mapsforge derives the allocation size solely from [DemFile.size]. Reducing its shading output
 * after that point still leaves it to allocate a full 3600x3600 Detailed HGT grid. This wrapper
 * exposes every nth source sample instead, retaining the Detailed source while bounding the input
 * grid that Mapsforge sees.
 */
internal fun limitHillshadeDemFileInput(
    demFile: DemFile,
    maxAxisLen: Int = WEAR_HILLSHADE_MAX_INPUT_AXIS,
): DemFile {
    val sourceAxisLen = HgtFileInfo.computeAxisLen(demFile.size)
    val stride = hillshadeInputDownsamplingStride(sourceAxisLen, maxAxisLen)
    return if (stride == 1) demFile else DownsampledHgtDemFile(demFile, sourceAxisLen, stride)
}

internal fun hillshadeInputDownsamplingStride(
    sourceAxisLen: Int,
    maxAxisLen: Int = WEAR_HILLSHADE_MAX_INPUT_AXIS,
): Int {
    if (sourceAxisLen <= maxAxisLen || sourceAxisLen <= 0) return 1

    var stride = ((sourceAxisLen + maxAxisLen - 1) / maxAxisLen).coerceAtLeast(1)
    while (sourceAxisLen % stride != 0) {
        stride += 1
    }
    return stride
}

private class DownsampledHgtDemFile(
    private val source: DemFile,
    private val sourceAxisLen: Int,
    private val stride: Int,
) : DemFile {
    private val outputAxisLen = sourceAxisLen / stride
    private val outputSize = (outputAxisLen + 1L) * (outputAxisLen + 1L) * HGT_SAMPLE_BYTES

    init {
        require(sourceAxisLen > 0)
        require(stride > 1)
        require(sourceAxisLen % stride == 0)
    }

    override fun getName(): String = source.name

    override fun getSize(): Long = outputSize

    override fun openInputStream(bufferSize: Int): InputStream =
        DownsamplingHgtInputStream(
            source = source.openInputStream(bufferSize),
            sourceAxisLen = sourceAxisLen,
            stride = stride,
        )

    override fun asStream(): InputStream =
        DownsamplingHgtInputStream(
            source = source.asStream(),
            sourceAxisLen = sourceAxisLen,
            stride = stride,
        )

    override fun asRawStream(): InputStream =
        DownsamplingHgtInputStream(
            source = source.asRawStream(),
            sourceAxisLen = sourceAxisLen,
            stride = stride,
        )
}

private class DownsamplingHgtInputStream(
    private val source: InputStream,
    sourceAxisLen: Int,
    private val stride: Int,
) : InputStream() {
    private val sourcePointCount = sourceAxisLen + 1
    private val outputPointCount = (sourceAxisLen / stride) + 1
    private val sourceRow = ByteArray(sourcePointCount * HGT_SAMPLE_BYTES)
    private val outputRow = ByteArray(outputPointCount * HGT_SAMPLE_BYTES)
    private var emittedRowCount = 0
    private var outputOffset = outputRow.size

    override fun read(): Int {
        if (!ensureOutputRow()) return -1
        return outputRow[outputOffset++].toInt() and BYTE_MASK
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
        if (length == 0) return 0

        var written = 0
        while (written < length && ensureOutputRow()) {
            val available = outputRow.size - outputOffset
            val count = minOf(length - written, available)
            outputRow.copyInto(
                destination = buffer,
                destinationOffset = offset + written,
                startIndex = outputOffset,
                endIndex = outputOffset + count,
            )
            outputOffset += count
            written += count
        }
        return written.takeIf { it > 0 } ?: -1
    }

    override fun close() {
        source.close()
    }

    private fun ensureOutputRow(): Boolean {
        if (outputOffset < outputRow.size) return true
        if (emittedRowCount >= outputPointCount) return false

        if (emittedRowCount > 0) {
            repeat(stride - 1) { readFully(sourceRow) }
        }
        readFully(sourceRow)

        var sourceOffset = 0
        outputRow.indices.step(HGT_SAMPLE_BYTES).forEach { outputIndex ->
            outputRow[outputIndex] = sourceRow[sourceOffset]
            outputRow[outputIndex + 1] = sourceRow[sourceOffset + 1]
            sourceOffset += stride * HGT_SAMPLE_BYTES
        }
        emittedRowCount += 1
        outputOffset = 0
        return true
    }

    private fun readFully(destination: ByteArray) {
        var offset = 0
        while (offset < destination.size) {
            val count = source.read(destination, offset, destination.size - offset)
            if (count <= 0) throw java.io.EOFException("Incomplete HGT input")
            offset += count
        }
    }
}

internal data class VisibleHillshadeTerrainCoverage(
    val requiredTileIds: Set<String>,
    val detailedTileCount: Int,
    val standardFallbackTileCount: Int,
    val missingTileCount: Int,
) {
    val availableTileCount: Int
        get() = detailedTileCount + standardFallbackTileCount

    val hasAnyTerrain: Boolean
        get() = availableTileCount > 0

    val diagnosticKey: String
        get() = requiredTileIds.sorted().joinToString(",")
}

internal fun resolveVisibleHillshadeTerrainCoverage(
    demRootDirs: List<File>,
    requiredTileIds: Set<String>,
): VisibleHillshadeTerrainCoverage {
    var detailedTileCount = 0
    var standardFallbackTileCount = 0
    var missingTileCount = 0

    requiredTileIds.forEach { tileId ->
        val rootIndex =
            demRootDirs.indexOfFirst { root ->
                root.containsHillshadeDemTile(tileId)
            }
        when {
            rootIndex == 0 -> detailedTileCount += 1
            rootIndex > 0 -> standardFallbackTileCount += 1
            else -> missingTileCount += 1
        }
    }

    return VisibleHillshadeTerrainCoverage(
        requiredTileIds = requiredTileIds,
        detailedTileCount = detailedTileCount,
        standardFallbackTileCount = standardFallbackTileCount,
        missingTileCount = missingTileCount,
    )
}

/**
 * Keeps Mapsforge's hillshade index focused on roots that contain real elevation data.
 *
 * A DEM root can exist with only partial downloads or `.missing` markers. Passing that root to
 * Mapsforge makes it look like the preferred source is available even though there is nothing it
 * can render. Relevant roots remain in priority order so Detailed cells shadow Standard cells,
 * while Standard can fill individual gaps in Detailed coverage.
 */
internal fun resolveHillshadeDemRootDirs(
    demRootDirs: List<File>,
    requiredTileIds: Set<String>? = null,
): List<File> =
    if (requiredTileIds.isNullOrEmpty()) {
        demRootDirs.filter(::containsHillshadeDemFile)
    } else {
        val coverageByRoot =
            demRootDirs.map { root ->
                root to requiredTileIds.count { tileId -> root.containsHillshadeDemTile(tileId) }
            }

        // If the preferred Detailed source covers the whole map, Standard cannot contribute.
        // Otherwise keep every relevant root in preference order so each missing Detailed cell
        // can fall back independently to its Standard counterpart.
        val preferredCoverage = coverageByRoot.firstOrNull()?.second ?: 0
        if (preferredCoverage == requiredTileIds.size) {
            listOf(coverageByRoot.first().first)
        } else {
            coverageByRoot
                .filter { (_, availableTiles) -> availableTiles > 0 }
                .map { (root, _) -> root }
        }
    }

private fun containsHillshadeDemFile(root: File): Boolean {
    if (!root.exists() || !root.isDirectory) return false
    return root
        .walkTopDown()
        .maxDepth(HILLSHADE_DEM_SCAN_MAX_DEPTH)
        .any { file ->
            file.isFile && file.name.isHillshadeDemFileName()
        }
}

private fun String.isHillshadeDemFileName(): Boolean {
    val lowerName = lowercase(Locale.ROOT)
    return lowerName.endsWith(".hgt") ||
        lowerName.endsWith(".hgt.zip") ||
        lowerName.endsWith(".hgt.gz")
}

private fun File.containsHillshadeDemTile(tileId: String): Boolean {
    val normalizedTileId = tileId.uppercase(Locale.ROOT)
    return exists() &&
        isDirectory &&
        normalizedTileId.length >= 3 &&
        hillshadeDemTileCandidates(normalizedTileId).any { candidate ->
            candidate.isFile && candidate.length() > 0L
        }
}

private fun File.hillshadeDemTileCandidates(tileId: String): Sequence<File> {
    if (tileId.length < 3) return emptySequence()
    val folder = tileId.substring(0, 3)
    return sequenceOf(
        File(File(this, folder), "$tileId.hgt"),
        File(File(this, folder), "$tileId.hgt.zip"),
        File(File(this, folder), "$tileId.hgt.gz"),
        File(this, "$tileId.hgt"),
        File(this, "$tileId.hgt.zip"),
        File(this, "$tileId.hgt.gz"),
    )
}

private class ZipHgtDemFile(
    private val file: File,
) : DemFile {
    override fun getName(): String = file.name.removeSuffix(".zip")

    override fun getSize(): Long =
        ZipFile(file).use { zip ->
            val entry = zip.firstHgtEntry() ?: return 0L
            entry.size.takeIf { it > 0L }
                ?: zip.getInputStream(entry).use(::countBytes)
        }

    override fun openInputStream(bufferSize: Int): InputStream {
        val zip = ZipFile(file)
        val entry =
            zip.firstHgtEntry()
                ?: run {
                    zip.close()
                    throw java.io.FileNotFoundException("No HGT entry in ${file.name}")
                }
        return object : FilterInputStream(BufferedInputStream(zip.getInputStream(entry), bufferSize)) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    zip.close()
                }
            }
        }
    }

    override fun asStream(): InputStream = openInputStream(DemFile.BufferSizeDefault)

    override fun asRawStream(): InputStream = openInputStream(DemFile.BufferSizeRaw)
}

private class GzipHgtDemFile(
    private val file: File,
) : DemFile {
    @Volatile
    private var cachedSize: Long? = null

    override fun getName(): String = file.name.removeSuffix(".gz")

    override fun getSize(): Long {
        cachedSize?.let { return it }
        return readGzipUncompressedSize(file).also { cachedSize = it }
    }

    override fun openInputStream(bufferSize: Int): InputStream = GZIPInputStream(BufferedInputStream(FileInputStream(file), bufferSize))

    override fun asStream(): InputStream = openInputStream(DemFile.BufferSizeDefault)

    override fun asRawStream(): InputStream = openInputStream(DemFile.BufferSizeRaw)
}

private fun ZipFile.firstHgtEntry() =
    entries()
        .asSequence()
        .firstOrNull { !it.isDirectory && it.name.lowercase(Locale.ROOT).endsWith(".hgt") }

private fun countBytes(input: InputStream): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
    }
    return total
}

private fun readGzipUncompressedSize(file: File): Long {
    if (file.length() < GZIP_FOOTER_SIZE_BYTES) return 0L
    RandomAccessFile(file, "r").use { raf ->
        raf.seek(file.length() - GZIP_FOOTER_SIZE_BYTES)
        var size = 0L
        repeat(GZIP_FOOTER_SIZE_BYTES) { index ->
            size = size or ((raf.readUnsignedByte().toLong() and 0xffL) shl (8 * index))
        }
        return size
    }
}

private const val GZIP_FOOTER_SIZE_BYTES = 4
private const val HILLSHADE_DEM_SCAN_MAX_DEPTH = 6
private const val HGT_SAMPLE_BYTES = 2
private const val BYTE_MASK = 0xff
