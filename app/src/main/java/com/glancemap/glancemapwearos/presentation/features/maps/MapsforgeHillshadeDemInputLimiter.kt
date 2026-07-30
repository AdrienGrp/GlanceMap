package com.glancemap.glancemapwearos.presentation.features.maps

import org.mapsforge.map.layer.hills.DemFile
import org.mapsforge.map.layer.hills.HgtFileInfo
import java.io.InputStream

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

    private fun ensureOutputRow(): Boolean =
        when {
            outputOffset < outputRow.size -> true
            emittedRowCount >= outputPointCount -> false
            else -> {
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
                true
            }
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

private const val HGT_SAMPLE_BYTES = 2
private const val BYTE_MASK = 0xff
