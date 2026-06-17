package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.glancemap.glancemapwearos.data.repository.SettingsRepository

@Composable
fun RecordingSourceSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenRecordingSettings: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()
    val elevationSource by viewModel.recordingElevationSource.collectAsState()
    val heartRateSource by viewModel.recordingHeartRateSource.collectAsState()
    val cadenceSource by viewModel.recordingCadenceSource.collectAsState()
    val speedSource by viewModel.recordingSpeedSource.collectAsState()
    val distanceSource by viewModel.recordingDistanceSource.collectAsState()
    val stepsSource by viewModel.recordingStepsSource.collectAsState()
    val linkedHeartRateAddress by viewModel.recordingExternalHeartRateAddress.collectAsState()
    val linkedRunPodAddress by viewModel.recordingExternalRunPodAddress.collectAsState()
    var showElevationSourcePicker by remember { mutableStateOf(false) }
    var showHeartRateSourcePicker by remember { mutableStateOf(false) }
    var showCadenceSourcePicker by remember { mutableStateOf(false) }
    var showSpeedSourcePicker by remember { mutableStateOf(false) }
    var showDistanceSourcePicker by remember { mutableStateOf(false) }
    var showStepsSourcePicker by remember { mutableStateOf(false) }

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            SettingsSectionChip(
                label = "Recording settings",
                secondaryLabel = "Back to REC settings",
                onClick = onOpenRecordingSettings,
            )
        }
        item {
            RecordingElevationSourceSetting(
                elevationSource = elevationSource,
                onClick = { showElevationSourcePicker = true },
            )
        }
        item {
            RecordingHeartRateSourceSetting(
                heartRateSource = heartRateSource,
                hasLinkedStrap = !linkedHeartRateAddress.isNullOrBlank(),
                onClick = { showHeartRateSourcePicker = true },
            )
        }
        item {
            RecordingMetricSourceSetting(
                label = "Cadence source",
                source = cadenceSource,
                podLabel = if (!linkedRunPodAddress.isNullOrBlank()) "Linked pod" else "Link pod first",
                onClick = { showCadenceSourcePicker = true },
            )
        }
        item {
            RecordingMetricSourceSetting(
                label = "Speed source",
                source = speedSource,
                podLabel = if (!linkedRunPodAddress.isNullOrBlank()) "Linked pod" else "Link pod first",
                onClick = { showSpeedSourcePicker = true },
            )
        }
        item {
            RecordingMetricSourceSetting(
                label = "Distance source",
                source = distanceSource,
                podLabel = if (!linkedRunPodAddress.isNullOrBlank()) "Linked pod" else "Link pod first",
                onClick = { showDistanceSourcePicker = true },
            )
        }
        item {
            RecordingMetricSourceSetting(
                label = "Steps source",
                source = stepsSource,
                podLabel = if (!linkedRunPodAddress.isNullOrBlank()) "Pod if available" else "Link pod first",
                onClick = { showStepsSourcePicker = true },
            )
        }
    }

    OptionPickerDialog(
        visible = showElevationSourcePicker,
        title = "Elevation source",
        selectedValue = elevationSource,
        options = RECORDING_ELEVATION_SOURCE_OPTIONS.map { it to recordingElevationSourceLabel(it) },
        onDismiss = { showElevationSourcePicker = false },
        onSelect = viewModel::setRecordingElevationSource,
    )
    OptionPickerDialog(
        visible = showHeartRateSourcePicker,
        title = "HR source",
        selectedValue = heartRateSource,
        options = RECORDING_HEART_RATE_SOURCE_OPTIONS.map { it to recordingHeartRateSourceLabel(it) },
        onDismiss = { showHeartRateSourcePicker = false },
        onSelect = viewModel::setRecordingHeartRateSource,
    )
    OptionPickerDialog(
        visible = showCadenceSourcePicker,
        title = "Cadence source",
        selectedValue = cadenceSource,
        options = RECORDING_SENSOR_SOURCE_OPTIONS.map { it to recordingSensorSourceLabel(it) },
        onDismiss = { showCadenceSourcePicker = false },
        onSelect = viewModel::setRecordingCadenceSource,
    )
    OptionPickerDialog(
        visible = showSpeedSourcePicker,
        title = "Speed source",
        selectedValue = speedSource,
        options = RECORDING_SENSOR_SOURCE_OPTIONS.map { it to recordingSensorSourceLabel(it) },
        onDismiss = { showSpeedSourcePicker = false },
        onSelect = viewModel::setRecordingSpeedSource,
    )
    OptionPickerDialog(
        visible = showDistanceSourcePicker,
        title = "Distance source",
        selectedValue = distanceSource,
        options = RECORDING_SENSOR_SOURCE_OPTIONS.map { it to recordingSensorSourceLabel(it) },
        onDismiss = { showDistanceSourcePicker = false },
        onSelect = viewModel::setRecordingDistanceSource,
    )
    OptionPickerDialog(
        visible = showStepsSourcePicker,
        title = "Steps source",
        selectedValue = stepsSource,
        options = RECORDING_STEPS_SOURCE_OPTIONS.map { it to recordingSensorSourceLabel(it) },
        onDismiss = { showStepsSourcePicker = false },
        onSelect = viewModel::setRecordingStepsSource,
    )
}

@Composable
private fun RecordingElevationSourceSetting(
    elevationSource: String,
    onClick: () -> Unit,
) {
    SettingsPickerChip(
        label = "Elevation source",
        secondaryLabel = recordingElevationSourceLabel(elevationSource),
        onClick = onClick,
    )
}

@Composable
private fun RecordingHeartRateSourceSetting(
    heartRateSource: String,
    hasLinkedStrap: Boolean,
    onClick: () -> Unit,
) {
    SettingsPickerChip(
        label = "Heart rate source",
        secondaryLabel = recordingHeartRateSourceSecondaryLabel(heartRateSource, hasLinkedStrap),
        onClick = onClick,
    )
}

@Composable
private fun RecordingMetricSourceSetting(
    label: String,
    source: String,
    podLabel: String,
    onClick: () -> Unit,
) {
    SettingsPickerChip(
        label = label,
        secondaryLabel = recordingMetricSourceSecondaryLabel(source, podLabel),
        onClick = onClick,
    )
}

private val RECORDING_ELEVATION_SOURCE_OPTIONS =
    listOf(
        SettingsRepository.RECORDING_SOURCE_DISABLED,
        SettingsRepository.RECORDING_ELEVATION_SOURCE_GPS,
        SettingsRepository.RECORDING_ELEVATION_SOURCE_DEM,
        SettingsRepository.RECORDING_ELEVATION_SOURCE_AUTO,
    )
private val RECORDING_HEART_RATE_SOURCE_OPTIONS =
    listOf(
        SettingsRepository.RECORDING_SOURCE_DISABLED,
        SettingsRepository.RECORDING_HEART_RATE_SOURCE_WATCH,
        SettingsRepository.RECORDING_HEART_RATE_SOURCE_STRAP,
    )
private val RECORDING_SENSOR_SOURCE_OPTIONS =
    listOf(
        SettingsRepository.RECORDING_SOURCE_DISABLED,
        SettingsRepository.RECORDING_SENSOR_SOURCE_WATCH_GPS,
        SettingsRepository.RECORDING_SENSOR_SOURCE_POD,
    )
private val RECORDING_STEPS_SOURCE_OPTIONS =
    listOf(
        SettingsRepository.RECORDING_SOURCE_DISABLED,
        SettingsRepository.RECORDING_SENSOR_SOURCE_WATCH_GPS,
    )

private fun recordingElevationSourceLabel(source: String): String =
    when (source) {
        SettingsRepository.RECORDING_SOURCE_DISABLED -> "Deactivated"
        SettingsRepository.RECORDING_ELEVATION_SOURCE_DEM -> "DEM"
        SettingsRepository.RECORDING_ELEVATION_SOURCE_AUTO -> "Auto"
        else -> "GPS altitude"
    }

private fun recordingHeartRateSourceLabel(source: String): String =
    when (source) {
        SettingsRepository.RECORDING_SOURCE_DISABLED -> "Deactivated"
        SettingsRepository.RECORDING_HEART_RATE_SOURCE_STRAP -> "Strap"
        else -> "Watch"
    }

private fun recordingSensorSourceLabel(source: String): String =
    when (source) {
        SettingsRepository.RECORDING_SOURCE_DISABLED -> "Deactivated"
        SettingsRepository.RECORDING_SENSOR_SOURCE_POD -> "Pod"
        else -> "Watch/GPS"
    }

private fun recordingMetricSourceSecondaryLabel(
    source: String,
    podLabel: String,
): String =
    when (source) {
        SettingsRepository.RECORDING_SOURCE_DISABLED -> "Deactivated"
        SettingsRepository.RECORDING_SENSOR_SOURCE_POD -> podLabel
        else -> "Watch/GPS"
    }

private fun recordingHeartRateSourceSecondaryLabel(
    source: String,
    hasLinkedStrap: Boolean,
): String =
    when (source) {
        SettingsRepository.RECORDING_SOURCE_DISABLED -> "Deactivated"
        SettingsRepository.RECORDING_HEART_RATE_SOURCE_STRAP ->
            if (hasLinkedStrap) {
                "Linked strap"
            } else {
                "Link strap first"
            }
        else -> "Watch sensor"
    }
