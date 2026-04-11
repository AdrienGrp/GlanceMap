package com.glancemap.glancemapwearos.presentation.features.maps

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

internal const val DISK_CACHE_SCHEMA_VERSION = 2
internal const val DISK_CACHE_MAX_FILES = 1200
internal const val DISK_CACHE_SOFT_LIMIT_BYTES = 64L * 1024L * 1024L
internal const val DISK_CACHE_TARGET_LIMIT_BYTES = 56L * 1024L * 1024L
internal const val DISK_CACHE_CLEANUP_INTERVAL_MS = 60_000L

internal class ReliefOverlayDiskCache(
    diskCacheRootDir: File?,
    cacheNamespace: String,
    private val tag: String
) {
    private val normalizedCacheNamespace: String = sanitizeCacheNamespace(cacheNamespace)
    private val overlayDiskCacheDir: File? = diskCacheRootDir?.let {
        File(it, "${normalizedCacheNamespace}_v$DISK_CACHE_SCHEMA_VERSION")
    }

    private val diskCleanupLock = Any()

    @Volatile
    private var diskCacheCleanupInProgress: Boolean = false

    private var lastDiskCacheCleanupElapsedMs: Long = 0L

    fun loadOverlayTileEntryFromDisk(key: OverlayTileKey): OverlayTileEntry? {
        val file = overlayTileDiskFile(key) ?: return null
        if (!file.exists() || !file.isFile) return null

        val decoded = runCatching {
            BitmapFactory.Options().apply { inScaled = false }.let { options ->
                BitmapFactory.decodeFile(file.absolutePath, options)
            } ?: return@runCatching null
        }.getOrNull() ?: return null

        if (decoded.width != key.tileSize || decoded.height != key.tileSize) {
            runCatching { decoded.recycle() }
            runCatching { file.delete() }
            return null
        }

        val mapsforgeBitmap = runCatching {
            val out = AndroidGraphicFactory.INSTANCE.createBitmap(key.tileSize, key.tileSize, true)
            val pixels = IntArray(key.tileSize * key.tileSize)
            decoded.getPixels(pixels, 0, key.tileSize, 0, 0, key.tileSize, key.tileSize)
            AndroidGraphicFactory.getBitmap(out).setPixels(
                pixels,
                0,
                key.tileSize,
                0,
                0,
                key.tileSize,
                key.tileSize
            )
            out
        }.onFailure {
            runCatching { decoded.recycle() }
        }.getOrNull()

        runCatching { decoded.recycle() }
        if (mapsforgeBitmap == null) {
            runCatching { file.delete() }
            return null
        }

        return OverlayTileEntry(
            bitmap = mapsforgeBitmap,
            builtElapsedMs = SystemClock.elapsedRealtime(),
            status = OverlayTileStatus.READY,
            drawMode = OverlayTileDrawMode.STEADY,
            quality = OverlayBuildQuality.FINE
        )
    }

    fun persistOverlayTileEntryToDisk(key: OverlayTileKey, entry: OverlayTileEntry) {
        if (entry.status != OverlayTileStatus.READY) return
        if (entry.quality != OverlayBuildQuality.FINE) return
        val bitmap = entry.bitmap ?: return
        val file = overlayTileDiskFile(key) ?: return
        val parent = file.parentFile ?: return

        runCatching {
            if (!parent.exists()) parent.mkdirs()
            val temp = File(parent, "${file.name}.tmp")
            FileOutputStream(temp).buffered().use { out ->
                AndroidGraphicFactory.getBitmap(bitmap)
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            if (!temp.renameTo(file)) {
                temp.delete()
            } else {
                file.setLastModified(System.currentTimeMillis())
            }
            maybeCleanupOverlayDiskCacheAsync()
        }.onFailure { error ->
            Log.d(tag, "Failed to persist relief overlay tile cache: ${file.absolutePath}", error)
        }
    }

    private fun overlayTileDiskFile(key: OverlayTileKey): File? {
        val root = overlayDiskCacheDir ?: return null
        val zoomDir = File(root, key.zoom.toString())
        val xDir = File(zoomDir, key.tileX.toString())
        return File(xDir, "${key.tileY}_${key.tileSize}.png")
    }

    private fun maybeCleanupOverlayDiskCacheAsync() {
        val root = overlayDiskCacheDir ?: return
        val nowElapsedMs = SystemClock.elapsedRealtime()
        synchronized(diskCleanupLock) {
            if ((nowElapsedMs - lastDiskCacheCleanupElapsedMs) < DISK_CACHE_CLEANUP_INTERVAL_MS) {
                return
            }
            if (diskCacheCleanupInProgress) return
            lastDiskCacheCleanupElapsedMs = nowElapsedMs
            diskCacheCleanupInProgress = true
        }

        Thread(
            {
                try {
                    cleanupOverlayDiskCache(root)
                } catch (error: Throwable) {
                    Log.d(tag, "Failed to cleanup relief overlay disk cache", error)
                } finally {
                    diskCacheCleanupInProgress = false
                }
            },
            "relief-overlay-cache-cleanup"
        ).apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 2
        }.start()
    }

    private fun cleanupOverlayDiskCache(root: File) {
        if (!root.exists() || !root.isDirectory) return
        val files = runCatching {
            root.walkTopDown()
                .maxDepth(4)
                .filter { file -> file.isFile && file.extension.equals("png", ignoreCase = true) }
                .toList()
        }.getOrElse { emptyList() }
        if (files.isEmpty()) return

        var remainingFiles = files.size
        var remainingBytes = files.sumOf { file ->
            runCatching { file.length() }.getOrDefault(0L)
        }
        if (remainingFiles <= DISK_CACHE_MAX_FILES && remainingBytes <= DISK_CACHE_SOFT_LIMIT_BYTES) {
            return
        }

        files
            .sortedBy { it.lastModified() }
            .forEach { file ->
                if (remainingFiles <= DISK_CACHE_MAX_FILES && remainingBytes <= DISK_CACHE_TARGET_LIMIT_BYTES) {
                    return@forEach
                }
                val sizeBeforeDelete = runCatching { file.length() }.getOrDefault(0L)
                val deleted = runCatching { file.delete() }.getOrDefault(false)
                if (deleted) {
                    remainingFiles = (remainingFiles - 1).coerceAtLeast(0)
                    remainingBytes = (remainingBytes - sizeBeforeDelete).coerceAtLeast(0L)
                }
            }
        pruneEmptyCacheDirs(root)
    }

    private fun pruneEmptyCacheDirs(root: File) {
        if (!root.exists() || !root.isDirectory) return
        root.walkBottomUp()
            .filter { it.isDirectory && it != root }
            .forEach { dir ->
                val children = dir.list()
                if (children == null || children.isEmpty()) {
                    runCatching { dir.delete() }
                }
            }
    }

    private fun sanitizeCacheNamespace(raw: String): String {
        val normalized = raw.trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9._-]"), "_")
            .trim('_')
        return normalized.ifBlank { "default" }
    }
}
