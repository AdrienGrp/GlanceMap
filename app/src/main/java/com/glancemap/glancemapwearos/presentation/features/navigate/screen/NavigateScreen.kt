package com.glancemap.glancemapwearos.presentation.features.navigate

import android.hardware.SensorManager
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glancemap.glancemapwearos.core.service.location.model.LocationScreenState
import com.glancemap.glancemapwearos.core.service.location.model.isInteractive
import com.glancemap.glancemapwearos.core.service.location.model.isNonInteractive
import com.glancemap.glancemapwearos.core.service.location.model.resolveLocationScreenState
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.data.repository.UserPoiRecord
import com.glancemap.glancemapwearos.domain.sensors.CompassHeadingSourceMode
import com.glancemap.glancemapwearos.domain.sensors.CompassProviderType
import com.glancemap.glancemapwearos.domain.sensors.CompassViewModel
import com.glancemap.glancemapwearos.domain.sensors.NorthReferenceMode
import com.glancemap.glancemapwearos.presentation.features.gpx.GpxViewModel
import com.glancemap.glancemapwearos.presentation.features.maps.MapHolder
import com.glancemap.glancemapwearos.presentation.features.maps.MapRenderer
import com.glancemap.glancemapwearos.presentation.features.maps.MapViewModel
import com.glancemap.glancemapwearos.presentation.features.navigate.effects.NavigateCalibrationEffects
import com.glancemap.glancemapwearos.presentation.features.navigate.effects.NavigateCompassEffects
import com.glancemap.glancemapwearos.presentation.features.navigate.effects.rememberNavigateLocationUiState
import com.glancemap.glancemapwearos.presentation.features.navigate.motion.MarkerMotionTelemetry
import com.glancemap.glancemapwearos.presentation.features.offline.OfflineStartCenteringEffect
import com.glancemap.glancemapwearos.presentation.features.poi.PoiNavigateTarget
import com.glancemap.glancemapwearos.presentation.features.poi.PoiOverlayMarker
import com.glancemap.glancemapwearos.presentation.features.poi.PoiViewModel
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteCreateMode
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteModifyMode
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolCreatePreview
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolKind
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolLoopRetryOption
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolModifyPreview
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolOptions
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolSaveResult
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolSession
import com.glancemap.glancemapwearos.presentation.features.routetools.withVisibleLoopDefaults
import com.glancemap.glancemapwearos.presentation.features.settings.SettingsViewModel
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec
import com.glancemap.glancemapwearos.presentation.ui.rememberWearScreenSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidBitmap

@Composable
fun NavigateScreen(
    mapViewModel: MapViewModel,
    gpxViewModel: GpxViewModel,
    poiViewModel: PoiViewModel,
    settingsViewModel: SettingsViewModel,
    locationViewModel: LocationViewModel,
    isAmbient: Boolean,
    isDeviceInteractive: Boolean,
    ambientTickMs: Long,
    onMenuClick: () -> Unit,
    compassViewModel: CompassViewModel = viewModel(),
    navigateViewModel: NavigateViewModel =
        viewModel(
            factory =
                NavigateViewModelFactory(
                    application = LocalContext.current.applicationContext as android.app.Application,
                    locationViewModel = locationViewModel,
                    compassViewModel = compassViewModel,
                ),
        ),
) {
    val context = LocalContext.current
    val adaptive = rememberWearAdaptiveSpec()
    val screenSize = rememberWearScreenSize()
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var isScreenResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> isScreenResumed = true
                    Lifecycle.Event.ON_PAUSE -> isScreenResumed = false
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ---- UI STATE ----
    val uiState by navigateViewModel.uiState.collectAsState()
    val navMode by navigateViewModel.navMode.collectAsState()
    val showCalibrationDialog = uiState.showCalibrationDialog
    val currentZoomLevel = uiState.currentZoomLevel

    // ---- Persisted Keep App Open ----
    val keepAppOpen by settingsViewModel.keepAppOpen.collectAsState(initial = false)
    val keepAppOpenTipShown by settingsViewModel.keepAppOpenTipShown.collectAsState(initial = false)
    var pendingKeepAppOpen by rememberSaveable { mutableStateOf(false) }
    var showKeepAppOpenInfoDialog by rememberSaveable { mutableStateOf(false) }
    var hasAppliedInitialKeepAppOpenSync by rememberSaveable { mutableStateOf(false) }

    // ---- PERMISSIONS ----
    val notificationPermissionState =
        rememberNotificationPermissionState(context) { granted ->
            if (granted && pendingKeepAppOpen) {
                settingsViewModel.setKeepAppOpen(true)
                pendingKeepAppOpen = false
            } else if (!granted) {
                pendingKeepAppOpen = false
            }
        }

    val locationPermissionState =
        rememberLocationPermissionState(context) { granted ->
            if (granted && pendingKeepAppOpen) {
                if (
                    notificationPermissionState.isPermissionRequired &&
                    !notificationPermissionState.hasNotificationPermission
                ) {
                    notificationPermissionState.launchPermissionRequest()
                } else {
                    settingsViewModel.setKeepAppOpen(true)
                    pendingKeepAppOpen = false
                }
            } else if (!granted) {
                pendingKeepAppOpen = false
            }
        }

    // ---- SETTINGS ----
    val zoomDefault by settingsViewModel.mapZoomDefault.collectAsState()
    val zoomMin by settingsViewModel.mapZoomMin.collectAsState()
    val zoomMax by settingsViewModel.mapZoomMax.collectAsState()
    val northIndicatorMode by settingsViewModel.northIndicatorMode.collectAsState()
    val northReferenceMode by settingsViewModel.northReferenceMode.collectAsState(
        initial = SettingsRepository.NORTH_REFERENCE_TRUE,
    )
    val compassProviderMode by settingsViewModel.compassProviderMode.collectAsState(
        initial = SettingsRepository.COMPASS_PROVIDER_GOOGLE_FUSED,
    )
    val headingSourceMode by settingsViewModel.compassHeadingSourceMode.collectAsState(
        initial = SettingsRepository.COMPASS_HEADING_SOURCE_AUTO,
    )
    val gpxTrackColor by settingsViewModel.gpxTrackColor.collectAsState()
    val gpxTrackWidth by settingsViewModel.gpxTrackWidth.collectAsState()
    val gpxTrackOpacityPercent by settingsViewModel.gpxTrackOpacityPercent.collectAsState()
    val autoRecenterEnabled by settingsViewModel.autoRecenterEnabled.collectAsState()
    val autoRecenterDelay by settingsViewModel.autoRecenterDelay.collectAsState(initial = 5)
    val promptForCalibration by settingsViewModel.promptForCalibration.collectAsState(initial = false)
    val keepGpsInAmbient by settingsViewModel.gpsInAmbientMode.collectAsState(initial = false)
    val crownZoomEnabled by settingsViewModel.crownZoomEnabled.collectAsState(initial = true)
    val crownZoomInverted by settingsViewModel.crownZoomInverted.collectAsState(initial = true)
    val navigateTimeFormat by settingsViewModel.navigateTimeFormat.collectAsState()
    val mapZoomButtonsMode by settingsViewModel.mapZoomButtonsMode.collectAsState()
    val navigationMarkerStyleSetting by settingsViewModel.navigationMarkerStyle.collectAsState()
    val gpsAccuracyCircleEnabled by settingsViewModel.gpsAccuracyCircleEnabled.collectAsState(initial = false)
    val liveElevationEnabled by settingsViewModel.liveElevation.collectAsState(initial = false)
    val liveDistanceEnabled by settingsViewModel.liveDistance.collectAsState(initial = false)
    val offlineMode by settingsViewModel.offlineMode.collectAsState(initial = false)
    val gpsDebugTelemetry by settingsViewModel.gpsDebugTelemetry.collectAsState()
    val gpsDebugTelemetryPopupEnabled by settingsViewModel.gpsDebugTelemetryPopupEnabled.collectAsState(initial = true)
    val isGpxInspectionEnabled by settingsViewModel.isGpxInspectionEnabled.collectAsState()
    val isMetric by settingsViewModel.isMetric.collectAsState()
    val poiIconSizePx by settingsViewModel.poiIconSizePx.collectAsState()
    val poiPopupTimeoutSeconds by settingsViewModel.poiPopupTimeoutSeconds.collectAsState(
        initial = SettingsRepository.POI_POPUP_TIMEOUT_DEFAULT_SECONDS,
    )
    val poiPopupManualCloseOnly by settingsViewModel.poiPopupManualCloseOnly.collectAsState(initial = false)
    val compassConeAccuracyColorsEnabled by settingsViewModel.compassConeAccuracyColorsEnabled.collectAsState(
        initial = true,
    )

    // ---- VMS ----
    val selectedMapPath by mapViewModel.selectedMapPath.collectAsState()
    val activeGpxDetails by gpxViewModel.activeGpxDetails.collectAsState()
    val activePoiOverlaySources by poiViewModel.activeOverlaySources.collectAsState()
    val navigateTarget by poiViewModel.navigateTarget.collectAsState()
    val offlinePoiSearchUiState by poiViewModel.offlineSearchUiState.collectAsState()

    // Inspection UI state
    val inspectionUiState by gpxViewModel.inspectionUiState.collectAsState()

    // A/B marker points
    val selectedPointA by gpxViewModel.selectedPointA.collectAsState()
    val selectedPointB by gpxViewModel.selectedPointB.collectAsState()
    var shortcutTrayExpanded by rememberSaveable { mutableStateOf(false) }
    var showRouteToolsPanel by rememberSaveable { mutableStateOf(false) }
    var routeToolOptions by rememberSaveable(stateSaver = routeToolOptionsSaver) {
        mutableStateOf(RouteToolOptions())
    }
    var routeToolSession by rememberSaveable(stateSaver = routeToolSessionSaver) {
        mutableStateOf<RouteToolSession?>(null)
    }
    var poiCreationSelectionActive by rememberSaveable { mutableStateOf(false) }
    var completedRouteToolDraft by remember { mutableStateOf<RouteToolSession?>(null) }
    var routeToolExecutionInProgress by remember { mutableStateOf(false) }
    var routeToolExecutionStatus by remember { mutableStateOf<String?>(null) }
    var routeToolExecutionMessage by remember { mutableStateOf<String?>(null) }
    var routeToolLoopRetryOptions by remember { mutableStateOf<List<RouteToolLoopRetryOption>>(emptyList()) }
    var routeToolResult by remember { mutableStateOf<RouteToolSaveResult?>(null) }
    var routeToolRenameInProgress by remember { mutableStateOf(false) }
    var routeToolRenameError by remember { mutableStateOf<String?>(null) }
    var routeToolPreview by remember { mutableStateOf<RouteToolModifyPreview?>(null) }
    var routeToolCreatePreview by remember { mutableStateOf<RouteToolCreatePreview?>(null) }
    var routeToolCreatePreviewInProgress by remember { mutableStateOf(false) }
    var routeToolCreatePreviewMessage by remember { mutableStateOf<String?>(null) }
    var routeToolPreflightMessage by remember { mutableStateOf<String?>(null) }
    var createdPoiCreateInProgress by remember { mutableStateOf(false) }
    var createdPoiPendingRename by remember { mutableStateOf<UserPoiRecord?>(null) }
    var showCreatedPoiRenameDialog by remember { mutableStateOf(false) }
    var createdPoiRenameInProgress by remember { mutableStateOf(false) }
    var createdPoiRenameError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val screenState =
        remember(isAmbient, isDeviceInteractive) {
            resolveLocationScreenState(
                isAmbient = isAmbient,
                isDeviceInteractive = isDeviceInteractive,
            )
        }

    // ---- Should track location? ----
    val backgroundGpsModeActive = keepGpsInAmbient && screenState.isNonInteractive
    val shouldTrackLocation =
        locationPermissionState.hasLocationPermission &&
            !offlineMode &&
            (isScreenResumed || backgroundGpsModeActive)
    val effectiveNavMode = if (offlineMode) NavMode.PANNING else navMode
    val effectiveCompassProviderMode = compassProviderMode
    val selectedCompassProviderType =
        when (effectiveCompassProviderMode) {
            SettingsRepository.COMPASS_PROVIDER_SENSOR_MANAGER ->
                CompassProviderType.SENSOR_MANAGER
            else -> CompassProviderType.GOOGLE_FUSED
        }
    val effectiveHeadingSourceMode =
        if (
            effectiveCompassProviderMode == SettingsRepository.COMPASS_PROVIDER_SENSOR_MANAGER
        ) {
            headingSourceMode
        } else {
            SettingsRepository.COMPASS_HEADING_SOURCE_AUTO
        }

    // ---- Heading + Accuracy ----
    val compassRenderState by compassViewModel.renderState.collectAsState()
    val compassAccuracy = compassRenderState.accuracy
    val magneticInterference = compassRenderState.magneticInterference
    val liveCompassQualityReading =
        compassQualityReadingFromRenderState(
            renderState = compassRenderState,
            nowElapsedMs = SystemClock.elapsedRealtime(),
        )
    val navigationMarkerStyle =
        remember(navigationMarkerStyleSetting) {
            navigationMarkerStyleFromSetting(navigationMarkerStyleSetting)
        }
    var displayedCompassQualityName by rememberSaveable {
        mutableStateOf(CompassMarkerQuality.MEDIUM.name)
    }
    val displayedCompassQuality =
        remember(displayedCompassQualityName) {
            runCatching { CompassMarkerQuality.valueOf(displayedCompassQualityName) }
                .getOrDefault(CompassMarkerQuality.MEDIUM)
        }
    var compassQualityWarmupUntilMs by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(isScreenResumed, screenState, offlineMode) {
        if (isScreenResumed && screenState.isInteractive && !offlineMode) {
            compassQualityWarmupUntilMs =
                SystemClock.elapsedRealtime() + COMPASS_QUALITY_STARTUP_GRACE_MS
        }
    }
    LaunchedEffect(compassQualityWarmupUntilMs, compassAccuracy) {
        val nowElapsedMs = SystemClock.elapsedRealtime()
        if (
            compassAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE &&
            compassQualityWarmupUntilMs > nowElapsedMs
        ) {
            delay(compassQualityWarmupUntilMs - nowElapsedMs)
            compassQualityWarmupUntilMs = 0L
        }
    }
    var lastCalibrationConfirmedAtMs by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(
        liveCompassQualityReading.quality,
        liveCompassQualityReading.hasQualitySample,
        liveCompassQualityReading.isStale,
        lastCalibrationConfirmedAtMs,
        compassQualityWarmupUntilMs,
    ) {
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val rawQuality = liveCompassQualityReading.quality ?: CompassMarkerQuality.UNRELIABLE
        val effectiveRawQuality =
            applyCompassStartupWarmupGuard(
                rawQuality = rawQuality,
                displayedQuality = displayedCompassQuality,
                nowElapsedMs = nowElapsedMs,
                warmupUntilElapsedMs = compassQualityWarmupUntilMs,
            )
        val targetQuality =
            displayTargetCompassQuality(
                rawQuality = effectiveRawQuality,
                nowElapsedMs = nowElapsedMs,
                lastCalibrationConfirmedAtElapsedMs = lastCalibrationConfirmedAtMs,
            )
        if (targetQuality == displayedCompassQuality) return@LaunchedEffect
        val holdMs =
            compassQualityTransitionHoldMs(
                from = displayedCompassQuality,
                to = targetQuality,
            )
        if (holdMs > 0L) {
            delay(holdMs)
        }
        val refreshedNowElapsedMs = SystemClock.elapsedRealtime()
        val refreshedReading =
            compassQualityReadingFromRenderState(
                renderState = compassRenderState,
                nowElapsedMs = refreshedNowElapsedMs,
            )
        val refreshedRawQuality = refreshedReading.quality ?: CompassMarkerQuality.UNRELIABLE
        val refreshedEffectiveRawQuality =
            applyCompassStartupWarmupGuard(
                rawQuality = refreshedRawQuality,
                displayedQuality = displayedCompassQuality,
                nowElapsedMs = refreshedNowElapsedMs,
                warmupUntilElapsedMs = compassQualityWarmupUntilMs,
            )
        val refreshedTargetQuality =
            displayTargetCompassQuality(
                rawQuality = refreshedEffectiveRawQuality,
                nowElapsedMs = refreshedNowElapsedMs,
                lastCalibrationConfirmedAtElapsedMs = lastCalibrationConfirmedAtMs,
            )
        if (refreshedTargetQuality == targetQuality) {
            displayedCompassQualityName = targetQuality.name
        }
    }
    val navigationMarkerSizePx =
        when (navigationMarkerStyle) {
            NavigationMarkerStyle.DOT -> 50
            NavigationMarkerStyle.TRIANGLE -> 50
        }
    val showCompassConeOverlay = navigationMarkerStyle == NavigationMarkerStyle.DOT
    val effectiveCompassConeAccuracyColorsEnabled =
        compassConeAccuracyColorsEnabled &&
            selectedCompassProviderType == CompassProviderType.SENSOR_MANAGER
    val compassConeQuality =
        if (effectiveCompassConeAccuracyColorsEnabled) {
            displayedCompassQuality
        } else {
            CompassMarkerQuality.GOOD
        }
    val compassConeHeadingErrorDeg =
        if (
            effectiveCompassConeAccuracyColorsEnabled &&
            liveCompassQualityReading.hasQualitySample &&
            !liveCompassQualityReading.isStale
        ) {
            liveCompassQualityReading.headingErrorDeg
        } else {
            null
        }
    val compassConeBaseSizePx =
        with(density) {
            when (screenSize) {
                WearScreenSize.LARGE -> 64.dp.roundToPx()
                WearScreenSize.MEDIUM -> 58.dp.roundToPx()
                WearScreenSize.SMALL -> 52.dp.roundToPx()
            }
        }

    LaunchedEffect(northReferenceMode, isScreenResumed) {
        if (!isScreenResumed) return@LaunchedEffect
        val mode =
            if (northReferenceMode == SettingsRepository.NORTH_REFERENCE_MAGNETIC) {
                NorthReferenceMode.MAGNETIC
            } else {
                NorthReferenceMode.TRUE
            }
        compassViewModel.setNorthReferenceMode(mode)
    }
    LaunchedEffect(effectiveCompassProviderMode) {
        compassViewModel.setProviderType(selectedCompassProviderType)
    }
    LaunchedEffect(effectiveHeadingSourceMode, isScreenResumed) {
        if (!isScreenResumed) return@LaunchedEffect
        val mode =
            when (effectiveHeadingSourceMode) {
                SettingsRepository.COMPASS_HEADING_SOURCE_TYPE_HEADING ->
                    CompassHeadingSourceMode.TYPE_HEADING
                SettingsRepository.COMPASS_HEADING_SOURCE_ROTATION_VECTOR ->
                    CompassHeadingSourceMode.ROTATION_VECTOR
                SettingsRepository.COMPASS_HEADING_SOURCE_MAGNETOMETER ->
                    CompassHeadingSourceMode.MAGNETOMETER
                else -> CompassHeadingSourceMode.AUTO
            }
        compassViewModel.setHeadingSourceMode(mode)
    }

    // ---- MAP OBJECTS ----
    val mapHolder: MapHolder =
        remember(zoomDefault, zoomMin, zoomMax) {
            mapViewModel.getOrCreateMapHolder(
                context = context,
                zoomDefault = zoomDefault,
                zoomMin = zoomMin,
                zoomMax = zoomMax,
            )
        }

    LaunchedEffect(mapHolder) {
        mapViewModel.setMapRenderer(mapHolder.renderer)
    }

    LaunchedEffect(mapHolder, northReferenceMode) {
        if (northReferenceMode != SettingsRepository.NORTH_REFERENCE_TRUE) return@LaunchedEffect
        val center = mapHolder.mapView.model.mapViewPosition.center
        compassViewModel.primeDeclinationFromApproximateLocation(
            latitude = center.latitude,
            longitude = center.longitude,
        )
    }

    val navigationMarkerBitmap =
        remember(navigationMarkerStyle, navigationMarkerSizePx) {
            val bitmap =
                createNavigationMarkerBitmap(
                    style = navigationMarkerStyle,
                    sizePx = navigationMarkerSizePx,
                )
            AndroidBitmap(bitmap)
        }

    NavigateCompassEffects(
        compassViewModel = compassViewModel,
        compassProviderType = selectedCompassProviderType,
        screenState = screenState,
        isOfflineMode = offlineMode,
    )

    // ---- Drive foreground pinning from keepAppOpen ----
    // Keep-app-open controls foreground pinning only; GPS runtime is gated by shouldTrackLocation.
    LaunchedEffect(keepAppOpen) {
        if (!hasAppliedInitialKeepAppOpenSync) {
            hasAppliedInitialKeepAppOpenSync = true
            if (!keepAppOpen) return@LaunchedEffect
        }
        locationViewModel.setKeepAppOpen(keepAppOpen)
    }

    // In follow modes, keep user location centered at all times.
    // autoRecenterEnabled only controls whether we exit panning automatically.
    val shouldFollowPosition = effectiveNavMode != NavMode.PANNING

    // Ensure zoom initialised once
    LaunchedEffect(zoomDefault) {
        navigateViewModel.initZoom(zoomDefault)
    }

    // Keep service runtime state in sync with one combined update so
    // screen-state transitions do not trigger back-to-back request recomputes.
    LaunchedEffect(screenState, shouldTrackLocation) {
        locationViewModel.syncRuntimeState(
            screenState = screenState,
            trackingEnabled = shouldTrackLocation,
        )
    }
    // isScreenResumed already reflects lifecycle resume, so one state-driven wake reacquire
    // effect is enough for both plain resume and ambient exit.
    LaunchedEffect(screenState, isScreenResumed, offlineMode, locationPermissionState.hasLocationPermission) {
        if (
            isScreenResumed &&
            screenState == LocationScreenState.INTERACTIVE &&
            !offlineMode &&
            locationPermissionState.hasLocationPermission
        ) {
            locationViewModel.requestImmediateLocation(
                source = NAVIGATE_WAKE_REACQUIRE_AMBIENT_EXIT_SOURCE,
            )
        }
    }
    DisposableEffect(locationViewModel) {
        onDispose {
            // Leaving Navigate resets runtime state to the safe baseline in one pass.
            locationViewModel.syncRuntimeState(
                screenState = LocationScreenState.INTERACTIVE,
                trackingEnabled = false,
            )
        }
    }

    NavigateCalibrationEffects(
        compassViewModel = compassViewModel,
        compassProviderType = selectedCompassProviderType,
        compassAccuracy = compassAccuracy,
        magneticInterference = magneticInterference,
        navMode = effectiveNavMode,
        isAmbient = isAmbient,
        promptForCalibration = promptForCalibration,
        showCalibrationDialog = showCalibrationDialog,
        onShowCalibrationDialog = { navigateViewModel.showCalibrationDialog() },
        onHideCalibrationDialog = { navigateViewModel.hideCalibrationDialog() },
        onApplyRecalibration = { compassViewModel.recalibrate() },
        onRecalibrationSucceeded = { lastCalibrationConfirmedAtMs = SystemClock.elapsedRealtime() },
    )

    val screenActions =
        rememberNavigateScreenActions(
            context = context,
            settingsViewModel = settingsViewModel,
            locationPermissionState = locationPermissionState,
            notificationPermissionState = notificationPermissionState,
            keepAppOpen = keepAppOpen,
            keepAppOpenTipShown = keepAppOpenTipShown,
            offlineMode = offlineMode,
            setPendingKeepAppOpen = { pendingKeepAppOpen = it },
            setShowKeepAppOpenInfoDialog = { showKeepAppOpenInfoDialog = it },
            setShortcutTrayExpanded = { shortcutTrayExpanded = it },
            isShortcutTrayExpanded = shortcutTrayExpanded,
        )

    // ---- Auto-recenter timer ----
    LaunchedEffect(effectiveNavMode, autoRecenterEnabled, autoRecenterDelay, offlineMode) {
        if (!offlineMode && effectiveNavMode == NavMode.PANNING && autoRecenterEnabled) {
            delay(autoRecenterDelay.toLong() * 1000L)
            navigateViewModel.onRecenterRequested()
        }
    }

    if (isAmbient) {
        AmbientScreen(
            ambientTick = ambientTickMs,
            timeFormat = navigateTimeFormat,
        )
        return
    }

    var pendingPoiFocusTarget by remember { mutableStateOf<PoiNavigateTarget?>(null) }
    var markerMotionDebugOverlayLabel by remember { mutableStateOf<String?>(null) }

    val mapView = mapHolder.mapView

    LaunchedEffect(gpsDebugTelemetry, gpsDebugTelemetryPopupEnabled, offlineMode) {
        if (!gpsDebugTelemetry || !gpsDebugTelemetryPopupEnabled || offlineMode) {
            markerMotionDebugOverlayLabel = null
            return@LaunchedEffect
        }

        while (isActive) {
            markerMotionDebugOverlayLabel = MarkerMotionTelemetry.latestSnapshot().overlayLabel()
            delay(250L)
        }
    }

    LaunchedEffect(navigateTarget, mapView, zoomMin, zoomMax) {
        val target = navigateTarget ?: return@LaunchedEffect
        navigateViewModel.onUserPanStarted()
        mapView.setCenter(LatLong(target.lat, target.lon))
        val focusZoom =
            poiFocusZoomLevel(
                mapView = mapView,
                latitude = target.lat,
                minZoom = zoomMin,
                maxZoom = zoomMax,
            )
        mapView.model.mapViewPosition.setZoomLevel(focusZoom.toByte(), false)
        navigateViewModel.onZoomChanged(focusZoom)
        pendingPoiFocusTarget = target
        poiViewModel.consumeNavigateTarget()
    }

    val locationUiState =
        rememberNavigateLocationUiState(
            mapView = mapView,
            locationViewModel = locationViewModel,
            compassViewModel = compassViewModel,
            navigateViewModel = navigateViewModel,
            shouldTrackLocation = shouldTrackLocation,
            shouldFollowPosition = shouldFollowPosition,
            screenState = screenState,
            // Keep marker-motion timing stable; the 1s wake burst is a service detail.
            expectedGpsIntervalMs = SettingsRepository.DEFAULT_GPS_INTERVAL_MS,
            navigationMarkerBitmap = navigationMarkerBitmap,
            suppressLocationMarker = offlineMode,
        )

    val locationMarker = locationUiState.locationMarker
    val gpsIndicatorState = locationUiState.gpsIndicatorState
    val effectiveGpsIndicatorState =
        if (offlineMode) {
            GpsFixIndicatorState.UNAVAILABLE
        } else {
            gpsIndicatorState
        }
    val gpsSignalSnapshot by locationViewModel.gpsSignalSnapshot.collectAsState()
    val gpsFixFreshForAccuracyCircle =
        gpsSignalSnapshot.isLocationAvailable &&
            gpsSignalSnapshot.lastFixElapsedRealtimeMs > 0L &&
            gpsSignalSnapshot.lastFixAgeMs in 0..gpsSignalSnapshot.lastFixFreshMaxAgeMs
    val watchGpsDegradedWarning = locationUiState.watchGpsDegradedWarning
    val mapAppearanceApplyInProgress by mapViewModel.mapAppearanceApplyInProgress.collectAsState()
    val slopeOverlayToggleEnabled by mapViewModel.reliefOverlayToggleEnabled.collectAsState()
    var slopeOverlayState by remember {
        mutableStateOf(
            MapRenderer.ReliefOverlayState(
                enabled = false,
                processing = false,
                progressPercent = null,
            ),
        )
    }

    DisposableEffect(mapHolder) {
        val listener: (MapRenderer.ReliefOverlayState) -> Unit = { state ->
            slopeOverlayState = state
        }
        mapHolder.renderer.addReliefOverlayStateListener(listener)
        onDispose {
            mapHolder.renderer.removeReliefOverlayStateListener(listener)
        }
    }

    // All overlays + popups + yellow A/B markers
    var renderedMapRotationDeg by remember { mutableFloatStateOf(0f) }
    var renderedCompassHeadingDeg by remember { mutableFloatStateOf(0f) }
    var visiblePoiMarkers by remember { mutableStateOf<List<PoiOverlayMarker>>(emptyList()) }
    MapOverlays(
        mapHolder = mapHolder,
        activeGpxDetails = activeGpxDetails,
        routeToolPreviewPoints =
            routeToolPreview?.previewPoints
                ?: routeToolCreatePreview?.previewPoints
                ?: emptyList(),
        routeToolCreatePreviewActive = routeToolCreatePreview != null,
        routeToolDraftPoints =
            routeToolSession
                ?.takeIf { it.isMultiPointCreate }
                ?.chainPoints
                ?: emptyList(),
        poiViewModel = poiViewModel,
        activePoiOverlaySources = activePoiOverlaySources,
        poiMarkerSizePx = poiIconSizePx,
        gpxTrackColor = gpxTrackColor,
        gpxTrackWidth = gpxTrackWidth,
        gpxTrackOpacityPercent = gpxTrackOpacityPercent,
        compassRenderStateFlow = compassViewModel.renderState,
        navMode = effectiveNavMode,
        forceNorthUpInPanning = offlineMode,
        showRealMarkerInCompassMode = true,
        showCompassConeOverlay = showCompassConeOverlay,
        compassConeBaseSizePx = compassConeBaseSizePx,
        compassQuality = compassConeQuality,
        compassHeadingErrorDeg = compassConeHeadingErrorDeg,
        gpsAccuracyCircleEnabled = gpsAccuracyCircleEnabled && !offlineMode,
        gpsFixAccuracyM = gpsSignalSnapshot.lastFixAccuracyM,
        gpsFixFresh = gpsFixFreshForAccuracyCircle,
        renderedHeadingDeg = renderedCompassHeadingDeg,
        locationMarker = locationMarker,
        inspectionUiState = inspectionUiState,
        selectedPointA = selectedPointA,
        selectedPointB = selectedPointB,
        onDismissInspection = { gpxViewModel.dismissInspection() },
        onStartSelectB = { gpxViewModel.startSelectingB() },
        isMetric = isMetric,
        onRenderedHeadingChanged = { renderedCompassHeadingDeg = it },
        onRenderedMapRotationChanged = { renderedMapRotationDeg = it },
        onPoiMarkersSnapshotChanged = { markers -> visiblePoiMarkers = markers },
    )

    BackHandler(enabled = true) {
        when {
            createdPoiCreateInProgress -> Unit
            completedRouteToolDraft != null -> {
                if (!routeToolExecutionInProgress) {
                    completedRouteToolDraft = null
                    routeToolPreview = null
                }
            }
            routeToolSession != null -> {
                routeToolSession = null
                routeToolCreatePreview = null
                routeToolCreatePreviewMessage = null
                routeToolCreatePreviewInProgress = false
            }
            showCreatedPoiRenameDialog -> {
                if (!createdPoiRenameInProgress) {
                    showCreatedPoiRenameDialog = false
                    createdPoiPendingRename = null
                    createdPoiRenameError = null
                    poiCreationSelectionActive = false
                }
            }
            poiCreationSelectionActive -> {
                poiCreationSelectionActive = false
            }
            showRouteToolsPanel -> {
                showRouteToolsPanel = false
                routeToolPreview = null
                routeToolCreatePreview = null
                routeToolCreatePreviewMessage = null
                routeToolCreatePreviewInProgress = false
                poiViewModel.clearOfflinePoiSearch()
            }
            shortcutTrayExpanded -> shortcutTrayExpanded = false
            else -> Unit
        }
    }

    val reshapePreviewInspectDraft =
        completedRouteToolDraft?.takeIf { draft ->
            draft.options.toolKind == RouteToolKind.MODIFY &&
                draft.options.modifyMode == RouteModifyMode.RESHAPE_ROUTE &&
                routeToolPreview != null
        }
    val reshapePreviewInspectMode = reshapePreviewInspectDraft != null

    OfflineStartCenteringEffect(
        isOfflineMode = offlineMode,
        mapView = mapView,
        mapViewModel = mapViewModel,
        selectedMapPath = selectedMapPath,
        activeGpxDetails = activeGpxDetails,
        skipInitialCentering = navigateTarget != null || pendingPoiFocusTarget != null,
    )

    val recenterTarget: LatLong? =
        if (offlineMode) {
            null
        } else {
            locationMarker?.latLong ?: uiState.lastKnownLocation
        }

    val routeToolActions =
        rememberNavigateRouteToolActions(
            context = context,
            scope = scope,
            mapView = mapView,
            gpxViewModel = gpxViewModel,
            poiViewModel = poiViewModel,
            locationViewModel = locationViewModel,
            recenterTarget = recenterTarget,
            gpsSignalSnapshot = gpsSignalSnapshot,
            offlineMode = offlineMode,
            activeGpxDetailsCount = activeGpxDetails.size,
            selectedMapPath = selectedMapPath,
            triggerHaptic = screenActions.triggerHaptic,
            routeToolOptions = routeToolOptions,
            setRouteToolOptions = { routeToolOptions = it },
            routeToolSession = routeToolSession,
            setRouteToolSession = { routeToolSession = it },
            completedRouteToolDraft = completedRouteToolDraft,
            setCompletedRouteToolDraft = { completedRouteToolDraft = it },
            routeToolExecutionInProgress = routeToolExecutionInProgress,
            setRouteToolExecutionInProgress = { routeToolExecutionInProgress = it },
            setRouteToolExecutionStatus = { routeToolExecutionStatus = it },
            setRouteToolExecutionMessage = { routeToolExecutionMessage = it },
            setRouteToolLoopRetryOptions = { routeToolLoopRetryOptions = it },
            setRouteToolResult = { routeToolResult = it },
            setRouteToolRenameInProgress = { routeToolRenameInProgress = it },
            setRouteToolRenameError = { routeToolRenameError = it },
            routeToolPreview = routeToolPreview,
            setRouteToolPreview = { routeToolPreview = it },
            routeToolCreatePreview = routeToolCreatePreview,
            setRouteToolCreatePreview = { routeToolCreatePreview = it },
            routeToolCreatePreviewInProgress = routeToolCreatePreviewInProgress,
            setRouteToolCreatePreviewInProgress = { routeToolCreatePreviewInProgress = it },
            routeToolCreatePreviewMessage = routeToolCreatePreviewMessage,
            setRouteToolCreatePreviewMessage = { routeToolCreatePreviewMessage = it },
            setRouteToolPreflightMessage = { routeToolPreflightMessage = it },
            setShortcutTrayExpanded = { shortcutTrayExpanded = it },
            setShowRouteToolsPanel = { showRouteToolsPanel = it },
            setPoiCreationSelectionActive = { poiCreationSelectionActive = it },
            createdPoiCreateInProgress = createdPoiCreateInProgress,
            setCreatedPoiCreateInProgress = { createdPoiCreateInProgress = it },
            setCreatedPoiPendingRename = { createdPoiPendingRename = it },
            setCreatedPoiRenameError = { createdPoiRenameError = it },
            setShowCreatedPoiRenameDialog = { showCreatedPoiRenameDialog = it },
        )

    NavigateKeepAppOpenDialog(
        visible = showKeepAppOpenInfoDialog,
        helpDialogMaxHeight = adaptive.helpDialogMaxHeight,
        onContinue = {
            showKeepAppOpenInfoDialog = false
            screenActions.continueKeepAppOpenEnableFlow()
        },
        onDismiss = {
            showKeepAppOpenInfoDialog = false
            pendingKeepAppOpen = false
        },
    )

    NavigateCreatedPoiRenameDialog(
        visible = showCreatedPoiRenameDialog,
        pendingRename = createdPoiPendingRename,
        isSaving = createdPoiRenameInProgress,
        error = createdPoiRenameError,
        onDismiss = {
            if (!createdPoiRenameInProgress) {
                showCreatedPoiRenameDialog = false
                createdPoiPendingRename = null
                createdPoiRenameError = null
                poiCreationSelectionActive = false
            }
        },
        onConfirm = { newName ->
            val target = createdPoiPendingRename ?: return@NavigateCreatedPoiRenameDialog
            if (createdPoiRenameInProgress) return@NavigateCreatedPoiRenameDialog
            createdPoiRenameInProgress = true
            createdPoiRenameError = null
            scope.launch {
                runCatching {
                    poiViewModel.renameMyCreationPoi(target.id, newName)
                }.onSuccess {
                    createdPoiRenameInProgress = false
                    showCreatedPoiRenameDialog = false
                    createdPoiPendingRename = null
                    createdPoiRenameError = null
                    poiCreationSelectionActive = false
                }.onFailure { error ->
                    createdPoiRenameInProgress = false
                    createdPoiRenameError = error.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "Failed to rename the POI."
                }
            }
        },
    )

    NavigateRouteToolDialogs(
        showRouteToolsPanel = showRouteToolsPanel,
        canModifyActiveGpx = activeGpxDetails.size == 1,
        coordinateSeed = mapView.model.mapViewPosition.center,
        poiSearchState = offlinePoiSearchUiState,
        options = routeToolOptions,
        preflightMessage = routeToolPreflightMessage,
        onOptionsChange = {
            routeToolPreflightMessage = null
            routeToolOptions = it.withVisibleLoopDefaults()
        },
        onSearchPoi = { query -> poiViewModel.searchOfflinePoi(query) },
        onClearPoiSearch = { poiViewModel.clearOfflinePoiSearch() },
        onDismissRouteToolsPanel = {
            showRouteToolsPanel = false
            routeToolPreflightMessage = null
            poiViewModel.clearOfflinePoiSearch()
        },
        onStartRouteToolSelection = routeToolActions.startRouteToolSelection,
        completedRouteToolDraft = if (reshapePreviewInspectMode) null else completedRouteToolDraft,
        routeToolExecutionInProgress = routeToolExecutionInProgress,
        routeToolExecutionMessage = routeToolExecutionMessage,
        routeToolLoopRetryOptions = routeToolLoopRetryOptions,
        onDismissDraftSummary = {
            if (!routeToolExecutionInProgress) {
                completedRouteToolDraft = null
                routeToolExecutionMessage = null
                routeToolLoopRetryOptions = emptyList()
                routeToolPreview = null
            }
        },
        onConfirmCreateDraft =
            completedRouteToolDraft
                ?.takeIf { it.options.toolKind == RouteToolKind.CREATE }
                ?.let { draft ->
                    {
                        routeToolLoopRetryOptions = emptyList()
                        if (draft.options.createMode == RouteCreateMode.LOOP_AROUND_HERE) {
                            routeToolActions.startRouteToolSelection(draft)
                        } else {
                            routeToolActions.executeCreateDraft(draft, false)
                        }
                    }
                },
        onConfirmModifyDraft =
            completedRouteToolDraft
                ?.takeIf { it.options.toolKind == RouteToolKind.MODIFY }
                ?.let { draft ->
                    {
                        routeToolActions.executeModifyDraft(draft, false)
                    }
                },
        onSelectLoopRetryOption = { retryOption ->
            val draft = completedRouteToolDraft ?: return@NavigateRouteToolDialogs
            routeToolExecutionMessage = null
            routeToolLoopRetryOptions = emptyList()
            routeToolOptions = retryOption.options.withVisibleLoopDefaults()
            routeToolActions.startRouteToolSelection(
                draft.copy(
                    options = retryOption.options,
                    loopVariationIndex = 0,
                ),
            )
        },
        routeToolProgressVisible =
            routeToolExecutionInProgress &&
                completedRouteToolDraft == null &&
                routeToolExecutionStatus != null,
        routeToolProgressMessage = routeToolExecutionStatus ?: "Working...",
        routeToolResult = routeToolResult,
        isMetric = isMetric,
        routeToolRenameInProgress = routeToolRenameInProgress,
        routeToolRenameError = routeToolRenameError,
        onDismissRouteToolResult = {
            if (!routeToolRenameInProgress) {
                routeToolResult = null
                routeToolRenameError = null
            }
        },
        onDeleteRouteToolResult = {
            val currentResult = routeToolResult ?: return@NavigateRouteToolDialogs
            if (routeToolRenameInProgress) return@NavigateRouteToolDialogs
            gpxViewModel.deleteGpxFile(currentResult.filePath)
            routeToolResult = null
            routeToolRenameError = null
        },
        onOpenRouteToolRename = {
            routeToolRenameError = null
        },
        onConfirmRouteToolRename = { newName ->
            val currentResult = routeToolResult ?: return@NavigateRouteToolDialogs
            if (routeToolRenameInProgress) return@NavigateRouteToolDialogs
            routeToolRenameInProgress = true
            routeToolRenameError = null
            gpxViewModel.renameRouteToolResult(
                filePath = currentResult.filePath,
                newName = newName,
            ) { result ->
                routeToolRenameInProgress = false
                result
                    .onSuccess { updatedResult ->
                        routeToolRenameError = null
                        routeToolResult = updatedResult
                    }.onFailure { error ->
                        routeToolRenameError = error.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: "Failed to rename the GPX."
                    }
            }
        },
    )

    NavigateContent(
        hasLocationPermission = locationPermissionState.hasLocationPermission || offlineMode,
        focusRequester = focusRequester,
        mapHolder = mapHolder,
        onMapHolderChange = { /* no-op */ },
        onMapViewReadyForRendering = { mapViewModel.onMapViewReadyForRendering() },
        mapAppearanceApplyInProgress = mapAppearanceApplyInProgress,
        slopeOverlayToggleEnabled = slopeOverlayToggleEnabled,
        slopeOverlayEnabled = slopeOverlayState.enabled,
        slopeOverlayProcessing = slopeOverlayState.processing,
        slopeOverlayProgressPercent = slopeOverlayState.progressPercent,
        zoomDefault = zoomDefault,
        zoomMin = zoomMin,
        zoomMax = zoomMax,
        crownZoomEnabled = crownZoomEnabled,
        crownZoomInverted = crownZoomInverted,
        mapZoomButtonsMode = mapZoomButtonsMode,
        northIndicatorMode = northIndicatorMode,
        currentZoomLevel = currentZoomLevel,
        onZoomLevelChange = { newZoom -> navigateViewModel.onZoomChanged(newZoom) },
        onViewportChanged = { center, zoomLevel ->
            if (offlineMode) {
                mapViewModel.saveOfflineViewport(
                    selectedMapPath = selectedMapPath,
                    activeGpxDetails = activeGpxDetails,
                    center = center,
                    zoomLevel = zoomLevel,
                )
            }
        },
        isMetric = isMetric,
        navMode = effectiveNavMode,
        lastKnownLocation = recenterTarget,
        onToggleOrientation = {
            if (!offlineMode) {
                navigateViewModel.onToggleOrientation()
            }
        },
        onUserPanStarted = { navigateViewModel.onUserPanStarted() },
        onRecenter = { navigateViewModel.onRecenterRequested() },
        onRecenterRequested = {
            if (!offlineMode) {
                locationViewModel.requestImmediateLocation(source = "ui_recenter_from_panning")
            }
        },
        triggerHaptic = screenActions.triggerHaptic,
        onMenuClick = onMenuClick,
        onPermissionLaunch = { locationPermissionState.launchPermissions() },
        mapRotationDeg = renderedMapRotationDeg,
        compassHeadingDeg = renderedCompassHeadingDeg,
        liveElevationEnabled = liveElevationEnabled,
        liveDistanceEnabled = liveDistanceEnabled && !offlineMode,
        keepAppOpen = keepAppOpen,
        onKeepAppOpenToggle = screenActions.toggleKeepAppOpen,
        shortcutTrayExpanded = shortcutTrayExpanded,
        onShortcutTrayToggle = screenActions.toggleShortcutTray,
        onShortcutTrayDismiss = { shortcutTrayExpanded = false },
        onOpenGpxTools = routeToolActions.openRouteToolsPanel,
        onStartPoiCreation = routeToolActions.startPoiCreationSelection,
        gpsIndicatorState = effectiveGpsIndicatorState,
        watchGpsDegradedWarning = watchGpsDegradedWarning,
        isOfflineMode = offlineMode,
        isGpxInspectionEnabled = isGpxInspectionEnabled,
        activeGpxDetails = activeGpxDetails,
        gpxTrackColor = gpxTrackColor,
        routeToolSession = routeToolSession,
        crosshairSelectionActive = poiCreationSelectionActive,
        crosshairSelectionTitle = "+ POI",
        crosshairSelectionInstruction = "Move map, then check.",
        crosshairSelectionBusy = createdPoiCreateInProgress,
        crosshairSelectionBusyMessage = "Saving POI...",
        routeToolCreatePreview = routeToolCreatePreview,
        routeToolCreatePreviewInProgress = routeToolCreatePreviewInProgress,
        routeToolCreatePreviewMessage = routeToolCreatePreviewMessage,
        reshapePreviewInspectMode = reshapePreviewInspectMode,
        reshapePreviewPoints = routeToolPreview?.previewPoints ?: emptyList(),
        reshapePreviewBusy = routeToolExecutionInProgress,
        reshapePreviewBusyMessage = routeToolExecutionStatus,
        reshapePreviewMessage = routeToolExecutionMessage,
        onRouteToolPickHere = routeToolActions.captureRouteToolPoint,
        onRouteToolUndoLastPoint = routeToolActions.undoRouteToolPoint,
        onRouteToolSaveCreatePreview = routeToolActions.saveCreatePreview,
        onRouteToolRefreshCreatePreview = routeToolActions.refreshLoopPreview,
        onCancelRouteToolMode = {
            routeToolSession = null
            routeToolCreatePreview = null
            routeToolCreatePreviewMessage = null
            routeToolCreatePreviewInProgress = false
        },
        onDismissReshapePreview = {
            if (!routeToolExecutionInProgress) {
                completedRouteToolDraft = null
                routeToolExecutionMessage = null
                routeToolLoopRetryOptions = emptyList()
                routeToolPreview = null
            }
        },
        onSaveReshapePreview = {
            reshapePreviewInspectDraft?.let { draft ->
                routeToolActions.executeModifyDraft(draft, false)
            }
        },
        onCrosshairSelectionPickHere = routeToolActions.savePoiAtCurrentMapCenter,
        onCancelCrosshairSelection = { poiCreationSelectionActive = false },
        onInspectTrack = { latLong -> gpxViewModel.onMapLongPress(latLong) },
        visiblePoiMarkers = visiblePoiMarkers,
        poiFocusTarget = pendingPoiFocusTarget,
        onPoiFocusTargetConsumed = { pendingPoiFocusTarget = null },
        onPoiTapCreateGpx = routeToolActions.createRouteToPoi,
        poiPopupTimeoutSeconds = poiPopupTimeoutSeconds,
        poiPopupManualCloseOnly = poiPopupManualCloseOnly,
        markerMotionDebugOverlayLabel = markerMotionDebugOverlayLabel,
    )

    LaunchedEffect(isScreenResumed) {
        if (isScreenResumed) {
            focusRequester.requestFocus()
        }
    }

    DisposableEffect(mapView, offlineMode, selectedMapPath, activeGpxDetails) {
        onDispose {
            if (offlineMode) {
                mapViewModel.saveOfflineViewport(
                    selectedMapPath = selectedMapPath,
                    activeGpxDetails = activeGpxDetails,
                    center = mapView.model.mapViewPosition.center,
                    zoomLevel =
                        mapView.model.mapViewPosition.zoomLevel
                            .toInt(),
                )
            }
        }
    }
}
