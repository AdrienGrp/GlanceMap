package com.glancemap.glancemapwearos.presentation.features.download

data class OamDownloadArea(
    val id: String,
    val continent: String,
    val region: String,
    val mapSizeLabel: String,
    val mapSizeBytes: Long,
    val poiSizeLabel: String,
    val poiSizeBytes: Long,
    val notes: String,
    val contourLabel: String,
    val mapZipUrl: String,
    val poiZipUrl: String,
)

enum class OamBundleChoice(
    val label: String,
    val secondaryLabel: String,
    val includeMap: Boolean,
    val includePoi: Boolean,
) {
    MAP_ONLY("Map only", "OAM map with contours", includeMap = true, includePoi = false),
    POI_ONLY("POI only", "Mapsforge POI", includeMap = false, includePoi = true),
    MAP_AND_POI("Map + POI", "Map and Mapsforge POI", includeMap = true, includePoi = true),
}

data class OamDownloadSelection(
    val includeMap: Boolean = true,
    val includePoi: Boolean = true,
    val includeRouting: Boolean = false,
) {
    val canDownload: Boolean
        get() = includeMap || includePoi

    fun toBundleChoice(): OamBundleChoice =
        when {
            includeMap && includePoi -> OamBundleChoice.MAP_AND_POI
            includeMap -> OamBundleChoice.MAP_ONLY
            includePoi -> OamBundleChoice.POI_ONLY
            else -> OamBundleChoice.MAP_AND_POI
        }

    fun label(): String =
        when {
            includeMap && includePoi -> "Map + POI"
            includeMap -> "Map only"
            includePoi -> "POI only"
            else -> "Nothing selected"
        }
}

data class OamInstalledBundle(
    val areaId: String,
    val areaLabel: String,
    val bundleChoice: OamBundleChoice,
    val mapFileName: String?,
    val poiFileName: String?,
    val installedAtMillis: Long,
)

object OamDownloadCatalog {
    val areas: List<OamDownloadArea> =
        listOf(
            OamDownloadArea(
                id = "andorra",
                continent = "Europe",
                region = "Andorra",
                mapSizeLabel = "26 MB",
                mapSizeBytes = 26L * 1024 * 1024,
                poiSizeLabel = "1 MB",
                poiSizeBytes = 1L * 1024 * 1024,
                notes = "Compact mountain test area",
                contourLabel = "SRTM1/Lidar 10m",
                mapZipUrl = "https://ftp.gwdg.de/pub/misc/openstreetmap/openandromaps/mapsV5/europe/Andorra.zip",
                poiZipUrl = "https://ftp.gwdg.de/pub/misc/openstreetmap/openandromaps/pois/mapsforge/europe/Andorra.Poi.zip",
            ),
            OamDownloadArea(
                id = "alps",
                continent = "Europe",
                region = "Alps",
                mapSizeLabel = "2777 MB",
                mapSizeBytes = 2_777L * 1024 * 1024,
                poiSizeLabel = "28 MB",
                poiSizeBytes = 28L * 1024 * 1024,
                notes = "Large contour bundle",
                contourLabel = "SRTM1/Lidar 10m",
                mapZipUrl = "https://ftp.gwdg.de/pub/misc/openstreetmap/openandromaps/mapsV5/europe/Alps.zip",
                poiZipUrl = "https://ftp.gwdg.de/pub/misc/openstreetmap/openandromaps/pois/mapsforge/europe/Alps.Poi.zip",
            ),
            OamDownloadArea(
                id = "pyrenees",
                continent = "Europe",
                region = "Pyrenees",
                mapSizeLabel = "1014 MB",
                mapSizeBytes = 1_014L * 1024 * 1024,
                poiSizeLabel = "14 MB",
                poiSizeBytes = 14L * 1024 * 1024,
                notes = "North Spain and Jakobsweg",
                contourLabel = "SRTM1/Lidar 10m",
                mapZipUrl = "https://ftp.gwdg.de/pub/misc/openstreetmap/openandromaps/mapsV5/europe/Pyrenees.zip",
                poiZipUrl = "https://ftp.gwdg.de/pub/misc/openstreetmap/openandromaps/pois/mapsforge/europe/Pyrenees.Poi.zip",
            ),
            OamDownloadArea(
                id = "france-south",
                continent = "Europe",
                region = "France-South",
                mapSizeLabel = "1907 MB",
                mapSizeBytes = 1_907L * 1024 * 1024,
                poiSizeLabel = "24 MB",
                poiSizeBytes = 24L * 1024 * 1024,
                notes = "Southern France region",
                contourLabel = "SRTM1/Lidar 10m",
                mapZipUrl = "https://ftp.gwdg.de/pub/misc/openstreetmap/openandromaps/mapsV5/europe/France-South.zip",
                poiZipUrl = "https://ftp.gwdg.de/pub/misc/openstreetmap/openandromaps/pois/mapsforge/europe/France-South.Poi.zip",
            ),
        )
}
