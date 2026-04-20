package com.glancemap.glancemapwearos.presentation.features.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.material.ToggleChip
import com.google.android.horologist.compose.material.ToggleChipToggleControl

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun GpsSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenGeneralSettings: () -> Unit,
) {
    val listTokens =
        rememberSettingsListTokens(
            compactTop = 12.dp,
            standardTop = 14.dp,
            expandedTop = 16.dp,
            compactBottom = 12.dp,
            standardBottom = 14.dp,
            expandedBottom = 16.dp,
        )
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val gpsInAmbientMode by viewModel.gpsInAmbientMode.collectAsState()
    val isWatchGpsOnly by viewModel.watchGpsOnly.collectAsState()
    val gpsDebugTelemetry by viewModel.gpsDebugTelemetry.collectAsState()
    val gpsPassiveLocationExperiment by viewModel.gpsPassiveLocationExperiment.collectAsState()

    // Foreground location permission (fine or coarse)
    var hasLocationPermission by remember { mutableStateOf(hasForegroundLocationPermission(context)) }
    var hasFineLocationPermission by remember { mutableStateOf(hasFineForegroundLocationPermission(context)) }

    // Refresh permission state when coming back from settings, etc.
    DisposableEffect(lifecycleOwner) {
        val obs =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasLocationPermission = hasForegroundLocationPermission(context)
                    hasFineLocationPermission = hasFineForegroundLocationPermission(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
            onResult = { permissions ->
                val fineGranted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)
                val coarseGranted = permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
                hasLocationPermission = fineGranted || coarseGranted
                hasFineLocationPermission = fineGranted

                // If user was trying to enable ambient GPS, complete the action.
                if (hasLocationPermission) {
                    viewModel.setGpsInAmbientMode(true)
                }
            },
        )

    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding =
                PaddingValues(
                    start = listTokens.horizontalPadding,
                    end = listTokens.horizontalPadding,
                    top = listTokens.topPadding,
                    bottom = listTokens.bottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(listTokens.itemSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                GeneralSettingsShortcutChip(onClick = onOpenGeneralSettings)
            }

            item {
                ToggleChip(
                    checked = isWatchGpsOnly,
                    onCheckedChanged = { viewModel.setWatchGpsOnly(it) },
                    label = "GPS Source",
                    secondaryLabel =
                        when {
                            isWatchGpsOnly && !hasFineLocationPermission ->
                                "Watch Only (enable Precise)"
                            isWatchGpsOnly -> "Watch Only (more ⚡)"
                            else -> "Auto (Watch + Phone)"
                        },
                    toggleControl = ToggleChipToggleControl.Switch,
                )
            }

            item {
                GpsIntervalSummary(
                    primaryText = "GPS interval is fixed to 3s while the screen is on.",
                    secondaryText = "This keeps walking motion smooth without pushing battery too hard.",
                )
            }

            if (gpsDebugTelemetry) {
                item {
                    ToggleChip(
                        checked = gpsPassiveLocationExperiment,
                        onCheckedChanged = { viewModel.setGpsPassiveLocationExperiment(it) },
                        label = "Use GPS from other apps",
                        secondaryLabel =
                            if (gpsPassiveLocationExperiment) {
                                "On during capture"
                            } else {
                                "Off during capture"
                            },
                        toggleControl = ToggleChipToggleControl.Switch,
                    )
                }
            }

            item {
                ToggleChip(
                    checked = gpsInAmbientMode,
                    onCheckedChanged = { enable ->
                        if (enable) {
                            // No background permission. Just ensure foreground location permission exists.
                            if (hasLocationPermission) {
                                viewModel.setGpsInAmbientMode(true)
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            }
                        } else {
                            viewModel.setGpsInAmbientMode(false)
                        }
                    },
                    label = "GPS AOD/Screen Off",
                    secondaryLabel =
                        when {
                            gpsInAmbientMode && hasLocationPermission -> "On (more ⚡)"
                            gpsInAmbientMode && !hasLocationPermission -> "Needs location permission"
                            else -> "Off (saves battery)"
                        },
                    toggleControl = ToggleChipToggleControl.Switch,
                )
            }

            item {
                GpsIntervalSummary(
                    primaryText = "AOD/Screen Off uses a fixed 60s interval when enabled.",
                    secondaryText =
                        if (gpsInAmbientMode) {
                            "Ambient updates stay available, but at a battery-friendly pace."
                        } else {
                            "Turn on AOD/Screen Off above if you want background location updates."
                        },
                )
            }
        }
    }
}

private fun hasForegroundLocationPermission(context: Context): Boolean {
    val fine =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    val coarse =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun hasFineForegroundLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@Suppress("FunctionName")
@Composable
private fun GpsIntervalSummary(
    primaryText: String,
    secondaryText: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = primaryText,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
        Text(
            text = secondaryText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}
