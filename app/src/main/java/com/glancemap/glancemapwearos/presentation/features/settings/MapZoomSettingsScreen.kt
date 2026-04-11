package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.Text
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScreenScaffold
import java.util.Locale
import kotlin.math.cos
import kotlin.math.pow

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun MapZoomSettingsScreen(
    viewModel: SettingsViewModel,
) {
    val listTokens = rememberSettingsListTokens()
    val crownZoomEnabled by viewModel.crownZoomEnabled.collectAsState()
    val crownZoomInverted by viewModel.crownZoomInverted.collectAsState()
    val zoomDefault by viewModel.mapZoomDefault.collectAsState()
    val zoomMin by viewModel.mapZoomMin.collectAsState()
    val zoomMax by viewModel.mapZoomMax.collectAsState()
    val isMetric by viewModel.isMetric.collectAsState()
    var showCrownDirectionPicker by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val approximateViewportWidthPx =
        remember(configuration.screenWidthDp, density.density) {
            with(density) {
                configuration.screenWidthDp.dp
                    .toPx()
                    .toDouble()
            }
        }

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
                SettingsToggleChip(
                    checked = crownZoomEnabled,
                    onCheckedChanged = { viewModel.setCrownZoomEnabled(it) },
                    label = "Crown zoom",
                    secondaryLabel = if (crownZoomEnabled) "Enabled" else "Disabled",
                )
            }
            if (crownZoomEnabled) {
                item {
                    SettingsPickerChip(
                        modifier = Modifier.fillMaxWidth(),
                        label = "Crown direction",
                        iconImageVector = Icons.Filled.UnfoldMore,
                        secondaryLabel = if (crownZoomInverted) "Inverted" else "Normal",
                        onClick = { showCrownDirectionPicker = true },
                    )
                }
            }
            item {
                ZoomSetting(
                    label = "Default zoom",
                    value = zoomDefault,
                    isMetric = isMetric,
                    approximateViewportWidthPx = approximateViewportWidthPx,
                    onValueChange = viewModel::setMapZoomDefault,
                )
            }
            item {
                ZoomSetting(
                    label = "Min zoom",
                    value = zoomMin,
                    isMetric = isMetric,
                    approximateViewportWidthPx = approximateViewportWidthPx,
                    onValueChange = viewModel::setMapZoomMin,
                )
            }
            item {
                ZoomSetting(
                    label = "Max zoom",
                    value = zoomMax,
                    isMetric = isMetric,
                    approximateViewportWidthPx = approximateViewportWidthPx,
                    onValueChange = viewModel::setMapZoomMax,
                )
            }
        }
    }

    OptionPickerDialog(
        visible = showCrownDirectionPicker,
        title = "Crown direction",
        selectedValue = crownZoomInverted,
        options =
            listOf(
                false to "Normal",
                true to "Inverted",
            ),
        onDismiss = { showCrownDirectionPicker = false },
        onSelect = { inverted -> viewModel.setCrownZoomInverted(inverted) },
    )
}

@Composable
private fun ZoomSetting(
    label: String,
    value: Int,
    isMetric: Boolean,
    approximateViewportWidthPx: Double,
    onValueChange: (Int) -> Unit,
) {
    var internalValue by remember(value) { mutableStateOf(value.toFloat()) }
    val zoomLevel = internalValue.toInt()
    val approxScaleText =
        remember(zoomLevel, isMetric, approximateViewportWidthPx) {
            val approxMeters = approximateScaleMetersForZoom(zoomLevel, approximateViewportWidthPx)
            "~${formatScaleDistance(approxMeters, isMetric)}"
        }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "$label: $approxScaleText",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Slider(
            value = internalValue,
            onValueChange = {
                internalValue = it
                onValueChange(it.toInt())
            },
            valueRange = 1f..20f,
            steps = 18,
            increaseIcon = { Icon(Icons.Filled.Add, contentDescription = "Increase") },
            decreaseIcon = { Icon(Icons.Filled.Remove, contentDescription = "Decrease") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val MetricScaleStepsMeters =
    doubleArrayOf(
        5.0,
        10.0,
        20.0,
        25.0,
        50.0,
        100.0,
        200.0,
        250.0,
        500.0,
        1000.0,
        2000.0,
        2500.0,
        5000.0,
        10000.0,
        20000.0,
        25000.0,
        50000.0,
        100000.0,
        200000.0,
        250000.0,
        500000.0,
        1000000.0,
        2000000.0,
        2500000.0,
        5000000.0,
    )

private val ImperialScaleStepsFeet =
    doubleArrayOf(
        20.0,
        50.0,
        100.0,
        150.0,
        200.0,
        250.0,
        300.0,
        500.0,
        800.0,
        1000.0,
        2000.0,
        3000.0,
        5000.0,
        8000.0,
    )

private val ImperialScaleStepsMiles =
    doubleArrayOf(
        0.1,
        0.2,
        0.5,
        1.0,
        2.0,
        5.0,
        10.0,
        20.0,
        25.0,
        30.0,
        40.0,
        50.0,
        80.0,
        100.0,
        200.0,
        250.0,
        500.0,
        1000.0,
        2000.0,
        2500.0,
        5000.0,
    )

private fun approximateScaleMetersForZoom(
    zoom: Int,
    viewportWidthPx: Double,
): Double {
    val clampedZoom = zoom.coerceIn(1, 20)
    val metersPerPixelAtEquator = 156543.03392 / (2.0.pow(clampedZoom.toDouble()))
    val representativeLatitudeFactor = cos(Math.toRadians(45.0))
    val metersPerPixel = metersPerPixelAtEquator * representativeLatitudeFactor
    val usableWidthPx = viewportWidthPx.takeIf { it.isFinite() && it > 0.0 } ?: 320.0
    val targetScaleMeters = metersPerPixel * usableWidthPx * 0.28
    return targetScaleMeters.coerceAtLeast(5.0)
}

private fun formatScaleDistance(
    meters: Double,
    isMetric: Boolean,
): String {
    val roundedMeters = chooseScaleDistanceMeters(meters, isMetric)
    if (isMetric) {
        return if (roundedMeters >= 1000.0) {
            val km = roundedMeters / 1000.0
            if (km >= 10.0) {
                "${km.toInt()} km"
            } else {
                String.format(Locale.getDefault(), "%.1f km", km)
            }
        } else {
            "${roundedMeters.toInt()} m"
        }
    }

    val feet = roundedMeters * 3.28084
    return if (feet < 2640.0) {
        "${feet.toInt()} ft"
    } else {
        val miles = roundedMeters * 0.000621371
        if (miles >= 10.0) {
            "${miles.toInt()} mi"
        } else {
            String.format(Locale.getDefault(), "%.1f mi", miles)
        }
    }
}

private fun chooseScaleDistanceMeters(
    targetMeters: Double,
    isMetric: Boolean,
): Double {
    if (targetMeters <= 0.0 || !targetMeters.isFinite()) return 0.0

    if (isMetric) {
        return pickLargestNotExceeding(MetricScaleStepsMeters, targetMeters)
    }

    val targetFeet = targetMeters * 3.28084
    return if (targetFeet < 2640.0) {
        val feet = pickLargestNotExceeding(ImperialScaleStepsFeet, targetFeet)
        feet / 3.28084
    } else {
        val targetMiles = targetMeters * 0.000621371
        val miles = pickLargestNotExceeding(ImperialScaleStepsMiles, targetMiles)
        miles / 0.000621371
    }
}

private fun pickLargestNotExceeding(
    steps: DoubleArray,
    target: Double,
): Double {
    var candidate = steps.firstOrNull() ?: target
    for (step in steps) {
        if (step <= target) candidate = step else break
    }
    return candidate
}
