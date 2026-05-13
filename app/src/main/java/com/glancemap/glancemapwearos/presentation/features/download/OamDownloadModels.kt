package com.glancemap.glancemapwearos.presentation.features.download

data class OamDownloadArea(
    val id: String,
    val continent: String,
    val region: String,
    val mapSizeLabel: String,
    val poiSizeLabel: String,
    val notes: String,
    val contourLabel: String,
    val mapZipUrl: String,
    val poiZipUrl: String,
)

enum class OamBundleChoice(
    val label: String,
    val secondaryLabel: String,
) {
    MAP_ONLY("Map only", "OAM map with contours"),
    MAP_AND_POI("Map + POI", "Map and Mapsforge POI"),
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
                poiSizeLabel = "POI zip",
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
                poiSizeLabel = "POI zip",
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
                poiSizeLabel = "POI zip",
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
                poiSizeLabel = "POI zip",
                notes = "Southern France region",
                contourLabel = "SRTM1/Lidar 10m",
                mapZipUrl = "https://ftp.gwdg.de/pub/misc/openstreetmap/openandromaps/mapsV5/europe/France-South.zip",
                poiZipUrl = "https://ftp.gwdg.de/pub/misc/openstreetmap/openandromaps/pois/mapsforge/europe/France-South.Poi.zip",
            ),
        )
}
