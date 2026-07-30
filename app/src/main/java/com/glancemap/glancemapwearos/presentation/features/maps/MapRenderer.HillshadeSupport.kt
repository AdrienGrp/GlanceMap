package com.glancemap.glancemapwearos.presentation.features.maps

import org.mapsforge.map.layer.hills.AClasyHillShading
import org.mapsforge.map.layer.hills.AdaptiveClasyHillShading
import org.mapsforge.map.layer.hills.HgtFileInfo
import java.security.MessageDigest

internal const val WEAR_HILLSHADE_QUALITY_SCALE = 0.5
internal const val WEAR_HILLSHADE_MIN_ZOOM_LEVEL = 10
internal const val WEAR_HILLSHADE_MAX_OUTPUT_AXIS = 1800
internal const val WEAR_HILLSHADE_READING_THREADS = 1
internal const val WEAR_HILLSHADE_COMPUTING_THREADS = 1

internal fun createWearHillShadingParams(): AClasyHillShading.ClasyParams =
    AClasyHillShading
        .ClasyParams()
        .setReadingThreadsCount(WEAR_HILLSHADE_READING_THREADS)
        .setComputingThreadsCount(WEAR_HILLSHADE_COMPUTING_THREADS)
        .setPreprocess(false)

internal fun createWearHillShadingAlgorithm(): AdaptiveClasyHillShading =
    WearAdaptiveClasyHillShading(createWearHillShadingParams())
        .setAdaptiveZoomEnabled(true)
        .setCustomQualityScale(WEAR_HILLSHADE_QUALITY_SCALE)
        .setZoomMinOverride(WEAR_HILLSHADE_MIN_ZOOM_LEVEL)

internal fun shouldRenderHillshadeAtZoom(zoomLevel: Byte): Boolean = zoomLevel.toInt() >= WEAR_HILLSHADE_MIN_ZOOM_LEVEL

internal fun resolveWearHillshadeQualityFactor(
    inputAxisLen: Int,
    adaptiveQualityFactor: Int,
): Int {
    val adaptiveOutputAxis =
        AdaptiveClasyHillShading.scaleByQualityFactor(
            inputAxisLen,
            adaptiveQualityFactor,
        )
    if (adaptiveOutputAxis <= WEAR_HILLSHADE_MAX_OUTPUT_AXIS) return adaptiveQualityFactor

    var stride =
        ((inputAxisLen + WEAR_HILLSHADE_MAX_OUTPUT_AXIS - 1) / WEAR_HILLSHADE_MAX_OUTPUT_AXIS)
            .coerceAtLeast(1)
    while (stride < inputAxisLen && inputAxisLen % stride != 0) {
        stride += 1
    }
    return -stride
}

private class WearAdaptiveClasyHillShading(
    params: AClasyHillShading.ClasyParams,
) : AdaptiveClasyHillShading(params, false) {
    override fun getQualityFactor(
        hgtFileInfo: HgtFileInfo,
        zoomLevel: Int,
        pxPerLat: Double,
        pxPerLon: Double,
    ): Int =
        resolveWearHillshadeQualityFactor(
            inputAxisLen = getInputAxisLen(hgtFileInfo),
            adaptiveQualityFactor =
                super.getQualityFactor(
                    hgtFileInfo,
                    zoomLevel,
                    pxPerLat,
                    pxPerLon,
                ),
        )
}

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
            append("|MAX_OUTPUT_AXIS:").append(WEAR_HILLSHADE_MAX_OUTPUT_AXIS)
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
