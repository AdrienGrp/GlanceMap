package com.glancemap.glancemapwearos.presentation.features.offline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.glancemap.glancemapwearos.presentation.features.gpx.GpxTrackDetails
import com.glancemap.glancemapwearos.presentation.features.maps.MapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.reader.MapFile
import java.io.File

@Composable
internal fun OfflineStartCenteringEffect(
    isOfflineMode: Boolean,
    mapView: MapView,
    mapViewModel: MapViewModel,
    selectedMapPath: String?,
    activeGpxDetails: List<GpxTrackDetails>,
    skipInitialCentering: Boolean = false,
) {
    LaunchedEffect(isOfflineMode) {
        if (!isOfflineMode) {
            mapViewModel.resetOfflineStartCenterTracking()
        }
    }

    LaunchedEffect(
        isOfflineMode,
        selectedMapPath,
        activeGpxDetails,
        skipInitialCentering,
        mapViewModel,
        mapView,
    ) {
        if (!isOfflineMode) return@LaunchedEffect
        val forceStartupCenter =
            mapViewModel.shouldForceOfflineStartCenter(
                selectedMapPath = selectedMapPath,
                activeGpxDetails = activeGpxDetails,
            )
        if (!skipInitialCentering && !forceStartupCenter) {
            mapViewModel.restoreOfflineViewport(selectedMapPath, activeGpxDetails)?.let { (center, zoomLevel) ->
                mapView.model.mapViewPosition.setZoomLevel(zoomLevel.toByte(), false)
                mapView.setCenter(center)
                mapViewModel.markOfflineStartCenterHandled(selectedMapPath, activeGpxDetails)
                return@LaunchedEffect
            }
        }
        if (!mapViewModel.shouldApplyOfflineStartCenter(selectedMapPath, activeGpxDetails)) {
            return@LaunchedEffect
        }
        if (skipInitialCentering) {
            mapViewModel.markOfflineStartCenterHandled(selectedMapPath, activeGpxDetails)
            if (forceStartupCenter) {
                mapViewModel.consumeForcedOfflineStartCenter(
                    selectedMapPath = selectedMapPath,
                    activeGpxDetails = activeGpxDetails,
                )
            }
            return@LaunchedEffect
        }

        val targetCenter =
            withContext(Dispatchers.IO) {
                if (forceStartupCenter && !selectedMapPath.isNullOrBlank()) {
                    resolveSelectedMapCenter(selectedMapPath)
                } else {
                    resolveOfflineStartCenter(selectedMapPath, activeGpxDetails)
                }
            }
        targetCenter?.let { mapView.setCenter(it) }
        mapViewModel.markOfflineStartCenterHandled(selectedMapPath, activeGpxDetails)
        if (forceStartupCenter) {
            mapViewModel.consumeForcedOfflineStartCenter(
                selectedMapPath = selectedMapPath,
                activeGpxDetails = activeGpxDetails,
            )
        }
    }
}

private fun resolveOfflineStartCenter(
    selectedMapPath: String?,
    activeGpxDetails: List<GpxTrackDetails>,
): LatLong? = resolveSelectedMapCenter(selectedMapPath) ?: resolveActiveGpxCenter(activeGpxDetails)

private fun resolveSelectedMapCenter(selectedMapPath: String?): LatLong? {
    val path = selectedMapPath?.takeIf { it.isNotBlank() } ?: return null
    val mapFile = File(path)
    if (!mapFile.exists() || !mapFile.isFile) return null

    return runCatching {
        val map = MapFile(mapFile)
        val bbox =
            try {
                map.boundingBox()
            } finally {
                runCatching { map.close() }
            }
        LatLong(
            (bbox.minLatitude + bbox.maxLatitude) * 0.5,
            (bbox.minLongitude + bbox.maxLongitude) * 0.5,
        )
    }.getOrNull()
}

private fun resolveActiveGpxCenter(activeGpxDetails: List<GpxTrackDetails>): LatLong? {
    if (activeGpxDetails.isEmpty()) return null

    var minLat = Double.POSITIVE_INFINITY
    var maxLat = Double.NEGATIVE_INFINITY
    var minLon = Double.POSITIVE_INFINITY
    var maxLon = Double.NEGATIVE_INFINITY
    var hasPoint = false

    activeGpxDetails.forEach { track ->
        track.points.forEach { point ->
            if (!point.latitude.isFinite() || !point.longitude.isFinite()) return@forEach
            hasPoint = true
            if (point.latitude < minLat) minLat = point.latitude
            if (point.latitude > maxLat) maxLat = point.latitude
            if (point.longitude < minLon) minLon = point.longitude
            if (point.longitude > maxLon) maxLon = point.longitude
        }
    }

    if (!hasPoint) return null
    return LatLong(
        (minLat + maxLat) * 0.5,
        (minLon + maxLon) * 0.5,
    )
}
