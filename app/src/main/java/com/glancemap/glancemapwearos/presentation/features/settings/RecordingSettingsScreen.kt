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
fun RecordingSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenGeneralSettings: () -> Unit,
    onOpenExternalSensors: () -> Unit,
    onOpenDashboardSettings: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()
    val sampleIntervalSeconds by viewModel.recordingSampleIntervalSeconds.collectAsState()
    val elevationSource by viewModel.recordingElevationSource.collectAsState()
    val showSavedGpxOnMap by viewModel.recordingShowSavedGpxOnMap.collectAsState()
    val startWithTurnByTurn by viewModel.recordingStartWithTurnByTurn.collectAsState()
    var showIntervalPicker by remember { mutableStateOf(false) }
    var showElevationSourcePicker by remember { mutableStateOf(false) }

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            GeneralSettingsShortcutChip(onClick = onOpenGeneralSettings)
        }
        item {
            RecordingGpsIntervalSetting(
                sampleIntervalSeconds = sampleIntervalSeconds,
                onClick = { showIntervalPicker = true },
            )
        }
        item {
            RecordingElevationSourceSetting(
                elevationSource = elevationSource,
                onClick = { showElevationSourcePicker = true },
            )
        }
        item {
            SettingsToggleChip(
                checked = showSavedGpxOnMap,
                onCheckedChanged = viewModel::setRecordingShowSavedGpxOnMap,
                label = "Show saved activity",
                secondaryLabel = if (showSavedGpxOnMap) "On map after save" else "Saved but hidden",
            )
        }
        item {
            SettingsToggleChip(
                checked = startWithTurnByTurn,
                onCheckedChanged = viewModel::setRecordingStartWithTurnByTurn,
                label = "REC with guidance",
                secondaryLabel = if (startWithTurnByTurn) "Starts with turn-by-turn" else "Manual start",
            )
        }
        item {
            RecordingExternalSensorsSetting(onClick = onOpenExternalSensors)
        }
        item {
            RecordingDashboardSettingsFolder(onClick = onOpenDashboardSettings)
        }
    }

    OptionPickerDialog(
        visible = showIntervalPicker,
        title = "Record GPS every",
        selectedValue = sampleIntervalSeconds,
        options = RECORDING_INTERVAL_OPTIONS_SECONDS.map { it to recordingIntervalLabel(it) },
        onDismiss = { showIntervalPicker = false },
        onSelect = viewModel::setRecordingSampleIntervalSeconds,
    )
    OptionPickerDialog(
        visible = showElevationSourcePicker,
        title = "Elevation source",
        selectedValue = elevationSource,
        options = RECORDING_ELEVATION_SOURCE_OPTIONS.map { it to recordingElevationSourceLabel(it) },
        onDismiss = { showElevationSourcePicker = false },
        onSelect = viewModel::setRecordingElevationSource,
    )
}

@Composable
private fun RecordingGpsIntervalSetting(
    sampleIntervalSeconds: Int,
    onClick: () -> Unit,
) {
    SettingsPickerChip(
        label = "Record GPS every",
        secondaryLabel = recordingIntervalLabel(sampleIntervalSeconds),
        onClick = onClick,
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
private fun RecordingExternalSensorsSetting(onClick: () -> Unit) {
    SettingsSectionChip(
        label = "External sensors",
        secondaryLabel = "Heart, steps, pods",
        onClick = onClick,
    )
}

@Composable
private fun RecordingDashboardSettingsFolder(
    onClick: () -> Unit,
) {
    SettingsSectionChip(
        label = "Dashboard",
        secondaryLabel = "Pages and metrics",
        onClick = onClick,
    )
}

private val RECORDING_INTERVAL_OPTIONS_SECONDS = listOf(1, 2, 5, 10, 15, 30, 60)
private val RECORDING_ELEVATION_SOURCE_OPTIONS =
    listOf(
        SettingsRepository.RECORDING_ELEVATION_SOURCE_GPS,
        SettingsRepository.RECORDING_ELEVATION_SOURCE_DEM,
        SettingsRepository.RECORDING_ELEVATION_SOURCE_AUTO,
    )
private fun recordingIntervalLabel(seconds: Int): String =
    if (seconds <= 1) {
        "1 second"
    } else {
        "$seconds seconds"
    }

private fun recordingElevationSourceLabel(source: String): String =
    when (source) {
        SettingsRepository.RECORDING_ELEVATION_SOURCE_DEM -> "DEM"
        SettingsRepository.RECORDING_ELEVATION_SOURCE_AUTO -> "Auto"
        else -> "GPS altitude"
    }
