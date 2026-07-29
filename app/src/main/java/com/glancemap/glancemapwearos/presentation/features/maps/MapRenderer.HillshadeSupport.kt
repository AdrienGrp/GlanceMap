package com.glancemap.glancemapwearos.presentation.features.maps

import org.mapsforge.map.layer.hills.AdaptiveClasyHillShading
import java.security.MessageDigest

internal const val WEAR_HILLSHADE_QUALITY_SCALE = 0.5

internal fun createWearHillShadingAlgorithm(): AdaptiveClasyHillShading =
    AdaptiveClasyHillShading(false)
        .setAdaptiveZoomEnabled(true)
        .setCustomQualityScale(WEAR_HILLSHADE_QUALITY_SCALE)

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
        }
    val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray(Charsets.UTF_8))
    val shortHex =
        digest.take(CACHE_HASH_BYTES).joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    return "${CACHE_ID_PREFIX}_hillshade_$shortHex"
}
