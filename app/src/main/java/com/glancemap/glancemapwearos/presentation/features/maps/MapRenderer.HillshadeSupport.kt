package com.glancemap.glancemapwearos.presentation.features.maps

import org.mapsforge.map.layer.hills.AClasyHillShading
import org.mapsforge.map.layer.hills.AdaptiveClasyHillShading
import java.security.MessageDigest

internal const val WEAR_HILLSHADE_QUALITY_SCALE = 0.5
internal const val WEAR_HILLSHADE_MIN_ZOOM_LEVEL = 8
internal const val WEAR_HILLSHADE_READING_THREADS = 1
internal const val WEAR_HILLSHADE_COMPUTING_THREADS = 1

internal fun createWearHillShadingParams(): AClasyHillShading.ClasyParams =
    AClasyHillShading
        .ClasyParams()
        .setReadingThreadsCount(WEAR_HILLSHADE_READING_THREADS)
        .setComputingThreadsCount(WEAR_HILLSHADE_COMPUTING_THREADS)
        .setPreprocess(false)

internal fun createWearHillShadingAlgorithm(): AdaptiveClasyHillShading =
    AdaptiveClasyHillShading(createWearHillShadingParams(), false)
        .setAdaptiveZoomEnabled(true)
        .setCustomQualityScale(WEAR_HILLSHADE_QUALITY_SCALE)
        .setZoomMinOverride(WEAR_HILLSHADE_MIN_ZOOM_LEVEL)

internal fun shouldRenderHillshadeAtZoom(zoomLevel: Byte): Boolean = zoomLevel.toInt() >= WEAR_HILLSHADE_MIN_ZOOM_LEVEL

internal fun resolveMapRendererHillshadeCacheId(
    baseCacheId: String,
    demSourceId: String,
    demSignature: String,
): String {
    val signature =
        buildString {
            append("BASE:").append(baseCacheId)
            append("|DEM_SOURCE:").append(demSourceId)
            append("|DEM:").append(demSignature)
            append("|HILLSHADE_CACHE:").append(HILLSHADE_TILE_CACHE_SCHEMA_VERSION)
            append("|ALGORITHM:adaptive_no_hq")
            append("|QUALITY:").append(WEAR_HILLSHADE_QUALITY_SCALE)
            append("|ZOOM_MIN:").append(WEAR_HILLSHADE_MIN_ZOOM_LEVEL)
            append("|READERS:").append(WEAR_HILLSHADE_READING_THREADS)
            append("|COMPUTERS:").append(WEAR_HILLSHADE_COMPUTING_THREADS)
            append("|PREPROCESS:false")
        }
    val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray(Charsets.UTF_8))
    val shortHex =
        digest.take(CACHE_HASH_BYTES).joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    return "${CACHE_ID_PREFIX}_hillshade_$shortHex"
}
