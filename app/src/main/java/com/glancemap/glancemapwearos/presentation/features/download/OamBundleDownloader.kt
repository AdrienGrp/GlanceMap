@file:Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "LongParameterList",
    "LoopWithTooManyJumpStatements",
    "LargeClass",
    "TooManyFunctions",
)

package com.glancemap.glancemapwearos.presentation.features.download

import android.content.Context
import com.glancemap.glancemapwearos.core.maps.Dem3CoverageUtils
import com.glancemap.glancemapwearos.core.maps.DemSignatureStore
import com.glancemap.glancemapwearos.core.routing.RoutingCoverageUtils
import com.glancemap.glancemapwearos.core.routing.routingSegmentPartFile
import com.glancemap.glancemapwearos.core.routing.routingSegmentTargetFile
import com.glancemap.glancemapwearos.core.routing.routingSegmentsDir
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.data.repository.MapRepository
import com.glancemap.glancemapwearos.data.repository.PoiRepository
import com.glancemap.glancemapwearos.data.repository.internal.AtomicStreamWriter
import com.glancemap.glancemapwearos.presentation.features.maps.theme.createMissingDemMarker
import com.glancemap.glancemapwearos.presentation.features.maps.theme.validateDemTileFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.Collections
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

data class OamDownloadProgress(
    val phase: String,
    val detail: String,
    val bytesDone: Long = 0L,
    val totalBytes: Long? = null,
)

private data class RoutingSegmentDownloadResult(
    val fileName: String,
    val downloaded: Boolean,
    val available: Boolean = true,
)

private data class DemTileDownloadResult(
    val tileId: String,
    val stored: Boolean,
)

private data class RemoteFileRequest(
    val url: String,
    val fileName: String,
)

class OamBundleDownloader(
    private val context: Context,
    private val mapRepository: MapRepository,
    private val poiRepository: PoiRepository,
    private val bundleStore: OamBundleStore = OamBundleStore(context),
) {
    private val downloadDir: File by lazy { context.getDir("oam_downloads", Context.MODE_PRIVATE) }
    private val activeConnections = Collections.synchronizedSet(mutableSetOf<HttpURLConnection>())

    suspend fun installedBundles(): List<OamInstalledBundle> = bundleStore.listInstalledBundles()

    suspend fun checkBundleUpdates(bundle: OamInstalledBundle): OamBundleUpdateCheck =
        withContext(Dispatchers.IO) {
            val area =
                OamDownloadCatalog.areas.firstOrNull { it.id == bundle.areaId }
                    ?: return@withContext OamBundleUpdateCheck(
                        bundle = bundle,
                        status = OamBundleUpdateStatus.UNKNOWN,
                        checkedFileCount = 0,
                        unknownFileNames = listOf(bundle.areaLabel),
                    )
            val requests = remoteFileRequestsForBundle(area = area, bundle = bundle)
            val previousByUrl = bundle.remoteFiles.associateBy { it.url }
            val changedFileNames = mutableListOf<String>()
            val unknownFileNames = mutableListOf<String>()
            var checkedFileCount = 0

            requests.forEach { request ->
                coroutineContext.ensureActive()
                val previous = previousByUrl[request.url]
                if (previous == null || !previous.isComparable()) {
                    unknownFileNames += request.fileName
                    return@forEach
                }
                val current =
                    runCatching { fetchRemoteMetadata(request) }
                        .getOrNull()
                if (current == null || !current.isComparable()) {
                    unknownFileNames += request.fileName
                    return@forEach
                }
                checkedFileCount += 1
                when (previous.compareWith(current)) {
                    RemoteMetadataComparison.CHANGED -> changedFileNames += request.fileName
                    RemoteMetadataComparison.UNKNOWN -> unknownFileNames += request.fileName
                    RemoteMetadataComparison.SAME -> Unit
                }
            }

            OamBundleUpdateCheck(
                bundle = bundle,
                status =
                    when {
                        changedFileNames.isNotEmpty() -> OamBundleUpdateStatus.UPDATE_AVAILABLE
                        unknownFileNames.isNotEmpty() || checkedFileCount == 0 -> OamBundleUpdateStatus.UNKNOWN
                        else -> OamBundleUpdateStatus.UP_TO_DATE
                    },
                checkedFileCount = checkedFileCount,
                changedFileNames = changedFileNames.distinct(),
                unknownFileNames = unknownFileNames.distinct(),
            )
        }

    fun abortActiveDownloads(reason: String = "manual") {
        val connections = synchronized(activeConnections) { activeConnections.toList() }
        DebugTelemetry.log(
            OAM_DOWNLOAD_TELEMETRY_TAG,
            "event=abort_active_downloads reason=$reason activeConnections=${connections.size}",
        )
        connections.forEach { connection ->
            runCatching { connection.disconnect() }
        }
    }

    suspend fun deletePartialDownloads(
        areas: List<OamDownloadArea>,
        selection: OamDownloadSelection,
    ) {
        withContext(Dispatchers.IO) {
            val installedBundles = bundleStore.listInstalledBundles()
            areas.forEach { area ->
                if (selection.includeMap) {
                    deleteZipAndPartial("${area.id}.map.zip")
                }
                if (selection.includePoi) {
                    deleteZipAndPartial("${area.id}.poi.zip")
                }
                if (selection.includeRouting) {
                    routingSegmentNamesForArea(
                        area = area,
                        installedBundles = installedBundles,
                    ).forEach { fileName ->
                        routingSegmentPartFile(context, fileName).delete()
                    }
                }
                if (selection.includeDem) {
                    runCatching {
                        demTileIdsForArea(
                            area = area,
                            installedBundles = installedBundles,
                        )
                    }.getOrDefault(emptySet())
                        .forEach(::deleteDemPartial)
                }
            }
        }
    }

    suspend fun downloadBundle(
        area: OamDownloadArea,
        selection: OamDownloadSelection,
        forceMapAndPoi: Boolean = false,
        forceRoutingSegments: Boolean = false,
        forceDemTiles: Boolean = false,
        onProgress: (OamDownloadProgress) -> Unit,
    ): OamInstalledBundle {
        require(selection.canDownload) { "Select at least one download item." }

        val existingBundle = bundleStore.listInstalledBundles().firstOrNull { it.areaId == area.id }
        val remoteFilesByUrl =
            existingBundle
                ?.remoteFiles
                .orEmpty()
                .associateBy { it.url }
                .toMutableMap()
        var mapFileName: String? = existingBundle?.mapFileName
        if (selection.includeMap) {
            val existingMapFile =
                if (forceMapAndPoi) {
                    null
                } else {
                    existingMapFileForArea(area = area, knownFileName = existingBundle?.mapFileName)
                }
            fetchRemoteMetadataOrNull(
                RemoteFileRequest(
                    url = area.mapZipUrl,
                    fileName = remoteFileName(area.mapZipUrl),
                ),
            )?.let { remoteFilesByUrl[it.url] = it }
            if (existingMapFile != null) {
                mapFileName = existingMapFile.name
                reportExistingFile(
                    label = "Map",
                    file = existingMapFile,
                    onProgress = onProgress,
                )
            } else {
                val mapZip =
                    downloadFile(
                        url = area.mapZipUrl,
                        dir = downloadDir,
                        fileName = "${area.id}.map.zip",
                        label = "Map",
                        progressDetail = "Map zip",
                        bufferSize = OAM_ZIP_DOWNLOAD_BUFFER_SIZE,
                        progressStepBytes = 2L * 1024 * 1024,
                        fsync = false,
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
            upsertPartialBundle(
                area = area,
                selection = selection,
                existingBundle = existingBundle,
                mapFileName = mapFileName,
                poiFileName = existingBundle?.poiFileName,
                remoteFiles = remoteFilesByUrl.values,
            )
        }

        var poiFileName: String? = existingBundle?.poiFileName
        if (selection.includePoi) {
            val existingPoiFile =
                if (forceMapAndPoi) {
                    null
                } else {
                    existingPoiFileForArea(area = area, knownFileName = existingBundle?.poiFileName)
                }
            fetchRemoteMetadataOrNull(
                RemoteFileRequest(
                    url = area.poiZipUrl,
                    fileName = remoteFileName(area.poiZipUrl),
                ),
            )?.let { remoteFilesByUrl[it.url] = it }
            if (existingPoiFile != null) {
                poiFileName = existingPoiFile.name
                reportExistingFile(
                    label = "POI",
                    file = existingPoiFile,
                    onProgress = onProgress,
                )
            } else {
                val poiZip =
                    downloadFile(
                        url = area.poiZipUrl,
                        dir = downloadDir,
                        fileName = "${area.id}.poi.zip",
                        label = "POI",
                        progressDetail = "POI zip",
                        bufferSize = OAM_ZIP_DOWNLOAD_BUFFER_SIZE,
                        progressStepBytes = 2L * 1024 * 1024,
                        fsync = false,
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
            upsertPartialBundle(
                area = area,
                selection = selection,
                existingBundle = existingBundle,
                mapFileName = mapFileName,
                poiFileName = poiFileName,
                remoteFiles = remoteFilesByUrl.values,
            )
        }

        var downloadedRoutingFileNames = existingBundle?.downloadedRoutingFileNames.orEmpty()
        val routingFileNames =
            if (selection.includeRouting) {
                val requiredSegments = routingSegmentNamesForArea(area = area, mapFileName = mapFileName)
                val segmentResults =
                    requiredSegments.map { fileName ->
                        val segmentUrl = "$BROUTER_SEGMENTS_BASE_URL/$fileName"
                        fetchRemoteMetadataOrNull(
                            RemoteFileRequest(
                                url = segmentUrl,
                                fileName = fileName,
                            ),
                        )?.let { remoteFilesByUrl[it.url] = it }
                        downloadRoutingSegment(
                            fileName = fileName,
                            forceDownload = forceRoutingSegments,
                            onProgress = onProgress,
                        )
                    }
                val resultFileNames = segmentResults.filter { it.available }.map { it.fileName }
                downloadedRoutingFileNames =
                    (downloadedRoutingFileNames + segmentResults.filter { it.downloaded }.map { it.fileName })
                        .distinct()
                        .filter { it in resultFileNames }
                resultFileNames
            } else {
                existingBundle?.routingFileNames.orEmpty()
            }

        var downloadedDemTileIds = existingBundle?.downloadedDemTileIds.orEmpty()
        val demTileIds =
            if (selection.includeDem) {
                val requiredTiles = demTileIdsForArea(area = area, mapFileName = mapFileName)
                val tileResults =
                    requiredTiles.map { tileId ->
                        val tileRequest = demRemoteFileRequest(tileId)
                        fetchRemoteMetadataOrNull(tileRequest)?.let { remoteFilesByUrl[it.url] = it }
                        downloadDemTile(
                            tileId = tileId,
                            forceDownload = forceDemTiles,
                            onProgress = onProgress,
                        )
                    }
                downloadedDemTileIds =
                    (downloadedDemTileIds + tileResults.filter { it.stored }.map { it.tileId })
                        .distinct()
                        .filter { it in requiredTiles }
                requiredTiles
            } else {
                existingBundle?.demTileIds.orEmpty()
            }

        val installed =
            OamInstalledBundle(
                areaId = area.id,
                areaLabel = area.region,
                bundleChoice = selection.toBundleChoice(),
                mapFileName = mapFileName,
                poiFileName = poiFileName,
                routingFileNames = routingFileNames,
                downloadedRoutingFileNames = downloadedRoutingFileNames,
                demTileIds = demTileIds,
                downloadedDemTileIds = downloadedDemTileIds,
                installedAtMillis = System.currentTimeMillis(),
                remoteFiles = remoteFilesByUrl.values.sortedBy { it.url },
            )
        bundleStore.upsert(installed)
        if (selection.includeRouting) {
            RoutingCoverageUtils.clearCaches()
        }
        if (selection.includeDem) {
            DemSignatureStore.markDirty(context)
            Dem3CoverageUtils.clearCaches()
        }
        return installed
    }

    suspend fun deleteBundle(bundle: OamInstalledBundle) {
        withContext(Dispatchers.IO) {
            val routingFilesUsedByOtherBundles =
                bundleStore
                    .listInstalledBundles()
                    .asSequence()
                    .filterNot { it.areaId == bundle.areaId }
                    .flatMap { it.routingFileNames.asSequence() }
                    .toSet()
            val demTilesUsedByOtherBundles =
                bundleStore
                    .listInstalledBundles()
                    .asSequence()
                    .filterNot { it.areaId == bundle.areaId }
                    .flatMap { it.demTileIds.asSequence() }
                    .toSet()
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
            bundle.downloadedRoutingFileNames
                .filterNot { it in routingFilesUsedByOtherBundles }
                .forEach { fileName ->
                    routingSegmentTargetFile(context, fileName).delete()
                    routingSegmentPartFile(context, fileName).delete()
                }
            RoutingCoverageUtils.clearCaches()
            val remainingMapDemTiles =
                mapRepository
                    .listMapFiles()
                    .flatMap { file -> Dem3CoverageUtils.requiredTileIdsForMap(file).orEmpty() }
                    .toSet()
            Dem3CoverageUtils.deleteTiles(
                context = context,
                tileIds =
                    bundle.downloadedDemTileIds
                        .filterNot { it in demTilesUsedByOtherBundles || it in remainingMapDemTiles }
                        .toSet(),
            )
            Dem3CoverageUtils.clearCaches()
        }
        bundleStore.remove(bundle.areaId)
    }

    private fun deleteZipAndPartial(fileName: String) {
        val safeName = File(fileName).name
        File(downloadDir, safeName).delete()
        File(downloadDir, ".$safeName.part").delete()
    }

    private suspend fun routingSegmentNamesForArea(
        area: OamDownloadArea,
        installedBundles: List<OamInstalledBundle>,
    ): Set<String> =
        routingSegmentNamesForArea(
            area = area,
            mapFileName =
                installedBundles
                    .firstOrNull { it.areaId == area.id }
                    ?.mapFileName,
        ).toSet()

    private suspend fun routingSegmentNamesForArea(
        area: OamDownloadArea,
        mapFileName: String?,
    ): List<String> {
        val mapFile =
            mapFileForArea(area = area, mapFileName = mapFileName)
                ?: throw IOException("Routing needs the map in this bundle or an installed map for ${area.region}.")
        return RoutingCoverageUtils
            .requiredSegmentNamesForMapFile(mapFile)
            ?.sorted()
            ?: throw IOException("Cannot read map bounds for routing.")
    }

    private suspend fun demTileIdsForArea(
        area: OamDownloadArea,
        installedBundles: List<OamInstalledBundle>,
    ): Set<String> =
        demTileIdsForArea(
            area = area,
            mapFileName =
                installedBundles
                    .firstOrNull { it.areaId == area.id }
                    ?.mapFileName,
        ).toSet()

    private suspend fun demTileIdsForArea(
        area: OamDownloadArea,
        mapFileName: String?,
    ): List<String> {
        val mapFile =
            mapFileForArea(area = area, mapFileName = mapFileName)
                ?: throw IOException("DEM needs the map in this bundle or an installed map for ${area.region}.")
        return Dem3CoverageUtils
            .requiredTileIdsForMap(mapFile)
            ?.sorted()
            ?: throw IOException("Cannot read map bounds for DEM.")
    }

    private suspend fun mapFileForArea(
        area: OamDownloadArea,
        mapFileName: String?,
    ): File? {
        val candidateNames =
            listOfNotNull(
                mapFileName,
                "${area.region}.map",
            )
        return mapRepository
            .listMapFiles()
            .firstMatchingFileName(candidateNames)
    }

    private suspend fun existingMapFileForArea(
        area: OamDownloadArea,
        knownFileName: String?,
    ): File? =
        mapRepository
            .listMapFiles()
            .firstMatchingFileName(
                listOf(
                    knownFileName,
                    "${area.region}.map",
                ),
            )

    private suspend fun existingPoiFileForArea(
        area: OamDownloadArea,
        knownFileName: String?,
    ): File? =
        poiRepository
            .listPoiFiles()
            .firstMatchingFileName(
                listOf(
                    knownFileName,
                    "${area.region}.poi",
                ),
            )

    private fun List<File>.firstMatchingFileName(candidateNames: Iterable<String?>): File? =
        firstOrNull { file ->
            candidateNames
                .filterNotNull()
                .any { candidate -> candidate.matchesOamFileName(file.name) }
        }

    private fun String.matchesOamFileName(fileName: String): Boolean =
        equals(fileName, ignoreCase = true) ||
            normalizedOamFileStem() == fileName.normalizedOamFileStem()

    private fun String.normalizedOamFileStem(): String =
        substringBeforeLast('.')
            .filter(Char::isLetterOrDigit)
            .lowercase(Locale.ROOT)

    private suspend fun upsertPartialBundle(
        area: OamDownloadArea,
        selection: OamDownloadSelection,
        existingBundle: OamInstalledBundle?,
        mapFileName: String?,
        poiFileName: String?,
        remoteFiles: Collection<OamRemoteFileMetadata>,
    ) {
        bundleStore.upsert(
            OamInstalledBundle(
                areaId = area.id,
                areaLabel = area.region,
                bundleChoice = selection.toBundleChoice(),
                mapFileName = mapFileName,
                poiFileName = poiFileName,
                routingFileNames = existingBundle?.routingFileNames.orEmpty(),
                downloadedRoutingFileNames = existingBundle?.downloadedRoutingFileNames.orEmpty(),
                demTileIds = existingBundle?.demTileIds.orEmpty(),
                downloadedDemTileIds = existingBundle?.downloadedDemTileIds.orEmpty(),
                installedAtMillis =
                    existingBundle?.installedAtMillis?.takeIf { it > 0L }
                        ?: System.currentTimeMillis(),
                remoteFiles = remoteFiles.sortedBy { it.url },
            ),
        )
    }

    private fun reportExistingFile(
        label: String,
        file: File,
        onProgress: (OamDownloadProgress) -> Unit,
    ) {
        DebugTelemetry.log(
            OAM_DOWNLOAD_TELEMETRY_TAG,
            "event=reuse_existing_file label=$label file=${file.name} bytes=${file.length().coerceAtLeast(0L)}",
        )
        onProgress(
            OamDownloadProgress(
                phase = "READY",
                detail = "${file.name} already on watch",
                bytesDone = file.length().coerceAtLeast(0L),
                totalBytes = file.length().takeIf { it > 0L },
            ),
        )
    }

    private suspend fun downloadRoutingSegment(
        fileName: String,
        forceDownload: Boolean,
        onProgress: (OamDownloadProgress) -> Unit,
    ): RoutingSegmentDownloadResult {
        val safeName = File(fileName).name
        val targetFile = routingSegmentTargetFile(context, safeName)
        if (!forceDownload && targetFile.exists() && targetFile.length() > 0L) {
            onProgress(
                OamDownloadProgress(
                    phase = "READY",
                    detail = safeName,
                    bytesDone = targetFile.length(),
                    totalBytes = targetFile.length(),
                ),
            )
            return RoutingSegmentDownloadResult(fileName = safeName, downloaded = false)
        }
        val url = "$BROUTER_SEGMENTS_BASE_URL/$safeName"
        val result =
            runCatching {
                downloadFile(
                    url = url,
                    dir = routingSegmentsDir(context),
                    fileName = safeName,
                    label = "Routing",
                    progressDetail = safeName,
                    bufferSize = 512 * 1024,
                    progressStepBytes = 1L * 1024 * 1024,
                    fsync = true,
                    onProgress = onProgress,
                )
                RoutingSegmentDownloadResult(fileName = safeName, downloaded = true)
            }.getOrElse { error ->
                if (error.isHttpNotFound()) {
                    DebugTelemetry.log(
                        OAM_DOWNLOAD_TELEMETRY_TAG,
                        "event=skip_missing_routing_segment file=$safeName url=$url",
                    )
                    onProgress(
                        OamDownloadProgress(
                            phase = "SKIPPED",
                            detail = "$safeName unavailable",
                        ),
                    )
                    RoutingSegmentDownloadResult(
                        fileName = safeName,
                        downloaded = false,
                        available = false,
                    )
                } else {
                    throw error
                }
            }
        return result
    }

    private suspend fun downloadDemTile(
        tileId: String,
        forceDownload: Boolean,
        onProgress: (OamDownloadProgress) -> Unit,
    ): DemTileDownloadResult {
        val safeTileId = tileId.uppercase(Locale.ROOT)
        val targetFile = demTileTargetFile(safeTileId)
        if (!forceDownload && isDemTileStored(safeTileId, targetFile)) {
            onProgress(
                OamDownloadProgress(
                    phase = "READY",
                    detail = "$safeTileId DEM",
                    bytesDone = targetFile.takeIf { it.exists() }?.length()?.coerceAtLeast(0L) ?: 0L,
                    totalBytes = targetFile.takeIf { it.exists() }?.length()?.takeIf { it > 0L },
                ),
            )
            return DemTileDownloadResult(tileId = safeTileId, stored = true)
        }

        val request = demRemoteFileRequest(safeTileId)
        val result =
            runCatching {
                val file =
                    downloadFile(
                        url = request.url,
                        dir = targetFile.parentFile ?: Dem3CoverageUtils.demRootDir(context),
                        fileName = targetFile.name,
                        label = "DEM",
                        progressDetail = "$safeTileId DEM",
                        bufferSize = 512 * 1024,
                        progressStepBytes = 512L * 1024,
                        fsync = true,
                        onProgress = onProgress,
                    )
                runCatching { validateDemTileFile(file) }
                    .onFailure {
                        file.delete()
                    }.getOrThrow()
                DemTileDownloadResult(tileId = safeTileId, stored = true)
            }.getOrElse { error ->
                if (error.isHttpNotFound()) {
                    targetFile.delete()
                    File(targetFile.parentFile ?: Dem3CoverageUtils.demRootDir(context), ".${targetFile.name}.part")
                        .delete()
                    createMissingDemMarker(
                        target = targetFile,
                        demRoot = Dem3CoverageUtils.demRootDir(context),
                    )
                    DebugTelemetry.log(
                        OAM_DOWNLOAD_TELEMETRY_TAG,
                        "event=skip_missing_dem_tile tile=$safeTileId url=${request.url}",
                    )
                    onProgress(
                        OamDownloadProgress(
                            phase = "SKIPPED",
                            detail = "$safeTileId DEM unavailable",
                        ),
                    )
                    DemTileDownloadResult(tileId = safeTileId, stored = true)
                } else {
                    throw error
                }
            }
        return result
    }

    private fun isDemTileStored(
        tileId: String,
        targetFile: File,
    ): Boolean {
        val demRoot = Dem3CoverageUtils.demRootDir(context)
        if (targetFile.exists() && targetFile.isFile && runCatching { validateDemTileFile(targetFile) }.isSuccess) {
            return true
        }
        return Dem3CoverageUtils
            .missingTileMarkerCandidates(demRoot = demRoot, tileId = tileId)
            .any { it.exists() && it.isFile }
    }

    private fun deleteDemPartial(tileId: String) {
        val targetFile = demTileTargetFile(tileId)
        File(targetFile.parentFile ?: Dem3CoverageUtils.demRootDir(context), ".${targetFile.name}.part").delete()
    }

    private fun demRemoteFileRequest(tileId: String): RemoteFileRequest {
        val safeTileId = tileId.uppercase(Locale.ROOT)
        val folder = safeTileId.substring(0, 3)
        val fileName = "$safeTileId.hgt.zip"
        return RemoteFileRequest(
            url = "$DEM3_BASE_URL/$folder/$fileName",
            fileName = fileName,
        )
    }

    private fun demTileTargetFile(tileId: String): File {
        val safeTileId = tileId.uppercase(Locale.ROOT)
        val folder = safeTileId.substring(0, 3)
        return File(File(Dem3CoverageUtils.demRootDir(context), folder), "$safeTileId.hgt.zip")
    }

    private fun remoteFileRequestsForBundle(
        area: OamDownloadArea,
        bundle: OamInstalledBundle,
    ): List<RemoteFileRequest> =
        buildList {
            if (bundle.mapFileName != null) {
                add(
                    RemoteFileRequest(
                        url = area.mapZipUrl,
                        fileName = remoteFileName(area.mapZipUrl),
                    ),
                )
            }
            if (bundle.poiFileName != null) {
                add(
                    RemoteFileRequest(
                        url = area.poiZipUrl,
                        fileName = remoteFileName(area.poiZipUrl),
                    ),
                )
            }
            bundle.routingFileNames.forEach { fileName ->
                val safeName = File(fileName).name
                add(
                    RemoteFileRequest(
                        url = "$BROUTER_SEGMENTS_BASE_URL/$safeName",
                        fileName = safeName,
                    ),
                )
            }
            bundle.demTileIds.forEach { tileId ->
                add(demRemoteFileRequest(tileId))
            }
        }

    private suspend fun fetchRemoteMetadataOrNull(
        request: RemoteFileRequest,
    ): OamRemoteFileMetadata? =
        runCatching {
            fetchRemoteMetadata(request)
        }.getOrNull()

    private suspend fun fetchRemoteMetadata(request: RemoteFileRequest): OamRemoteFileMetadata =
        withContext(Dispatchers.IO) {
            val connection =
                (URI(request.url).toURL().openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    useCaches = false
                    setRequestProperty("Accept-Encoding", "identity")
                    setRequestProperty("User-Agent", USER_AGENT)
                }
            activeConnections += connection
            try {
                val code = connection.responseCode
                if (code !in 200..399) {
                    throw IOException("HTTP $code for ${request.url}")
                }
                OamRemoteFileMetadata(
                    url = request.url,
                    fileName = request.fileName,
                    entityTag = connection.getHeaderField("ETag")?.takeIf { it.isNotBlank() },
                    lastModifiedMillis =
                        connection
                            .getHeaderFieldDate("Last-Modified", -1L)
                            .takeIf { it >= 0L },
                    contentLengthBytes = connection.contentLengthLong.takeIf { it > 0L },
                )
            } finally {
                activeConnections -= connection
                connection.disconnect()
            }
        }

    private suspend fun downloadFile(
        url: String,
        dir: File,
        fileName: String,
        label: String,
        progressDetail: String,
        bufferSize: Int,
        progressStepBytes: Long,
        fsync: Boolean,
        onProgress: (OamDownloadProgress) -> Unit,
    ): File =
        withContext(Dispatchers.IO) {
            if (!dir.exists() && !dir.mkdirs()) {
                throw IOException("Cannot create download directory")
            }
            val safeName = File(fileName).name
            val finalFile = File(dir, safeName)
            val partFile = File(dir, ".$safeName.part")
            var resumeOffset = partFile.takeIf { it.exists() }?.length()?.coerceAtLeast(0L) ?: 0L
            var restartCount = 0
            var ioRetryCount = 0
            val downloadStartedAtMs = System.currentTimeMillis()
            var lastSpeedSampleAtMs = downloadStartedAtMs
            var lastSpeedSampleBytes = resumeOffset

            while (true) {
                coroutineContext.ensureActive()
                val connection =
                    openConnection(
                        url = url,
                        resumeOffset = resumeOffset,
                    )
                activeConnections += connection
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
                            detail = progressDetail,
                            bytesDone = resumeOffset,
                            totalBytes = expectedTotalBytes,
                        ),
                    )
                    logDownloadSpeed(
                        event = "download_start",
                        label = label,
                        fileName = safeName,
                        bytesDone = resumeOffset,
                        totalBytes = expectedTotalBytes,
                        currentSpeedMbps = null,
                        averageSpeedMbps = null,
                    )

                    var lastProgressAtMs = System.currentTimeMillis()
                    var lastProgressBytes = resumeOffset
                    val stallWatchdog =
                        launch {
                            while (isActive) {
                                delay(STALL_CHECK_INTERVAL_MS)
                                val idleMs = System.currentTimeMillis() - lastProgressAtMs
                                if (idleMs >= STALL_RECONNECT_TIMEOUT_MS) {
                                    DebugTelemetry.log(
                                        OAM_DOWNLOAD_TELEMETRY_TAG,
                                        "event=auto_reconnect_request reason=stall_timeout " +
                                            "file=$safeName idleMs=$idleMs bytes=$lastProgressBytes",
                                    )
                                    logDownloadSpeed(
                                        event = "download_stalled_reconnect",
                                        label = label,
                                        fileName = safeName,
                                        bytesDone = lastProgressBytes,
                                        totalBytes = expectedTotalBytes,
                                        currentSpeedMbps = null,
                                        averageSpeedMbps = null,
                                    )
                                    connection.disconnect()
                                    return@launch
                                }
                            }
                        }
                    try {
                        connection.inputStream.use { input ->
                            AtomicStreamWriter.writeAtomic(
                                dir = dir,
                                fileName = safeName,
                                inputStream = input,
                                onProgress = { bytes ->
                                    val nowMs = System.currentTimeMillis()
                                    val elapsedSinceLastMs = nowMs - lastSpeedSampleAtMs
                                    val bytesSinceLast = bytes - lastSpeedSampleBytes
                                    val currentSpeedMbps =
                                        if (elapsedSinceLastMs > 0L && bytesSinceLast > 0L) {
                                            bytesPerMsToMbps(bytesSinceLast, elapsedSinceLastMs)
                                        } else {
                                            null
                                        }
                                    val elapsedSinceStartMs = nowMs - downloadStartedAtMs
                                    val bytesSinceStart = bytes - resumeOffset
                                    val averageSpeedMbps =
                                        if (elapsedSinceStartMs > 0L && bytesSinceStart > 0L) {
                                            bytesPerMsToMbps(bytesSinceStart, elapsedSinceStartMs)
                                        } else {
                                            null
                                        }
                                    lastSpeedSampleAtMs = nowMs
                                    lastSpeedSampleBytes = bytes
                                    lastProgressAtMs = nowMs
                                    lastProgressBytes = bytes
                                    onProgress(
                                        OamDownloadProgress(
                                            phase = "DOWNLOADING",
                                            detail = progressDetail,
                                            bytesDone = bytes,
                                            totalBytes = expectedTotalBytes,
                                        ),
                                    )
                                    logDownloadSpeed(
                                        event = "download_progress",
                                        label = label,
                                        fileName = safeName,
                                        bytesDone = bytes,
                                        totalBytes = expectedTotalBytes,
                                        currentSpeedMbps = currentSpeedMbps,
                                        averageSpeedMbps = averageSpeedMbps,
                                    )
                                },
                                options =
                                    AtomicStreamWriter.Options(
                                        bufferSize = bufferSize,
                                        progressStepBytes = progressStepBytes,
                                        fsync = fsync,
                                        expectedSize = expectedTotalBytes,
                                        requireExactSize = expectedTotalBytes != null,
                                        resumeOffset = if (append) resumeOffset else 0L,
                                        keepPartialOnCancel = true,
                                        keepPartialOnFailure = true,
                                        computeSha256 = false,
                                    ),
                            )
                        }
                    } finally {
                        stallWatchdog.cancel()
                    }
                    val completedAtMs = System.currentTimeMillis()
                    logDownloadSpeed(
                        event = "download_complete",
                        label = label,
                        fileName = safeName,
                        bytesDone = finalFile.length().coerceAtLeast(0L),
                        totalBytes = expectedTotalBytes,
                        currentSpeedMbps = null,
                        averageSpeedMbps =
                            bytesPerMsToMbps(
                                bytes = finalFile.length().coerceAtLeast(0L) - resumeOffset,
                                elapsedMs = completedAtMs - downloadStartedAtMs,
                            ),
                    )
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
                            detail = "$progressDetail interrupted",
                            bytesDone = resumeOffset,
                        ),
                    )
                    logDownloadSpeed(
                        event = "download_interrupted",
                        label = label,
                        fileName = safeName,
                        bytesDone = resumeOffset,
                        totalBytes = null,
                        currentSpeedMbps = null,
                        averageSpeedMbps =
                            bytesPerMsToMbps(
                                bytes = resumeOffset - lastSpeedSampleBytes,
                                elapsedMs = System.currentTimeMillis() - lastSpeedSampleAtMs,
                            ),
                    )
                    delay(IO_RETRY_DELAY_MS * ioRetryCount)
                    continue
                } finally {
                    activeConnections -= connection
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

    private fun remoteFileName(url: String): String =
        runCatching { File(URI(url).path).name }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: url.substringAfterLast('/').ifBlank { "download" }

    private fun logDownloadSpeed(
        event: String,
        label: String,
        fileName: String,
        bytesDone: Long,
        totalBytes: Long?,
        currentSpeedMbps: Double?,
        averageSpeedMbps: Double?,
    ) {
        DebugTelemetry.log(
            OAM_DOWNLOAD_TELEMETRY_TAG,
            buildString {
                append("event=").append(event)
                append(" label=").append(label)
                append(" file=").append(fileName)
                append(" bytes=").append(bytesDone.coerceAtLeast(0L))
                append(" total=").append(totalBytes ?: "unknown")
                append(" currentMbps=").append(currentSpeedMbps?.formatSpeed() ?: "na")
                append(" averageMbps=").append(averageSpeedMbps?.formatSpeed() ?: "na")
            },
        )
    }

    private fun bytesPerMsToMbps(
        bytes: Long,
        elapsedMs: Long,
    ): Double? =
        if (bytes > 0L && elapsedMs > 0L) {
            (bytes * 8.0) / elapsedMs / 1000.0
        } else {
            null
        }

    private fun Double.formatSpeed(): String = java.lang.String.format(java.util.Locale.US, "%.2f", this)

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
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile), ZIP_READ_BUFFER_SIZE)).use { zip ->
                while (true) {
                    coroutineContext.ensureActive()
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || !entry.name.endsWith(extension, ignoreCase = true)) {
                        zip.closeEntry()
                        continue
                    }
                    val entryFileName = File(entry.name).name
                    val expectedSize = entry.size.takeIf { it > 0L }
                    val extractStartedAtMs = System.currentTimeMillis()
                    var extractedBytes = 0L
                    logExtraction(
                        event = "extract_start",
                        label = label,
                        zipFileName = zipFile.name,
                        entryFileName = entryFileName,
                        bytesDone = 0L,
                        totalBytes = expectedSize,
                        durationMs = null,
                    )
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
                        extractedBytes = bytes
                        onProgress(
                            OamDownloadProgress(
                                phase = "EXTRACTING",
                                detail = entryFileName,
                                bytesDone = bytes,
                                totalBytes = expectedSize,
                            ),
                        )
                    }
                    logExtraction(
                        event = "extract_complete",
                        label = label,
                        zipFileName = zipFile.name,
                        entryFileName = entryFileName,
                        bytesDone = extractedBytes,
                        totalBytes = expectedSize,
                        durationMs = System.currentTimeMillis() - extractStartedAtMs,
                    )
                    zip.closeEntry()
                    return@withContext entryFileName
                }
            }
            throw IOException("$label ZIP did not contain a $extension file")
        }

    private fun logExtraction(
        event: String,
        label: String,
        zipFileName: String,
        entryFileName: String,
        bytesDone: Long,
        totalBytes: Long?,
        durationMs: Long?,
    ) {
        DebugTelemetry.log(
            OAM_DOWNLOAD_TELEMETRY_TAG,
            buildString {
                append("event=").append(event)
                append(" label=").append(label)
                append(" zip=").append(zipFileName)
                append(" entry=").append(entryFileName)
                append(" bytes=").append(bytesDone.coerceAtLeast(0L))
                append(" total=").append(totalBytes ?: "unknown")
                append(" durationMs=").append(durationMs ?: "na")
            },
        )
    }

    private companion object {
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val STALL_CHECK_INTERVAL_MS = 15_000L
        private const val STALL_RECONNECT_TIMEOUT_MS = 75_000L
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private const val MAX_RANGE_RESTARTS = 1
        private const val MAX_IO_RETRIES = 3
        private const val IO_RETRY_DELAY_MS = 2_000L
        private const val OAM_ZIP_DOWNLOAD_BUFFER_SIZE = 2 * 1024 * 1024
        private const val ZIP_READ_BUFFER_SIZE = 1024 * 1024
        private const val BROUTER_SEGMENTS_BASE_URL = "https://brouter.de/brouter/segments4"
        private const val DEM3_BASE_URL = "https://download.mapsforge.org/maps/dem/dem3"
        private const val USER_AGENT = "GlanceMap-WearOS-OAM-Downloader/1.0 https://www.openandromaps.org"
        private const val OAM_DOWNLOAD_TELEMETRY_TAG = "OamDownload"
    }
}

internal enum class RemoteMetadataComparison {
    SAME,
    CHANGED,
    UNKNOWN,
}

private val OamRemoteFileMetadata.metadataValues: List<Any?>
    get() = listOf(entityTag, lastModifiedMillis, contentLengthBytes)

private fun OamRemoteFileMetadata.isComparable(): Boolean = metadataValues.any { it != null }

internal fun OamRemoteFileMetadata.compareWith(other: OamRemoteFileMetadata): RemoteMetadataComparison =
    when {
        url != other.url -> RemoteMetadataComparison.CHANGED
        entityTag != null && other.entityTag != null && entityTag == other.entityTag -> RemoteMetadataComparison.SAME
        hasSameStableIdentity(other) -> RemoteMetadataComparison.SAME
        entityTag != null && other.entityTag != null -> RemoteMetadataComparison.CHANGED
        lastModifiedMillis != null && other.lastModifiedMillis != null ->
            compareNullableValues(lastModifiedMillis, other.lastModifiedMillis)
        contentLengthBytes != null && other.contentLengthBytes != null ->
            compareNullableValues(contentLengthBytes, other.contentLengthBytes)
        else -> RemoteMetadataComparison.UNKNOWN
    }

private fun OamRemoteFileMetadata.hasSameStableIdentity(other: OamRemoteFileMetadata): Boolean =
    lastModifiedMillis != null &&
        other.lastModifiedMillis != null &&
        contentLengthBytes != null &&
        other.contentLengthBytes != null &&
        lastModifiedMillis == other.lastModifiedMillis &&
        contentLengthBytes == other.contentLengthBytes

private fun Throwable.isHttpNotFound(): Boolean = message?.contains("HTTP 404", ignoreCase = true) == true

private fun <T> compareNullableValues(
    previous: T,
    current: T,
): RemoteMetadataComparison =
    if (previous == current) {
        RemoteMetadataComparison.SAME
    } else {
        RemoteMetadataComparison.CHANGED
    }
