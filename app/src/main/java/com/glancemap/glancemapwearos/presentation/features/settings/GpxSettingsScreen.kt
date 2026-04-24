package com.glancemap.glancemapwearos.presentation.features.settings

import android.view.ViewConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.core.gpx.GpxElevationFilterDefaults
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec
import com.glancemap.glancemapwearos.presentation.ui.rememberWearScreenSize
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScreenScaffold
import kotlin.math.abs
import kotlin.math.roundToInt

private const val KMPH_TO_MPS = 1f / 3.6f
private const val MPS_TO_KMPH = 3.6f
private const val MPS_TO_MPH = 2.2369363f
private const val MPH_TO_MPS = 1f / MPS_TO_MPH
private const val METER_TO_FOOT = 3.28084f
private const val FOOT_TO_METER = 1f / METER_TO_FOOT
private const val VERTICAL_RATE_STEP_METERS_PER_HOUR = 50f
private const val VERTICAL_RATE_STEP_FEET_PER_HOUR = 100f

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun GpxSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenGeneralSettings: () -> Unit,
) {
    val screenSize = rememberWearScreenSize()
    val listTokens =
        rememberSettingsListTokens(
            compactTop = 12.dp,
            standardTop = 14.dp,
            expandedTop = 16.dp,
            compactBottom = 12.dp,
            standardBottom = 14.dp,
            expandedBottom = 16.dp,
            compactItemSpacing = 12.dp,
            standardItemSpacing = 14.dp,
            expandedItemSpacing = 16.dp,
        )
    val adaptive = rememberWearAdaptiveSpec()
    val trackColor by viewModel.gpxTrackColor.collectAsState()
    val trackWidth by viewModel.gpxTrackWidth.collectAsState()
    val trackOpacityPercent by viewModel.gpxTrackOpacityPercent.collectAsState()
    val isGpxInspectionEnabled by viewModel.isGpxInspectionEnabled.collectAsState()
    val gpxFlatSpeedMps by viewModel.gpxFlatSpeedMps.collectAsState()
    val gpxAdvancedEtaEnabled by viewModel.gpxAdvancedEtaEnabled.collectAsState()
    val gpxUphillVerticalMetersPerHour by viewModel.gpxUphillVerticalMetersPerHour.collectAsState()
    val gpxDownhillVerticalMetersPerHour by viewModel.gpxDownhillVerticalMetersPerHour.collectAsState()
    val gpxElevationSmoothingDistanceMeters by viewModel.gpxElevationSmoothingDistanceMeters.collectAsState()
    val gpxElevationNeutralDiffThresholdMeters by viewModel.gpxElevationNeutralDiffThresholdMeters.collectAsState()
    val gpxElevationTrendActivationThresholdMeters by viewModel.gpxElevationTrendActivationThresholdMeters.collectAsState()
    val gpxElevationAutoAdjustPerGpx by viewModel.gpxElevationAutoAdjustPerGpx.collectAsState()
    val isMetric by viewModel.isMetric.collectAsState()
    var showInspectionHelpDialog by remember { mutableStateOf(false) }
    var showAdvancedElevationFilter by remember { mutableStateOf(false) }
    val helpDialogScrollState = rememberScrollState()
    val helpDialogFocusRequester = remember { FocusRequester() }
    val currentLocale = LocalLocale.current.platformLocale
    val longPressSecondsLabel =
        remember(currentLocale) {
            val longPressSeconds = ViewConfiguration.getLongPressTimeout() / 1000f
            String.format(currentLocale, "%.1f", longPressSeconds)
        }
    LaunchedEffect(showInspectionHelpDialog) {
        if (showInspectionHelpDialog) {
            helpDialogFocusRequester.requestFocus()
        }
    }

    val colorPalette =
        listOf(
            Color.Magenta,
            Color.Blue,
            Color(0xFFFFA500), // Orange
            Color.Red,
        )

    val listState = rememberScalingLazyListState()
    val groupSpacing =
        when (screenSize) {
            WearScreenSize.LARGE -> 8.dp
            WearScreenSize.MEDIUM -> 7.dp
            WearScreenSize.SMALL -> 6.dp
        }
    val colorPickerSpacing =
        when (screenSize) {
            WearScreenSize.LARGE -> 10.dp
            WearScreenSize.MEDIUM -> 8.dp
            WearScreenSize.SMALL -> 6.dp
        }
    val colorButtonSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 32.dp
            WearScreenSize.MEDIUM -> 30.dp
            WearScreenSize.SMALL -> 28.dp
        }
    val selectedIconSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 16.dp
            WearScreenSize.MEDIUM -> 14.dp
            WearScreenSize.SMALL -> 12.dp
        }
    val trackWidthSpacing =
        when (screenSize) {
            WearScreenSize.LARGE -> 4.dp
            WearScreenSize.MEDIUM -> 3.dp
            WearScreenSize.SMALL -> 2.dp
        }
    val helpButtonSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 30.dp
            WearScreenSize.MEDIUM -> 28.dp
            WearScreenSize.SMALL -> 26.dp
        }
    val helpIconSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 18.dp
            WearScreenSize.MEDIUM -> 16.dp
            WearScreenSize.SMALL -> 14.dp
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
                GeneralSettingsShortcutChip(onClick = onOpenGeneralSettings)
            }

            item {
                SettingsToggleChip(
                    checked = isGpxInspectionEnabled,
                    onCheckedChanged = { viewModel.setGpxInspectionEnabled(it) },
                    label = "Route Analyzer",
                    secondaryLabel = "Long press on track",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                FlatSpeedSetting(
                    speedMps = gpxFlatSpeedMps,
                    isMetric = isMetric,
                    onSpeedChange = viewModel::setGpxFlatSpeedMps,
                )
            }
            item {
                SettingsToggleChip(
                    checked = gpxAdvancedEtaEnabled,
                    onCheckedChanged = viewModel::setGpxAdvancedEtaEnabled,
                    label = "Advanced ETA",
                    secondaryLabel =
                        if (gpxAdvancedEtaEnabled) {
                            "Use uphill/downhill rates"
                        } else {
                            "Flat speed only"
                        },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (gpxAdvancedEtaEnabled) {
                item {
                    AdjustableElevationFilterSetting(
                        label = "Uphill Rate",
                        valueText =
                            formatVerticalRate(
                                metersPerHour = gpxUphillVerticalMetersPerHour,
                                isMetric = isMetric,
                            ),
                        canDecrease =
                            gpxUphillVerticalMetersPerHour >
                                SettingsRepository.MIN_GPX_VERTICAL_METERS_PER_HOUR,
                        canIncrease =
                            gpxUphillVerticalMetersPerHour <
                                SettingsRepository.MAX_GPX_UPHILL_VERTICAL_METERS_PER_HOUR,
                        onDecrease = {
                            viewModel.setGpxUphillVerticalMetersPerHour(
                                stepVerticalRateMetersPerHour(
                                    currentMetersPerHour = gpxUphillVerticalMetersPerHour,
                                    isMetric = isMetric,
                                    increase = false,
                                    minMetersPerHour = SettingsRepository.MIN_GPX_VERTICAL_METERS_PER_HOUR,
                                    maxMetersPerHour = SettingsRepository.MAX_GPX_UPHILL_VERTICAL_METERS_PER_HOUR,
                                ),
                            )
                        },
                        onIncrease = {
                            viewModel.setGpxUphillVerticalMetersPerHour(
                                stepVerticalRateMetersPerHour(
                                    currentMetersPerHour = gpxUphillVerticalMetersPerHour,
                                    isMetric = isMetric,
                                    increase = true,
                                    minMetersPerHour = SettingsRepository.MIN_GPX_VERTICAL_METERS_PER_HOUR,
                                    maxMetersPerHour = SettingsRepository.MAX_GPX_UPHILL_VERTICAL_METERS_PER_HOUR,
                                ),
                            )
                        },
                    )
                }
                item {
                    AdjustableElevationFilterSetting(
                        label = "Downhill Rate",
                        valueText =
                            formatVerticalRate(
                                metersPerHour = gpxDownhillVerticalMetersPerHour,
                                isMetric = isMetric,
                            ),
                        canDecrease =
                            gpxDownhillVerticalMetersPerHour >
                                SettingsRepository.MIN_GPX_VERTICAL_METERS_PER_HOUR,
                        canIncrease =
                            gpxDownhillVerticalMetersPerHour <
                                SettingsRepository.MAX_GPX_DOWNHILL_VERTICAL_METERS_PER_HOUR,
                        onDecrease = {
                            viewModel.setGpxDownhillVerticalMetersPerHour(
                                stepVerticalRateMetersPerHour(
                                    currentMetersPerHour = gpxDownhillVerticalMetersPerHour,
                                    isMetric = isMetric,
                                    increase = false,
                                    minMetersPerHour = SettingsRepository.MIN_GPX_VERTICAL_METERS_PER_HOUR,
                                    maxMetersPerHour = SettingsRepository.MAX_GPX_DOWNHILL_VERTICAL_METERS_PER_HOUR,
                                ),
                            )
                        },
                        onIncrease = {
                            viewModel.setGpxDownhillVerticalMetersPerHour(
                                stepVerticalRateMetersPerHour(
                                    currentMetersPerHour = gpxDownhillVerticalMetersPerHour,
                                    isMetric = isMetric,
                                    increase = true,
                                    minMetersPerHour = SettingsRepository.MIN_GPX_VERTICAL_METERS_PER_HOUR,
                                    maxMetersPerHour = SettingsRepository.MAX_GPX_DOWNHILL_VERTICAL_METERS_PER_HOUR,
                                ),
                            )
                        },
                    )
                }
                item {
                    Text(
                        text = "Flat speed still drives easy terrain. Uphill and downhill rates only limit steeper climbs and descents.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(groupSpacing),
                ) {
                    Text(
                        text = "Track Color",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    SimpleColorPicker(
                        colors = colorPalette,
                        selectedColor = Color(trackColor),
                        itemSpacing = colorPickerSpacing,
                        buttonSize = colorButtonSize,
                        selectedIconSize = selectedIconSize,
                        onColorSelected = { color ->
                            viewModel.setGpxTrackColor(color.toArgb())
                        },
                    )
                }
            }

            item {
                TrackWidthSetting(
                    label = "Track Width",
                    value = trackWidth,
                    onValueChange = { newWidth ->
                        viewModel.setGpxTrackWidth(newWidth)
                    },
                    spacing = trackWidthSpacing,
                )
            }
            item {
                TrackOpacitySetting(
                    label = "Track Opacity",
                    valuePercent = trackOpacityPercent,
                    onValueChange = viewModel::setGpxTrackOpacityPercent,
                    spacing = trackWidthSpacing,
                )
            }
            item {
                Text(
                    text = "Fine-tune how GPX ascent and descent are calculated. Most tracks can stay on the defaults.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                )
            }
            item {
                SettingsToggleChip(
                    checked = showAdvancedElevationFilter,
                    onCheckedChanged = { showAdvancedElevationFilter = it },
                    label = "Advanced Filter",
                    secondaryLabel = "Tune GPX elevation totals",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showAdvancedElevationFilter) {
                item {
                    SettingsToggleChip(
                        checked = gpxElevationAutoAdjustPerGpx,
                        onCheckedChanged = viewModel::setGpxElevationAutoAdjustPerGpx,
                        label = "Auto-adjust per GPX",
                        secondaryLabel =
                            if (gpxElevationAutoAdjustPerGpx) {
                                "Use sliders as baseline"
                            } else {
                                "Apply sliders exactly"
                            },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    AdjustableElevationFilterSetting(
                        label = GpxElevationFilterUi.SMOOTHING_LABEL,
                        valueText =
                            GpxElevationFilterUi.formatSmoothingDistance(
                                gpxElevationSmoothingDistanceMeters,
                            ),
                        canDecrease =
                            gpxElevationSmoothingDistanceMeters >
                                GpxElevationFilterDefaults.MIN_SMOOTHING_DISTANCE_METERS,
                        canIncrease =
                            gpxElevationSmoothingDistanceMeters <
                                GpxElevationFilterDefaults.MAX_SMOOTHING_DISTANCE_METERS,
                        onDecrease = {
                            viewModel.setGpxElevationSmoothingDistanceMeters(
                                gpxElevationSmoothingDistanceMeters -
                                    GpxElevationFilterDefaults.STEP_SMOOTHING_DISTANCE_METERS,
                            )
                        },
                        onIncrease = {
                            viewModel.setGpxElevationSmoothingDistanceMeters(
                                gpxElevationSmoothingDistanceMeters +
                                    GpxElevationFilterDefaults.STEP_SMOOTHING_DISTANCE_METERS,
                            )
                        },
                    )
                }
                item {
                    AdjustableElevationFilterSetting(
                        label = GpxElevationFilterUi.NOISE_THRESHOLD_LABEL,
                        valueText =
                            GpxElevationFilterUi.formatThreshold(
                                gpxElevationNeutralDiffThresholdMeters,
                            ),
                        canDecrease =
                            gpxElevationNeutralDiffThresholdMeters >
                                GpxElevationFilterDefaults.MIN_NEUTRAL_DIFF_THRESHOLD_METERS,
                        canIncrease =
                            gpxElevationNeutralDiffThresholdMeters <
                                GpxElevationFilterDefaults.MAX_NEUTRAL_DIFF_THRESHOLD_METERS,
                        onDecrease = {
                            viewModel.setGpxElevationNeutralDiffThresholdMeters(
                                gpxElevationNeutralDiffThresholdMeters -
                                    GpxElevationFilterDefaults.STEP_NEUTRAL_DIFF_THRESHOLD_METERS,
                            )
                        },
                        onIncrease = {
                            viewModel.setGpxElevationNeutralDiffThresholdMeters(
                                gpxElevationNeutralDiffThresholdMeters +
                                    GpxElevationFilterDefaults.STEP_NEUTRAL_DIFF_THRESHOLD_METERS,
                            )
                        },
                    )
                }
                item {
                    AdjustableElevationFilterSetting(
                        label = GpxElevationFilterUi.TREND_THRESHOLD_LABEL,
                        valueText =
                            GpxElevationFilterUi.formatThreshold(
                                gpxElevationTrendActivationThresholdMeters,
                            ),
                        canDecrease =
                            gpxElevationTrendActivationThresholdMeters >
                                GpxElevationFilterDefaults.MIN_TREND_ACTIVATION_THRESHOLD_METERS,
                        canIncrease =
                            gpxElevationTrendActivationThresholdMeters <
                                GpxElevationFilterDefaults.MAX_TREND_ACTIVATION_THRESHOLD_METERS,
                        onDecrease = {
                            viewModel.setGpxElevationTrendActivationThresholdMeters(
                                gpxElevationTrendActivationThresholdMeters -
                                    GpxElevationFilterDefaults.STEP_TREND_ACTIVATION_THRESHOLD_METERS,
                            )
                        },
                        onIncrease = {
                            viewModel.setGpxElevationTrendActivationThresholdMeters(
                                gpxElevationTrendActivationThresholdMeters +
                                    GpxElevationFilterDefaults.STEP_TREND_ACTIVATION_THRESHOLD_METERS,
                            )
                        },
                    )
                }
                item {
                    Text(
                        text =
                            if (gpxElevationAutoAdjustPerGpx) {
                                "Auto-adjust can nudge these values depending on the GPX."
                            } else {
                                "These values are applied exactly to every GPX."
                            },
                        style = MaterialTheme.typography.bodySmall,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                    )
                }
            }
            item {
                IconButton(
                    onClick = { showInspectionHelpDialog = true },
                    modifier = Modifier.size(helpButtonSize),
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Route analyzer help",
                        modifier = Modifier.size(helpIconSize),
                    )
                }
            }
        }
    }

    AlertDialog(
        visible = showInspectionHelpDialog,
        onDismissRequest = { showInspectionHelpDialog = false },
        title = { Text("Route Analyzer") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = adaptive.dialogBodyMaxHeight)
                        .onPreRotaryScrollEvent { event ->
                            val consumed = helpDialogScrollState.dispatchRawDelta(event.verticalScrollPixels)
                            abs(consumed) > 0.5f
                        }.focusRequester(helpDialogFocusRequester)
                        .focusable()
                        .verticalScroll(helpDialogScrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Hold $longPressSecondsLabel s on map to inspect distance and elevation gain.",
                )
            }
        },
    )
}

@Composable
private fun SimpleColorPicker(
    colors: List<Color>,
    selectedColor: Color,
    itemSpacing: Dp,
    buttonSize: Dp,
    selectedIconSize: Dp,
    onColorSelected: (Color) -> Unit,
) {
    val selectionRingWidth = 2.dp
    val selectionRingInset = 2.dp
    val swatchSlotSize = buttonSize + (selectionRingInset * 2)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(itemSpacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEach { color ->
            val isSelected = color.toArgb() == selectedColor.toArgb()

            Box(
                modifier =
                    Modifier
                        .size(swatchSlotSize)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = selectionRingWidth,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    shape = CircleShape,
                                )
                            } else {
                                Modifier
                            },
                        ).padding(selectionRingInset)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { onColorSelected(color) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Selected",
                        modifier = Modifier.size(selectedIconSize),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun FlatSpeedSetting(
    speedMps: Float,
    isMetric: Boolean,
    onSpeedChange: (Float) -> Unit,
) {
    val currentLocale = LocalLocale.current.platformLocale
    val maxDisplaySpeed =
        if (isMetric) {
            20f
        } else {
            SettingsRepository.MAX_GPX_FLAT_SPEED_MPS * MPS_TO_MPH
        }
    val displayUnit = if (isMetric) "km/h" else "mph"
    val displaySpeed =
        if (isMetric) {
            speedMps * MPS_TO_KMPH
        } else {
            speedMps * MPS_TO_MPH
        }.coerceIn(0f, maxDisplaySpeed)

    var sliderValue by remember(speedMps, isMetric) { mutableStateOf(displaySpeed) }
    val sliderStep = 0.1f
    val steps = ((maxDisplaySpeed / sliderStep).roundToInt() - 1).coerceAtLeast(0)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text =
                String.format(
                    currentLocale,
                    "Flat Speed: %.1f %s",
                    sliderValue,
                    displayUnit,
                ),
            style = MaterialTheme.typography.titleMedium,
        )
        Slider(
            value = sliderValue,
            onValueChange = { rawValue ->
                val snapped =
                    ((rawValue / sliderStep).roundToInt() * sliderStep)
                        .coerceIn(0f, maxDisplaySpeed)
                sliderValue = snapped
                val speedMpsValue =
                    if (isMetric) {
                        snapped * KMPH_TO_MPS
                    } else {
                        snapped * MPH_TO_MPS
                    }
                onSpeedChange(speedMpsValue)
            },
            valueRange = 0f..maxDisplaySpeed,
            steps = steps,
            increaseIcon = { Icon(Icons.Filled.Add, contentDescription = "Increase") },
            decreaseIcon = { Icon(Icons.Filled.Remove, contentDescription = "Decrease") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TrackWidthSetting(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    spacing: Dp,
) {
    var internalValue by remember(value) { mutableStateOf(value) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Text(
            text = "$label: ${internalValue.toInt()} dp",
            style = MaterialTheme.typography.titleMedium,
        )
        Slider(
            value = internalValue,
            onValueChange = {
                internalValue = it
                onValueChange(it)
            },
            valueRange = 1f..15f,
            steps = 13,
            increaseIcon = { Icon(Icons.Filled.Add, contentDescription = "Increase") },
            decreaseIcon = { Icon(Icons.Filled.Remove, contentDescription = "Decrease") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TrackOpacitySetting(
    label: String,
    valuePercent: Int,
    onValueChange: (Int) -> Unit,
    spacing: Dp,
) {
    var internalValue by remember(valuePercent) { mutableStateOf(valuePercent.toFloat()) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Text(
            text = "$label: ${internalValue.roundToInt()}%",
            style = MaterialTheme.typography.titleMedium,
        )
        Slider(
            value = internalValue,
            onValueChange = {
                val snapped =
                    it.roundToInt().coerceIn(
                        SettingsRepository.MIN_GPX_TRACK_OPACITY_PERCENT,
                        SettingsRepository.MAX_GPX_TRACK_OPACITY_PERCENT,
                    )
                internalValue = snapped.toFloat()
                onValueChange(snapped)
            },
            valueRange =
                SettingsRepository.MIN_GPX_TRACK_OPACITY_PERCENT.toFloat()..SettingsRepository.MAX_GPX_TRACK_OPACITY_PERCENT.toFloat(),
            steps =
                (
                    SettingsRepository.MAX_GPX_TRACK_OPACITY_PERCENT -
                        SettingsRepository.MIN_GPX_TRACK_OPACITY_PERCENT - 1
                ).coerceAtLeast(0),
            increaseIcon = { Icon(Icons.Filled.Add, contentDescription = "Increase") },
            decreaseIcon = { Icon(Icons.Filled.Remove, contentDescription = "Decrease") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AdjustableElevationFilterSetting(
    label: String,
    valueText: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onDecrease,
                enabled = canDecrease,
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = Color.Black.copy(alpha = 0.35f),
                        disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    ),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease")
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(
                onClick = onIncrease,
                enabled = canIncrease,
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = Color.Black.copy(alpha = 0.35f),
                        disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    ),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Increase")
            }
        }
    }
}

private fun formatVerticalRate(
    metersPerHour: Float,
    isMetric: Boolean,
): String =
    if (isMetric) {
        "${metersPerHour.roundToInt()} m/h"
    } else {
        "${(metersPerHour * METER_TO_FOOT).roundToInt()} ft/h"
    }

private fun stepVerticalRateMetersPerHour(
    currentMetersPerHour: Float,
    isMetric: Boolean,
    increase: Boolean,
    minMetersPerHour: Float,
    maxMetersPerHour: Float,
): Float =
    if (isMetric) {
        val delta =
            if (increase) {
                VERTICAL_RATE_STEP_METERS_PER_HOUR
            } else {
                -VERTICAL_RATE_STEP_METERS_PER_HOUR
            }
        (currentMetersPerHour + delta).coerceIn(minMetersPerHour, maxMetersPerHour)
    } else {
        val currentFeetPerHour = currentMetersPerHour * METER_TO_FOOT
        val snappedFeetPerHour =
            (currentFeetPerHour / VERTICAL_RATE_STEP_FEET_PER_HOUR)
                .roundToInt() * VERTICAL_RATE_STEP_FEET_PER_HOUR
        val delta =
            if (increase) {
                VERTICAL_RATE_STEP_FEET_PER_HOUR
            } else {
                -VERTICAL_RATE_STEP_FEET_PER_HOUR
            }
        (
            (snappedFeetPerHour + delta)
                .coerceIn(
                    minMetersPerHour * METER_TO_FOOT,
                    maxMetersPerHour * METER_TO_FOOT,
                )
        ) * FOOT_TO_METER
    }
