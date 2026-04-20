package com.glancemap.glancemapwearos.presentation.features.navigate

import android.graphics.Rect
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.core.service.location.model.GpsEnvironmentWarning
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.features.gpx.GpxTrackDetails
import com.glancemap.glancemapwearos.presentation.features.maps.MapHolder
import com.glancemap.glancemapwearos.presentation.features.poi.PoiNavigateTarget
import com.glancemap.glancemapwearos.presentation.features.poi.PoiOverlayMarker
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteCreateMode
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteCrosshairOverlay
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteMultiPointMapProjection
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteMultiPointOverlayState
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteMultiPointPointsOverlay
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteReshapeHandlesOverlay
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteReshapePreviewOverlay
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolCreatePreview
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolKind
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolOptions
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolSession
import com.glancemap.glancemapwearos.presentation.formatting.UnitFormatter
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec
import com.glancemap.glancemapwearos.presentation.ui.rememberWearScreenSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.model.common.Observer

@Suppress(
    "CyclomaticComplexMethod",
    "FunctionName",
    "LongMethod",
    "LongParameterList",
)
@Composable
internal fun NavigateContent(
    hasLocationPermission: Boolean,
    focusRequester: FocusRequester,
    mapHolder: MapHolder?,
    onMapHolderChange: (MapHolder?) -> Unit,
    onMapViewReadyForRendering: () -> Unit,
    mapAppearanceApplyInProgress: Boolean,
    slopeOverlayToggleEnabled: Boolean,
    slopeOverlayEnabled: Boolean,
    slopeOverlayProcessing: Boolean,
    slopeOverlayProgressPercent: Int?,
    zoomDefault: Int,
    zoomMin: Int,
    zoomMax: Int,
    crownZoomEnabled: Boolean,
    crownZoomInverted: Boolean,
    mapZoomButtonsMode: String,
    northIndicatorMode: String,
    currentZoomLevel: Int,
    onZoomLevelChange: (Int) -> Unit,
    onViewportChanged: (LatLong, Int) -> Unit,
    isMetric: Boolean,
    navMode: NavMode,
    lastKnownLocation: LatLong?,
    onToggleOrientation: () -> Unit,
    onUserPanStarted: () -> Unit,
    onRecenter: () -> Unit,
    onRecenterRequested: () -> Unit,
    triggerHaptic: () -> Unit,
    onMenuClick: () -> Unit,
    onPermissionLaunch: () -> Unit,
    mapRotationDeg: Float,
    compassHeadingDeg: Float,
    liveElevationEnabled: Boolean,
    liveDistanceEnabled: Boolean,
    keepAppOpen: Boolean,
    onKeepAppOpenToggle: () -> Unit,
    shortcutTrayExpanded: Boolean,
    onShortcutTrayToggle: () -> Unit,
    onShortcutTrayDismiss: () -> Unit,
    onOpenGpxTools: () -> Unit,
    onStartPoiCreation: () -> Unit,
    gpsIndicatorState: GpsFixIndicatorState,
    gpsEnvironmentWarning: GpsEnvironmentWarning,
    watchGpsDegradedWarning: Boolean,
    isOfflineMode: Boolean,
    isGpxInspectionEnabled: Boolean,
    activeGpxDetails: List<GpxTrackDetails>,
    gpxTrackColor: Int,
    routeToolSession: RouteToolSession?,
    crosshairSelectionActive: Boolean,
    crosshairSelectionTitle: String? = null,
    crosshairSelectionInstruction: String? = null,
    crosshairSelectionBusy: Boolean = false,
    crosshairSelectionBusyMessage: String? = null,
    routeToolCreatePreview: RouteToolCreatePreview?,
    routeToolDraftConnectorPoints: List<LatLong>,
    routeToolCreatePreviewInProgress: Boolean,
    routeToolCreatePreviewMessage: String?,
    reshapePreviewInspectMode: Boolean,
    reshapePreviewPoints: List<LatLong>,
    reshapePreviewBusy: Boolean,
    reshapePreviewBusyMessage: String?,
    reshapePreviewMessage: String?,
    onRouteToolPickHere: (LatLong) -> Unit,
    onRouteToolUndoLastPoint: () -> Unit,
    onRouteToolSaveCreatePreview: () -> Unit,
    onRouteToolRefreshCreatePreview: () -> Unit,
    onCancelRouteToolMode: () -> Unit,
    onDismissReshapePreview: () -> Unit,
    onSaveReshapePreview: () -> Unit,
    onCrosshairSelectionPickHere: (() -> Unit)? = null,
    onCancelCrosshairSelection: (() -> Unit)? = null,
    onInspectTrack: (LatLong) -> Unit,
    visiblePoiMarkers: List<PoiOverlayMarker>,
    poiFocusTarget: PoiNavigateTarget?,
    onPoiFocusTargetConsumed: () -> Unit,
    onPoiTapCreateGpx: (PoiOverlayMarker) -> Unit,
    poiPopupTimeoutSeconds: Int,
    poiPopupManualCloseOnly: Boolean,
    markerMotionDebugOverlayLabel: String?,
) {
    val mapView = mapHolder?.mapView
    val context = LocalContext.current
    val screenSize = rememberWearScreenSize()
    val adaptive = rememberWearAdaptiveSpec()

    DisposableEffect(mapView, onMapViewReadyForRendering) {
        if (mapView == null) return@DisposableEffect onDispose {}

        val focusListener =
            ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                if (hasFocus && mapView.isAttachedToWindow && mapView.width > 0 && mapView.height > 0) {
                    onMapViewReadyForRendering()
                }
            }

        val observer = mapView.viewTreeObserver
        observer.addOnWindowFocusChangeListener(focusListener)

        onDispose {
            if (observer.isAlive) {
                observer.removeOnWindowFocusChangeListener(focusListener)
            }
        }
    }
    val gestureExclusionStripDp =
        if (adaptive.isRound) {
            when (screenSize) {
                WearScreenSize.LARGE -> 96f
                WearScreenSize.MEDIUM -> 84f
                WearScreenSize.SMALL -> 72f
            }
        } else {
            when (screenSize) {
                WearScreenSize.LARGE -> 84f
                WearScreenSize.MEDIUM -> 72f
                WearScreenSize.SMALL -> 60f
            }
        }
    val zoomButtonSize =
        if (adaptive.isRound) {
            when (screenSize) {
                WearScreenSize.LARGE -> 26.dp
                WearScreenSize.MEDIUM -> 24.dp
                WearScreenSize.SMALL -> 22.dp
            }
        } else {
            when (screenSize) {
                WearScreenSize.LARGE -> 28.dp
                WearScreenSize.MEDIUM -> 26.dp
                WearScreenSize.SMALL -> 24.dp
            }
        }
    val zoomIconSize =
        if (adaptive.isRound) {
            when (screenSize) {
                WearScreenSize.LARGE -> 18.dp
                WearScreenSize.MEDIUM -> 16.dp
                WearScreenSize.SMALL -> 14.dp
            }
        } else {
            when (screenSize) {
                WearScreenSize.LARGE -> 19.dp
                WearScreenSize.MEDIUM -> 17.dp
                WearScreenSize.SMALL -> 15.dp
            }
        }
    val zoomLabelTopPadding =
        if (adaptive.isRound) {
            when (screenSize) {
                WearScreenSize.LARGE -> 24.dp
                WearScreenSize.MEDIUM -> 22.dp
                WearScreenSize.SMALL -> 18.dp
            }
        } else {
            when (screenSize) {
                WearScreenSize.LARGE -> 20.dp
                WearScreenSize.MEDIUM -> 18.dp
                WearScreenSize.SMALL -> 16.dp
            }
        }
    val zoomScaleBarWidth =
        if (adaptive.isRound) {
            when (screenSize) {
                WearScreenSize.LARGE -> 52.dp
                WearScreenSize.MEDIUM -> 48.dp
                WearScreenSize.SMALL -> 42.dp
            }
        } else {
            when (screenSize) {
                WearScreenSize.LARGE -> 58.dp
                WearScreenSize.MEDIUM -> 54.dp
                WearScreenSize.SMALL -> 48.dp
            }
        }
    val (showZoomPlusButton, showZoomMinusButton) =
        when (mapZoomButtonsMode) {
            SettingsRepository.ZOOM_BUTTONS_HIDE_BOTH -> false to false
            SettingsRepository.ZOOM_BUTTONS_HIDE_PLUS -> false to true
            else -> true to true
        }
    val sideButtonSize =
        if (adaptive.isRound) {
            when (screenSize) {
                WearScreenSize.LARGE -> 30.dp
                WearScreenSize.MEDIUM -> 28.dp
                WearScreenSize.SMALL -> 26.dp
            }
        } else {
            when (screenSize) {
                WearScreenSize.LARGE -> 32.dp
                WearScreenSize.MEDIUM -> 30.dp
                WearScreenSize.SMALL -> 28.dp
            }
        }
    val sideButtonIconSize =
        if (adaptive.isRound) {
            when (screenSize) {
                WearScreenSize.LARGE -> 20.dp
                WearScreenSize.MEDIUM -> 18.dp
                WearScreenSize.SMALL -> 16.dp
            }
        } else {
            when (screenSize) {
                WearScreenSize.LARGE -> 21.dp
                WearScreenSize.MEDIUM -> 19.dp
                WearScreenSize.SMALL -> 17.dp
            }
        }
    val sideButtonEdgePadding =
        if (adaptive.isRound) {
            when (screenSize) {
                WearScreenSize.LARGE -> 4.dp
                WearScreenSize.MEDIUM -> 3.dp
                WearScreenSize.SMALL -> 2.dp
            }
        } else {
            when (screenSize) {
                WearScreenSize.LARGE -> 6.dp
                WearScreenSize.MEDIUM -> 5.dp
                WearScreenSize.SMALL -> 4.dp
            }
        }
    val liveElevationIconSize =
        if (adaptive.isRound) {
            when (screenSize) {
                WearScreenSize.LARGE -> 22.dp
                WearScreenSize.MEDIUM -> 20.dp
                WearScreenSize.SMALL -> 18.dp
            }
        } else {
            when (screenSize) {
                WearScreenSize.LARGE -> 24.dp
                WearScreenSize.MEDIUM -> 22.dp
                WearScreenSize.SMALL -> 20.dp
            }
        }
    val navButtonBottomPadding =
        if (adaptive.isRound) {
            when (screenSize) {
                WearScreenSize.LARGE -> 4.dp
                WearScreenSize.MEDIUM -> 3.dp
                WearScreenSize.SMALL -> 2.dp
            }
        } else {
            when (screenSize) {
                WearScreenSize.LARGE -> 8.dp
                WearScreenSize.MEDIUM -> 7.dp
                WearScreenSize.SMALL -> 6.dp
            }
        }
    val navButtonSize =
        if (adaptive.isRound) {
            when (screenSize) {
                WearScreenSize.LARGE -> 28.dp
                WearScreenSize.MEDIUM -> 26.dp
                WearScreenSize.SMALL -> 24.dp
            }
        } else {
            when (screenSize) {
                WearScreenSize.LARGE -> 30.dp
                WearScreenSize.MEDIUM -> 28.dp
                WearScreenSize.SMALL -> 26.dp
            }
        }
    val navButtonIconSize =
        if (adaptive.isRound) {
            when (screenSize) {
                WearScreenSize.LARGE -> 20.dp
                WearScreenSize.MEDIUM -> 18.dp
                WearScreenSize.SMALL -> 16.dp
            }
        } else {
            when (screenSize) {
                WearScreenSize.LARGE -> 21.dp
                WearScreenSize.MEDIUM -> 19.dp
                WearScreenSize.SMALL -> 17.dp
            }
        }
    val northIndicatorButtonSize =
        if (adaptive.isRound) {
            when (screenSize) {
                WearScreenSize.LARGE -> 20.dp
                WearScreenSize.MEDIUM -> 18.dp
                WearScreenSize.SMALL -> 16.dp
            }
        } else {
            when (screenSize) {
                WearScreenSize.LARGE -> 22.dp
                WearScreenSize.MEDIUM -> 20.dp
                WearScreenSize.SMALL -> 18.dp
            }
        }
    val northIndicatorIconSize =
        if (adaptive.isRound) {
            when (screenSize) {
                WearScreenSize.LARGE -> 14.dp
                WearScreenSize.MEDIUM -> 12.dp
                WearScreenSize.SMALL -> 11.dp
            }
        } else {
            when (screenSize) {
                WearScreenSize.LARGE -> 15.dp
                WearScreenSize.MEDIUM -> 13.dp
                WearScreenSize.SMALL -> 12.dp
            }
        }
    val permissionContentPadding =
        if (adaptive.isRound) {
            when (screenSize) {
                WearScreenSize.LARGE -> 16.dp
                WearScreenSize.MEDIUM -> 14.dp
                WearScreenSize.SMALL -> 12.dp
            }
        } else {
            when (screenSize) {
                WearScreenSize.LARGE -> 18.dp
                WearScreenSize.MEDIUM -> 16.dp
                WearScreenSize.SMALL -> 14.dp
            }
        }
    val latestNavMode = rememberUpdatedState(navMode)
    val latestOnUserPanStarted = rememberUpdatedState(onUserPanStarted)
    val latestOnInspectTrack = rememberUpdatedState(onInspectTrack)
    val latestInspectionEnabled =
        rememberUpdatedState(
            isGpxInspectionEnabled &&
                routeToolSession == null &&
                !crosshairSelectionActive &&
                !reshapePreviewInspectMode,
        )
    val latestRouteToolSession = rememberUpdatedState(routeToolSession)
    val latestCrosshairSelectionActive = rememberUpdatedState(crosshairSelectionActive)
    val latestReshapePreviewInspectMode = rememberUpdatedState(reshapePreviewInspectMode)
    val latestRouteToolModeActive =
        rememberUpdatedState(
            routeToolSession != null || crosshairSelectionActive || reshapePreviewInspectMode,
        )
    val latestMapView = rememberUpdatedState(mapView)
    val latestOnZoomLevelChange = rememberUpdatedState(onZoomLevelChange)
    val latestOnViewportChanged = rememberUpdatedState(onViewportChanged)
    val latestVisiblePoiMarkers = rememberUpdatedState(visiblePoiMarkers)
    var rotaryScrollAccumulator by remember(mapView, crownZoomEnabled, crownZoomInverted) {
        mutableStateOf(0f)
    }
    var poiTapMarker by remember { mutableStateOf<PoiOverlayMarker?>(null) }
    var poiTapPopup by remember { mutableStateOf<PoiTapPopupContent?>(null) }
    var poiTapPopupExpanded by remember { mutableStateOf(false) }
    var routeToolOverlayRevision by remember { mutableIntStateOf(0) }

    LaunchedEffect(poiFocusTarget) {
        val target = poiFocusTarget ?: return@LaunchedEffect
        val marker =
            PoiOverlayMarker(
                key = "focus:${target.lat},${target.lon}:${target.label.orEmpty()}",
                lat = target.lat,
                lon = target.lon,
                label = target.label,
                type = target.type,
                details = target.details,
            )
        poiTapMarker = marker
        poiTapPopup = buildPoiTapPopupContent(marker, isMetric = isMetric)
        poiTapPopupExpanded = false
        onPoiFocusTargetConsumed()
    }

    LaunchedEffect(reshapePreviewInspectMode, reshapePreviewPoints, mapView, zoomMin, zoomMax) {
        val currentMapView = mapView ?: return@LaunchedEffect
        if (!reshapePreviewInspectMode || reshapePreviewPoints.size < 2) return@LaunchedEffect
        onUserPanStarted()
        currentMapView.post {
            fitMapViewToPreviewPoints(
                mapView = currentMapView,
                points = reshapePreviewPoints,
                zoomMin = zoomMin,
                zoomMax = zoomMax,
            )
        }
    }

    fun applyRotaryZoomStep(step: Int): Boolean {
        val mv = mapView ?: return false
        val current =
            mv.model.mapViewPosition.zoomLevel
                .toInt()
        val next = (current + step).coerceIn(zoomMin, zoomMax)
        if (next == current) return false
        mv.model.mapViewPosition.setZoomLevel(next.toByte(), false)
        triggerHaptic()
        return true
    }

    fun canApplyRotaryZoomStep(step: Int): Boolean {
        val mv = mapView ?: return false
        val current =
            mv.model.mapViewPosition.zoomLevel
                .toInt()
        val next = (current + step).coerceIn(zoomMin, zoomMax)
        return next != current
    }

    val gestureDetector =
        remember {
            GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean = true

                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        val mv = latestMapView.value ?: return false
                        val (x, y) =
                            unrotateTouchToMapSpace(
                                x = e.x.toDouble(),
                                y = e.y.toDouble(),
                                mapWidth = mv.width.toDouble(),
                                mapHeight = mv.height.toDouble(),
                                mapRotationDeg = mv.mapRotation.degrees.toDouble(),
                            )
                        val ll = runCatching { mv.mapViewProjection.fromPixels(x, y) }.getOrNull() ?: return false
                        if (latestRouteToolSession.value != null) return false
                        if (latestCrosshairSelectionActive.value) {
                            return false
                        }
                        if (latestReshapePreviewInspectMode.value) {
                            return false
                        }
                        val zoomNow =
                            mv.model.mapViewPosition.zoomLevel
                                .toInt()
                        val tappedPoi =
                            findTappedPoiMarker(
                                tap = ll,
                                zoomLevel = zoomNow,
                                markers = latestVisiblePoiMarkers.value,
                            ) ?: return false

                        triggerHaptic()
                        poiTapMarker = tappedPoi
                        poiTapPopup = buildPoiTapPopupContent(tappedPoi, isMetric = isMetric)
                        poiTapPopupExpanded = false
                        return true
                    }

                    override fun onLongPress(e: MotionEvent) {
                        if (!latestInspectionEnabled.value) return
                        val mv = latestMapView.value ?: return
                        val (x, y) =
                            unrotateTouchToMapSpace(
                                x = e.x.toDouble(),
                                y = e.y.toDouble(),
                                mapWidth = mv.width.toDouble(),
                                mapHeight = mv.height.toDouble(),
                                mapRotationDeg = mv.mapRotation.degrees.toDouble(),
                            )
                        val ll =
                            runCatching {
                                mv.mapViewProjection.fromPixels(x, y)
                            }.getOrNull() ?: return
                        latestOnInspectTrack.value(ll)
                    }
                },
            )
        }
    var scaleIndicator by remember(mapView, isMetric) { mutableStateOf<ScaleIndicatorUi?>(null) }
    var hasSeenInitialZoomState by remember { mutableStateOf(false) }
    var showScaleBar by remember { mutableStateOf(false) }
    var liveElevationLabel by remember(mapHolder, isMetric) { mutableStateOf<String?>(null) }
    var liveDistanceLabel by remember(lastKnownLocation, isMetric) { mutableStateOf<String?>(null) }

    LaunchedEffect(mapView, isMetric) {
        scaleIndicator =
            mapView?.let {
                calculateScaleIndicator(mapView = it, isMetric = isMetric)
            }
    }

    LaunchedEffect(currentZoomLevel, mapView, isMetric) {
        if (currentZoomLevel <= 0) return@LaunchedEffect
        if (!hasSeenInitialZoomState) {
            hasSeenInitialZoomState = true
            return@LaunchedEffect
        }
        scaleIndicator =
            mapView?.let {
                calculateScaleIndicator(mapView = it, isMetric = isMetric)
            }
        if (scaleIndicator == null) return@LaunchedEffect
        showScaleBar = true
        delay(5_000L)
        showScaleBar = false
    }

    LaunchedEffect(poiTapPopup, poiTapPopupExpanded, poiPopupTimeoutSeconds, poiPopupManualCloseOnly) {
        if (poiTapPopup == null || poiPopupManualCloseOnly) return@LaunchedEffect
        val timeoutMs = poiPopupTimeoutSeconds.coerceAtLeast(1) * 1_000L
        delay(timeoutMs)
        poiTapMarker = null
        poiTapPopup = null
        poiTapPopupExpanded = false
    }

    LaunchedEffect(
        navMode,
        liveElevationEnabled,
        liveDistanceEnabled,
        mapHolder,
        mapView,
        lastKnownLocation,
        isMetric,
    ) {
        if (
            navMode != NavMode.PANNING ||
            (!liveElevationEnabled && !liveDistanceEnabled) ||
            mapView == null
        ) {
            liveElevationLabel = null
            liveDistanceLabel = null
            return@LaunchedEffect
        }

        var lastElevationSampleCenter: LatLong? = null
        while (isActive) {
            val center = mapView.model.mapViewPosition.center
            if (liveElevationEnabled) {
                val previousCenter = lastElevationSampleCenter
                val movedMeters =
                    if (previousCenter != null) {
                        navigateHaversineMeters(
                            lat1 = previousCenter.latitude,
                            lon1 = previousCenter.longitude,
                            lat2 = center.latitude,
                            lon2 = center.longitude,
                        )
                    } else {
                        Double.POSITIVE_INFINITY
                    }
                val shouldResampleElevation =
                    previousCenter == null ||
                        liveElevationLabel == null ||
                        movedMeters >= LIVE_ELEVATION_RESAMPLE_DISTANCE_METERS

                if (shouldResampleElevation) {
                    val sampledMeters =
                        withContext(Dispatchers.Default) {
                            mapHolder.renderer.sampleElevationMeters(
                                lat = center.latitude,
                                lon = center.longitude,
                            )
                        }
                    liveElevationLabel = sampledMeters?.let { meters ->
                        val (value, unit) = UnitFormatter.formatElevation(meters, isMetric)
                        "$value $unit"
                    } ?: "--"
                    lastElevationSampleCenter = center
                }
            } else {
                liveElevationLabel = null
                lastElevationSampleCenter = null
            }

            if (liveDistanceEnabled && lastKnownLocation != null) {
                val straightDistanceMeters =
                    navigateHaversineMeters(
                        lat1 = lastKnownLocation.latitude,
                        lon1 = lastKnownLocation.longitude,
                        lat2 = center.latitude,
                        lon2 = center.longitude,
                    )
                val (value, unit) = UnitFormatter.formatDistance(straightDistanceMeters, isMetric)
                liveDistanceLabel = "$value $unit"
            } else {
                liveDistanceLabel = null
            }
            delay(320L)
        }
    }

    val poiTapMessage =
        when {
            poiTapPopup == null -> null
            poiTapPopupExpanded -> poiTapPopup?.expandedText ?: poiTapPopup?.compactText
            else -> poiTapPopup?.compactText
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                // Crown/rotary zoom support for Navigate screen.
                .onPreRotaryScrollEvent { event ->
                    if (!crownZoomEnabled) return@onPreRotaryScrollEvent false
                    val delta = event.verticalScrollPixels
                    if (!delta.isFinite() || delta == 0f) return@onPreRotaryScrollEvent false

                    if (
                        rotaryScrollAccumulator != 0f &&
                        (rotaryScrollAccumulator > 0f) != (delta > 0f)
                    ) {
                        rotaryScrollAccumulator = 0f
                    }

                    rotaryScrollAccumulator += delta

                    // Keep the threshold low enough that a single crown detent feels immediate.
                    val thresholdPx = 24f
                    val positiveStep = if (crownZoomInverted) +1 else -1
                    val negativeStep = -positiveStep
                    var consumed = false

                    while (rotaryScrollAccumulator >= thresholdPx) {
                        consumed = applyRotaryZoomStep(step = positiveStep) || consumed
                        rotaryScrollAccumulator -= thresholdPx
                    }
                    while (rotaryScrollAccumulator <= -thresholdPx) {
                        consumed = applyRotaryZoomStep(step = negativeStep) || consumed
                        rotaryScrollAccumulator += thresholdPx
                    }

                    if (consumed) {
                        true
                    } else {
                        val pendingStep =
                            when {
                                rotaryScrollAccumulator > 0f -> positiveStep
                                rotaryScrollAccumulator < 0f -> negativeStep
                                else -> 0
                            }
                        pendingStep != 0 && canApplyRotaryZoomStep(step = pendingStep)
                    }
                }.focusRequester(focusRequester)
                .focusable(),
    ) {
        if (hasLocationPermission && mapView != null) {
            DisposableEffect(mapView, zoomMin, zoomMax) {
                mapView.setZoomLevelMin(zoomMin.toByte())
                mapView.setZoomLevelMax(zoomMax.toByte())
                onDispose { }
            }

            // Gesture exclusion: update only when size changes
            DisposableEffect(mapView) {
                var lastW = -1
                var lastH = -1
                val listener =
                    View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                        val w = v.width
                        val h = v.height
                        if (w <= 0 || h <= 0) return@OnLayoutChangeListener
                        if (w == lastW && h == lastH) return@OnLayoutChangeListener
                        lastW = w
                        lastH = h

                        val density = v.resources.displayMetrics.density
                        val leftStripPx = (gestureExclusionStripDp * density).toInt().coerceAtMost(w)
                        ViewCompat.setSystemGestureExclusionRects(
                            v,
                            listOf(Rect(0, 0, leftStripPx, h)),
                        )
                    }
                mapView.addOnLayoutChangeListener(listener)
                onDispose { mapView.removeOnLayoutChangeListener(listener) }
            }

            // Sync Map Zoom -> VM (deduped)
            DisposableEffect(mapView) {
                var lastZoom =
                    mapView.model.mapViewPosition.zoomLevel
                        .toInt()
                var lastCenter = mapView.model.mapViewPosition.center
                val observer =
                    Observer {
                        val newCenter = mapView.model.mapViewPosition.center
                        val newZoom =
                            mapView.model.mapViewPosition.zoomLevel
                                .toInt()
                        val zoomChanged = newZoom != lastZoom
                        val centerChanged =
                            newCenter.latitude != lastCenter.latitude ||
                                newCenter.longitude != lastCenter.longitude
                        if (zoomChanged) {
                            lastZoom = newZoom
                            latestOnZoomLevelChange.value(newZoom)
                        }
                        if (centerChanged || zoomChanged) {
                            lastCenter = newCenter
                            routeToolOverlayRevision++
                            latestOnViewportChanged.value(newCenter, newZoom)
                        }
                    }
                mapView.model.mapViewPosition.addObserver(observer)
                onDispose { mapView.model.mapViewPosition.removeObserver(observer) }
            }

            var isDragging by remember { mutableStateOf(false) }

            AndroidView(
                factory = {
                    (mapView.parent as? ViewGroup)?.removeView(mapView)
                    mapView.apply {
                        setOnTouchListener { v, event ->
                            if (latestInspectionEnabled.value) {
                                gestureDetector.onTouchEvent(event)
                            }

                            // ✅ Reliable panning detection (MapView gets these events)
                            when (event.actionMasked) {
                                MotionEvent.ACTION_MOVE -> {
                                    if (!isDragging) isDragging = true
                                    if (latestNavMode.value != NavMode.PANNING) {
                                        latestOnUserPanStarted.value.invoke()
                                    }
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                                MotionEvent.ACTION_UP,
                                MotionEvent.ACTION_CANCEL,
                                -> {
                                    isDragging = false
                                    v.parent?.requestDisallowInterceptTouchEvent(false)
                                }
                                else -> Unit
                            }

                            false // let Mapsforge handle pan/zoom
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    if (view.isAttachedToWindow && view.width > 0 && view.height > 0 && view.hasWindowFocus()) {
                        onMapViewReadyForRendering()
                    } else if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) {
                        view.post { onMapViewReadyForRendering() }
                    }
                },
            )

            NavigateOverlaysLayer(
                mapView = mapView,
                mapAppearanceApplyInProgress = mapAppearanceApplyInProgress,
                slopeOverlayToggleEnabled = slopeOverlayToggleEnabled,
                slopeOverlayEnabled = slopeOverlayEnabled,
                slopeOverlayProcessing = slopeOverlayProcessing,
                slopeOverlayProgressPercent = slopeOverlayProgressPercent,
                navMode = navMode,
                screenSize = screenSize,
                liveElevationEnabled = liveElevationEnabled,
                liveElevationLabel = liveElevationLabel,
                liveDistanceEnabled = liveDistanceEnabled,
                liveDistanceLabel = liveDistanceLabel,
                zoomLabelTopPadding = zoomLabelTopPadding,
                liveElevationIconSize = liveElevationIconSize,
                northIndicatorMode = northIndicatorMode,
                mapRotationDeg = mapRotationDeg,
                compassHeadingDeg = compassHeadingDeg,
                northIndicatorButtonSize = northIndicatorButtonSize,
                northIndicatorIconSize = northIndicatorIconSize,
                showZoomPlusButton = showZoomPlusButton,
                showZoomMinusButton = showZoomMinusButton,
                currentZoomLevel = currentZoomLevel,
                zoomMin = zoomMin,
                zoomMax = zoomMax,
                triggerHaptic = triggerHaptic,
                zoomButtonSize = zoomButtonSize,
                zoomIconSize = zoomIconSize,
                scaleIndicator = scaleIndicator,
                showScaleBar = showScaleBar,
                zoomScaleBarWidth = zoomScaleBarWidth,
                poiTapMessage = poiTapMessage,
                poiTapCanExpand = poiTapPopup?.canExpand == true,
                poiTapCanCreateGpx = poiTapMarker != null,
                poiTapExpanded = poiTapPopupExpanded,
                onPoiTapExpandToggle = {
                    poiTapPopupExpanded = !poiTapPopupExpanded
                },
                onPoiTapCreateGpx = {
                    poiTapMarker?.let { marker ->
                        onPoiTapCreateGpx(marker)
                        poiTapMarker = null
                        poiTapPopup = null
                        poiTapPopupExpanded = false
                    }
                },
                onPoiTapDismiss = {
                    poiTapMarker = null
                    poiTapPopup = null
                    poiTapPopupExpanded = false
                },
                onMenuClick = onMenuClick,
                sideButtonEdgePadding = sideButtonEdgePadding,
                sideButtonSize = sideButtonSize,
                sideButtonIconSize = sideButtonIconSize,
                shortcutTrayExpanded = shortcutTrayExpanded,
                routeToolModeActive = routeToolSession != null || crosshairSelectionActive || reshapePreviewInspectMode,
                onShortcutTrayToggle = onShortcutTrayToggle,
                onShortcutTrayDismiss = onShortcutTrayDismiss,
                onGpxToolsClick = onOpenGpxTools,
                onCreatePoiClick = onStartPoiCreation,
                keepAppOpen = keepAppOpen,
                onKeepAppOpenToggle = onKeepAppOpenToggle,
                gpsIndicatorState = gpsIndicatorState,
                watchGpsDegradedWarning = watchGpsDegradedWarning,
                navButtonBottomPadding = navButtonBottomPadding,
                navButtonSize = navButtonSize,
                navButtonIconSize = navButtonIconSize,
                lastKnownLocation = lastKnownLocation,
                onRecenter = onRecenter,
                onRecenterRequested = onRecenterRequested,
                onToggleOrientation = onToggleOrientation,
                isOfflineMode = isOfflineMode,
            )

            MarkerMotionDebugOverlay(
                label = markerMotionDebugOverlayLabel,
                screenSize = screenSize,
            )

            GpsEnvironmentWarningOverlay(
                warning = gpsEnvironmentWarning,
                visible = hasLocationPermission && !isOfflineMode,
            )

            if (routeToolSession != null) {
                val session = routeToolSession
                val activeRouteToolTrack = activeGpxDetails.singleOrNull()
                RouteReshapeHandlesOverlay(
                    session = session,
                    activeTrack = activeRouteToolTrack,
                    mapView = mapView,
                    mapRotationDeg = mapRotationDeg,
                    viewportRevision = routeToolOverlayRevision,
                )
                RouteCrosshairOverlay(
                    session = session,
                    screenSize = screenSize,
                    isMetric = isMetric,
                    createPreview = routeToolCreatePreview,
                    createPreviewInProgress = routeToolCreatePreviewInProgress,
                    createPreviewMessage = routeToolCreatePreviewMessage,
                    onPickHere = {
                        onRouteToolPickHere(mapView.model.mapViewPosition.center)
                    },
                    onCancel = onCancelRouteToolMode,
                    onUndoLastPoint = onRouteToolUndoLastPoint,
                    onSaveCreatePreview = onRouteToolSaveCreatePreview,
                    onRefreshCreatePreview = onRouteToolRefreshCreatePreview,
                )
                RouteMultiPointPointsOverlay(
                    overlayState =
                        RouteMultiPointOverlayState(
                            session = session,
                            draftConnectorPoints = routeToolDraftConnectorPoints,
                            gpxTrackColor = gpxTrackColor,
                        ),
                    mapProjection =
                        RouteMultiPointMapProjection(
                            mapView = mapView,
                            mapRotationDeg = mapRotationDeg,
                            viewportRevision = routeToolOverlayRevision,
                        ),
                )
            } else if (reshapePreviewInspectMode && reshapePreviewPoints.size >= 2) {
                RouteReshapePreviewOverlay(
                    screenSize = screenSize,
                    busy = reshapePreviewBusy,
                    busyMessage = reshapePreviewBusyMessage,
                    message = reshapePreviewMessage,
                    onDismiss = onDismissReshapePreview,
                    onSave = onSaveReshapePreview,
                )
            } else if (
                crosshairSelectionActive &&
                onCrosshairSelectionPickHere != null &&
                onCancelCrosshairSelection != null
            ) {
                val poiSelectionSession =
                    remember {
                        RouteToolSession(
                            options =
                                RouteToolOptions(
                                    toolKind = RouteToolKind.CREATE,
                                    createMode = RouteCreateMode.CURRENT_TO_HERE,
                                ),
                        )
                    }
                RouteCrosshairOverlay(
                    session = poiSelectionSession,
                    screenSize = screenSize,
                    isMetric = isMetric,
                    busy = crosshairSelectionBusy,
                    busyMessage = crosshairSelectionBusyMessage,
                    titleOverride = crosshairSelectionTitle ?: "+ POI",
                    instructionOverride = crosshairSelectionInstruction ?: "Move map, then check.",
                    showCapturedPoints = false,
                    onPickHere = onCrosshairSelectionPickHere,
                    onCancel = onCancelCrosshairSelection,
                )
            }
        } else {
            scaleIndicator = null
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(permissionContentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Location permission required for this screen.",
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onPermissionLaunch) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun BoxScope.GpsEnvironmentWarningOverlay(
    warning: GpsEnvironmentWarning,
    visible: Boolean,
) {
    val message =
        when (warning) {
            GpsEnvironmentWarning.NONE -> null
            GpsEnvironmentWarning.LOCATION_SETTINGS_UNSATISFIED -> "Turn on watch Location"
            GpsEnvironmentWarning.WATCH_GPS_UNAVAILABLE -> "Watch GPS unavailable"
            GpsEnvironmentWarning.AUTO_PHONE_DISCONNECTED_NO_WATCH_GPS -> "Phone out of reach"
            GpsEnvironmentWarning.AUTO_PHONE_DISCONNECTED_USING_WATCH_GPS -> "Phone away. Watch GPS active"
        }
    if (!visible || message == null) return

    Text(
        text = message,
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 38.dp, vertical = 10.dp)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        color = Color.White,
        fontSize = 11.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 2,
    )
}

@Suppress("FunctionName")
@Composable
private fun BoxScope.MarkerMotionDebugOverlay(
    label: String?,
    screenSize: WearScreenSize,
) {
    if (label.isNullOrBlank()) return

    val overlayPadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 24.dp
            WearScreenSize.MEDIUM -> 22.dp
            WearScreenSize.SMALL -> 20.dp
        }
    val overlayTextSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 10.sp
            WearScreenSize.MEDIUM -> 9.sp
            WearScreenSize.SMALL -> 8.sp
        }

    Text(
        text = label,
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = overlayPadding)
                .padding(horizontal = overlayPadding)
                .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        color = Color.White,
        fontSize = overlayTextSize,
        lineHeight = overlayTextSize,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
}

private fun fitMapViewToPreviewPoints(
    mapView: org.mapsforge.map.android.view.MapView,
    points: List<LatLong>,
    zoomMin: Int,
    zoomMax: Int,
) {
    if (points.isEmpty()) return
    val widthPx = mapView.width.toDouble()
    val heightPx = mapView.height.toDouble()
    if (widthPx <= 0.0 || heightPx <= 0.0) return

    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLon = points.minOf { it.longitude }
    val maxLon = points.maxOf { it.longitude }
    val center =
        LatLong(
            (minLat + maxLat) / 2.0,
            (minLon + maxLon) / 2.0,
        )

    val usableWidth = maxOf(96.0, widthPx * 0.68)
    val usableHeight = maxOf(96.0, heightPx * 0.52)
    val tileSize = mapView.model.displayModel.tileSize
    val horizontalPaddingPx = 28.0
    val verticalPaddingPx = 36.0

    var chosenZoom = zoomMin.coerceAtMost(zoomMax)
    for (zoom in zoomMax downTo zoomMin) {
        val mapSize = MercatorProjection.getMapSize(zoom.toByte(), tileSize)
        val spanX =
            points.maxOf { MercatorProjection.longitudeToPixelX(it.longitude, mapSize) } -
                points.minOf { MercatorProjection.longitudeToPixelX(it.longitude, mapSize) } +
                horizontalPaddingPx
        val spanY =
            points.maxOf { MercatorProjection.latitudeToPixelY(it.latitude, mapSize) } -
                points.minOf { MercatorProjection.latitudeToPixelY(it.latitude, mapSize) } +
                verticalPaddingPx
        if (spanX <= usableWidth && spanY <= usableHeight) {
            chosenZoom = zoom
            break
        }
    }

    mapView.setCenter(center)
    mapView.model.mapViewPosition.setZoomLevel(chosenZoom.toByte(), false)
}

private const val LIVE_ELEVATION_RESAMPLE_DISTANCE_METERS = 3.0
