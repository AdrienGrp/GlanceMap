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
    private const val KEY_SIGNATURE = "signature"
    private const val KEY_DIRTY = "dirty"
    private const val KEY_LAST_SCAN_MS = "last_scan_ms"
    private const val EMPTY_SIGNATURE_SENTINEL = "DEM:EMPTY"
    private const val SIGNATURE_MAX_AGE_MS = 5L * 60L * 1000L

    fun markDirty(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_DIRTY, true)
            .apply()
    }

    fun resolveSignature(
        context: Context,
        demRootDir: File,
        maxDepth: Int
    ): String? {
        val now = System.currentTimeMillis()
        val sharedPrefs = prefs(context)
        val cachedSignature = sharedPrefs.getString(KEY_SIGNATURE, null)
        val dirty = sharedPrefs.getBoolean(KEY_DIRTY, cachedSignature == null)
        val lastScanMs = sharedPrefs.getLong(KEY_LAST_SCAN_MS, 0L)
        val isFresh = (now - lastScanMs) <= SIGNATURE_MAX_AGE_MS

        if (!dirty && cachedSignature != null && isFresh) {
            return cachedSignature.takeUnless { it == EMPTY_SIGNATURE_SENTINEL }
        }

        val scannedSignature = scanDemSignature(demRootDir = demRootDir, maxDepth = maxDepth)
        sharedPrefs.edit()
            .putString(KEY_SIGNATURE, scannedSignature ?: EMPTY_SIGNATURE_SENTINEL)
            .putBoolean(KEY_DIRTY, false)
            .putLong(KEY_LAST_SCAN_MS, now)
            .apply()
        return scannedSignature
    }

    private fun scanDemSignature(demRootDir: File, maxDepth: Int): String? {
        if (!demRootDir.exists() || !demRootDir.isDirectory) return null

        var count = 0
        var totalBytes = 0L
        var latestModified = 0L
        demRootDir.walkTopDown()
            .maxDepth(maxDepth)
            .forEach { file ->
                if (!file.isFile) return@forEach
                val lowerName = file.name.lowercase(Locale.ROOT)
                val isDem = lowerName.endsWith(".hgt") || lowerName.endsWith(".hgt.zip")
                if (!isDem) return@forEach

                count += 1
                totalBytes += file.length()
                latestModified = maxOf(latestModified, file.lastModified())
            }

        if (count == 0) return null
        return "count=$count|bytes=$totalBytes|lm=$latestModified"
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
}
