package com.glancemap.glancemapwearos.core.cache

import android.content.Context
import java.io.File

data class AppDerivedCacheCleanupResult(
    val deletedTargets: Int,
    val deletedBytes: Long,
)

object AppDerivedCacheCleaner {
    private const val MAP_CACHE_PREFIX = "mapcache"
    private const val RELIEF_OVERLAY_CACHE_DIR_NAME = "relief_overlay"
    private const val LEGACY_SLOPE_OVERLAY_CACHE_DIR_NAME = "slope_overlay"
    private const val DYNAMIC_THEME_PREFIX = "dynamic_theme_"
    private const val DYNAMIC_THEME_SUFFIX = ".xml"
    private const val ELEVATE_RESOURCES_DIR_NAME = "ele-res"

    fun clear(context: Context): AppDerivedCacheCleanupResult {
        val targets = linkedSetOf<File>()
        collectExternalCacheTargets(context.externalCacheDir, targets)
        collectExternalCacheTargets(context.cacheDir, targets)
        collectInternalCacheTargets(context.cacheDir, targets)

        var deletedTargets = 0
        var deletedBytes = 0L

        targets.forEach { target ->
            if (!target.exists()) return@forEach
            deletedBytes += targetSizeBytes(target)
            if (target.deleteRecursively()) {
                deletedTargets += 1
            }
        }

        return AppDerivedCacheCleanupResult(
            deletedTargets = deletedTargets,
            deletedBytes = deletedBytes,
        )
    }

    private fun collectExternalCacheTargets(
        root: File?,
        out: MutableSet<File>,
    ) {
        if (root == null || !root.exists() || !root.isDirectory) return
        root
            .listFiles()
            ?.asSequence()
            ?.filter { candidate ->
                candidate.isDirectory &&
                    (
                        candidate.name.startsWith(MAP_CACHE_PREFIX) ||
                            candidate.name == RELIEF_OVERLAY_CACHE_DIR_NAME ||
                            candidate.name == LEGACY_SLOPE_OVERLAY_CACHE_DIR_NAME
                    )
            }?.forEach(out::add)
    }

    private fun collectInternalCacheTargets(
        root: File?,
        out: MutableSet<File>,
    ) {
        if (root == null || !root.exists() || !root.isDirectory) return
        root
            .listFiles()
            ?.asSequence()
            ?.filter { candidate ->
                (
                    candidate.isFile &&
                        candidate.name.startsWith(DYNAMIC_THEME_PREFIX) &&
                        candidate.name.endsWith(DYNAMIC_THEME_SUFFIX)
                ) ||
                    (candidate.isDirectory && candidate.name == ELEVATE_RESOURCES_DIR_NAME)
            }?.forEach(out::add)
    }

    private fun targetSizeBytes(target: File): Long {
        if (!target.exists()) return 0L
        if (target.isFile) return target.length()

        var total = 0L
        target.walkTopDown().forEach { child ->
            if (child.isFile) {
                total += child.length()
            }
        }
        return total
    }
}
