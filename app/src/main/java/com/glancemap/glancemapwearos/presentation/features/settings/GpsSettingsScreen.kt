package com.glancemap.glancemapwearos.presentation.features.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.Text
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.material.ToggleChip
import com.google.android.horologist.compose.material.ToggleChipToggleControl
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun GpsSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenGeneralSettings: () -> Unit
) {
    val listTokens = rememberSettingsListTokens(
        compactTop = 12.dp,
        standardTop = 14.dp,
        expandedTop = 16.dp,
        compactBottom = 12.dp,
        standardBottom = 14.dp,
        expandedBottom = 16.dp
    )
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val gpsInAmbientMode by viewModel.gpsInAmbientMode.collectAsState()
    val isWatchGpsOnly by viewModel.watchGpsOnly.collectAsState()
    val gpsIntervalMs by viewModel.gpsInterval.collectAsState()
    val ambientGpsIntervalMs by viewModel.ambientGpsInterval.collectAsState()

    // Foreground location permission (fine or coarse)
    var hasLocationPermission by remember { mutableStateOf(hasForegroundLocationPermission(context)) }
    var hasFineLocationPermission by remember { mutableStateOf(hasFineForegroundLocationPermission(context)) }

    // Refresh permission state when coming back from settings, etc.
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = hasForegroundLocationPermission(context)
                hasFineLocationPermission = hasFineForegroundLocationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
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
        }
    )

    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = listTokens.horizontalPadding,
                end = listTokens.horizontalPadding,
                top = listTokens.topPadding,
                bottom = listTokens.bottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(listTokens.itemSpacing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                GeneralSettingsShortcutChip(onClick = onOpenGeneralSettings)
            }

            item {
                ToggleChip(
                    checked = isWatchGpsOnly,
                    onCheckedChanged = { viewModel.setWatchGpsOnly(it) },
                    label = "GPS Source",
                    secondaryLabel = when {
                        isWatchGpsOnly && !hasFineLocationPermission ->
                            "Watch Only (enable Precise)"
                        isWatchGpsOnly -> "Watch Only (more ⚡)"
                        else -> "Auto (Watch + Phone)"
                    },
                    toggleControl = ToggleChipToggleControl.Switch
                )
            }

            item {
                GpsIntervalSlider(
                    label = "GPS Interval",
                    intervalMs = gpsIntervalMs,
                    minSeconds = 1,
                    maxSeconds = 120,
                    onValueChangeMs = { viewModel.setGpsInterval(it) }
                )
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
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        } else {
                            viewModel.setGpsInAmbientMode(false)
                        }
                    },
                    label = "GPS AOD/Screen Off",
                    secondaryLabel = when {
                        gpsInAmbientMode && hasLocationPermission -> "On (more ⚡)"
                        gpsInAmbientMode && !hasLocationPermission -> "Needs location permission"
                        else -> "Off (saves battery)"
                    },
                    toggleControl = ToggleChipToggleControl.Switch
                )
            }

            if (gpsInAmbientMode) {
                item {
                    GpsIntervalSlider(
                        label = "AOD/Screen Off Interval",
                        intervalMs = ambientGpsIntervalMs,
                        minSeconds = 1,
                        maxSeconds = 120,
                        presetSeconds = listOf(15, 30, 60, 90),
                        onValueChangeMs = { viewModel.setAmbientGpsInterval(it) }
                    )
                }
            }
        }
    }
}

private fun hasForegroundLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun hasFineForegroundLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
}

@OptIn(FlowPreview::class)
@Composable
private fun GpsIntervalSlider(
    label: String,
    intervalMs: Long,
    minSeconds: Int,
    maxSeconds: Int,
    presetSeconds: List<Int> = emptyList(),
    onValueChangeMs: (Long) -> Unit
) {
    val safeMinSeconds = minSeconds.coerceAtLeast(1)
    val safeMaxSeconds = maxOf(safeMinSeconds, maxSeconds)
    val safePresetSeconds = remember(presetSeconds, safeMinSeconds, safeMaxSeconds) {
        presetSeconds
            .map { it.coerceIn(safeMinSeconds, safeMaxSeconds) }
            .distinct()
    }
    val currentSeconds = (intervalMs / 1000L).toInt().coerceIn(safeMinSeconds, safeMaxSeconds)
    var sliderValue by remember(currentSeconds) { mutableStateOf(currentSeconds.toFloat()) }
    var lastPersistedSeconds by remember(currentSeconds) { mutableStateOf(currentSeconds) }

    // Debounce while dragging, but keep last value persisted even if screen closes quickly.
    LaunchedEffect(Unit) {
        snapshotFlow { sliderValue }
            .map { it.roundToInt().coerceIn(safeMinSeconds, safeMaxSeconds) }
            .distinctUntilChanged()
            .debounce(300L)
            .collect { seconds ->
                lastPersistedSeconds = seconds
                onValueChangeMs(seconds.toLong() * 1000L)
            }
    }
    DisposableEffect(Unit) {
        onDispose {
            val finalSeconds = sliderValue.roundToInt().coerceIn(safeMinSeconds, safeMaxSeconds)
            if (finalSeconds != lastPersistedSeconds) {
                onValueChangeMs(finalSeconds.toLong() * 1000L)
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$label: ${sliderValue.toInt()}s",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            valueRange = safeMinSeconds.toFloat()..safeMaxSeconds.toFloat(),
            steps = (safeMaxSeconds - safeMinSeconds - 1).coerceAtLeast(0),
            increaseIcon = { Icon(Icons.Filled.Add, contentDescription = "Increase") },
            decreaseIcon = { Icon(Icons.Filled.Remove, contentDescription = "Decrease") },
            modifier = Modifier.fillMaxWidth()
        )

        if (safePresetSeconds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            IntervalPresetButtons(
                presetSeconds = safePresetSeconds,
                selectedSeconds = sliderValue.toInt(),
                onPresetSelected = { sliderValue = it.toFloat() }
            )
        }
    }
}

@Composable
private fun IntervalPresetButtons(
    presetSeconds: List<Int>,
    selectedSeconds: Int,
    onPresetSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        presetSeconds.forEach { presetSecondsValue ->
            val selected = presetSecondsValue == selectedSeconds
            Button(
                modifier = Modifier.weight(1f),
                onClick = { onPresetSelected(presetSecondsValue) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            ) {
                Text(
                    text = "${presetSecondsValue}s",
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}
