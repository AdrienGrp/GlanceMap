package com.glancemap.glancemapwearos.presentation.features.navigate

import org.mapsforge.core.model.LatLong

// ✅ Define NavMode ONCE, publicly, in a shared file/package.
enum class NavMode { COMPASS_FOLLOW, NORTH_UP_FOLLOW, PANNING }
enum class GpsFixIndicatorState { UNAVAILABLE, SEARCHING, POOR, GOOD }

data class NavigateUiState(
    val navMode: NavMode = NavMode.COMPASS_FOLLOW,
    val showCalibrationDialog: Boolean = false,
    val currentZoomLevel: Int = 0,
    val lastKnownLocation: LatLong? = null
)
