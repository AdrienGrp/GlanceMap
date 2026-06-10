package com.glancemap.glancemapwearos.presentation.features.navigate

import android.hardware.SensorManager
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glancemap.glancemapwearos.core.maps.MAP_ZOOM_REPRESENTATIVE_LATITUDE_DEGREES
import com.glancemap.glancemapwearos.core.maps.mapZoomLevelsForScaleSettings
import com.glancemap.glancemapwearos.core.service.diagnostics.BenchmarkTrace
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
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
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.GpxGuidanceTuning
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.GpxGuidanceSession
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.TurnByTurnGuidanceState
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.bearingDegrees
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.buildCumulativeDistances
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.computeTurnByTurnGuidanceState
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.haversineMeters
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.isGuidanceStartReached
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.projectLocationToRoute
import com.glancemap.glancemapwearos.presentation.features.navigate.motion.MarkerMotionTelemetry
import com.glancemap.glancemapwearos.presentation.features.offline.OfflineStartCenteringEffect
import com.glancemap.glancemapwearos.presentation.features.poi.PoiNavigateTarget
import com.glancemap.glancemapwearos.presentation.features.poi.PoiOverlayMarker
import com.glancemap.glancemapwearos.presentation.features.poi.PoiViewModel
import com.glancemap.glancemapwearos.presentation.features.recording.TraceRecordingViewModel
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteCreateMode
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteModifyMode
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolCreatePreview
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolKind
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolLoopRetryOption
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolModifyPreview
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolOptions
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolProgressDialog
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolSaveResult
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolSession
import com.glancemap.glancemapwearos.presentation.features.routetools.routeToolMultiPointDraftConnectorPoints
import com.glancemap.glancemapwearos.presentation.features.routetools.visibleRouteToolCreatePreview
import com.glancemap.glancemapwearos.presentation.features.routetools.withVisibleLoopDefaults
import com.glancemap.glancemapwearos.presentation.features.settings.SettingsViewModel
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec
import com.glancemap.glancemapwearos.presentation.ui.rememberWearScreenSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mapsforge.core.model.LatLong
import java.util.Locale
import org.mapsforge.map.android.graphics.AndroidBitmap
import kotlin.math.abs

@Suppress("CyclomaticComplexMethod", "FunctionNaming", "LongMethod", "LongParameterList")
@Composable
fun NavigateScreen(
    mapViewModel: MapViewModel,
    gpxViewModel: GpxViewModel,
    poiViewModel: PoiViewModel,
    settingsViewModel: SettingsViewModel,
    locationViewModel: LocationViewModel,
    traceRecordingViewModel: TraceRecordingViewModel,
    isAmbient: Boolean,
    isDeviceInteractive: Boolean,
    ambientTickMs: Long,
    onNavigateTimeSuppressedChange: (Boolean) -> Unit = {},
    recordingDashboardExpandRequestToken: Long = 0L,
    recordingActionPromptRequestToken: Long = 0L,
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
    val configuration = LocalConfiguration.current
    val adaptive = rememberWearAdaptiveSpec()
    val screenSize = rememberWearScreenSize()
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current
    SideEffect {
        BenchmarkTrace.mark("recompose.NavigateScreen")
    }
    var isScreenResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var lastScreenResumeElapsedMs by remember(lifecycleOwner) {
        mutableLongStateOf(SystemClock.elapsedRealtime())
    }
    var menuClickGuardUntilElapsedMs by remember(lifecycleOwner) {
        mutableLongStateOf(lastScreenResumeElapsedMs + NAVIGATE_MENU_CLICK_RESUME_GUARD_MS)
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        val nowElapsedMs = SystemClock.elapsedRealtime()
                        isScreenResumed = true
                        lastScreenResumeElapsedMs = nowElapsedMs
                        menuClickGuardUntilElapsedMs = nowElapsedMs + NAVIGATE_MENU_CLICK_RESUME_GUARD_MS
                    }
                    Lifecycle.Event.ON_PAUSE -> isScreenResumed = false
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(isScreenResumed, isDeviceInteractive) {
        if (isScreenResumed && isDeviceInteractive) {
            val nowElapsedMs = SystemClock.elapsedRealtime()
            lastScreenResumeElapsedMs = nowElapsedMs
            menuClickGuardUntilElapsedMs = nowElapsedMs + NAVIGATE_MENU_CLICK_RESUME_GUARD_MS
        }
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
    var showNotificationPermissionDialog by rememberSaveable { mutableStateOf(false) }
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
                    showNotificationPermissionDialog = true
                } else {
                    settingsViewModel.setKeepAppOpen(true)
                    pendingKeepAppOpen = false
                }
            } else if (!granted) {
                pendingKeepAppOpen = false
            }
        }
    val notificationPermissionPromptState =
        notificationPermissionState.copy(
            launchPermissionRequest = { showNotificationPermissionDialog = true },
        )

    // ---- SETTINGS ----
    val zoomDefaultScaleMeters by settingsViewModel.mapZoomDefaultScaleMeters.collectAsState()
    val zoomMinScaleMeters by settingsViewModel.mapZoomMinScaleMeters.collectAsState()
    val zoomMaxScaleMeters by settingsViewModel.mapZoomMaxScaleMeters.collectAsState()
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
    val gpxTrackColorMode by settingsViewModel.gpxTrackColorMode.collectAsState()
    val gpxTrackWidth by settingsViewModel.gpxTrackWidth.collectAsState()
    val gpxTrackOpacityPercent by settingsViewModel.gpxTrackOpacityPercent.collectAsState()
    val gpxTrackDirectionArrowsEnabled by settingsViewModel.gpxTrackDirectionArrowsEnabled.collectAsState()
    val autoRecenterEnabled by settingsViewModel.autoRecenterEnabled.collectAsState()
    val autoRecenterDelay by settingsViewModel.autoRecenterDelay.collectAsState(initial = 5)
    val promptForCalibration by settingsViewModel.promptForCalibration.collectAsState(initial = false)
    val keepGpsInAmbient by settingsViewModel.gpsInAmbientMode.collectAsState(initial = false)
    val turnByTurnHapticsEnabled by settingsViewModel.turnByTurnHapticsEnabled.collectAsState(initial = true)
    val turnByTurnTurnAlertsMode by settingsViewModel.turnByTurnTurnAlertsMode.collectAsState(
        initial = SettingsRepository.TURN_BY_TURN_TURN_ALERTS_IMPORTANT,
    )
    val turnByTurnOffRouteAlertsEnabled by settingsViewModel.turnByTurnOffRouteAlertsEnabled.collectAsState(
        initial = true,
    )
    val turnByTurnOffRouteThresholdMeters by settingsViewModel.turnByTurnOffRouteAlertThresholdMeters.collectAsState(
        initial = SettingsRepository.DEFAULT_TURN_BY_TURN_OFF_ROUTE_ALERT_THRESHOLD_METERS,
    )
    val turnByTurnOffRouteRepeatSeconds by settingsViewModel.turnByTurnOffRouteRepeatSeconds.collectAsState(
        initial = SettingsRepository.DEFAULT_TURN_BY_TURN_OFF_ROUTE_REPEAT_SECONDS,
    )
    val turnByTurnGpsInAmbient by settingsViewModel.turnByTurnGpsInAmbientMode.collectAsState(initial = false)
    val turnByTurnBrouterGuideBackEnabled by settingsViewModel.turnByTurnBrouterGuideBackEnabled.collectAsState(
        initial = false,
    )
    val turnByTurnRouteStartBehavior by settingsViewModel.turnByTurnRouteStartBehavior.collectAsState(
        initial = SettingsRepository.TURN_BY_TURN_ROUTE_START_GO_TO_START,
    )
    val turnByTurnReverseSuggestionMode by settingsViewModel.turnByTurnReverseSuggestionMode.collectAsState(
        initial = SettingsRepository.TURN_BY_TURN_REVERSE_SUGGESTION_ASK,
    )
    val crownZoomEnabled by settingsViewModel.crownZoomEnabled.collectAsState(initial = true)
    val crownZoomInverted by settingsViewModel.crownZoomInverted.collectAsState(initial = true)
    val navigateTimeFormat by settingsViewModel.navigateTimeFormat.collectAsState()
    val mapZoomButtonsMode by settingsViewModel.mapZoomButtonsMode.collectAsState()
    val navigationMarkerStyleSetting by settingsViewModel.navigationMarkerStyle.collectAsState()
    val navigationMarkerAnchorMode by settingsViewModel.navigationMarkerAnchorMode.collectAsState()
    val gpsAccuracyCircleEnabled by settingsViewModel.gpsAccuracyCircleEnabled.collectAsState(initial = false)
    val liveElevationEnabled by settingsViewModel.liveElevation.collectAsState(initial = false)
    val liveDistanceEnabled by settingsViewModel.liveDistance.collectAsState(initial = false)
    val offlineMode by settingsViewModel.offlineMode.collectAsState(initial = false)
    val gpsDebugTelemetry by settingsViewModel.gpsDebugTelemetry.collectAsState()
    val gpsDebugTelemetryPopupEnabled by settingsViewModel.gpsDebugTelemetryPopupEnabled.collectAsState(initial = true)
    val isGpxInspectionEnabled by settingsViewModel.isGpxInspectionEnabled.collectAsState()
    val isMetric by settingsViewModel.isMetric.collectAsState()
    val backButtonExitsNavigation by settingsViewModel.backButtonExitsNavigation.collectAsState()
    val poiIconSizePx by settingsViewModel.poiIconSizePx.collectAsState()
    val poiMarkerStyle by settingsViewModel.poiMarkerStyle.collectAsState()
    val poiPopupTimeoutSeconds by settingsViewModel.poiPopupTimeoutSeconds.collectAsState(
        initial = SettingsRepository.POI_POPUP_TIMEOUT_DEFAULT_SECONDS,
    )
    val poiPopupManualCloseOnly by settingsViewModel.poiPopupManualCloseOnly.collectAsState(initial = false)
    val recordingDashboardMetricSlots by settingsViewModel.recordingDashboardMetricSlots.collectAsState()
    val compassConeAccuracyColorsEnabled by settingsViewModel.compassConeAccuracyColorsEnabled.collectAsState(
        initial = true,
    )

    // ---- VMS ----
    val selectedMapPath by mapViewModel.selectedMapPath.collectAsState()
    val activeGpxDetails by gpxViewModel.activeGpxDetails.collectAsState()
    val turnByTurnGuidanceSession by gpxViewModel.turnByTurnGuidanceSession.collectAsState()
    val turnByTurnGuidancePaused by gpxViewModel.turnByTurnGuidancePaused.collectAsState()
    val activeTurnByTurnGuidanceSession =
        if (turnByTurnGuidancePaused) {
            null
        } else {
            turnByTurnGuidanceSession
        }
    val effectiveNavigationMarkerAnchorMode =
        if (turnByTurnGuidanceSession != null) {
            SettingsRepository.NAVIGATION_MARKER_ANCHOR_LOWER
        } else {
            navigationMarkerAnchorMode
        }
    val activePoiOverlaySources by poiViewModel.activeOverlaySources.collectAsState()
    val navigateTarget by poiViewModel.navigateTarget.collectAsState()
    val offlinePoiSearchUiState by poiViewModel.offlineSearchUiState.collectAsState()
    val traceRecordingState by traceRecordingViewModel.uiState.collectAsState()
    val recordingTracePoints =
        remember(traceRecordingState.points) {
            traceRecordingState.points.map { it.latLong }
        }
    LaunchedEffect(traceRecordingState.message) {
        traceRecordingState.message
            ?.takeIf { it.isNotBlank() }
            ?.let { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

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
    val zoomDefault = mapZoomLevels.default
    val zoomMin = mapZoomLevels.min
    val zoomMax = mapZoomLevels.max

    // Inspection UI state
    val inspectionUiState by gpxViewModel.inspectionUiState.collectAsState()

    // A/B marker points
    val selectedPointA by gpxViewModel.selectedPointA.collectAsState()
    val selectedPointB by gpxViewModel.selectedPointB.collectAsState()
    val selectingGpxPointB by gpxViewModel.selectingPointB.collectAsState()
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
    val effectiveBackgroundGpsEnabled =
        keepGpsInAmbient ||
            (activeTurnByTurnGuidanceSession != null && turnByTurnGpsInAmbient) ||
            traceRecordingState.active
    val backgroundGpsModeActive = effectiveBackgroundGpsEnabled && screenState.isNonInteractive
    val shouldTrackLocation =
        locationPermissionState.hasLocationPermission &&
            !offlineMode &&
            (isScreenResumed || backgroundGpsModeActive || traceRecordingState.active)
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

    LaunchedEffect(mapHolder, selectedMapPath) {
        val mapView = mapHolder.mapView
        val center = mapView.model.mapViewPosition.center
        val nextLatitude = center.latitude.coerceIn(-85.0, 85.0)
        if (shouldUpdateZoomReferenceLatitude(zoomReferenceLatitude, nextLatitude)) {
            zoomReferenceLatitude = nextLatitude
        }
        if (mapView.width > 0 && mapView.width != zoomViewportWidthPx) {
            zoomViewportWidthPx = mapView.width
        }
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
    LaunchedEffect(screenState, shouldTrackLocation, effectiveBackgroundGpsEnabled) {
        locationViewModel.syncRuntimeState(
            screenState = screenState,
            trackingEnabled = shouldTrackLocation,
            backgroundGpsEnabled = effectiveBackgroundGpsEnabled,
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
                backgroundGpsEnabled = false,
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
            notificationPermissionState = notificationPermissionPromptState,
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
            navigationMarkerAnchorMode = effectiveNavigationMarkerAnchorMode,
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
    val rawCurrentLocation by locationViewModel.currentLocation.collectAsState()
    LaunchedEffect(rawCurrentLocation, traceRecordingState.active) {
        if (traceRecordingState.active) {
            traceRecordingViewModel.onLocation(rawCurrentLocation)
        }
    }
    val gpsFixFreshForAccuracyCircle =
        gpsSignalSnapshot.isLocationAvailable &&
            gpsSignalSnapshot.lastFixElapsedRealtimeMs > 0L &&
            gpsSignalSnapshot.lastFixAgeMs in 0..gpsSignalSnapshot.lastFixFreshMaxAgeMs
    val watchGpsDegradedWarning = locationUiState.watchGpsDegradedWarning
    val gpsEnvironmentWarning = locationUiState.gpsEnvironmentWarning
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
    var renderedCompassHeadingDeg by
        remember {
            mutableFloatStateOf(
                resolveNavigateInitialRenderedHeadingDeg(
                    renderState = compassRenderState,
                    nowElapsedMs = SystemClock.elapsedRealtime(),
                ),
            )
        }
    var visiblePoiMarkers by remember { mutableStateOf<List<PoiOverlayMarker>>(emptyList()) }
    val displayedRouteToolCreatePreview =
        visibleRouteToolCreatePreview(
            session = routeToolSession,
            createPreview = routeToolCreatePreview,
            createPreviewInProgress = routeToolCreatePreviewInProgress,
        )
    val routeToolDraftConnectorPoints =
        routeToolMultiPointDraftConnectorPoints(
            session = routeToolSession,
            visibleCreatePreview = displayedRouteToolCreatePreview,
            createPreviewInProgress = routeToolCreatePreviewInProgress,
        )
    MapOverlays(
        mapHolder = mapHolder,
        activeGpxDetails = activeGpxDetails,
        routeToolPreviewPoints =
            routeToolPreview?.previewPoints
                ?: displayedRouteToolCreatePreview?.previewPoints
                ?: emptyList(),
        recordingTracePoints = recordingTracePoints,
        routeToolCreatePreviewActive = displayedRouteToolCreatePreview != null,
        routeToolDraftPoints = routeToolDraftConnectorPoints,
        poiViewModel = poiViewModel,
        activePoiOverlaySources = activePoiOverlaySources,
        poiMarkerSizePx = poiIconSizePx,
        poiMarkerStyle = poiMarkerStyle,
        gpxTrackColor = gpxTrackColor,
        gpxTrackColorMode = gpxTrackColorMode,
        gpxTrackWidth = gpxTrackWidth,
        gpxTrackOpacityPercent = gpxTrackOpacityPercent,
        gpxTrackDirectionArrowsEnabled = gpxTrackDirectionArrowsEnabled,
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
        gpsFixSpeedMps = locationUiState.lastFixSpeedMps,
        gpsFixBearingDeg = locationUiState.lastFixBearingDeg,
        renderedHeadingDeg = renderedCompassHeadingDeg,
        locationMarker = locationMarker,
        navigationMarkerAnchorMode = effectiveNavigationMarkerAnchorMode,
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
            else -> {
                if (backButtonExitsNavigation) {
                    DebugTelemetry.log(
                        "NavigationTelemetry",
                        "event=navigate_back_to_menu route=navigate_screen reason=no_overlay_open compat=true",
                    )
                    onMenuClick()
                } else {
                    DebugTelemetry.log(
                        "NavigationTelemetry",
                        "event=navigate_back_ignored route=navigate_screen reason=no_overlay_open compat=false",
                    )
                }
            }
        }
    }

    val reshapePreviewInspectDraft =
        completedRouteToolDraft?.takeIf { draft ->
            draft.options.toolKind == RouteToolKind.MODIFY &&
                draft.options.modifyMode == RouteModifyMode.RESHAPE_ROUTE &&
                routeToolPreview != null
        }
    val reshapePreviewInspectMode = reshapePreviewInspectDraft != null
    val gpsStartupMapCenteringPending =
        !offlineMode &&
            shouldTrackLocation &&
            locationMarker == null &&
            uiState.lastKnownLocation == null
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
        uiState.lastKnownLocation.takeIf {
            !offlineMode &&
                shouldTrackLocation &&
                locationMarker == null &&
                navigateTarget == null &&
                pendingPoiFocusTarget == null
        }

    LaunchedEffect(gpsStartupLastKnownCenter, mapView, effectiveNavigationMarkerAnchorMode) {
        gpsStartupLastKnownCenter?.let {
            mapView.setCenterForNavigationMarker(it, effectiveNavigationMarkerAnchorMode)
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

    val recenterTarget: LatLong? =
        if (offlineMode) {
            null
        } else {
            locationMarker?.latLong ?: uiState.lastKnownLocation
        }
    val guidanceLocation: LatLong? =
        if (offlineMode) {
            null
        } else {
            rawCurrentLocation?.let { location ->
                LatLong(location.latitude, location.longitude)
            } ?: recenterTarget
        }
    val turnByTurnGuidanceTuning =
        remember(turnByTurnOffRouteThresholdMeters) {
            GpxGuidanceTuning(
                offRouteDistanceMeters = turnByTurnOffRouteThresholdMeters.toDouble(),
            )
        }
    val turnByTurnGuidanceState =
        computeTurnByTurnGuidanceState(
            session = activeTurnByTurnGuidanceSession,
            currentLocation = guidanceLocation,
            tuning = turnByTurnGuidanceTuning,
        )
    var guideBackToRouteActive by remember { mutableStateOf(false) }
    var brouterGuideBackRoute by remember { mutableStateOf<List<LatLong>>(emptyList()) }
    var dismissedGuideBackPromptTrackId by remember { mutableStateOf<String?>(null) }
    val guideBackTrackId = activeTurnByTurnGuidanceSession?.trackId
    val guideBackTargetPoint =
        nearestGuidanceRoutePoint(
            session = activeTurnByTurnGuidanceSession,
            currentLocation = guidanceLocation,
        )
    LaunchedEffect(
        turnByTurnGuidanceState.active,
        turnByTurnGuidanceState.offRoute,
        guideBackTrackId,
    ) {
        if (!turnByTurnGuidanceState.active || !turnByTurnGuidanceState.offRoute) {
            guideBackToRouteActive = false
            brouterGuideBackRoute = emptyList()
            dismissedGuideBackPromptTrackId = null
        }
    }
    val brouterGuideBackState =
        remember(guideBackToRouteActive, brouterGuideBackRoute, guidanceLocation, turnByTurnGuidanceState) {
            buildBrouterGuideBackState(
                baseState = turnByTurnGuidanceState,
                active = guideBackToRouteActive,
                route = brouterGuideBackRoute,
                currentLocation = guidanceLocation,
            )
        }
    val showGuideBackPrompt =
        turnByTurnGuidanceState.active &&
            turnByTurnGuidanceState.offRoute &&
            !guideBackToRouteActive &&
            dismissedGuideBackPromptTrackId != guideBackTrackId
    var pendingStartDecision by remember { mutableStateOf<GuidanceStartDecision?>(null) }
    var dismissedStartDecisionKey by remember { mutableStateOf<String?>(null) }
    var startHereStableSampleCount by remember { mutableStateOf(0) }
    val startDecisionKey =
        pendingStartDecision?.let { decision ->
            "$guideBackTrackId:${activeTurnByTurnGuidanceSession?.reversed}:$decision"
        }
    val startDecisionPrompt =
        pendingStartDecision?.let { decision ->
            when (decision) {
                GuidanceStartDecision.REVERSE_ROUTE ->
                    GuidanceDecisionPrompt(
                        title = "Closer to end",
                        detail = "Follow GPX in reverse?",
                        acceptText = "Reverse",
                        dismissText = "Start",
                    )
                GuidanceStartDecision.START_HERE ->
                    GuidanceDecisionPrompt(
                        title = "On route",
                        detail = "Start from nearest point?",
                        acceptText = "Start",
                        dismissText = "GPX start",
                    )
            }
        }

    LaunchedEffect(
        activeTurnByTurnGuidanceSession,
        guidanceLocation,
        rawCurrentLocation?.accuracy,
        turnByTurnRouteStartBehavior,
        turnByTurnReverseSuggestionMode,
        turnByTurnOffRouteThresholdMeters,
    ) {
        val session = activeTurnByTurnGuidanceSession
        val location = guidanceLocation
        val rawGuidanceLocation =
            rawCurrentLocation?.let { rawLocation ->
                LatLong(rawLocation.latitude, rawLocation.longitude)
            }
        if (session == null || location == null || session.startReached) {
            pendingStartDecision = null
            dismissedStartDecisionKey = null
            startHereStableSampleCount = 0
            return@LaunchedEffect
        }

        val points = session.trackPoints.map { it.latLong }
        val start = points.firstOrNull()
        val end = points.lastOrNull()
        val projection =
            projectLocationToRoute(
                points = points,
                cumulativeDistancesMeters = session.cumulativeDistancesMeters,
                location = location,
            )
        if (start == null || end == null || projection == null) {
            pendingStartDecision = null
            startHereStableSampleCount = 0
            return@LaunchedEffect
        }

        val distanceToStart = haversineMeters(location, start)
        val distanceToEnd = haversineMeters(location, end)
        val closeToRoute = projection.distanceToRouteMeters <= turnByTurnOffRouteThresholdMeters.toDouble()
        val midRouteCandidate =
            closeToRoute &&
                distanceToStart > turnByTurnGuidanceTuning.startReachedDistanceMeters &&
                projection.distanceFromStartMeters > START_HERE_MIN_PROGRESS_METERS &&
                session.totalDistanceMeters - projection.distanceFromStartMeters > START_HERE_MIN_REMAINING_METERS
        val locationAccurateEnough =
            rawCurrentLocation?.accuracy?.let { accuracy ->
                accuracy <= START_HERE_MAX_ACCURACY_METERS
            } ?: false
        val hasFreshGpsLocation = rawGuidanceLocation != null
        if (midRouteCandidate && locationAccurateEnough) {
            startHereStableSampleCount += 1
        } else {
            startHereStableSampleCount = 0
        }
        val stableMidRouteCandidate =
            midRouteCandidate &&
                hasFreshGpsLocation &&
                startHereStableSampleCount >= START_HERE_STABLE_SAMPLE_COUNT
        val reverseCandidate =
            !session.reversed &&
                turnByTurnReverseSuggestionMode == SettingsRepository.TURN_BY_TURN_REVERSE_SUGGESTION_ASK &&
                distanceToEnd + REVERSE_SUGGESTION_DISTANCE_MARGIN_METERS < distanceToStart &&
                distanceToEnd <= REVERSE_SUGGESTION_MAX_DISTANCE_METERS

        val nextDecision =
            when {
                reverseCandidate -> GuidanceStartDecision.REVERSE_ROUTE
                stableMidRouteCandidate &&
                    turnByTurnRouteStartBehavior == SettingsRepository.TURN_BY_TURN_ROUTE_START_NEAREST_POINT -> {
                    gpxViewModel.markTurnByTurnStartReached()
                    null
                }
                stableMidRouteCandidate &&
                    turnByTurnRouteStartBehavior == SettingsRepository.TURN_BY_TURN_ROUTE_START_ASK ->
                    GuidanceStartDecision.START_HERE
                else -> null
            }

        val nextKey = nextDecision?.let { "${session.trackId}:${session.reversed}:$it" }
        pendingStartDecision =
            if (nextKey != null && dismissedStartDecisionKey != nextKey) {
                nextDecision
            } else {
                null
            }
    }

    LaunchedEffect(activeTurnByTurnGuidanceSession, guidanceLocation, turnByTurnGuidanceTuning) {
        if (isGuidanceStartReached(activeTurnByTurnGuidanceSession, guidanceLocation, turnByTurnGuidanceTuning)) {
            gpxViewModel.markTurnByTurnStartReached()
        }
    }

    TurnByTurnGuidanceHapticEffect(
        context = context,
        state = turnByTurnGuidanceState,
        currentSpeedMps = rawCurrentLocation?.speed,
        hapticsEnabled = turnByTurnHapticsEnabled,
        turnAlertsMode = turnByTurnTurnAlertsMode,
        offRouteAlertsEnabled = turnByTurnOffRouteAlertsEnabled,
        offRouteRepeatSeconds = turnByTurnOffRouteRepeatSeconds,
    )

    LaunchedEffect(
        turnByTurnGuidanceState.active,
        turnByTurnGuidanceState.mode,
        turnByTurnGuidanceState.nextInstruction?.trackPointIndex,
        turnByTurnGuidanceState.distanceToInstructionMeters?.roundTelemetryMeters(),
        turnByTurnGuidanceState.distanceToStartMeters?.roundTelemetryMeters(),
        turnByTurnGuidanceState.distanceToRouteMeters?.roundTelemetryMeters(),
        turnByTurnGuidanceState.distanceRemainingMeters?.roundTelemetryMeters(),
        turnByTurnGuidanceState.routeProgressFraction?.roundTelemetryPercent(),
        turnByTurnGuidanceState.offRoute,
        turnByTurnGuidancePaused,
        turnByTurnGuidanceSession?.trackId,
        turnByTurnGuidanceSession?.reversed,
        turnByTurnGuidanceSession?.startReached,
        guideBackToRouteActive,
        showGuideBackPrompt,
        pendingStartDecision,
        turnByTurnRouteStartBehavior,
        turnByTurnReverseSuggestionMode,
        turnByTurnOffRouteThresholdMeters,
        turnByTurnHapticsEnabled,
        turnByTurnTurnAlertsMode,
        turnByTurnOffRouteAlertsEnabled,
        turnByTurnGpsInAmbient,
    ) {
        if (!turnByTurnGuidanceState.active && turnByTurnGuidanceSession == null) return@LaunchedEffect
        DebugTelemetry.log(
            "TurnByTurn",
            buildTurnByTurnTelemetryMessage(
                state = turnByTurnGuidanceState,
                paused = turnByTurnGuidancePaused,
                trackId = turnByTurnGuidanceSession?.trackId,
                reversed = turnByTurnGuidanceSession?.reversed,
                startReached = turnByTurnGuidanceSession?.startReached,
                guideBackToRouteActive = guideBackToRouteActive,
                showGuideBackPrompt = showGuideBackPrompt,
                pendingStartDecision = pendingStartDecision,
                routeStartBehavior = turnByTurnRouteStartBehavior,
                reverseSuggestionMode = turnByTurnReverseSuggestionMode,
                offRouteThresholdMeters = turnByTurnOffRouteThresholdMeters,
                hapticsEnabled = turnByTurnHapticsEnabled,
                turnAlertsMode = turnByTurnTurnAlertsMode,
                offRouteAlertsEnabled = turnByTurnOffRouteAlertsEnabled,
                guidanceGpsInAmbient = turnByTurnGpsInAmbient,
            ),
        )
    }

    LaunchedEffect(
        effectiveNavigationMarkerAnchorMode,
        effectiveNavMode,
        recenterTarget,
        mapView,
    ) {
        if (!offlineMode && effectiveNavMode != NavMode.PANNING) {
            recenterTarget?.let { mapView.setCenterForNavigationMarker(it, effectiveNavigationMarkerAnchorMode) }
        }
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

    NavigateNotificationPermissionDialog(
        visible = showNotificationPermissionDialog,
        onContinue = {
            showNotificationPermissionDialog = false
            notificationPermissionState.launchPermissionRequest()
        },
        onDismiss = {
            showNotificationPermissionDialog = false
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

    RouteToolProgressDialog(
        visible = createdPoiCreateInProgress,
        message = "Saving POI...",
        fullScreenBackground = true,
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
        onStartRouteToolGuidance = {
            val currentResult = routeToolResult ?: return@NavigateRouteToolDialogs
            if (routeToolRenameInProgress) return@NavigateRouteToolDialogs
            routeToolRenameError = null
            gpxViewModel.startTurnByTurnGuidance(currentResult.filePath) { result ->
                result
                    .onSuccess {
                        routeToolResult = null
                        routeToolRenameError = null
                    }.onFailure { error ->
                        routeToolRenameError =
                            error.localizedMessage?.takeIf { it.isNotBlank() }
                                ?: "Failed to start guidance."
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
        onNavigateTimeSuppressedChange = onNavigateTimeSuppressedChange,
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
            val nextLatitude = center.latitude.coerceIn(-85.0, 85.0)
            if (shouldUpdateZoomReferenceLatitude(zoomReferenceLatitude, nextLatitude)) {
                zoomReferenceLatitude = nextLatitude
            }
            if (mapView.width > 0 && mapView.width != zoomViewportWidthPx) {
                zoomViewportWidthPx = mapView.width
            }
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
        locationMarker = locationMarker,
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
        onMenuClick = {
            val nowElapsedMs = SystemClock.elapsedRealtime()
            if (nowElapsedMs < menuClickGuardUntilElapsedMs) {
                DebugTelemetry.log(
                    "NavigationTelemetry",
                    "event=menu_click_ignored route=navigate_screen reason=recent_resume " +
                        "ageMs=${nowElapsedMs - lastScreenResumeElapsedMs} " +
                        "remainingMs=${menuClickGuardUntilElapsedMs - nowElapsedMs}",
                )
            } else {
                onMenuClick()
            }
        },
        onPermissionLaunch = { locationPermissionState.launchPermissions() },
        mapRotationDeg = renderedMapRotationDeg,
        navigationMarkerAnchorMode = effectiveNavigationMarkerAnchorMode,
        compassHeadingDeg = renderedCompassHeadingDeg,
        liveElevationEnabled = liveElevationEnabled,
        liveDistanceEnabled = liveDistanceEnabled && !offlineMode,
        keepAppOpen = keepAppOpen,
        onKeepAppOpenToggle = screenActions.toggleKeepAppOpen,
        backButtonExitsNavigation = backButtonExitsNavigation,
        traceRecordingState = traceRecordingState,
        recordingDashboardMetricSlots = recordingDashboardMetricSlots,
        recordingDashboardExpandRequestToken = recordingDashboardExpandRequestToken,
        recordingActionPromptRequestToken = recordingActionPromptRequestToken,
        onStartRecording = {
            shortcutTrayExpanded = false
            traceRecordingViewModel.startRecording()
        },
        onPauseRecording = traceRecordingViewModel::pauseRecording,
        onResumeRecording = traceRecordingViewModel::resumeRecording,
        onFinishRecording = traceRecordingViewModel::finishAndSaveRecording,
        onDiscardRecording = traceRecordingViewModel::discardRecording,
        onRecordingMetricSelected = settingsViewModel::setRecordingDashboardMetricSlot,
        shortcutTrayExpanded = shortcutTrayExpanded,
        onShortcutTrayToggle = screenActions.toggleShortcutTray,
        onShortcutTrayDismiss = { shortcutTrayExpanded = false },
        onOpenGpxTools = routeToolActions.openRouteToolsPanel,
        onStartPoiCreation = routeToolActions.startPoiCreationSelection,
        gpsIndicatorState = effectiveGpsIndicatorState,
        gpsEnvironmentWarning = gpsEnvironmentWarning,
        watchGpsDegradedWarning = watchGpsDegradedWarning,
        isOfflineMode = offlineMode,
        isGpxInspectionEnabled = isGpxInspectionEnabled,
        selectingGpxPointB = selectingGpxPointB,
        onCancelSelectingGpxPointB = { gpxViewModel.cancelSelectingB() },
        turnByTurnGuidanceState = brouterGuideBackState,
        turnByTurnGuidancePaused = turnByTurnGuidancePaused,
        turnByTurnPausedTrackTitle = turnByTurnGuidanceSession?.trackTitle,
        guideBackToRouteActive = guideBackToRouteActive && turnByTurnGuidanceState.offRoute,
        showGuideBackPrompt = showGuideBackPrompt,
        startDecisionPrompt = startDecisionPrompt,
        onPauseTurnByTurnGuidance = { gpxViewModel.pauseTurnByTurnGuidance() },
        onResumeTurnByTurnGuidance = { gpxViewModel.resumeTurnByTurnGuidance() },
        onStopTurnByTurnGuidance = { gpxViewModel.stopTurnByTurnGuidance() },
        onGuideBackToRoute = {
            guideBackToRouteActive = true
            dismissedGuideBackPromptTrackId = guideBackTrackId
            brouterGuideBackRoute = emptyList()
            val origin = guidanceLocation
            val destination = guideBackTargetPoint
            if (turnByTurnBrouterGuideBackEnabled && origin != null && destination != null) {
                gpxViewModel.buildTurnByTurnGuideBackRoute(
                    origin = origin,
                    destination = destination,
                ) { result ->
                    result.onSuccess { route ->
                        if (guideBackToRouteActive) {
                            brouterGuideBackRoute = route
                        }
                    }
                }
            }
        },
        onDismissGuideBackPrompt = {
            dismissedGuideBackPromptTrackId = guideBackTrackId
        },
        onAcceptStartDecisionPrompt = {
            when (pendingStartDecision) {
                GuidanceStartDecision.REVERSE_ROUTE -> gpxViewModel.reverseTurnByTurnGuidance()
                GuidanceStartDecision.START_HERE -> gpxViewModel.markTurnByTurnStartReached()
                null -> Unit
            }
            dismissedStartDecisionKey = startDecisionKey
            pendingStartDecision = null
        },
        onDismissStartDecisionPrompt = {
            dismissedStartDecisionKey = startDecisionKey
            pendingStartDecision = null
        },
        activeGpxDetails = activeGpxDetails,
        gpxTrackColor = gpxTrackColor,
        routeToolSession = routeToolSession,
        crosshairSelectionActive = poiCreationSelectionActive,
        crosshairSelectionTitle = "+ POI",
        crosshairSelectionInstruction = "Move map, then check.",
        crosshairSelectionBusy = createdPoiCreateInProgress,
        crosshairSelectionBusyMessage = "Saving POI...",
        routeToolCreatePreview = displayedRouteToolCreatePreview,
        routeToolDraftConnectorPoints = routeToolDraftConnectorPoints,
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
        onCrosshairSelectionPickHere = routeToolActions.savePoiAt,
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

private fun shouldUpdateZoomReferenceLatitude(
    currentLatitude: Double,
    nextLatitude: Double,
): Boolean =
    !currentLatitude.isFinite() ||
        abs(currentLatitude - nextLatitude) >= MAP_ZOOM_LATITUDE_UPDATE_THRESHOLD_DEGREES

private fun buildTurnByTurnTelemetryMessage(
    state: TurnByTurnGuidanceState,
    trackId: String?,
    reversed: Boolean?,
    startReached: Boolean?,
    guideBackToRouteActive: Boolean,
    showGuideBackPrompt: Boolean,
    pendingStartDecision: GuidanceStartDecision?,
    paused: Boolean,
    routeStartBehavior: String,
    reverseSuggestionMode: String,
    offRouteThresholdMeters: Int,
    hapticsEnabled: Boolean,
    turnAlertsMode: String,
    offRouteAlertsEnabled: Boolean,
    guidanceGpsInAmbient: Boolean,
): String {
    val instruction = state.nextInstruction
    return buildString {
        append("active=${state.active}")
        append(" paused=$paused")
        append(" mode=${state.mode}")
        append(" track=${trackId.telemetryTrackName()}")
        append(" reversed=${reversed ?: "na"}")
        append(" startReached=${startReached ?: "na"}")
        append(" next=${instruction?.command ?: "na"}")
        append(" nextSource=${instruction?.source ?: "na"}")
        append(" nextIndex=${instruction?.trackPointIndex ?: "na"}")
        append(" distToInstructionM=${state.distanceToInstructionMeters.telemetryDistance()}")
        append(" distToStartM=${state.distanceToStartMeters.telemetryDistance()}")
        append(" distToRouteM=${state.distanceToRouteMeters.telemetryDistance()}")
        append(" remainingM=${state.distanceRemainingMeters.telemetryDistance()}")
        append(" progressPct=${state.routeProgressFraction.telemetryPercent()}")
        append(" offRoute=${state.offRoute}")
        append(" guideBackActive=$guideBackToRouteActive")
        append(" guideBackPrompt=$showGuideBackPrompt")
        append(" startDecision=${pendingStartDecision ?: "none"}")
        append(" routeStartBehavior=$routeStartBehavior")
        append(" reverseSuggestion=$reverseSuggestionMode")
        append(" offRouteThresholdM=$offRouteThresholdMeters")
        append(" haptics=$hapticsEnabled")
        append(" turnAlerts=$turnAlertsMode")
        append(" offRouteAlerts=$offRouteAlertsEnabled")
        append(" guidanceGpsAmbient=$guidanceGpsInAmbient")
    }
}

private fun nearestGuidanceRoutePoint(
    session: GpxGuidanceSession?,
    currentLocation: LatLong?,
): LatLong? {
    if (session == null || currentLocation == null) return null
    val points = session.trackPoints.map { it.latLong }
    val projection =
        projectLocationToRoute(
            points = points,
            cumulativeDistancesMeters = session.cumulativeDistancesMeters,
            location = currentLocation,
        ) ?: return null
    return projectedLatLongOnRoute(points = points, projectionSegmentIndex = projection.segmentIndex, t = projection.t)
}

private fun buildBrouterGuideBackState(
    baseState: TurnByTurnGuidanceState,
    active: Boolean,
    route: List<LatLong>,
    currentLocation: LatLong?,
): TurnByTurnGuidanceState {
    if (!active || currentLocation == null || route.size < 2) return baseState
    val cumulative = buildCumulativeDistances(route)
    val projection =
        projectLocationToRoute(
            points = route,
            cumulativeDistancesMeters = cumulative,
            location = currentLocation,
        ) ?: return baseState
    val targetDistance =
        (projection.distanceFromStartMeters + GUIDE_BACK_ROUTE_BEARING_LOOKAHEAD_METERS)
            .coerceAtMost(cumulative.lastOrNull() ?: projection.distanceFromStartMeters)
    val targetIndex = cumulative.indexOfFirst { it >= targetDistance }.takeIf { it >= 0 } ?: route.lastIndex
    val target =
        route.getOrNull(targetIndex)
            ?: projectedLatLongOnRoute(route, projection.segmentIndex, projection.t)
            ?: return baseState
    val remaining = ((cumulative.lastOrNull() ?: 0.0) - projection.distanceFromStartMeters).coerceAtLeast(0.0)
    return baseState.copy(
        distanceToRouteMeters = remaining,
        bearingToRouteDegrees = bearingDegrees(currentLocation, target).toFloat(),
    )
}

private fun projectedLatLongOnRoute(
    points: List<LatLong>,
    projectionSegmentIndex: Int,
    t: Double,
): LatLong? {
    val a = points.getOrNull(projectionSegmentIndex) ?: return null
    val b = points.getOrNull(projectionSegmentIndex + 1) ?: return a
    val clampedT = t.coerceIn(0.0, 1.0)
    return LatLong(
        a.latitude + (b.latitude - a.latitude) * clampedT,
        a.longitude + (b.longitude - a.longitude) * clampedT,
    )
}

private fun String?.telemetryTrackName(): String =
    this
        ?.substringAfterLast('/')
        ?.take(MAX_TELEMETRY_TRACK_NAME_CHARS)
        ?: "none"

private fun Double?.telemetryDistance(): String =
    this?.let { String.format(Locale.US, "%.1f", it) } ?: "na"

private fun Float?.telemetryPercent(): String =
    this?.let { String.format(Locale.US, "%.1f", it.coerceIn(0f, 1f) * 100f) } ?: "na"

private fun Double.roundTelemetryMeters(): Int? = if (isFinite()) toInt() else null

private fun Float.roundTelemetryPercent(): Int? = if (isFinite()) (coerceIn(0f, 1f) * 100f).toInt() else null

private enum class GuidanceStartDecision {
    START_HERE,
    REVERSE_ROUTE,
}

private const val MAX_TELEMETRY_TRACK_NAME_CHARS = 48
private const val START_HERE_MIN_PROGRESS_METERS = 50.0
private const val START_HERE_MIN_REMAINING_METERS = 50.0
private const val START_HERE_STABLE_SAMPLE_COUNT = 2
private const val START_HERE_MAX_ACCURACY_METERS = 60f
private const val REVERSE_SUGGESTION_DISTANCE_MARGIN_METERS = 50.0
private const val REVERSE_SUGGESTION_MAX_DISTANCE_METERS = 300.0
private const val GUIDE_BACK_ROUTE_BEARING_LOOKAHEAD_METERS = 20.0
private const val MAP_ZOOM_LATITUDE_UPDATE_THRESHOLD_DEGREES = 0.25
private const val NORMAL_STARTUP_MAP_FALLBACK_GRACE_MS = 15_000L
private const val NAVIGATE_MENU_CLICK_RESUME_GUARD_MS = 1_500L
