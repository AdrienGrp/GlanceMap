package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.presentation.features.maps.RotatableMarker

internal data class MarkerRenderState(
    val isVisible: Boolean,
    val headingDeg: Float,
)

internal fun markerRenderStateForMode(
    navMode: NavMode,
    displayedHeadingDeg: Float,
    displayedMapRotationDeg: Float,
    frozenMapRotationDeg: Float,
    showRealMarkerInCompassMode: Boolean,
): MarkerRenderState =
    when (navMode) {
        NavMode.COMPASS_FOLLOW ->
            MarkerRenderState(
                isVisible = showRealMarkerInCompassMode,
                headingDeg = 0f,
            )
        NavMode.NORTH_UP_FOLLOW ->
            MarkerRenderState(
                isVisible = true,
                headingDeg = normalizeMarkerHeading360(displayedHeadingDeg + displayedMapRotationDeg),
            )
        NavMode.PANNING ->
            MarkerRenderState(
                isVisible = true,
                headingDeg = normalizeMarkerHeading360(displayedHeadingDeg + frozenMapRotationDeg),
            )
    }

internal fun applyMarkerRenderState(
    marker: RotatableMarker?,
    state: MarkerRenderState,
    requestRedraw: Boolean = false,
) {
    marker ?: return
    marker.isVisible = state.isVisible
    marker.heading = state.headingDeg
    if (requestRedraw && state.isVisible) {
        marker.requestRedraw()
    }
}

private fun normalizeMarkerHeading360(deg: Float): Float = (deg % 360f + 360f) % 360f
