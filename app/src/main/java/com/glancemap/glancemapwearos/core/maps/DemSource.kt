@file:Suppress("LongParameterList")

package com.glancemap.glancemapwearos.core.maps

import android.content.Context
import java.io.File
import java.util.Locale

enum class DemSource(
    val id: String,
    val displayName: String,
    val shortLabel: String,
    val detailLabel: String,
    val rootDirName: String,
    val baseUrl: String,
    val remoteExtension: String,
) {
    MAPSFORGE_DEM3(
        id = "mapsforge_dem3",
        displayName = "Standard terrain",
        shortLabel = "Standard",
        detailLabel = "Smaller files, good for most maps",
        rootDirName = "dem3",
        baseUrl = "https://download.mapsforge.org/maps/dem/dem3",
        remoteExtension = ".hgt.zip",
    ),
    MAPZEN_SKADI_1S(
        id = "mapzen_skadi_1s",
        displayName = "Detailed terrain",
        shortLabel = "Detailed",
        detailLabel = "Sharper hills, much larger files",
        rootDirName = "dem1",
        baseUrl = "https://s3.amazonaws.com/elevation-tiles-prod/skadi",
        remoteExtension = ".hgt.gz",
    ),
    ;

    fun rootDir(context: Context): File =
        context.getExternalFilesDir(rootDirName)
            ?: File(context.getDir("maps", Context.MODE_PRIVATE), rootDirName)

    fun folderForTile(tileId: String): String = tileId.uppercase(Locale.ROOT).substring(0, 3)

    fun remoteFileName(tileId: String): String = tileId.uppercase(Locale.ROOT) + remoteExtension

    fun localFileName(tileId: String): String = remoteFileName(tileId)

    fun remoteUrl(tileId: String): String {
        val safeTileId = tileId.uppercase(Locale.ROOT)
        return "${baseUrl.trimEnd('/')}/${folderForTile(safeTileId)}/${remoteFileName(safeTileId)}"
    }

    companion object {
        val DEFAULT = MAPSFORGE_DEM3
        val LOAD_PRIORITY = listOf(MAPZEN_SKADI_1S, MAPSFORGE_DEM3)

        fun fromId(id: String?): DemSource = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
