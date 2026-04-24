package com.glancemap.glancemapwearos.presentation.features.navigate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.AnchorType
import androidx.wear.compose.foundation.ArcPaddingValues
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.curvedComposable
import androidx.wear.compose.foundation.padding
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.features.maps.RotatableMarker
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteShortcutTray
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolInlineProgressBanner
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import kotlinx.coroutines.delay
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.view.MapView

@Suppress(
    "CyclomaticComplexMethod",
    "FunctionName",
    "LongMethod",
    "LongParameterList",
)
@Composable
internal fun BoxScope.NavigateOverlaysLayer(
    mapView: MapView,
    mapAppearanceApplyInProgress: Boolean,
    slopeOverlayToggleEnabled: Boolean,
    slopeOverlayEnabled: Boolean,
    slopeOverlayProcessing: Boolean,
    slopeOverlayProgressPercent: Int?,
    navMode: NavMode,
    screenSize: WearScreenSize,
    liveElevationEnabled: Boolean,
    liveElevationLabel: String?,
    liveDistanceEnabled: Boolean,
    liveDistanceLabel: String?,
    zoomLabelTopPadding: Dp,
    liveElevationIconSize: Dp,
    northIndicatorMode: String,
    mapRotationDeg: Float,
    compassHeadingDeg: Float,
    northIndicatorButtonSize: Dp,
    northIndicatorIconSize: Dp,
    showZoomPlusButton: Boolean,
    showZoomMinusButton: Boolean,
    currentZoomLevel: Int,
    zoomMin: Int,
    zoomMax: Int,
    triggerHaptic: () -> Unit,
    zoomButtonSize: Dp,
    zoomIconSize: Dp,
    scaleIndicator: ScaleIndicatorUi?,
    showScaleBar: Boolean,
    zoomScaleBarWidth: Dp,
    poiTapMessage: String?,
    poiTapCanExpand: Boolean,
    poiTapCanCreateGpx: Boolean,
    poiTapExpanded: Boolean,
    onPoiTapExpandToggle: () -> Unit,
    onPoiTapCreateGpx: () -> Unit,
    onPoiTapDismiss: () -> Unit,
    onPoiTapScrollInProgressChanged: (Boolean) -> Unit,
    onMenuClick: () -> Unit,
    sideButtonEdgePadding: Dp,
    sideButtonSize: Dp,
    sideButtonIconSize: Dp,
    shortcutTrayExpanded: Boolean,
    routeToolModeActive: Boolean,
    onShortcutTrayToggle: () -> Unit,
    onShortcutTrayDismiss: () -> Unit,
    onGpxToolsClick: () -> Unit,
    onCreatePoiClick: () -> Unit,
    keepAppOpen: Boolean,
    onKeepAppOpenToggle: () -> Unit,
    gpsIndicatorState: GpsFixIndicatorState,
    watchGpsDegradedWarning: Boolean,
    navButtonBottomPadding: Dp,
    navButtonSize: Dp,
    navButtonIconSize: Dp,
    locationMarker: RotatableMarker?,
    lastKnownLocation: LatLong?,
    onRecenter: () -> Unit,
    onRecenterRequested: () -> Unit,
    onToggleOrientation: () -> Unit,
    isOfflineMode: Boolean,
) {
    var liveDistanceLineStart by
        remember(mapView, locationMarker, lastKnownLocation) {
            mutableStateOf<Offset?>(null)
        }
    val slopeIndicatorButtonSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 28.dp
            WearScreenSize.MEDIUM -> 26.dp
            WearScreenSize.SMALL -> 24.dp
        }
    val slopeIndicatorToolGap =
        when (screenSize) {
            WearScreenSize.LARGE -> 5.dp
            WearScreenSize.MEDIUM -> 4.dp
            WearScreenSize.SMALL -> 3.dp
        }
    val showSlopeIndicatorAccessory = slopeOverlayToggleEnabled && slopeOverlayEnabled && slopeOverlayProcessing
    val routeShortcutAccessoryWidth =
        if (showSlopeIndicatorAccessory) {
            slopeIndicatorButtonSize + slopeIndicatorToolGap
        } else {
            0.dp
        }

    LaunchedEffect(shortcutTrayExpanded, routeToolModeActive) {
        if (!shortcutTrayExpanded || routeToolModeActive) return@LaunchedEffect
        delay(5_000L)
        if (shortcutTrayExpanded) {
            onShortcutTrayDismiss()
        }
    }

    LaunchedEffect(navMode, liveDistanceEnabled, locationMarker, lastKnownLocation, mapView, mapRotationDeg) {
        if (navMode != NavMode.PANNING || !liveDistanceEnabled) {
            liveDistanceLineStart = null
            return@LaunchedEffect
        }
        while (true) {
            val liveDistanceOrigin =
                resolveLiveDistanceOrigin(
                    currentMarkerLatLong = locationMarker?.latLong,
                    fallbackLatLong = lastKnownLocation,
                )
            liveDistanceLineStart =
                liveDistanceOrigin?.let { origin ->
                    projectLatLongToScreenOffset(
                        mapView = mapView,
                        latLong = origin,
                        mapRotationDeg = mapRotationDeg,
                    )
                }
            delay(80L)
        }
    }

    PanningDistanceGuide(
        navMode = navMode,
        liveDistanceEnabled = liveDistanceEnabled,
        liveDistanceLineStart = liveDistanceLineStart,
    )

    RouteToolInlineProgressBanner(
        visible = mapAppearanceApplyInProgress,
        message = "Updating map",
        startInset = sideButtonEdgePadding + sideButtonSize + 8.dp,
        endInset = sideButtonEdgePadding + sideButtonSize + routeShortcutAccessoryWidth + 8.dp,
    )

    SlopeOverlayStatusIndicator(
        visible = slopeOverlayToggleEnabled,
        processing = slopeOverlayProcessing,
        progressPercent = slopeOverlayProgressPercent,
        enabled = slopeOverlayEnabled,
        currentZoomLevel = currentZoomLevel,
        screenSize = screenSize,
        sideButtonEdgePadding = sideButtonEdgePadding,
        sideButtonSize = sideButtonSize,
    )

    PanningLiveMetricsOverlay(
        navMode = navMode,
        liveElevationEnabled = liveElevationEnabled,
        liveElevationLabel = liveElevationLabel,
        liveDistanceEnabled = liveDistanceEnabled,
        liveDistanceLabel = liveDistanceLabel,
        zoomLabelTopPadding = zoomLabelTopPadding,
        liveElevationIconSize = liveElevationIconSize,
        navButtonBottomPadding = navButtonBottomPadding,
        navButtonSize = navButtonSize,
    )

    NorthIndicatorOverlay(
        northIndicatorMode = northIndicatorMode,
        navMode = navMode,
        mapRotationDeg = mapRotationDeg,
        compassHeadingDeg = compassHeadingDeg,
        indicatorButtonSize = northIndicatorButtonSize,
        indicatorIconSize = northIndicatorIconSize,
    )

    if (showZoomPlusButton) {
        CurvedLayout(
            modifier = Modifier.fillMaxSize(),
            anchor = 320f,
            anchorType = AnchorType.Center,
        ) {
            curvedComposable(
                modifier =
                    CurvedModifier.padding(
                        ArcPaddingValues(outer = sideButtonEdgePadding),
                    ),
            ) {
                IconButton(
                    onClick = {
                        val currentZoom =
                            mapView.model.mapViewPosition.zoomLevel
                                .toInt()
                        val newZoom = (currentZoom + 1).coerceAtMost(zoomMax)
                        if (newZoom != currentZoom) {
                            mapView.model.mapViewPosition.setZoomLevel(newZoom.toByte(), false)
                            triggerHaptic()
                        }
                    },
                    modifier = Modifier.size(zoomButtonSize),
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.7f),
                            contentColor = Color.White,
                        ),
                ) {
                    Icon(Icons.Default.Add, "Zoom In", Modifier.size(zoomIconSize))
                }
            }
        }
    }

    if (showZoomMinusButton) {
        CurvedLayout(
            modifier = Modifier.fillMaxSize(),
            anchor = 338f,
            anchorType = AnchorType.Center,
        ) {
            curvedComposable(
                modifier =
                    CurvedModifier.padding(
                        ArcPaddingValues(outer = sideButtonEdgePadding),
                    ),
            ) {
                IconButton(
                    onClick = {
                        val currentZoom =
                            mapView.model.mapViewPosition.zoomLevel
                                .toInt()
                        val newZoom = (currentZoom - 1).coerceAtLeast(zoomMin)
                        if (newZoom != currentZoom) {
                            mapView.model.mapViewPosition.setZoomLevel(newZoom.toByte(), false)
                            triggerHaptic()
                        }
                    },
                    modifier = Modifier.size(zoomButtonSize),
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.7f),
                            contentColor = Color.White,
                        ),
                ) {
                    Icon(Icons.Default.Remove, "Zoom Out", Modifier.size(zoomIconSize))
                }
            }
        }
    }

    scaleIndicator?.let { indicator ->
        AnimatedVisibility(
            visible = showScaleBar,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220)),
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = zoomLabelTopPadding),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = indicator.label,
                    modifier =
                        Modifier
                            .padding(top = 2.dp)
                            .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                )
                StandardScaleBar(
                    width = zoomScaleBarWidth,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }

    PoiTapMessageOverlay(
        poiTapMessage = poiTapMessage,
        poiTapCanExpand = poiTapCanExpand,
        poiTapCanCreateGpx = poiTapCanCreateGpx,
        poiTapExpanded = poiTapExpanded,
        onPoiTapExpandToggle = onPoiTapExpandToggle,
        onPoiTapCreateGpx = onPoiTapCreateGpx,
        onPoiTapDismiss = onPoiTapDismiss,
        onPoiTapScrollInProgressChanged = onPoiTapScrollInProgressChanged,
        screenSize = screenSize,
        sideButtonEdgePadding = sideButtonEdgePadding,
        sideButtonSize = sideButtonSize,
        navButtonBottomPadding = navButtonBottomPadding,
        navButtonSize = navButtonSize,
    )

    IconButton(
        onClick = onMenuClick,
        modifier =
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = sideButtonEdgePadding)
                .size(sideButtonSize),
        colors =
            IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.7f),
                contentColor = Color.White,
            ),
    ) {
        Icon(Icons.Default.Menu, "Menu", Modifier.size(sideButtonIconSize))
    }

    if (!routeToolModeActive) {
        RouteShortcutTray(
            expanded = shortcutTrayExpanded,
            keepAppOpen = keepAppOpen,
            edgePadding = sideButtonEdgePadding,
            anchorSize = sideButtonSize,
            adjacentAccessoryWidth = routeShortcutAccessoryWidth,
            actionHeight = sideButtonSize,
            iconSize = sideButtonIconSize,
            onToggleExpanded = onShortcutTrayToggle,
            onKeepAppOpenClick = onKeepAppOpenToggle,
            onGpxToolsClick = onGpxToolsClick,
            onCreatePoiClick = onCreatePoiClick,
        )
    }

    NavModeButtonOverlay(
        mapView = mapView,
        navMode = navMode,
        isOfflineMode = isOfflineMode,
        gpsIndicatorState = gpsIndicatorState,
        watchGpsDegradedWarning = watchGpsDegradedWarning,
        lastKnownLocation = lastKnownLocation,
        triggerHaptic = triggerHaptic,
        navButtonBottomPadding = navButtonBottomPadding,
        navButtonSize = navButtonSize,
        navButtonIconSize = navButtonIconSize,
        onRecenter = onRecenter,
        onRecenterRequested = onRecenterRequested,
        onToggleOrientation = onToggleOrientation,
    )
}
