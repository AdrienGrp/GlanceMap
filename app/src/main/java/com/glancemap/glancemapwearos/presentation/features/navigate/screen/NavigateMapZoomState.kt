package com.glancemap.glancemapwearos.presentation.features.navigate

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.glancemap.glancemapwearos.core.maps.MAP_ZOOM_REPRESENTATIVE_LATITUDE_DEGREES
import com.glancemap.glancemapwearos.core.maps.mapZoomLevelsForScaleSettings
import org.mapsforge.core.model.LatLong

internal data class NavigateMapZoomState(
    val default: Int,
    val min: Int,
    val max: Int,
    val updateReference: (center: LatLong, viewportWidthPx: Int) -> Unit,
)

@Composable
internal fun rememberNavigateMapZoomState(
    configuration: Configuration,
    density: Density,
    zoomDefaultScaleMeters: Int,
    zoomMinScaleMeters: Int,
    zoomMaxScaleMeters: Int,
): NavigateMapZoomState {
    val fallbackMapViewportWidthPx =
        remember(configuration.screenWidthDp, density.density) {
            with(density) {
                configuration.screenWidthDp.dp
                    .toPx()
                    .toDouble()
            }
        }
    var zoomReferenceLatitude by remember {
        mutableStateOf(MAP_ZOOM_REPRESENTATIVE_LATITUDE_DEGREES)
    }
    var zoomViewportWidthPx by remember { mutableStateOf(0) }
    val mapZoomLevels =
        remember(
            zoomDefaultScaleMeters,
            zoomMinScaleMeters,
            zoomMaxScaleMeters,
            fallbackMapViewportWidthPx,
            zoomReferenceLatitude,
            zoomViewportWidthPx,
        ) {
            mapZoomLevelsForScaleSettings(
                defaultScaleMeters = zoomDefaultScaleMeters,
                minScaleMeters = zoomMinScaleMeters,
                maxScaleMeters = zoomMaxScaleMeters,
                viewportWidthPx =
                    zoomViewportWidthPx
                        .takeIf { it > 0 }
                        ?.toDouble()
                        ?: fallbackMapViewportWidthPx,
                latitudeDegrees = zoomReferenceLatitude,
            )
        }
    return NavigateMapZoomState(
        default = mapZoomLevels.default,
        min = mapZoomLevels.min,
        max = mapZoomLevels.max,
        updateReference = { center, viewportWidthPx ->
            val nextLatitude = center.latitude.coerceIn(-85.0, 85.0)
            if (shouldUpdateZoomReferenceLatitude(zoomReferenceLatitude, nextLatitude)) {
                zoomReferenceLatitude = nextLatitude
            }
            if (viewportWidthPx > 0 && viewportWidthPx != zoomViewportWidthPx) {
                zoomViewportWidthPx = viewportWidthPx
            }
        },
    )
}
