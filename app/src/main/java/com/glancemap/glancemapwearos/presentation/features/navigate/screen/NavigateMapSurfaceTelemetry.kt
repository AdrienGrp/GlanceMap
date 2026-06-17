package com.glancemap.glancemapwearos.presentation.features.navigate

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.unit.IntSize
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import org.mapsforge.map.android.view.MapView
import java.util.Locale

internal fun navigationMapViewLayoutParams(
    expandedMapSurfaceEnabled: Boolean,
    expandedMapSurfaceHeightPx: Int,
): FrameLayout.LayoutParams =
    FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        if (expandedMapSurfaceEnabled) {
            expandedMapSurfaceHeightPx
        } else {
            ViewGroup.LayoutParams.MATCH_PARENT
        },
        Gravity.TOP,
    )

internal data class NavigationMapSurfaceTelemetryState(
    val visibleMapSizePx: IntSize,
    val navigationMarkerAnchorMode: String,
    val expandedMapSurfaceEnabled: Boolean,
    val targetMapSurfaceHeightPx: Int,
)

internal fun logNavigationMapSurfaceTelemetryIfChanged(
    mapView: MapView,
    visibleContainer: View,
    state: NavigationMapSurfaceTelemetryState,
    lastSignature: String?,
    onLogged: (String) -> Unit,
) {
    if (!DebugTelemetry.isEnabled()) return
    val anchor = mapView.resolveNavigationMarkerScreenAnchor(state.navigationMarkerAnchorMode)
    val signature =
        buildNavigationMapSurfaceTelemetrySignature(
            state = state,
            visibleContainer = visibleContainer,
            mapView = mapView,
            anchor = anchor,
        )
    if (signature == lastSignature) return
    onLogged(signature)
    val center = mapView.model.mapViewPosition.center
    DebugTelemetry.log(
        "NavigationTelemetry",
        "event=map_surface_geometry " +
            "anchorMode=${state.navigationMarkerAnchorMode} " +
            "surface=${if (state.expandedMapSurfaceEnabled) "expanded" else "normal"} " +
            "visibleState=${state.visibleMapSizePx.width}x${state.visibleMapSizePx.height} " +
            "container=${visibleContainer.width}x${visibleContainer.height} " +
            "mapView=${mapView.width}x${mapView.height} " +
            "targetChildHeight=${state.targetMapSurfaceHeightPx} " +
            "pivot=${anchor.x.formatTelemetryDouble()},${anchor.y.formatTelemetryDouble()} " +
            "rotation=${mapView.mapRotation.degrees.formatTelemetryFloat()} " +
            "center=${center.latitude.formatTelemetryDouble()},${center.longitude.formatTelemetryDouble()}",
    )
}

private fun buildNavigationMapSurfaceTelemetrySignature(
    state: NavigationMapSurfaceTelemetryState,
    visibleContainer: View,
    mapView: MapView,
    anchor: ScreenAnchor,
): String =
    listOf(
        state.navigationMarkerAnchorMode,
        state.expandedMapSurfaceEnabled,
        state.visibleMapSizePx.width,
        state.visibleMapSizePx.height,
        visibleContainer.width,
        visibleContainer.height,
        mapView.width,
        mapView.height,
        state.targetMapSurfaceHeightPx,
        anchor.x.toInt(),
        anchor.y.toInt(),
        mapView.mapRotation.degrees.toInt(),
    ).joinToString(separator = "|")

private fun Double.formatTelemetryDouble(): String = "%.5f".format(Locale.US, this)

private fun Float.formatTelemetryFloat(): String = "%.2f".format(Locale.US, this)
