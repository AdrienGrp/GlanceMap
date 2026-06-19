package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.ui.WearActionDialog

@Composable
fun RecordingSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenGeneralSettings: () -> Unit,
    onOpenSourceSettings: () -> Unit,
    onOpenExternalSensors: () -> Unit,
    onOpenDashboardSettings: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()
    val sampleIntervalSeconds by viewModel.recordingSampleIntervalSeconds.collectAsState()
    val showSavedGpxOnMap by viewModel.recordingShowSavedGpxOnMap.collectAsState()
    val startWithTurnByTurn by viewModel.recordingStartWithTurnByTurn.collectAsState()
    var showGpsDisabledWarning by remember { mutableStateOf(false) }
    val intervalOptions = RECORDING_INTERVAL_OPTIONS_SECONDS.map { it to recordingIntervalLabel(it) }

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            GeneralSettingsShortcutChip(onClick = onOpenGeneralSettings)
        }
        item {
            SettingsOptionPickerRow(
                label = "GPS Frequency",
                selectedValue = sampleIntervalSeconds,
                options = intervalOptions,
                secondaryLabel = recordingIntervalLabel(sampleIntervalSeconds),
                onSelect = { seconds ->
                    viewModel.setRecordingSampleIntervalSeconds(seconds)
                    if (seconds == SettingsRepository.RECORDING_SAMPLE_INTERVAL_DISABLED_SECONDS) {
                        showGpsDisabledWarning = true
                    }
                },
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
            SettingsToggleChip(
                checked = showSavedGpxOnMap,
                onCheckedChanged = viewModel::setRecordingShowSavedGpxOnMap,
                label = "Show saved activity",
                secondaryLabel = if (showSavedGpxOnMap) "On map after save" else "Saved but hidden",
            )
        }
        item {
            RecordingDashboardSettingsFolder(onClick = onOpenDashboardSettings)
        }
        item {
            RecordingSourceSettingsFolder(onClick = onOpenSourceSettings)
        }
        item {
            RecordingExternalSensorsSetting(onClick = onOpenExternalSensors)
        }
    }

    WearActionDialog(
        visible = showGpsDisabledWarning,
        title = "GPS deactivated",
        message = "REC will not record GPS points. Distance, speed, elevation, track, and calories may be unavailable unless another source provides them.",
        confirmText = "OK",
        onConfirm = { showGpsDisabledWarning = false },
        onDismissRequest = { showGpsDisabledWarning = false },
    )
}

@Composable
private fun RecordingSourceSettingsFolder(
    onClick: () -> Unit,
) {
    SettingsSectionChip(
        label = "Source",
        secondaryLabel = "GPS, DEM, sensors",
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

private val RECORDING_INTERVAL_OPTIONS_SECONDS =
    listOf(SettingsRepository.RECORDING_SAMPLE_INTERVAL_DISABLED_SECONDS, 1, 2, 5, 10, 15, 30, 60)
private fun recordingIntervalLabel(seconds: Int): String =
    when {
        seconds == SettingsRepository.RECORDING_SAMPLE_INTERVAL_DISABLED_SECONDS -> "Deactivated"
        seconds <= 1 -> "1 second"
        else -> "$seconds seconds"
    }
