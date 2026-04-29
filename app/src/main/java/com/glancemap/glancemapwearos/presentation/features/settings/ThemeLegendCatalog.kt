package com.glancemap.glancemapwearos.presentation.features.settings

import com.glancemap.glancemapwearos.domain.model.maps.theme.mapsforge.MapsforgeThemeCatalog

internal data class ThemeLegendSpec(
    val title: String,
    val assetPath: String,
)

internal object ThemeLegendCatalog {
    fun legendFor(
        themeId: String?,
        styleId: String?,
    ): ThemeLegendSpec? =
        when {
            MapsforgeThemeCatalog.isMapsforgeFamilyTheme(themeId) ||
                MapsforgeThemeCatalog.isMapsforgeStyleId(styleId) -> {
                ThemeLegendSpec(
                    title = "Mapsforge Legend",
                    assetPath = "theme-legends/mapsforge.md",
                )
            }

            MapsforgeThemeCatalog.isElevateFamilyTheme(themeId) -> {
                ThemeLegendSpec(
                    title =
                        if (themeId == MapsforgeThemeCatalog.ELEVATE_THEME_ID) {
                            "Elevate Legend"
                        } else {
                            "Elevate Winter Legend"
                        },
                    assetPath = "theme-legends/elevate.md",
                )
            }

            themeId == MapsforgeThemeCatalog.OPENHIKING_THEME_ID -> {
                ThemeLegendSpec(
                    title = "OpenHiking Legend",
                    assetPath = "theme-legends/openhiking.md",
                )
            }

            themeId == MapsforgeThemeCatalog.FRENCH_KISS_THEME_ID -> {
                ThemeLegendSpec(
                    title = "French Kiss Legend",
                    assetPath = "theme-legends/frenchkiss.md",
                )
            }

            themeId == MapsforgeThemeCatalog.TIRAMISU_THEME_ID -> {
                ThemeLegendSpec(
                    title = "Tiramisu Legend",
                    assetPath = "theme-legends/tiramisu.md",
                )
            }

            themeId == MapsforgeThemeCatalog.HIKE_RIDE_SIGHT_THEME_ID -> {
                ThemeLegendSpec(
                    title = "Hike, Ride & Sight Legend",
                    assetPath = "theme-legends/hike-ride-sight.md",
                )
            }

            else -> null
        }
}
