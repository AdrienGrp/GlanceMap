package com.glancemap.glancemapwearos.data.repository.internal

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

internal object AtomicStreamWriter {
    private const val TAG = "AtomicStreamWriter"

    data class Options(
        val bufferSize: Int = 512 * 1024,
        val progressStepBytes: Long = 1_048_576L,
        val fsync: Boolean = true,
        val failIfExists: Boolean = false,
        val expectedSize: Long? = null,
        val requireExactSize: Boolean = false,
        // ✅ Resume support
        val resumeOffset: Long = 0L, // append starting at this offset
        val keepPartialOnCancel: Boolean = false, // keep .part on pause
        val keepPartialOnFailure: Boolean = false, // keep .part on recoverable IO failure
        // Fresh writes stream SHA-256 inline; resumed writes fall back to a final-file hash.
        val computeSha256: Boolean = false,
    )

    data class WriteResult(
        val bytesCopied: Long,
        val sha256: String?,
    )

    suspend fun writeAtomic(
        dir: File,
        fileName: String,
        inputStream: InputStream,
        onProgress: (Long) -> Unit,
        options: Options = Options(),
    ): WriteResult {
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Cannot create directory: ${dir.absolutePath}")
        }

        val safeName = File(fileName).name
        val finalFile = File(dir, safeName)

        if (options.failIfExists && finalFile.exists()) {
            throw IOException("FILE_EXISTS: $safeName")
        }

        // ✅ Stable hidden partial file name (resume-friendly)
        val partFile = File(dir, ".$safeName.part")

        val expected = options.expectedSize?.takeIf { it > 0L }

        var existingLen = if (partFile.exists()) partFile.length() else 0L
        if (expected != null && existingLen > expected) {
            runCatching { partFile.delete() }
            existingLen = 0L
        }

        val startOffset =
            when {
                options.resumeOffset > 0L -> minOf(existingLen, options.resumeOffset)
                else -> 0L
            }

        if (startOffset == 0L && partFile.exists()) {
            runCatching { partFile.delete() }
            existingLen = 0L
        } else if (partFile.exists() && existingLen > startOffset) {
            safeLogWarn(
                TAG,
                "Truncating partial $safeName from $existingLen to resumeOffset=$startOffset before append",
            )
            RandomAccessFile(partFile, "rw").use { raf ->
                raf.setLength(startOffset)
            }
            existingLen = startOffset
        }

        var bytesCopied = existingLen
        var lastProgress = bytesCopied
        val isResumingFromPartial = bytesCopied > 0L && partFile.exists()
        val digest =
            if (options.computeSha256 && !isResumingFromPartial) {
                MessageDigest.getInstance("SHA-256")
            } else {
                null
            }

        try {
            // Append if we have a partial
            val writeStartMs = monotonicNowMs()
            FileOutputStream(partFile, bytesCopied > 0L).use { fos ->
                bytesCopied =
                    writeWithPipeline(
                        inputStream = inputStream,
                        fos = fos,
                        digest = digest,
                        bufferSize = options.bufferSize,
                        initialBytesCopied = bytesCopied,
                        expected = expected,
                        requireExactSize = options.requireExactSize,
                        progressStepBytes = options.progressStepBytes,
                        onProgress = onProgress,
                        lastProgress = lastProgress,
                    )

                fos.flush()
                if (options.fsync) {
                    runCatching { fos.fd.sync() }
                }
            }
            val writeDurationMs = monotonicNowMs() - writeStartMs
            val writtenSincePart = bytesCopied - startOffset
            val writeThroughputMBps =
                if (writeDurationMs > 0 && writtenSincePart > 0) {
                    (writtenSincePart / 1_048_576.0) / (writeDurationMs / 1000.0)
                } else {
                    0.0
                }
            safeLogDebug(
                TAG,
                "Disk write $safeName: written=${writtenSincePart}B durationMs=$writeDurationMs " +
                    "throughput=${String.format("%.2f", writeThroughputMBps)}MB/s fsync=${options.fsync}",
            )

            onProgress(bytesCopied)

            if (options.requireExactSize) {
                val exp = expected ?: -1L
                if (exp > 0 && bytesCopied != exp) {
                    throw IOException("INCOMPLETE_FILE: expected=$exp got=$bytesCopied")
                }
            }

            // Promote .part -> final
            if (finalFile.exists() && !finalFile.delete()) {
                throw IOException("Cannot delete existing file: ${finalFile.absolutePath}")
            }

            if (!moveAtomic(partFile, finalFile)) {
                if (!copyAndDel(partFile, finalFile)) {
                    throw IOException("Rename and Copy failed for $safeName")
                }
            }

            val sha256 = digest?.digest()?.joinToString("") { b -> "%02x".format(b) }

            return WriteResult(
                bytesCopied = bytesCopied,
                sha256 = sha256,
            )
        } catch (e: Exception) {
            // ✅ Keep partial on PAUSE only (CancellationException path)
            if (e is CancellationException && options.keepPartialOnCancel) {
                // keep ".$name.part"
                throw e
            }

            // Keep partial for resumable failures (e.g., network interruption).
            if (e is IOException && options.keepPartialOnFailure) {
                throw e
            }

            // Otherwise, cleanup partial
            runCatching { partFile.delete() }
            throw e
        }
    }

    /**
     * Pipelined read/write: reads the next chunk from the network while the previous
     * chunk is being written to disk, overlapping network I/O with disk I/O.
     *
     * Uses two pre-allocated buffers with explicit ownership handoff:
     * the reader can only refill a buffer after the writer returns it.
     */
    private suspend fun writeWithPipeline(
        inputStream: InputStream,
        fos: FileOutputStream,
        digest: MessageDigest?,
        bufferSize: Int,
        initialBytesCopied: Long,
        expected: Long?,
        requireExactSize: Boolean,
        progressStepBytes: Long,
        onProgress: (Long) -> Unit,
        lastProgress: Long,
    ): Long =
        coroutineScope {
            val buffers = arrayOf(ByteArray(bufferSize), ByteArray(bufferSize))
            val availableBuffers = Channel<Int>(2)
            val chunks = Channel<Pair<Int, Int>>(1) // (bufferIndex, bytesRead)

            availableBuffers.trySend(0)
            availableBuffers.trySend(1)

            var bytesCopied = initialBytesCopied
            var progress = lastProgress

            // Producer: reads from input stream on IO dispatcher
            val reader =
                launch(Dispatchers.IO) {
                    try {
                        while (true) {
                            coroutineContext.ensureActive()
                            val idx = availableBuffers.receive()
                            val read = inputStream.read(buffers[idx])
                            if (read < 0) {
                                availableBuffers.trySend(idx)
                                break
                            }
                            chunks.send(idx to read)
                        }
                    } finally {
                        chunks.close()
                    }
                }

            // Consumer: writes to disk on current (IO) dispatcher
            try {
                for ((idx, read) in chunks) {
                    coroutineContext.ensureActive()

                    if (requireExactSize) {
                        val exp = expected ?: -1L
                        if (exp > 0 && bytesCopied + read > exp) {
                            reader.cancel()
                            throw IOException("TOO_MUCH_DATA: expected=$exp got>${bytesCopied + read}")
                        }
                    }

                    fos.write(buffers[idx], 0, read)
                    digest?.update(buffers[idx], 0, read)
                    bytesCopied += read
                    availableBuffers.send(idx)

                    if (bytesCopied - progress >= progressStepBytes) {
                        progress = bytesCopied
                        onProgress(bytesCopied)
                    }
                }
            } catch (e: Exception) {
                reader.cancel()
                throw e
            } finally {
                availableBuffers.close()
            }

            bytesCopied
        }

    private fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000L

    private fun safeLogDebug(
        tag: String,
        message: String,
    ) {
        runCatching { Log.d(tag, message) }
    }

    private fun safeLogWarn(
        tag: String,
        message: String,
    ) {
        runCatching { Log.w(tag, message) }
    }

    private fun moveAtomic(
        src: File,
        dst: File,
    ): Boolean =
        runCatching {
            Files.move(
                src.toPath(),
                dst.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
            true
        }.getOrElse {
            false
        }

    private fun copyAndDel(
        src: File,
        dst: File,
    ): Boolean =
        try {
            src.copyTo(dst, overwrite = true)
            src.delete()
            true
        } catch (_: Exception) {
            false
        }
}
