package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import com.glancemap.glancemapwearos.data.repository.SettingsRepository

@Composable
fun RecordingSourceSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenRecordingSettings: () -> Unit,
    onOpenBikeSensorSettings: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()
    val elevationSource by viewModel.recordingElevationSource.collectAsState()
    val heartRateSource by viewModel.recordingHeartRateSource.collectAsState()
    val cadenceSource by viewModel.recordingCadenceSource.collectAsState()
    val speedSource by viewModel.recordingSpeedSource.collectAsState()
    val distanceSource by viewModel.recordingDistanceSource.collectAsState()
    val stepsSource by viewModel.recordingStepsSource.collectAsState()
    val activityProfile by viewModel.activityProfile.collectAsState()
    val linkedHeartRateAddress by viewModel.recordingExternalHeartRateAddress.collectAsState()
    val linkedRunPodAddress by viewModel.recordingExternalRunPodAddress.collectAsState()
    val isBikeProfile = activityProfile == SettingsRepository.ACTIVITY_PROFILE_BIKE
    val linkedSensorLabel =
        if (isBikeProfile) {
            if (!linkedRunPodAddress.isNullOrBlank()) "Linked bike sensor" else "Link bike sensor"
        } else {
            if (!linkedRunPodAddress.isNullOrBlank()) "Linked foot pod" else "Link foot pod"
        }
    val elevationSourceOptions = RECORDING_ELEVATION_SOURCE_OPTIONS.map { it to recordingElevationSourceLabel(it) }
    val heartRateSourceOptions = RECORDING_HEART_RATE_SOURCE_OPTIONS.map { it to recordingHeartRateSourceLabel(it) }
    val sensorSourceOptions = RECORDING_SENSOR_SOURCE_OPTIONS.map { it to recordingSensorSourceLabel(it, isBikeProfile) }
    val stepsSourceOptions = RECORDING_STEPS_SOURCE_OPTIONS.map { it to recordingSensorSourceLabel(it, isBikeProfile) }

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            RecordingSettingsShortcutChip(
                onClick = onOpenRecordingSettings,
            )
        }
        if (isBikeProfile) {
            item {
                SettingsSectionChip(
                    label = "Bike sensor",
                    secondaryLabel = "Wheel size + BLE setup",
                    iconImageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                    compactRoundWidthFraction = 0.86f,
                    onClick = onOpenBikeSensorSettings,
                )
            }
        }
        item {
            RecordingSourceToggle(
                label = "Elevation",
                source = elevationSource,
                defaultSource = SettingsRepository.DEFAULT_RECORDING_ELEVATION_SOURCE,
                enabledLabel = recordingElevationSourceLabel(elevationSource),
                onSourceChange = viewModel::setRecordingElevationSource,
            )
        }
        if (elevationSource.isRecordingSourceEnabled()) {
            item {
                SettingsOptionPickerRow(
                    label = "Elevation source",
                    selectedValue = elevationSource,
                    options = elevationSourceOptions,
                    secondaryLabel = recordingElevationSourceLabel(elevationSource),
                    onSelect = viewModel::setRecordingElevationSource,
                )
            }
        }
        item {
            RecordingSourceToggle(
                label = "Heart rate",
                source = heartRateSource,
                defaultSource = SettingsRepository.DEFAULT_RECORDING_HEART_RATE_SOURCE,
                enabledLabel =
                    recordingHeartRateSourceSecondaryLabel(
                        heartRateSource,
                        !linkedHeartRateAddress.isNullOrBlank(),
                    ),
                onSourceChange = viewModel::setRecordingHeartRateSource,
            )
        }
        if (heartRateSource.isRecordingSourceEnabled()) {
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
        }
        item {
            RecordingSourceToggle(
                label = "Cadence",
                source = cadenceSource,
                defaultSource = SettingsRepository.DEFAULT_RECORDING_CADENCE_SOURCE,
                enabledLabel =
                    recordingMetricSourceSecondaryLabel(
                        cadenceSource,
                        linkedSensorLabel,
                    ),
                onSourceChange = viewModel::setRecordingCadenceSource,
            )
        }
        if (cadenceSource.isRecordingSourceEnabled()) {
            item {
                SettingsOptionPickerRow(
                    label = "Cadence source",
                    selectedValue = cadenceSource,
                    options = sensorSourceOptions,
                    secondaryLabel =
                        recordingMetricSourceSecondaryLabel(
                            cadenceSource,
                            linkedSensorLabel,
                        ),
                    onSelect = viewModel::setRecordingCadenceSource,
                )
            }
        }
        item {
            RecordingSourceToggle(
                label = "Speed",
                source = speedSource,
                defaultSource = SettingsRepository.DEFAULT_RECORDING_SPEED_SOURCE,
                enabledLabel =
                    recordingMetricSourceSecondaryLabel(
                        speedSource,
                        linkedSensorLabel,
                    ),
                onSourceChange = viewModel::setRecordingSpeedSource,
            )
        }
        if (speedSource.isRecordingSourceEnabled()) {
            item {
                SettingsOptionPickerRow(
                    label = "Speed source",
                    selectedValue = speedSource,
                    options = sensorSourceOptions,
                    secondaryLabel =
                        recordingMetricSourceSecondaryLabel(
                            speedSource,
                            linkedSensorLabel,
                        ),
                    onSelect = viewModel::setRecordingSpeedSource,
                )
            }
        }
        item {
            RecordingSourceToggle(
                label = "Distance",
                source = distanceSource,
                defaultSource = SettingsRepository.DEFAULT_RECORDING_DISTANCE_SOURCE,
                enabledLabel =
                    recordingMetricSourceSecondaryLabel(
                        distanceSource,
                        linkedSensorLabel,
                    ),
                onSourceChange = viewModel::setRecordingDistanceSource,
            )
        }
        if (distanceSource.isRecordingSourceEnabled()) {
            item {
                SettingsOptionPickerRow(
                    label = "Distance source",
                    selectedValue = distanceSource,
                    options = sensorSourceOptions,
                    secondaryLabel =
                        recordingMetricSourceSecondaryLabel(
                            distanceSource,
                            linkedSensorLabel,
                        ),
                    onSelect = viewModel::setRecordingDistanceSource,
                )
            }
        }
        item {
            RecordingSourceToggle(
                label = "Steps",
                source = stepsSource,
                defaultSource = SettingsRepository.DEFAULT_RECORDING_STEPS_SOURCE,
                enabledLabel =
                    recordingMetricSourceSecondaryLabel(
                        stepsSource,
                        if (!linkedRunPodAddress.isNullOrBlank()) "Sensor if available" else "Link sensor first",
                    ),
                onSourceChange = viewModel::setRecordingStepsSource,
            )
        }
        if (stepsSource.isRecordingSourceEnabled()) {
            item {
                SettingsOptionPickerRow(
                    label = "Steps source",
                    selectedValue = stepsSource,
                    options = stepsSourceOptions,
                    secondaryLabel =
                        recordingMetricSourceSecondaryLabel(
                            stepsSource,
                            if (!linkedRunPodAddress.isNullOrBlank()) "Sensor if available" else "Link sensor first",
                        ),
                    onSelect = viewModel::setRecordingStepsSource,
                )
            }
        }
    }
}

private val RECORDING_ELEVATION_SOURCE_OPTIONS =
    listOf(
        SettingsRepository.RECORDING_ELEVATION_SOURCE_GPS,
        SettingsRepository.RECORDING_ELEVATION_SOURCE_DEM,
        SettingsRepository.RECORDING_ELEVATION_SOURCE_AUTO,
    )
private val RECORDING_HEART_RATE_SOURCE_OPTIONS =
    listOf(
        SettingsRepository.RECORDING_HEART_RATE_SOURCE_WATCH,
        SettingsRepository.RECORDING_HEART_RATE_SOURCE_STRAP,
    )
private val RECORDING_SENSOR_SOURCE_OPTIONS =
    listOf(
        SettingsRepository.RECORDING_SENSOR_SOURCE_WATCH_GPS,
        SettingsRepository.RECORDING_SENSOR_SOURCE_POD,
    )
private val RECORDING_STEPS_SOURCE_OPTIONS =
    listOf(
        SettingsRepository.RECORDING_SENSOR_SOURCE_WATCH_GPS,
    )

@Composable
private fun RecordingSourceToggle(
    label: String,
    source: String,
    defaultSource: String,
    enabledLabel: String,
    onSourceChange: (String) -> Unit,
) {
    val enabled = source.isRecordingSourceEnabled()
    SettingsToggleChip(
        checked = enabled,
        onCheckedChanged = { checked ->
            onSourceChange(
                if (checked) {
                    defaultSource
                } else {
                    SettingsRepository.RECORDING_SOURCE_DISABLED
                },
            )
        },
        label = label,
        secondaryLabel = if (enabled) enabledLabel else "Off",
    )
}

private fun String.isRecordingSourceEnabled(): Boolean = this != SettingsRepository.RECORDING_SOURCE_DISABLED

private fun recordingElevationSourceLabel(source: String): String =
    when (source) {
        SettingsRepository.RECORDING_SOURCE_DISABLED -> "Off"
        SettingsRepository.RECORDING_ELEVATION_SOURCE_DEM -> "DEM"
        SettingsRepository.RECORDING_ELEVATION_SOURCE_AUTO -> "Auto"
        else -> "GPS altitude"
    }

private fun recordingHeartRateSourceLabel(source: String): String =
    when (source) {
        SettingsRepository.RECORDING_SOURCE_DISABLED -> "Off"
        SettingsRepository.RECORDING_HEART_RATE_SOURCE_STRAP -> "Strap"
        else -> "Watch"
    }

private fun recordingSensorSourceLabel(
    source: String,
    isBikeProfile: Boolean,
): String =
    when (source) {
        SettingsRepository.RECORDING_SOURCE_DISABLED -> "Off"
        SettingsRepository.RECORDING_SENSOR_SOURCE_POD -> if (isBikeProfile) "Bike sensor" else "Foot pod"
        else -> "Watch/GPS"
    }

private fun recordingMetricSourceSecondaryLabel(
    source: String,
    podLabel: String,
): String =
    when (source) {
        SettingsRepository.RECORDING_SOURCE_DISABLED -> "Off"
        SettingsRepository.RECORDING_SENSOR_SOURCE_POD -> podLabel
        else -> "Watch/GPS"
    }

private fun recordingHeartRateSourceSecondaryLabel(
    source: String,
    hasLinkedStrap: Boolean,
): String =
    when (source) {
        SettingsRepository.RECORDING_SOURCE_DISABLED -> "Off"
        SettingsRepository.RECORDING_HEART_RATE_SOURCE_STRAP ->
            if (hasLinkedStrap) {
                "Linked strap"
            } else {
                "Link strap first"
            }
        else -> "Watch sensor"
    }
