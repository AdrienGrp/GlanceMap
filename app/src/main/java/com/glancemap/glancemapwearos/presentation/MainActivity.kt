package com.glancemap.glancemapwearos.presentation

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.timeTextCurvedText
import com.glancemap.glancemapwearos.GlanceMapWearApp
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.core.service.diagnostics.FieldMarkerDiagnostics
import com.glancemap.glancemapwearos.presentation.design.theme.GlanceMapTheme
import com.glancemap.glancemapwearos.presentation.features.download.DownloadScreen
import com.glancemap.glancemapwearos.presentation.features.download.DownloadSettingsScreen
import com.glancemap.glancemapwearos.presentation.features.gpx.GpxScreen
import com.glancemap.glancemapwearos.presentation.features.home.MainScreen
import com.glancemap.glancemapwearos.presentation.features.maps.MapsScreen
import com.glancemap.glancemapwearos.presentation.features.navigate.NavigateScreen
import com.glancemap.glancemapwearos.presentation.features.navigate.formatNavigateClockTime
import com.glancemap.glancemapwearos.presentation.features.navigate.navigateTimePattern
import com.glancemap.glancemapwearos.core.service.location.model.resolveLocationScreenState
import com.glancemap.glancemapwearos.presentation.features.poi.PoiScreen
import com.glancemap.glancemapwearos.presentation.features.settings.CompassSettingsScreen
import com.glancemap.glancemapwearos.presentation.features.settings.DebuggingSettingsScreen
import com.glancemap.glancemapwearos.presentation.features.settings.GpsSettingsScreen
import com.glancemap.glancemapwearos.presentation.features.settings.GpxSettingsScreen
import com.glancemap.glancemapwearos.presentation.features.settings.LicensesScreen
import com.glancemap.glancemapwearos.presentation.features.settings.MapDisplaySettingsScreen
import com.glancemap.glancemapwearos.presentation.features.settings.MapSettingsScreen
import com.glancemap.glancemapwearos.presentation.features.settings.MapZoomSettingsScreen
import com.glancemap.glancemapwearos.presentation.features.settings.PoiSettingsScreen
import com.glancemap.glancemapwearos.presentation.features.settings.RecordingSettingsScreen
import com.glancemap.glancemapwearos.presentation.features.settings.ResetDefaultsConfirmScreen
import com.glancemap.glancemapwearos.presentation.features.settings.SettingsScreen
import com.glancemap.glancemapwearos.presentation.features.settings.ThemeSettingsScreen
import com.glancemap.glancemapwearos.presentation.features.settings.TurnByTurnSettingsScreen
import com.glancemap.glancemapwearos.presentation.navigation.WatchRoutes
import com.glancemap.glancemapwearos.presentation.ui.cappedFontScale
import com.google.android.horologist.compose.layout.AppScaffold
import kotlinx.coroutines.delay
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

class MainActivity : ComponentActivity() {
    private var _isAmbient by mutableStateOf(false)
    private var _ambientTickMs by mutableStateOf(0L)
    private var _isDeviceInteractive by mutableStateOf(true)

    @Volatile
    private var activeRoute: String? = null
    private var thermalStatusListener: PowerManager.OnThermalStatusChangedListener? = null

    private val screenStateReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                _isDeviceInteractive = getSystemService(PowerManager::class.java)?.isInteractive ?: true
            }
        }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidGraphicFactory.createInstance(this.application)
        _isDeviceInteractive = getSystemService(PowerManager::class.java)?.isInteractive ?: true

        val screenStateFilter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, screenStateFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenStateReceiver, screenStateFilter)
        }
        registerThermalTelemetry()

        val ambientObserver =
            AmbientLifecycleObserver(
                this,
                object : AmbientLifecycleObserver.AmbientLifecycleCallback {
                    override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                        _isAmbient = true
                        _ambientTickMs = System.currentTimeMillis()
                        _isDeviceInteractive = getSystemService(PowerManager::class.java)?.isInteractive ?: false
                        logScreenTelemetry(event = "ambient_enter")
                    }

                    override fun onExitAmbient() {
                        _isAmbient = false
                        _ambientTickMs = System.currentTimeMillis()
                        _isDeviceInteractive = getSystemService(PowerManager::class.java)?.isInteractive ?: true
                        logScreenTelemetry(event = "ambient_exit")
                    }

                    override fun onUpdateAmbient() {
                        _ambientTickMs = System.currentTimeMillis()
                    }
                },
            )
        lifecycle.addObserver(ambientObserver)

        setContent {
            val appContainer = (application as GlanceMapWearApp).container
            val showTimeInNavigate by appContainer.settingsViewModel.showTimeInNavigate
                .collectAsState(initial = true)
            val navigateTimeFormat by appContainer.settingsViewModel.navigateTimeFormat.collectAsState()
            val isMetric by appContainer.settingsViewModel.isMetric.collectAsState()
            val traceRecordingState by appContainer.traceRecordingViewModel.uiState.collectAsState()

            val isAmbient = _isAmbient
            val ambientTickMs = _ambientTickMs
            val isDeviceInteractive = _isDeviceInteractive
            val activityLocationScreenState =
                remember(isAmbient, isDeviceInteractive) {
                    resolveLocationScreenState(
                        isAmbient = isAmbient,
                        isDeviceInteractive = isDeviceInteractive,
                    )
                }

            GlanceMapTheme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val route = backStackEntry?.destination?.route
                val routeLabel = route ?: WatchRoutes.NAVIGATE
                val compositionContext = LocalContext.current
                val locationPermissionGranted =
                    ContextCompat.checkSelfPermission(
                        compositionContext,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                            compositionContext,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED
                var suppressNavigateTime by remember { mutableStateOf(false) }
                var recordingDashboardExpandRequestToken by remember { mutableLongStateOf(0L) }
                var recordingActionPromptRequestToken by remember { mutableLongStateOf(0L) }
                LaunchedEffect(routeLabel) {
                    activeRoute = routeLabel
                    logNavigationTelemetry(event = "route_visible", route = routeLabel)
                }
                val isNavigateScreen = routeLabel == WatchRoutes.NAVIGATE
                LaunchedEffect(isNavigateScreen) {
                    if (!isNavigateScreen) {
                        suppressNavigateTime = false
                    }
                }
                LaunchedEffect(
                    isNavigateScreen,
                    traceRecordingState.active,
                    activityLocationScreenState,
                    locationPermissionGranted,
                ) {
                    if (isNavigateScreen) return@LaunchedEffect
                    val recordingTrackingActive = traceRecordingState.active && locationPermissionGranted
                    appContainer.locationViewModel.syncRuntimeState(
                        screenState = activityLocationScreenState,
                        trackingEnabled = recordingTrackingActive,
                        backgroundGpsEnabled = recordingTrackingActive,
                    )
                    DebugTelemetry.log(
                        "TraceRecording",
                        "event=activity_runtime_sync active=${traceRecordingState.active} " +
                            "tracking=$recordingTrackingActive route=$routeLabel",
                    )
                }
                val navigateViaSwipeLeft: () -> Unit = {
                    val popped = navController.popBackStack(WatchRoutes.NAVIGATE, inclusive = false)
                    if (!popped) {
                        navController.navigate(WatchRoutes.NAVIGATE) {
                            popUpTo(WatchRoutes.NAVIGATE) {
                                inclusive = false
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                AppScaffold(
                    timeText = {
                        val recordingChipActive = traceRecordingState.active || traceRecordingState.saving
                        val canShowNavigateTime = showTimeInNavigate && isNavigateScreen && !isAmbient
                        val canShowRecordingChip = recordingChipActive && isNavigateScreen && !isAmbient
                        val shouldShowStatusChip =
                            (canShowNavigateTime || canShowRecordingChip) && !suppressNavigateTime
                        if (shouldShowStatusChip) {
                            cappedFontScale(maxFontScale = 1f) {
                                val context = LocalContext.current
                                val recordingStatusColor =
                                    when {
                                        traceRecordingState.saving -> Color(0xFFFFB74D)
                                        traceRecordingState.paused -> Color(0xFFFFB74D)
                                        recordingChipActive -> Color(0xFFFF1744)
                                        else -> Color.White
                                    }
                                val statusChipModifier =
                                    Modifier
                                        .padding(top = 2.dp)
                                        .then(
                                            if (recordingChipActive) {
                                                Modifier.pointerInput(traceRecordingState.active, traceRecordingState.saving) {
                                                    detectTapGestures(
                                                        onTap = {
                                                            recordingDashboardExpandRequestToken =
                                                                System.currentTimeMillis()
                                                        },
                                                        onLongPress = {
                                                            recordingActionPromptRequestToken =
                                                                System.currentTimeMillis()
                                                        },
                                                    )
                                                }
                                            } else {
                                                Modifier
                                            },
                                        )
                                if (recordingChipActive) {
                                    RecordingTimeChip(
                                        showTime = canShowNavigateTime,
                                        timeFormat = navigateTimeFormat,
                                        accentColor = recordingStatusColor,
                                        modifier = statusChipModifier,
                                    )
                                } else {
                                    TimeText(
                                        modifier = statusChipModifier,
                                        timeSource =
                                            TimeTextDefaults.rememberTimeSource(
                                                navigateTimePattern(context, navigateTimeFormat),
                                            ),
                                    ) { time ->
                                        timeTextCurvedText(
                                            time = time,
                                            style =
                                                CurvedTextStyle(
                                                    color = Color.White,
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    },
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = WatchRoutes.NAVIGATE,
                        modifier = Modifier.background(Color.Black),
                    ) {
                        composable(WatchRoutes.NAVIGATE) {
                            // ✅ NO swipe container here -> swipe-to-dismiss cannot happen
                            DisposableEffect(Unit) {
                                logNavigationTelemetry(
                                    event = "navigate_compose_enter",
                                    route = WatchRoutes.NAVIGATE,
                                )
                                onDispose {
                                    logNavigationTelemetry(
                                        event = "navigate_compose_dispose",
                                        route = activeRoute ?: WatchRoutes.NAVIGATE,
                                    )
                                }
                            }
                            NavigateScreen(
                                mapViewModel = appContainer.mapViewModel,
                                gpxViewModel = appContainer.gpxViewModel,
                                poiViewModel = appContainer.poiViewModel,
                                compassViewModel = appContainer.compassViewModel,
                                settingsViewModel = appContainer.settingsViewModel,
                                locationViewModel = appContainer.locationViewModel,
                                traceRecordingViewModel = appContainer.traceRecordingViewModel,
                                isAmbient = isAmbient,
                                isDeviceInteractive = isDeviceInteractive,
                                ambientTickMs = ambientTickMs,
                                onNavigateTimeSuppressedChange = { suppressNavigateTime = it },
                                recordingDashboardExpandRequestToken = recordingDashboardExpandRequestToken,
                                recordingActionPromptRequestToken = recordingActionPromptRequestToken,
                                onMenuClick = {
                                    logNavigationTelemetry(
                                        event = "menu_click",
                                        route = activeRoute ?: WatchRoutes.NAVIGATE,
                                    )
                                    navController.navigate(WatchRoutes.MAIN_MENU) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            )
                        }

                        composable(WatchRoutes.MAIN_MENU) {
                            BackHandler(enabled = true) {
                                finishAndRemoveTask()
                            }
                            DismissableScreen(
                                onDismiss = { finishAndRemoveTask() },
                                onSwipeLeftNavigate = navigateViaSwipeLeft,
                                rightEdgeGestureWidthOverride = 8.dp,
                            ) {
                                MainScreen(
                                    navController = navController,
                                    settingsViewModel = appContainer.settingsViewModel,
                                )
                            }
                        }

                        composable(WatchRoutes.GPX) {
                            DismissableScreen(
                                onDismiss = { navController.popBackStack() },
                                onSwipeLeftNavigate = navigateViaSwipeLeft,
                            ) {
                                GpxScreen(navController, appContainer.gpxViewModel, isMetric)
                            }
                        }

                        composable(WatchRoutes.POI) {
                            DismissableScreen(
                                onDismiss = { navController.popBackStack() },
                                onSwipeLeftNavigate = navigateViaSwipeLeft,
                            ) {
                                PoiScreen(
                                    navController = navController,
                                    poiViewModel = appContainer.poiViewModel,
                                    mapViewModel = appContainer.mapViewModel,
                                )
                            }
                        }

                        composable(WatchRoutes.MAPS) {
                            DismissableScreen(
                                onDismiss = { navController.popBackStack() },
                                onSwipeLeftNavigate = navigateViaSwipeLeft,
                            ) {
                                MapsScreen(
                                    navController = navController,
                                    mapViewModel = appContainer.mapViewModel,
                                    themeViewModel = appContainer.themeViewModel,
                                )
                            }
                        }

                        composable(WatchRoutes.DOWNLOAD) {
                            var isDownloadAreaPickerOpen by rememberSaveable { mutableStateOf(false) }
                            var downloadAreaFolder by rememberSaveable { mutableStateOf<String?>(null) }
                            var downloadAreaSearchQuery by rememberSaveable { mutableStateOf("") }
                            DismissableScreen(
                                onDismiss = {
                                    if (isDownloadAreaPickerOpen) {
                                        when {
                                            downloadAreaSearchQuery.isNotBlank() -> downloadAreaSearchQuery = ""
                                            downloadAreaFolder != null -> downloadAreaFolder = null
                                            else -> isDownloadAreaPickerOpen = false
                                        }
                                    } else {
                                        navController.popBackStack()
                                    }
                                },
                                onSwipeLeftNavigate = navigateViaSwipeLeft,
                            ) {
                                DownloadScreen(
                                    viewModel = appContainer.downloadViewModel,
                                    areaPickerOpen = isDownloadAreaPickerOpen,
                                    onAreaPickerOpenChange = { isDownloadAreaPickerOpen = it },
                                    selectedAreaFolder = downloadAreaFolder,
                                    onSelectedAreaFolderChange = { downloadAreaFolder = it },
                                    areaSearchQuery = downloadAreaSearchQuery,
                                    onAreaSearchQueryChange = { downloadAreaSearchQuery = it },
                                    onLibraryChanged = {
                                        appContainer.mapViewModel.loadMapFiles()
                                        appContainer.mapViewModel.loadRoutingPackFiles()
                                        appContainer.poiViewModel.loadPoiFiles()
                                    },
                                    onOpenSettings = {
                                        navController.navigate(WatchRoutes.DOWNLOAD_SETTINGS)
                                    },
                                )
                            }
                        }

                        composable(WatchRoutes.DOWNLOAD_SETTINGS) {
                            DismissableScreen(
                                onDismiss = { navController.popBackStack() },
                                onSwipeLeftNavigate = navigateViaSwipeLeft,
                            ) {
                                DownloadSettingsScreen(
                                    viewModel = appContainer.downloadViewModel,
                                    onOpenGeneralSettings = {
                                        navController.navigate(WatchRoutes.SETTINGS) {
                                            popUpTo(WatchRoutes.SETTINGS) { inclusive = false }
                                        }
                                    },
                                )
                            }
                        }

                        composable(WatchRoutes.SETTINGS) {
                            DismissableScreen(
                                onDismiss = { navController.popBackStack() },
                                onSwipeLeftNavigate = navigateViaSwipeLeft,
                            ) {
                                SettingsScreen(
                                    navController,
                                    appContainer.settingsViewModel,
                                    appContainer.mapViewModel,
                                    appContainer.gpxViewModel,
                                )
                            }
                        }

                        composable(WatchRoutes.COMPASS_SETTINGS) {
                            DismissableScreen(
                                onDismiss = { navController.popBackStack() },
                                onSwipeLeftNavigate = navigateViaSwipeLeft,
                            ) {
                                CompassSettingsScreen(
                                    viewModel = appContainer.settingsViewModel,
                                    compassViewModel = appContainer.compassViewModel,
                                    onOpenGeneralSettings = {
                                        navController.navigate(WatchRoutes.SETTINGS) {
                                            popUpTo(WatchRoutes.SETTINGS) { inclusive = false }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                )
                            }
                        }

                        composable(WatchRoutes.RESET_DEFAULTS_CONFIRM) {
                            DismissableScreen(
                                onDismiss = { navController.popBackStack() },
                                onSwipeLeftNavigate = navigateViaSwipeLeft,
                            ) {
                                ResetDefaultsConfirmScreen(
                                    onCancel = { navController.popBackStack() },
                                    onConfirmReset = {
                                        appContainer.settingsViewModel.resetToDefaults()
                                        appContainer.themeViewModel.resetToDefaults()
                                        navController.navigate(WatchRoutes.SETTINGS) {
                                            popUpTo(WatchRoutes.SETTINGS) { inclusive = false }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                )
                            }
                        }

                        composable(WatchRoutes.GPS_SETTINGS) {
                            DismissableScreen(
                                onDismiss = { navController.popBackStack() },
                                onSwipeLeftNavigate = navigateViaSwipeLeft,
                            ) {
                                GpsSettingsScreen(
                                    viewModel = appContainer.settingsViewModel,
                                    onOpenGeneralSettings = {
                                        navController.navigate(WatchRoutes.SETTINGS) {
                                            popUpTo(WatchRoutes.SETTINGS) { inclusive = false }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                )
                            }
                        }

                        composable(WatchRoutes.RECORDING_SETTINGS) {
                            DismissableScreen(
                                onDismiss = { navController.popBackStack() },
                                onSwipeLeftNavigate = navigateViaSwipeLeft,
                            ) {
                                RecordingSettingsScreen(
                                    viewModel = appContainer.settingsViewModel,
                                    onOpenGeneralSettings = {
                                        navController.navigate(WatchRoutes.SETTINGS) {
                                            popUpTo(WatchRoutes.SETTINGS) { inclusive = false }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                )
                            }
                        }

                        composable(WatchRoutes.TURN_BY_TURN_SETTINGS) {
                            DismissableScreen(
                                onDismiss = { navController.popBackStack() },
                                onSwipeLeftNavigate = navigateViaSwipeLeft,
                            ) {
                                TurnByTurnSettingsScreen(
                                    viewModel = appContainer.settingsViewModel,
                                    onOpenGeneralSettings = {
                                        navController.navigate(WatchRoutes.SETTINGS) {
                                            popUpTo(WatchRoutes.SETTINGS) { inclusive = false }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                )
                            }
                        }

                        composable(WatchRoutes.DEBUG_SETTINGS) {
                            DismissableScreen(
                                onDismiss = { navController.popBackStack() },
                                onSwipeLeftNavigate = navigateViaSwipeLeft,
                            ) {
                                DebuggingSettingsScreen(
                                    viewModel = appContainer.settingsViewModel,
                                    compassViewModel = appContainer.compassViewModel,
                                    onOpenGeneralSettings = {
                                        navController.navigate(WatchRoutes.SETTINGS) {
                                            popUpTo(WatchRoutes.SETTINGS) { inclusive = false }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                )
                            }
                        }

                        composable(WatchRoutes.GPX_SETTINGS) {
                            DismissableScreen(
                                onDismiss = { navController.popBackStack() },
                                onSwipeLeftNavigate = navigateViaSwipeLeft,
                            ) {
                                GpxSettingsScreen(
                                    viewModel = appContainer.settingsViewModel,
                                    onOpenGeneralSettings = {
                                        navController.navigate(WatchRoutes.SETTINGS) {
                                            popUpTo(WatchRoutes.SETTINGS) { inclusive = false }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onOpenTurnByTurnSettings = {
                                        navController.navigate(WatchRoutes.TURN_BY_TURN_SETTINGS)
                                    },
                                )
                            }
                        }

                        composable(WatchRoutes.POI_SETTINGS) {
                            DismissableScreen(
                                onDismiss = { navController.popBackStack() },
                                onSwipeLeftNavigate = navigateViaSwipeLeft,
                            ) {
                                PoiSettingsScreen(
                                    viewModel = appContainer.settingsViewModel,
                                    onOpenGeneralSettings = {
                                        navController.navigate(WatchRoutes.SETTINGS) {
                                            popUpTo(WatchRoutes.SETTINGS) { inclusive = false }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                )
                            }
                        }

                        composable(WatchRoutes.MAP_SETTINGS) {
                            DismissableScreen(
                                onDismiss = { navController.popBackStack() },
                                onSwipeLeftNavigate = navigateViaSwipeLeft,
                            ) {
                                MapSettingsScreen(
                                    navController = navController,
                                    viewModel = appContainer.settingsViewModel,
                                    themeViewModel = appContainer.themeViewModel,
                                    onOpenGeneralSettings = {
                                        navController.navigate(WatchRoutes.SETTINGS) {
                                            popUpTo(WatchRoutes.SETTINGS) { inclusive = false }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                )
                            }
                        }

                        composable(WatchRoutes.MAP_ZOOM_SETTINGS) {
                            DismissableScreen(
                                onDismiss = { navController.popBackStack() },
                                onSwipeLeftNavigate = navigateViaSwipeLeft,
                            ) {
                                MapZoomSettingsScreen(
                                    viewModel = appContainer.settingsViewModel,
                                    onOpenGeneralSettings = {
                                        navController.navigate(WatchRoutes.SETTINGS) {
                                            popUpTo(WatchRoutes.SETTINGS) { inclusive = false }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                )
                            }
                        }

                        composable(WatchRoutes.MAP_DISPLAY_SETTINGS) {
                            DismissableScreen(
                                onDismiss = { navController.popBackStack() },
                                onSwipeLeftNavigate = navigateViaSwipeLeft,
                            ) {
                                MapDisplaySettingsScreen(
                                    viewModel = appContainer.settingsViewModel,
                                    onOpenGeneralSettings = {
                                        navController.navigate(WatchRoutes.SETTINGS) {
                                            popUpTo(WatchRoutes.SETTINGS) { inclusive = false }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                )
                            }
                        }

                        composable(WatchRoutes.THEME_SETTINGS) {
                            DismissableScreen(
                                onDismiss = { navController.popBackStack() },
                                onSwipeLeftNavigate = navigateViaSwipeLeft,
                            ) {
                                ThemeSettingsScreen(
                                    themeViewModel = appContainer.themeViewModel,
                                    mapViewModel = appContainer.mapViewModel,
                                    onOpenMaps = {
                                        navController.navigate(WatchRoutes.MAPS) {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onOpenGeneralSettings = {
                                        navController.navigate(WatchRoutes.SETTINGS) {
                                            popUpTo(WatchRoutes.SETTINGS) { inclusive = false }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                )
                            }
                        }

                        composable(WatchRoutes.LICENSES) {
                            DismissableScreen(
                                onDismiss = { navController.popBackStack() },
                                onSwipeLeftNavigate = navigateViaSwipeLeft,
                            ) {
                                LicensesScreen(
                                    onOpenGeneralSettings = {
                                        navController.navigate(WatchRoutes.SETTINGS) {
                                            popUpTo(WatchRoutes.SETTINGS) { inclusive = false }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        _isDeviceInteractive = getSystemService(PowerManager::class.java)?.isInteractive ?: true
        logScreenTelemetry(event = "activity_resume")
    }

    override fun onPause() {
        _isDeviceInteractive = getSystemService(PowerManager::class.java)?.isInteractive ?: false
        logScreenTelemetry(event = "activity_pause")
        super.onPause()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenStateReceiver) }
        unregisterThermalTelemetry()
        val appContainer = (application as GlanceMapWearApp).container
        appContainer.mapViewModel.destroyMapHolder()
        if (appContainer.traceRecordingViewModel.uiState.value.active) {
            DebugTelemetry.log("TraceRecording", "event=activity_destroy_retaining_gps active=true")
        } else {
            appContainer.locationViewModel.setTrackingEnabled(false)
        }
        super.onDestroy()
    }

    private fun registerThermalTelemetry() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        val listener =
            PowerManager.OnThermalStatusChangedListener { status ->
                logThermalTelemetry(event = "status", status = status)
            }
        thermalStatusListener = listener
        powerManager.addThermalStatusListener(mainExecutor, listener)
        logThermalTelemetry(event = "initial", status = powerManager.currentThermalStatus)
    }

    private fun unregisterThermalTelemetry() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val listener = thermalStatusListener ?: return
        getSystemService(PowerManager::class.java)?.removeThermalStatusListener(listener)
        thermalStatusListener = null
    }

    private fun logThermalTelemetry(
        event: String,
        status: Int,
    ) {
        DebugTelemetry.log(
            "ThermalTelemetry",
            "event=$event status=$status label=${thermalStatusLabel(status)} " +
                "route=${activeRoute ?: "unknown"} ambient=$_isAmbient interactive=$_isDeviceInteractive",
        )
    }

    private fun thermalStatusLabel(status: Int): String =
        when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "none"
            PowerManager.THERMAL_STATUS_LIGHT -> "light"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
            else -> "unknown"
        }

    private fun logScreenTelemetry(event: String) {
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive
        val message =
            buildString {
                append("event=").append(event)
                append(" route=").append(activeRoute ?: "unknown")
                append(" ambient=").append(_isAmbient)
                append(" interactive=").append(interactive?.toString() ?: "na")
            }
        DebugTelemetry.log("ScreenTelemetry", message)
        FieldMarkerDiagnostics.recordMarker(type = event, note = activeRoute ?: "unknown")
    }

    private fun logNavigationTelemetry(
        event: String,
        route: String?,
    ) {
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive
        val message =
            buildString {
                append("event=").append(event)
                append(" route=").append(route ?: "unknown")
                append(" ambient=").append(_isAmbient)
                append(" interactive=").append(interactive?.toString() ?: "na")
            }
        DebugTelemetry.log("NavigationTelemetry", message)
        FieldMarkerDiagnostics.recordMarker(type = event, note = route ?: "unknown")
    }
}

@Composable
private fun RecordingTimeChip(
    showTime: Boolean,
    timeFormat: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val label =
        if (showTime) {
            formatNavigateClockTime(context, nowMillis, timeFormat)
        } else {
            "REC"
        }
    Box(
        modifier =
            modifier
                .height(28.dp)
                .background(Color.Black.copy(alpha = 0.74f), RoundedCornerShape(percent = 50))
                .border(1.dp, accentColor.copy(alpha = 0.96f), RoundedCornerShape(percent = 50))
                .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(5.dp)
                        .background(accentColor, CircleShape),
            )
            Text(
                text = label,
                modifier = Modifier.padding(start = 5.dp),
                color = Color.White,
                fontSize = 17.sp,
                lineHeight = 17.sp,
                maxLines = 1,
            )
        }
    }
}
