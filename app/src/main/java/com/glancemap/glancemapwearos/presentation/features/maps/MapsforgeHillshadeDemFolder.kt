package com.glancemap.glancemapwearos.presentation.features.maps

import org.mapsforge.map.layer.hills.DemFile
import org.mapsforge.map.layer.hills.DemFileFS
import org.mapsforge.map.layer.hills.DemFolder
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
) : DemFolder {
    override fun files(): Iterable<DemFile> =
        demRootDirs
            .asSequence()
            .filter { it.exists() && it.isDirectory }
            .flatMap { root -> root.walkTopDown().filter { it.isFile } }
            .mapNotNull(::toHillshadeDemFile)
            .distinctBy { demFile -> demFile.name.uppercase(Locale.ROOT) }
            .toList()

    override fun subs(): Iterable<DemFolder> = emptyList()

    private fun toHillshadeDemFile(file: File): DemFile? {
        val lowerName = file.name.lowercase(Locale.ROOT)
        return when {
            lowerName.endsWith(".hgt") -> DemFileFS(file)
            lowerName.endsWith(".hgt.zip") -> ZipHgtDemFile(file)
            lowerName.endsWith(".hgt.gz") -> GzipHgtDemFile(file)
            else -> null
        }
    }
}

/**
 * Keeps Mapsforge's hillshade index focused on roots that contain real elevation data.
 *
 * A DEM root can exist with only partial downloads or `.missing` markers. Passing that root to
 * Mapsforge makes it look like the preferred source is available even though there is nothing it
 * can render. In particular, Detailed selected with only Standard installed must use the exact
 * same single-root path as Standard selected.
 */
internal fun resolveHillshadeDemRootDirs(
    demRootDirs: List<File>,
    requiredTileIds: Set<String>? = null,
): List<File> {
    if (requiredTileIds.isNullOrEmpty()) {
        return demRootDirs.filter(::containsHillshadeDemFile)
    }

    val coverageByRoot =
        demRootDirs.map { root ->
            root to requiredTileIds.count { tileId -> root.containsHillshadeDemTile(tileId) }
        }

    // Prefer one complete source. Detailed selected with incomplete or unrelated coverage must
    // resolve to complete Standard terrain instead of building a mixed hillshade index.
    val completeRoot =
        coverageByRoot
            .firstOrNull { (_, availableTiles) -> availableTiles == requiredTileIds.size }

    // Neither source covers the whole map. Retain only roots that can contribute relevant tiles,
    // preserving the selected-to-fallback preference order.
    return if (completeRoot != null) {
        listOf(completeRoot.first)
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
    return if (!exists() || !isDirectory || normalizedTileId.length < 3) {
        false
    } else {
        val folder = normalizedTileId.substring(0, 3)
        listOf(
            File(File(this, folder), "$normalizedTileId.hgt"),
            File(File(this, folder), "$normalizedTileId.hgt.zip"),
            File(File(this, folder), "$normalizedTileId.hgt.gz"),
            File(this, "$normalizedTileId.hgt"),
            File(this, "$normalizedTileId.hgt.zip"),
            File(this, "$normalizedTileId.hgt.gz"),
        ).any { candidate -> candidate.isFile }
    }
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
