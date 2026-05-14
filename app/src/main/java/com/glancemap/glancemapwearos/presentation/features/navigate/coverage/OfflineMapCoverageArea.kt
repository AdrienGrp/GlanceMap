package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.core.maps.GeoBounds

internal enum class OfflineMapCoverageKind {
    POI,
    ROUTING,
}

internal data class OfflineMapCoverageArea(
    val id: String,
    val kind: OfflineMapCoverageKind,
    val bounds: GeoBounds,
)
