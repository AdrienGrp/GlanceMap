package com.glancemap.glancemapcompanionapp

data class WatchInstalledMap(
    val fileName: String,
    val filePath: String,
    val bbox: String,
)

enum class WatchInstalledCoverageKind {
    POI,
    ROUTING,
}

data class WatchInstalledCoverageArea(
    val fileName: String,
    val filePath: String,
    val bbox: String,
    val kind: WatchInstalledCoverageKind,
)
