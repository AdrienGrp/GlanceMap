package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.ui.WearHelpDialog

@Composable
fun RecordingSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenGeneralSettings: () -> Unit,
    onOpenSourceSettings: () -> Unit,
    onOpenExternalSensors: () -> Unit,
    onOpenDashboardSettings: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()
    val showSavedGpxOnMap by viewModel.recordingShowSavedGpxOnMap.collectAsState()
    val startWithTurnByTurn by viewModel.recordingStartWithTurnByTurn.collectAsState()
    val trackSmoothingMode by viewModel.recordingTrackSmoothingMode.collectAsState()
    var showInfoDialog by remember { mutableStateOf(false) }

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            SettingsInfoButton(
                contentDescription = "Recording info",
                onClick = { showInfoDialog = true },
            )
        }
        item {
            GeneralSettingsShortcutChip(
                onClick = onOpenGeneralSettings,
                applyTopPadding = false,
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
            SettingsOptionPickerRow(
                label = "Track smoothing",
                selectedValue = trackSmoothingMode,
                options = RECORDING_TRACK_SMOOTHING_OPTIONS.map { it to recordingTrackSmoothingLabel(it) },
                secondaryLabel = recordingTrackSmoothingLabel(trackSmoothingMode),
                onSelect = viewModel::setRecordingTrackSmoothingMode,
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

    WearHelpDialog(
        visible = showInfoDialog,
        title = "Recording",
        lines =
            listOf(
                "Tap REC to start recording.",
                "While recording, tap the time or REC popup to open the dashboard.",
                "Long press the time or REC popup for pause, stop and discard controls.",
                "Use the crown or swipe vertically to change dashboard pages.",
                "Long press a dashboard measure to replace it.",
                "Set REC GPS timing in GPS settings. Shorter timing gives a more detailed track but uses more battery.",
                "Adaptive track smoothing reduces GPS noise while preserving confirmed turns. " +
                    "Strong creates a cleaner track but may shorten tight corners or switchbacks.",
                "Pausing creates a break in the saved track, so stopped time is not joined by a straight line.",
            ),
        onDismiss = { showInfoDialog = false },
    )
}

private val RECORDING_TRACK_SMOOTHING_OPTIONS =
    listOf(
        SettingsRepository.RECORDING_TRACK_SMOOTHING_OFF,
        SettingsRepository.RECORDING_TRACK_SMOOTHING_ADAPTIVE,
        SettingsRepository.RECORDING_TRACK_SMOOTHING_STRONG,
    )

private fun recordingTrackSmoothingLabel(mode: String): String =
    when (mode) {
        SettingsRepository.RECORDING_TRACK_SMOOTHING_OFF -> "Off · quality checks only"
        SettingsRepository.RECORDING_TRACK_SMOOTHING_STRONG -> "Strong · cleaner track"
        else -> "Adaptive · recommended"
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
