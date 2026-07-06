package com.glancemap.glancemapwearos.presentation.features.navigate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.glancemap.glancemapwearos.presentation.features.gpx.GpxTrackDetails
import com.glancemap.glancemapwearos.presentation.features.maps.MapViewModel
import com.glancemap.glancemapwearos.presentation.features.offline.OfflineStartCenteringEffect
import com.glancemap.glancemapwearos.presentation.features.poi.PoiNavigateTarget
import kotlinx.coroutines.delay
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.view.MapView

@Composable
internal fun NavigateStartupCenteringEffects(
    offlineMode: Boolean,
    shouldTrackLocation: Boolean,
    locationMarkerLatLong: LatLong?,
    lastKnownLocation: LatLong?,
    navigateTarget: PoiNavigateTarget?,
    pendingPoiFocusTarget: PoiNavigateTarget?,
    mapView: MapView,
    mapViewModel: MapViewModel,
    selectedMapPath: String?,
    activeGpxDetails: List<GpxTrackDetails>,
    navigationMarkerAnchorMode: String,
): LatLong? {
    val gpsStartupMapCenteringPending =
        !offlineMode &&
            shouldTrackLocation &&
            locationMarkerLatLong == null &&
            lastKnownLocation == null
    var gpsStartupMapFallbackAllowed by remember { mutableStateOf(false) }
    LaunchedEffect(gpsStartupMapCenteringPending) {
        gpsStartupMapFallbackAllowed = false
        if (gpsStartupMapCenteringPending) {
            delay(NORMAL_STARTUP_MAP_FALLBACK_GRACE_MS)
            gpsStartupMapFallbackAllowed = true
        }
    }
    val gpsStartupMapCenteringActive =
        gpsStartupMapCenteringPending &&
            gpsStartupMapFallbackAllowed
    val gpsStartupLastKnownCenter =
        lastKnownLocation.takeIf {
            !offlineMode &&
                shouldTrackLocation &&
                locationMarkerLatLong == null &&
                navigateTarget == null &&
                pendingPoiFocusTarget == null
        }

    LaunchedEffect(gpsStartupLastKnownCenter, mapView, navigationMarkerAnchorMode) {
        gpsStartupLastKnownCenter?.let {
            mapView.setCenterForNavigationMarker(it, navigationMarkerAnchorMode)
        }
    }

    OfflineStartCenteringEffect(
        isOfflineMode = offlineMode,
        mapView = mapView,
        mapViewModel = mapViewModel,
        selectedMapPath = selectedMapPath,
        activeGpxDetails = activeGpxDetails,
        skipInitialCentering = navigateTarget != null || pendingPoiFocusTarget != null,
        enabled = offlineMode || gpsStartupMapCenteringActive,
    )

    return if (offlineMode) {
        null
    } else {
        locationMarkerLatLong ?: lastKnownLocation
    }
}

private const val NORMAL_STARTUP_MAP_FALLBACK_GRACE_MS = 15_000L
