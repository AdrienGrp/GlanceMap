package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScreenScaffold

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun MapDisplaySettingsScreen(
    viewModel: SettingsViewModel,
) {
    val listTokens = rememberSettingsListTokens()
    val northIndicatorMode by viewModel.northIndicatorMode.collectAsState()
    val navigationMarkerStyle by viewModel.navigationMarkerStyle.collectAsState()
    val showTimeInNavigate by viewModel.showTimeInNavigate.collectAsState()
    val navigateTimeFormat by viewModel.navigateTimeFormat.collectAsState()
    val mapZoomButtonsMode by viewModel.mapZoomButtonsMode.collectAsState()
    val gpsAccuracyCircleEnabled by viewModel.gpsAccuracyCircleEnabled.collectAsState()

    val listState = rememberScalingLazyListState()
    val northIndicatorModes = listOf("ALWAYS", "COMPASS_ONLY", "NORTH_UP_ONLY", "NEVER")
    val markerStyles =
        listOf(
            SettingsRepository.MARKER_STYLE_DOT,
            SettingsRepository.MARKER_STYLE_TRIANGLE,
        )
    val zoomButtonModes =
        listOf(
            SettingsRepository.ZOOM_BUTTONS_BOTH,
            SettingsRepository.ZOOM_BUTTONS_HIDE_BOTH,
            SettingsRepository.ZOOM_BUTTONS_HIDE_PLUS,
        )
    val timeFormats =
        listOf(
            SettingsRepository.TIME_FORMAT_24_HOUR,
            SettingsRepository.TIME_FORMAT_12_HOUR,
        )
    var showTimeFormatPicker by remember { mutableStateOf(false) }
    var showNorthIndicatorPicker by remember { mutableStateOf(false) }
    var showMarkerStylePicker by remember { mutableStateOf(false) }
    var showZoomButtonsPicker by remember { mutableStateOf(false) }
    val timeFormatOptions =
        remember {
            timeFormats.map { it to timeFormatLabel(it) }
        }
    val northIndicatorOptions =
        remember {
            northIndicatorModes.map { it to northIndicatorModeLabel(it) }
        }
    val markerStyleOptions =
        remember {
            markerStyles.map { it to markerStyleLabel(it) }
        }
    val zoomButtonOptions =
        remember {
            zoomButtonModes.map { it to zoomButtonsModeLabel(it) }
        }

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
                SettingsToggleChip(
                    checked = showTimeInNavigate,
                    onCheckedChanged = { viewModel.setShowTimeInNavigate(it) },
                    label = "Show time on map",
                )
            }
            if (showTimeInNavigate) {
                item {
                    SettingsPickerChip(
                        label = "Time format",
                        onClick = { showTimeFormatPicker = true },
                        iconImageVector = Icons.Filled.UnfoldMore,
                        secondaryLabel = timeFormatLabel(navigateTimeFormat),
                    )
                }
            }
            item {
                SettingsPickerChip(
                    label = "North indicator",
                    onClick = { showNorthIndicatorPicker = true },
                    iconImageVector = Icons.Filled.UnfoldMore,
                    secondaryLabel = northIndicatorModeLabel(northIndicatorMode),
                )
            }
            item {
                SettingsPickerChip(
                    label = "Zoom buttons",
                    onClick = { showZoomButtonsPicker = true },
                    iconImageVector = Icons.Filled.UnfoldMore,
                    secondaryLabel = zoomButtonsModeLabel(mapZoomButtonsMode),
                )
            }
            item {
                SettingsPickerChip(
                    label = "Marker style",
                    onClick = { showMarkerStylePicker = true },
                    iconImageVector = Icons.Filled.UnfoldMore,
                    secondaryLabel = markerStyleLabel(navigationMarkerStyle),
                )
            }
            item {
                SettingsToggleChip(
                    checked = gpsAccuracyCircleEnabled,
                    onCheckedChanged = { viewModel.setGpsAccuracyCircleEnabled(it) },
                    label = "GPS accuracy circle",
                    secondaryLabel = "Show uncertainty radius",
                )
            }
        }
    }

    OptionPickerDialog(
        visible = showTimeFormatPicker,
        title = "Time format",
        selectedValue = navigateTimeFormat,
        options = timeFormatOptions,
        onDismiss = { showTimeFormatPicker = false },
        onSelect = { selected -> viewModel.setNavigateTimeFormat(selected) },
    )
    OptionPickerDialog(
        visible = showNorthIndicatorPicker,
        title = "North indicator",
        selectedValue = northIndicatorMode,
        options = northIndicatorOptions,
        onDismiss = { showNorthIndicatorPicker = false },
        onSelect = { selected -> viewModel.setNorthIndicatorMode(selected) },
    )
    OptionPickerDialog(
        visible = showMarkerStylePicker,
        title = "Marker style",
        selectedValue = navigationMarkerStyle,
        options = markerStyleOptions,
        onDismiss = { showMarkerStylePicker = false },
        onSelect = { selected -> viewModel.setNavigationMarkerStyle(selected) },
    )
    OptionPickerDialog(
        visible = showZoomButtonsPicker,
        title = "Zoom buttons",
        selectedValue = mapZoomButtonsMode,
        options = zoomButtonOptions,
        onDismiss = { showZoomButtonsPicker = false },
        onSelect = { selected -> viewModel.setMapZoomButtonsMode(selected) },
    )
}

private fun timeFormatLabel(format: String): String =
    when (format) {
        SettingsRepository.TIME_FORMAT_24_HOUR -> "24-hour"
        SettingsRepository.TIME_FORMAT_12_HOUR -> "12-hour"
        else -> "24-hour"
    }

private fun markerStyleLabel(style: String): String =
    when (style) {
        SettingsRepository.MARKER_STYLE_DOT -> "Dot + cone"
        SettingsRepository.MARKER_STYLE_TRIANGLE -> "Arrow"
        else -> "Dot + cone"
    }

private fun northIndicatorModeLabel(mode: String): String =
    when (mode) {
        "ALWAYS" -> "Always"
        "COMPASS_ONLY" -> "Compass only"
        "NORTH_UP_ONLY" -> "North-up only"
        "NEVER" -> "Never"
        else ->
            mode
                .replace("_", " ")
                .lowercase()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

private fun zoomButtonsModeLabel(mode: String): String =
    when (mode) {
        SettingsRepository.ZOOM_BUTTONS_HIDE_BOTH -> "Hide + and -"
        SettingsRepository.ZOOM_BUTTONS_HIDE_PLUS -> "Hide + only"
        else -> "Show + and -"
    }
