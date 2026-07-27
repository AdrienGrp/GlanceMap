package com.glancemap.glancemapwearos.data.repository

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.Locale

/** Handles the small POI-file metadata index created for GPX waypoints. */
internal class PoiGpxWaypointLinks(
    private val poiDir: File,
) {
    fun read(path: String): String? {
        val poiFile = File(path)
        if (!poiFile.exists() || !poiFile.isFile) return null
        return readMetadataFileName(poiFile) ?: legacyFileName(poiFile)
    }

    fun find(linkedGpxFileName: String): List<File> {
        val normalizedFileName = normalizeFileName(linkedGpxFileName)
        if (normalizedFileName.isBlank() || !poiDir.exists()) return emptyList()
        val legacyFileName = legacyPoiFileName(normalizedFileName)
        return poiDir
            .listFiles { _, name -> name.endsWith(".poi", ignoreCase = true) }
            .orEmpty()
            .filter { poiFile ->
                readMetadataFileName(poiFile)?.equals(normalizedFileName, ignoreCase = true) == true ||
                    poiFile.name.equals(legacyFileName, ignoreCase = true)
            }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    fun update(
        previousGpxFileName: String,
        newGpxFileName: String,
    ): Int {
        val previousName = normalizeFileName(previousGpxFileName)
        val newName = normalizeFileName(newGpxFileName)
        if (previousName.isBlank() || newName.isBlank() || !poiDir.exists()) return 0
        return poiDir
            .listFiles { _, name -> name.endsWith(".poi", ignoreCase = true) }
            .orEmpty()
            .filter { poiFile ->
                readMetadataFileName(poiFile)?.equals(previousName, ignoreCase = true) == true
            }.count { poiFile ->
                runCatching {
                    SQLiteDatabase
                        .openDatabase(
                            poiFile.absolutePath,
                            null,
                            SQLiteDatabase.OPEN_READWRITE,
                        ).use { db ->
                            db.execSQL(
                                "UPDATE metadata SET value = ? WHERE name = ?",
                                arrayOf(newName, LINKED_GPX_FILE_NAME_KEY),
                            )
                        }
                }.isSuccess
            }
    }

    private fun readMetadataFileName(poiFile: File): String? =
        runCatching {
            SQLiteDatabase
                .openDatabase(poiFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                .use { db ->
                    db
                        .rawQuery(
                            "SELECT value FROM metadata WHERE name = ? LIMIT 1",
                            arrayOf(LINKED_GPX_FILE_NAME_KEY),
                        ).use { cursor ->
                            if (!cursor.moveToFirst()) return@use null
                            cursor
                                .getString(0)
                                ?.trim()
                                ?.takeIf { it.isNotBlank() }
                                ?.let(::normalizeFileName)
                        }
                }
        }.getOrNull()

    private fun legacyFileName(poiFile: File): String? {
        val name = poiFile.name
        if (!name.endsWith(LEGACY_WAYPOINT_SUFFIX, ignoreCase = true)) return null
        return name
            .substring(0, name.length - LEGACY_WAYPOINT_SUFFIX.length)
            .takeIf { it.isNotBlank() }
            ?.plus(".gpx")
    }

    private fun legacyPoiFileName(gpxFileName: String): String {
        val base =
            normalizeFileName(gpxFileName)
                .removeSuffix(".gpx")
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .trim('_')
                .ifBlank { "gpx-waypoints" }
        return "${base}$LEGACY_WAYPOINT_SUFFIX"
    }

    private fun normalizeFileName(value: String): String = File(value).name.trim()

    private companion object {
        const val LINKED_GPX_FILE_NAME_KEY = "linked_gpx_file_name"
        const val LEGACY_WAYPOINT_SUFFIX = "__waypoints.poi"
    }
}
