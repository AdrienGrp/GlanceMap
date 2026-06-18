package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    val elevationSourceOptions = RECORDING_ELEVATION_SOURCE_OPTIONS.map { it to recordingElevationSourceLabel(it) }
    val heartRateSourceOptions = RECORDING_HEART_RATE_SOURCE_OPTIONS.map { it to recordingHeartRateSourceLabel(it) }
    val sensorSourceOptions = RECORDING_SENSOR_SOURCE_OPTIONS.map { it to recordingSensorSourceLabel(it) }
    val stepsSourceOptions = RECORDING_STEPS_SOURCE_OPTIONS.map { it to recordingSensorSourceLabel(it) }

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            RecordingSettingsShortcutChip(
                onClick = onOpenRecordingSettings,
            )
        }
        item {
            SettingsOptionPickerRow(
                label = "Elevation source",
                selectedValue = elevationSource,
                options = elevationSourceOptions,
                secondaryLabel = recordingElevationSourceLabel(elevationSource),
                onSelect = viewModel::setRecordingElevationSource,
            )
        }
        item {
            SettingsOptionPickerRow(
                label = "Heart rate source",
                dialogTitle = "HR source",
                selectedValue = heartRateSource,
                options = heartRateSourceOptions,
                secondaryLabel =
                    recordingHeartRateSourceSecondaryLabel(
                        heartRateSource,
                        !linkedHeartRateAddress.isNullOrBlank(),
                    ),
                onSelect = viewModel::setRecordingHeartRateSource,
            )
        }
        item {
            SettingsOptionPickerRow(
                label = "Cadence source",
                selectedValue = cadenceSource,
                options = sensorSourceOptions,
                secondaryLabel =
                    recordingMetricSourceSecondaryLabel(
                        cadenceSource,
                        if (!linkedRunPodAddress.isNullOrBlank()) "Linked pod" else "Link pod first",
                    ),
                onSelect = viewModel::setRecordingCadenceSource,
            )
        }
        item {
            SettingsOptionPickerRow(
                label = "Speed source",
                selectedValue = speedSource,
                options = sensorSourceOptions,
                secondaryLabel =
                    recordingMetricSourceSecondaryLabel(
                        speedSource,
                        if (!linkedRunPodAddress.isNullOrBlank()) "Linked pod" else "Link pod first",
                    ),
                onSelect = viewModel::setRecordingSpeedSource,
            )
        }
        item {
            SettingsOptionPickerRow(
                label = "Distance source",
                selectedValue = distanceSource,
                options = sensorSourceOptions,
                secondaryLabel =
                    recordingMetricSourceSecondaryLabel(
                        distanceSource,
                        if (!linkedRunPodAddress.isNullOrBlank()) "Linked pod" else "Link pod first",
                    ),
                onSelect = viewModel::setRecordingDistanceSource,
            )
        }
        item {
            SettingsOptionPickerRow(
                label = "Steps source",
                selectedValue = stepsSource,
                options = stepsSourceOptions,
                secondaryLabel =
                    recordingMetricSourceSecondaryLabel(
                        stepsSource,
                        if (!linkedRunPodAddress.isNullOrBlank()) "Pod if available" else "Link pod first",
                    ),
                onSelect = viewModel::setRecordingStepsSource,
            )
        }
    }
}

private val RECORDING_ELEVATION_SOURCE_OPTIONS =
    listOf(
        SettingsRepository.RECORDING_ELEVATION_SOURCE_GPS,
        SettingsRepository.RECORDING_ELEVATION_SOURCE_DEM,
        SettingsRepository.RECORDING_ELEVATION_SOURCE_AUTO,
        SettingsRepository.RECORDING_SOURCE_DISABLED,
    )
private val RECORDING_HEART_RATE_SOURCE_OPTIONS =
    listOf(
        SettingsRepository.RECORDING_HEART_RATE_SOURCE_WATCH,
        SettingsRepository.RECORDING_HEART_RATE_SOURCE_STRAP,
        SettingsRepository.RECORDING_SOURCE_DISABLED,
    )
private val RECORDING_SENSOR_SOURCE_OPTIONS =
    listOf(
        SettingsRepository.RECORDING_SENSOR_SOURCE_WATCH_GPS,
        SettingsRepository.RECORDING_SENSOR_SOURCE_POD,
        SettingsRepository.RECORDING_SOURCE_DISABLED,
    )
private val RECORDING_STEPS_SOURCE_OPTIONS =
    listOf(
        SettingsRepository.RECORDING_SENSOR_SOURCE_WATCH_GPS,
        SettingsRepository.RECORDING_SOURCE_DISABLED,
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
