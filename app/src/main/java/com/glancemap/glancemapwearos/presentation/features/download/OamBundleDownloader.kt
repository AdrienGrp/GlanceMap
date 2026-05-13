package com.glancemap.glancemapwearos.presentation.features.download

import android.content.Context
import com.glancemap.glancemapwearos.data.repository.MapRepository
import com.glancemap.glancemapwearos.data.repository.PoiRepository
import com.glancemap.glancemapwearos.data.repository.internal.AtomicStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

data class OamDownloadProgress(
    val phase: String,
    val detail: String,
    val bytesDone: Long = 0L,
    val totalBytes: Long? = null,
)

class OamBundleDownloader(
    private val context: Context,
    private val mapRepository: MapRepository,
    private val poiRepository: PoiRepository,
    private val bundleStore: OamBundleStore = OamBundleStore(context),
) {
    private val downloadDir: File by lazy { context.getDir("oam_downloads", Context.MODE_PRIVATE) }

    suspend fun installedBundles(): List<OamInstalledBundle> = bundleStore.listInstalledBundles()

    suspend fun downloadBundle(
        area: OamDownloadArea,
        choice: OamBundleChoice,
        onProgress: (OamDownloadProgress) -> Unit,
    ): OamInstalledBundle {
        require(choice.includeMap || choice.includePoi) { "Select at least one download item." }

        var mapFileName: String? = null
        if (choice.includeMap) {
            val mapZip =
                downloadZip(
                    url = area.mapZipUrl,
                    fileName = "${area.id}.map.zip",
                    label = "Map",
                    onProgress = onProgress,
                )
            mapFileName =
                extractFirstEntry(
                    zipFile = mapZip,
                    extension = ".map",
                    label = "Map",
                    onProgress = onProgress,
                ) { fileName, input, expectedSize, progress ->
                    mapRepository.saveMapFileAtomic(
                        fileName = fileName,
                        inputStream = input,
                        expectedSize = expectedSize,
                        resumeOffset = 0L,
                        computeSha256 = false,
                        onProgress = progress,
                    )
                }
            mapZip.delete()
        }

        var poiFileName: String? = null
        if (choice.includePoi) {
            val poiZip =
                downloadZip(
                    url = area.poiZipUrl,
                    fileName = "${area.id}.poi.zip",
                    label = "POI",
                    onProgress = onProgress,
                )
            poiFileName =
                extractFirstEntry(
                    zipFile = poiZip,
                    extension = ".poi",
                    label = "POI",
                    onProgress = onProgress,
                ) { fileName, input, expectedSize, progress ->
                    poiRepository.savePoiFileAtomic(
                        fileName = fileName,
                        inputStream = input,
                        expectedSize = expectedSize,
                        resumeOffset = 0L,
                        onProgress = progress,
                    )
                }
            poiZip.delete()
        }

        val installed =
            OamInstalledBundle(
                areaId = area.id,
                areaLabel = area.region,
                bundleChoice = choice,
                mapFileName = mapFileName,
                poiFileName = poiFileName,
                installedAtMillis = System.currentTimeMillis(),
            )
        bundleStore.upsert(installed)
        return installed
    }

    suspend fun deleteBundle(bundle: OamInstalledBundle) {
        withContext(Dispatchers.IO) {
            bundle.mapFileName?.let { fileName ->
                mapRepository
                    .listMapFiles()
                    .firstOrNull { it.name == fileName }
                    ?.let { mapRepository.deleteMapFile(it.absolutePath) }
            }
            bundle.poiFileName?.let { fileName ->
                poiRepository
                    .listPoiFiles()
                    .firstOrNull { it.name == fileName }
                    ?.let { poiRepository.deletePoiFile(it.absolutePath) }
            }
            deleteZipAndPartial("${bundle.areaId}.map.zip")
            deleteZipAndPartial("${bundle.areaId}.poi.zip")
        }
        bundleStore.remove(bundle.areaId)
    }

    private fun deleteZipAndPartial(fileName: String) {
        val safeName = File(fileName).name
        File(downloadDir, safeName).delete()
        File(downloadDir, ".$safeName.part").delete()
    }

    private suspend fun downloadZip(
        url: String,
        fileName: String,
        label: String,
        onProgress: (OamDownloadProgress) -> Unit,
    ): File =
        withContext(Dispatchers.IO) {
            if (!downloadDir.exists() && !downloadDir.mkdirs()) {
                throw IOException("Cannot create OAM download directory")
            }
            val safeName = File(fileName).name
            val finalFile = File(downloadDir, safeName)
            val partFile = File(downloadDir, ".$safeName.part")
            var resumeOffset = partFile.takeIf { it.exists() }?.length()?.coerceAtLeast(0L) ?: 0L
            var restartCount = 0
            var ioRetryCount = 0

            while (true) {
                coroutineContext.ensureActive()
                val connection =
                    openConnection(
                        url = url,
                        resumeOffset = resumeOffset,
                    )
                try {
                    val code = connection.responseCode
                    val append =
                        when {
                            resumeOffset > 0L && code == HttpURLConnection.HTTP_PARTIAL -> true
                            resumeOffset > 0L && code == HttpURLConnection.HTTP_OK -> {
                                partFile.delete()
                                resumeOffset = 0L
                                restartCount += 1
                                connection.disconnect()
                                continue
                            }
                            resumeOffset > 0L && code == HTTP_RANGE_NOT_SATISFIABLE -> {
                                partFile.delete()
                                resumeOffset = 0L
                                restartCount += 1
                                connection.disconnect()
                                continue
                            }
                            code == HttpURLConnection.HTTP_OK -> false
                            else -> throw IOException("HTTP $code for $url")
                        }

                    if (restartCount > MAX_RANGE_RESTARTS) {
                        throw IOException("Server rejected resume too many times")
                    }

                    val expectedTotalBytes =
                        connection.contentLengthLong
                            .takeIf { it > 0L }
                            ?.let { contentLength -> (if (append) resumeOffset else 0L) + contentLength }

                    onProgress(
                        OamDownloadProgress(
                            phase = "DOWNLOADING",
                            detail = "$label zip",
                            bytesDone = resumeOffset,
                            totalBytes = expectedTotalBytes,
                        ),
                    )

                    connection.inputStream.use { input ->
                        AtomicStreamWriter.writeAtomic(
                            dir = downloadDir,
                            fileName = safeName,
                            inputStream = input,
                            onProgress = { bytes ->
                                onProgress(
                                    OamDownloadProgress(
                                        phase = "DOWNLOADING",
                                        detail = "$label zip",
                                        bytesDone = bytes,
                                        totalBytes = expectedTotalBytes,
                                    ),
                                )
                            },
                            options =
                                AtomicStreamWriter.Options(
                                    bufferSize = 1024 * 1024,
                                    progressStepBytes = 2L * 1024 * 1024,
                                    fsync = true,
                                    expectedSize = expectedTotalBytes,
                                    requireExactSize = expectedTotalBytes != null,
                                    resumeOffset = if (append) resumeOffset else 0L,
                                    keepPartialOnCancel = true,
                                    keepPartialOnFailure = true,
                                    computeSha256 = false,
                                ),
                        )
                    }
                    return@withContext finalFile
                } catch (error: IOException) {
                    resumeOffset = partFile.takeIf { it.exists() }?.length()?.coerceAtLeast(0L) ?: 0L
                    if (resumeOffset <= 0L || ioRetryCount >= MAX_IO_RETRIES) {
                        throw error
                    }
                    ioRetryCount += 1
                    onProgress(
                        OamDownloadProgress(
                            phase = "PAUSED",
                            detail = "$label zip interrupted",
                            bytesDone = resumeOffset,
                        ),
                    )
                    delay(IO_RETRY_DELAY_MS * ioRetryCount)
                    continue
                } finally {
                    connection.disconnect()
                }
            }

            finalFile
        }

    private fun openConnection(
        url: String,
        resumeOffset: Long,
    ): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", USER_AGENT)
            if (resumeOffset > 0L) {
                setRequestProperty("Range", "bytes=$resumeOffset-")
            }
        }

    private suspend fun extractFirstEntry(
        zipFile: File,
        extension: String,
        label: String,
        onProgress: (OamDownloadProgress) -> Unit,
        saveEntry: suspend (
            fileName: String,
            input: ZipInputStream,
            expectedSize: Long?,
            onEntryProgress: (Long) -> Unit,
        ) -> Unit,
    ): String =
        withContext(Dispatchers.IO) {
            ZipInputStream(FileInputStream(zipFile)).use { zip ->
                while (true) {
                    coroutineContext.ensureActive()
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || !entry.name.endsWith(extension, ignoreCase = true)) {
                        zip.closeEntry()
                        continue
                    }
                    val entryFileName = File(entry.name).name
                    val expectedSize = entry.size.takeIf { it > 0L }
                    onProgress(
                        OamDownloadProgress(
                            phase = "EXTRACTING",
                            detail = entryFileName,
                            totalBytes = expectedSize,
                        ),
                    )
                    saveEntry(
                        entryFileName,
                        zip,
                        expectedSize,
                    ) { bytes ->
                        onProgress(
                            OamDownloadProgress(
                                phase = "EXTRACTING",
                                detail = entryFileName,
                                bytesDone = bytes,
                                totalBytes = expectedSize,
                            ),
                        )
                    }
                    zip.closeEntry()
                    return@withContext entryFileName
                }
            }
            throw IOException("$label ZIP did not contain a $extension file")
        }

    private companion object {
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private const val MAX_RANGE_RESTARTS = 1
        private const val MAX_IO_RETRIES = 3
        private const val IO_RETRY_DELAY_MS = 2_000L
        private const val USER_AGENT = "GlanceMap-WearOS-OAM-Downloader/1.0 https://www.openandromaps.org"
    }
}
