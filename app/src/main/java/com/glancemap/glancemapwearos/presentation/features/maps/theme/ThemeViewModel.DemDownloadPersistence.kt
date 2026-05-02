package com.glancemap.glancemapwearos.presentation.features.maps.theme

import com.glancemap.glancemapwearos.core.maps.Dem3CoverageUtils
import com.glancemap.glancemapwearos.core.service.diagnostics.DemDownloadDiagnostics
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection

internal fun copyDemResponse(
    connection: HttpURLConnection,
    context: DemDownloadContext,
    response: DemDownloadResponse,
) {
    connection.inputStream.use { input ->
        FileOutputStream(context.part, response.append).use { out ->
            input.copyTo(out)
            out.flush()
            runCatching { out.fd.sync() }
        }
    }
    requireCompleteDemPart(context = context, expectedTotalBytes = response.expectedTotalBytes)
}

internal fun promoteAndValidateDemPart(context: DemDownloadContext) {
    context.target.delete()
    if (!context.part.renameTo(context.target)) {
        throw IOException("Failed moving temp DEM file to ${context.target.absolutePath}")
    }
    runCatching { validateDemTileFile(context.target) }
        .onFailure { error ->
            recordDemValidationFailure(context = context, error = error)
            context.target.delete()
        }.getOrThrow()
}

internal fun recordSavedDemTile(
    context: DemDownloadContext,
    response: DemDownloadResponse,
) {
    val resumedFromBytes = if (response.append) context.resumeOffset else 0L
    DemDownloadDiagnostics.record(
        event = "tile_saved",
        detail =
            "tile=${context.tileName} code=${response.code} bytes=${context.target.length()} " +
                "resumedFromBytes=$resumedFromBytes",
    )
}

internal fun createMissingDemMarker(
    target: File,
    demRoot: File,
) {
    val tileId = target.name.removeSuffix(".hgt.zip").removeSuffix(".hgt")
    val marker =
        Dem3CoverageUtils
            .missingTileMarkerCandidates(demRoot = demRoot, tileId = tileId)
            .firstOrNull { it.parentFile == target.parentFile }
            ?: File(target.parentFile ?: demRoot, "$tileId.hgt.missing")
    marker.parentFile?.mkdirs()
    marker.writeText("missing_upstream\n")
}

private fun requireCompleteDemPart(
    context: DemDownloadContext,
    expectedTotalBytes: Long?,
) {
    if (expectedTotalBytes == null || context.part.length() == expectedTotalBytes) return
    DemDownloadDiagnostics.record(
        event = "tile_incomplete",
        detail =
            "tile=${context.tileName} expectedBytes=$expectedTotalBytes " +
                "actualBytes=${context.part.length()}",
    )
    throw IOException("INCOMPLETE_DEM_TILE: expected=$expectedTotalBytes got=${context.part.length()}")
}

private fun recordDemValidationFailure(
    context: DemDownloadContext,
    error: Throwable,
) {
    DemDownloadDiagnostics.record(
        event = "tile_validation_failed",
        detail =
            "tile=${context.tileName} bytes=${context.target.length()} " +
                "error=${error.javaClass.simpleName} message=${error.message.orEmpty().demDiagValue()}",
    )
}
