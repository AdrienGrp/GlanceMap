package com.glancemap.glancemapwearos.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.timeTextCurvedText
import com.glancemap.glancemapwearos.GlanceMapWearApp
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.presentation.design.theme.GlanceMapTheme
import com.glancemap.glancemapwearos.presentation.features.download.DownloadSettingsScreen
import com.glancemap.glancemapwearos.presentation.features.download.DownloadScreen
import com.glancemap.glancemapwearos.presentation.features.gpx.GpxScreen
import com.glancemap.glancemapwearos.presentation.features.home.MainScreen
import com.glancemap.glancemapwearos.presentation.features.maps.MapsScreen
import com.glancemap.glancemapwearos.presentation.features.navigate.NavigateScreen
import com.glancemap.glancemapwearos.presentation.features.navigate.navigateTimePattern
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
import com.glancemap.glancemapwearos.presentation.features.settings.ResetDefaultsConfirmScreen
import com.glancemap.glancemapwearos.presentation.features.settings.SettingsScreen
import com.glancemap.glancemapwearos.presentation.features.settings.ThemeSettingsScreen
import com.glancemap.glancemapwearos.presentation.navigation.WatchRoutes
import com.google.android.horologist.compose.layout.AppScaffold
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

class MainActivity : ComponentActivity() {
    private var _isAmbient by mutableStateOf(false)
    private var _ambientTickMs by mutableStateOf(0L)
    private var _isDeviceInteractive by mutableStateOf(true)
    private val screenStateReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                _isDeviceInteractive = getSystemService(PowerManager::class.java)?.isInteractive ?: true
            }
        }

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

            val isAmbient = _isAmbient
            val ambientTickMs = _ambientTickMs
            val isDeviceInteractive = _isDeviceInteractive

            GlanceMapTheme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val route = backStackEntry?.destination?.route
                val isNavigateScreen = route == null || route == WatchRoutes.NAVIGATE
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
                        if (showTimeInNavigate && isNavigateScreen && !isAmbient) {
                            val context = LocalContext.current
                            TimeText(
                                modifier = Modifier.padding(top = 2.dp),
                                timeSource =
                                    TimeTextDefaults.rememberTimeSource(
                                        navigateTimePattern(context, navigateTimeFormat),
                                    ),
                            ) { time ->
                                timeTextCurvedText(
                                    time = time,
                                    style = CurvedTextStyle(color = Color.White),
                                )
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
                            NavigateScreen(
                                mapViewModel = appContainer.mapViewModel,
                                gpxViewModel = appContainer.gpxViewModel,
                                poiViewModel = appContainer.poiViewModel,
                                compassViewModel = appContainer.compassViewModel,
                                settingsViewModel = appContainer.settingsViewModel,
                                locationViewModel = appContainer.locationViewModel,
                                isAmbient = isAmbient,
                                isDeviceInteractive = isDeviceInteractive,
                                ambientTickMs = ambientTickMs,
                                onMenuClick = {
                                    navController.navigate(WatchRoutes.MAIN_MENU) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            )
                        }

                        composable(WatchRoutes.MAIN_MENU) {
                            DismissableScreen(
                                onDismiss = { navController.popBackStack() },
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
                            var isDownloadAreaPickerOpen by remember { mutableStateOf(false) }
                            var downloadAreaFolder by remember { mutableStateOf<String?>(null) }
                            var downloadAreaSearchQuery by remember { mutableStateOf("") }
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
        val appContainer = (application as GlanceMapWearApp).container
        appContainer.mapViewModel.destroyMapHolder()
        appContainer.locationViewModel.setTrackingEnabled(false)
        super.onDestroy()
    }

    private fun logScreenTelemetry(event: String) {
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive
        val message =
            buildString {
                append("event=").append(event)
                append(" ambient=").append(_isAmbient)
                append(" interactive=").append(interactive?.toString() ?: "na")
            }
        DebugTelemetry.log("ScreenTelemetry", message)
    }
}
