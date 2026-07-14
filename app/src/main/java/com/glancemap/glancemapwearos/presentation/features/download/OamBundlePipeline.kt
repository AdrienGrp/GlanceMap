package com.glancemap.glancemapwearos.presentation.features.download

import java.io.File
import java.util.zip.ZipFile

@Suppress("ReturnCount")
internal fun reusableBundleArchiveOrNull(
    directory: File,
    fileName: String,
    entryExtension: String,
    expectedSize: Long? = null,
): File? {
    val archive = File(directory, File(fileName).name)
    if (!archive.isFile || archive.length() <= 0L) return null
    if (expectedSize != null && expectedSize > 0L && archive.length() != expectedSize) {
        archive.delete()
        return null
    }
    val containsExpectedEntry =
        runCatching {
            ZipFile(archive).use { zip ->
                zip.entries().asSequence().any { entry ->
                    !entry.isDirectory && entry.name.endsWith(entryExtension, ignoreCase = true)
                }
            }
        }.getOrDefault(false)
    if (containsExpectedEntry) return archive
    archive.delete()
    return null
}

internal class OamBundleProgressArbiter(
    private val emit: (OamDownloadProgress) -> Unit,
) {
    private val lock = Any()
    private var networkOperationCount = 0
    private var extractionActive = false
    private var latestExtractionProgress: OamDownloadProgress? = null

    suspend fun <T> runNetwork(block: suspend ((OamDownloadProgress) -> Unit) -> T): T {
        synchronized(lock) { networkOperationCount += 1 }
        return try {
            block(::onNetworkProgress)
        } finally {
            val extractionToShow =
                synchronized(lock) {
                    networkOperationCount = (networkOperationCount - 1).coerceAtLeast(0)
                    latestExtractionProgress.takeIf { networkOperationCount == 0 && extractionActive }
                }
            extractionToShow?.let(emit)
        }
    }

    suspend fun <T> runExtraction(block: suspend ((OamDownloadProgress) -> Unit) -> T): T {
        synchronized(lock) {
            extractionActive = true
            latestExtractionProgress = null
        }
        return try {
            block(::onExtractionProgress)
        } finally {
            synchronized(lock) {
                extractionActive = false
                latestExtractionProgress = null
            }
        }
    }

    fun emitForeground(progress: OamDownloadProgress) {
        emit(progress)
    }

    private fun onNetworkProgress(progress: OamDownloadProgress) {
        emit(progress)
    }

    private fun onExtractionProgress(progress: OamDownloadProgress) {
        val shouldEmit =
            synchronized(lock) {
                latestExtractionProgress = progress
                networkOperationCount == 0
            }
        if (shouldEmit) emit(progress)
    }
}
