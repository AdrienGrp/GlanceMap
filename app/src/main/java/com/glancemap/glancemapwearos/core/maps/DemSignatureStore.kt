package com.glancemap.glancemapwearos.core.maps

import android.content.Context
import java.io.File
import java.util.Locale

/**
 * Caches DEM directory signature and refreshes it only when marked dirty
 * or when the cache ages out, to avoid scanning DEM files on every map refresh.
 */
object DemSignatureStore {
    private const val PREFS_NAME = "dem_signature_store"
    private const val KEY_SIGNATURE_PREFIX = "signature_"
    private const val KEY_LAST_SCAN_MS_PREFIX = "last_scan_ms_"
    private const val EMPTY_SIGNATURE_SENTINEL = "DEM:EMPTY"
    private const val SIGNATURE_MAX_AGE_MS = 5L * 60L * 1000L

    fun markDirty(context: Context) {
        prefs(context)
            .edit()
            .clear()
            .apply()
    }

    fun resolveSignature(
        context: Context,
        demRootDir: File,
        maxDepth: Int,
    ): String? =
        resolveSignature(
            context = context,
            demRootDirs = listOf(demRootDir),
            maxDepth = maxDepth,
        )

    fun resolveSignature(
        context: Context,
        demRootDirs: List<File>,
        maxDepth: Int,
    ): String? {
        val now = System.currentTimeMillis()
        val sharedPrefs = prefs(context)
        val cacheKey = cacheKeyFor(demRootDirs)
        val cachedSignature = sharedPrefs.getString(KEY_SIGNATURE_PREFIX + cacheKey, null)
        val lastScanMs = sharedPrefs.getLong(KEY_LAST_SCAN_MS_PREFIX + cacheKey, 0L)
        val isFresh = (now - lastScanMs) <= SIGNATURE_MAX_AGE_MS

        if (cachedSignature != null && isFresh) {
            return cachedSignature.takeUnless { it == EMPTY_SIGNATURE_SENTINEL }
        }

        val scannedSignature = scanDemSignature(demRootDirs = demRootDirs, maxDepth = maxDepth)
        sharedPrefs
            .edit()
            .putString(KEY_SIGNATURE_PREFIX + cacheKey, scannedSignature ?: EMPTY_SIGNATURE_SENTINEL)
            .putLong(KEY_LAST_SCAN_MS_PREFIX + cacheKey, now)
            .apply()
        return scannedSignature
    }

    private fun cacheKeyFor(demRootDirs: List<File>): String =
        demRootDirs
            .map { it.absolutePath }
            .sorted()
            .joinToString("|")
            .hashCode()
            .toString()

    private fun scanDemSignature(
        demRootDirs: List<File>,
        maxDepth: Int,
    ): String? {
        var count = 0
        var totalBytes = 0L
        var latestModified = 0L
        demRootDirs.forEach { demRootDir ->
            if (!demRootDir.exists() || !demRootDir.isDirectory) return@forEach
            demRootDir
                .walkTopDown()
                .maxDepth(maxDepth)
                .forEach { file ->
                    if (!file.isFile) return@forEach
                    val lowerName = file.name.lowercase(Locale.ROOT)
                    val isDem =
                        lowerName.endsWith(".hgt") ||
                            lowerName.endsWith(".hgt.zip") ||
                            lowerName.endsWith(".hgt.gz") ||
                            lowerName.endsWith(".hgt.missing")
                    if (!isDem) return@forEach

                    count += 1
                    totalBytes += file.length()
                    latestModified = maxOf(latestModified, file.lastModified())
                }
        }

        if (count == 0) return null
        return "count=$count|bytes=$totalBytes|lm=$latestModified"
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )
}
