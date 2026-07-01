package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.material.ToggleChip
import com.google.android.horologist.compose.material.ToggleChipToggleControl
import com.glancemap.glancemapwearos.data.repository.SettingsRepository

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun GpsSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenGeneralSettings: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()

    val isWatchGpsOnly by viewModel.watchGpsOnly.collectAsState()
    val recordingSampleIntervalSeconds by viewModel.recordingSampleIntervalSeconds.collectAsState()
    val recordingScreenOffSampleIntervalSeconds by viewModel.recordingScreenOffSampleIntervalSeconds.collectAsState()
    val recordingAutoPauseMode by viewModel.recordingAutoPauseMode.collectAsState()
    val turnByTurnGpsIntervalSeconds by viewModel.turnByTurnGpsIntervalSeconds.collectAsState()
    val turnByTurnScreenOffGpsIntervalSeconds by viewModel.turnByTurnScreenOffGpsIntervalSeconds.collectAsState()
    val gpsDebugTelemetry by viewModel.gpsDebugTelemetry.collectAsState()
    val gpsPassiveLocationExperiment by viewModel.gpsPassiveLocationExperiment.collectAsState()
    val recordingScreenOffDisabled =
        recordingScreenOffSampleIntervalSeconds == SettingsRepository.RECORDING_SAMPLE_INTERVAL_DISABLED_SECONDS
    val turnByTurnScreenOffDisabled =
        turnByTurnScreenOffGpsIntervalSeconds == SettingsRepository.RECORDING_SAMPLE_INTERVAL_DISABLED_SECONDS

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            GeneralSettingsShortcutChip(onClick = onOpenGeneralSettings)
        }

        item {
            ToggleChip(
                checked = isWatchGpsOnly,
                onCheckedChanged = { viewModel.setWatchGpsOnly(it) },
                label = "GPS Source",
                secondaryLabel =
                    when {
                        isWatchGpsOnly -> "Watch Only (more ⚡)"
                        else -> "Auto (Watch + Phone)"
                    },
                toggleControl = ToggleChipToggleControl.Switch,
            )
        }

        item {
            GpsIntervalSummary(
                primaryText = "Basic GPS uses 3s when the screen is on.",
                secondaryText = "REC and TBT can use their own GPS timing.",
            )
        }

        item {
            GpsSectionTitle(text = "REC")
        }
        item {
            SettingsOptionPickerRow(
                label = "Screen on",
                selectedValue = recordingSampleIntervalSeconds,
                options = REC_SCREEN_ON_OPTIONS_SECONDS.map { it to gpsIntervalLabel(it) },
                secondaryLabel = gpsIntervalLabel(recordingSampleIntervalSeconds),
                onSelect = viewModel::setRecordingSampleIntervalSeconds,
            )
        }
        item {
            SettingsOptionPickerRow(
                label = "Screen off",
                selectedValue = recordingScreenOffSampleIntervalSeconds,
                options = SCREEN_OFF_OPTIONS_SECONDS.map { it to gpsScreenOffIntervalLabel(it) },
                secondaryLabel =
                    gpsScreenOffIntervalLabel(
                        seconds = recordingScreenOffSampleIntervalSeconds,
                        screenOnSeconds = recordingSampleIntervalSeconds,
                    ),
                onSelect = viewModel::setRecordingScreenOffSampleIntervalSeconds,
            )
        }
        if (recordingScreenOffDisabled) {
            item {
                GpsWarningText(
                    text = "Screen-off REC GPS is off. Saved recordings can have gaps, and GPS may take a moment to catch up when you look at the watch.",
                )
            }
        }
        item {
            SettingsOptionPickerRow(
                label = "Auto-pause",
                selectedValue = recordingAutoPauseMode,
                options = AUTO_PAUSE_OPTIONS.map { it to autoPauseModeLabel(it) },
                secondaryLabel = autoPauseModeLabel(recordingAutoPauseMode),
                onSelect = viewModel::setRecordingAutoPauseMode,
            )
        }

        item {
            GpsSectionTitle(text = "TBT")
        }
        item {
            SettingsOptionPickerRow(
                label = "Screen on",
                selectedValue = turnByTurnGpsIntervalSeconds,
                options = SCREEN_ON_OPTIONS_SECONDS.map { it to gpsIntervalLabel(it) },
                secondaryLabel = gpsIntervalLabel(turnByTurnGpsIntervalSeconds),
                onSelect = viewModel::setTurnByTurnGpsIntervalSeconds,
            )
        }
        item {
            SettingsOptionPickerRow(
                label = "Screen off",
                selectedValue = turnByTurnScreenOffGpsIntervalSeconds,
                options = SCREEN_OFF_OPTIONS_SECONDS.map { it to gpsScreenOffIntervalLabel(it) },
                secondaryLabel =
                    gpsScreenOffIntervalLabel(
                        seconds = turnByTurnScreenOffGpsIntervalSeconds,
                        screenOnSeconds = turnByTurnGpsIntervalSeconds,
                    ),
                onSelect = viewModel::setTurnByTurnScreenOffGpsIntervalSeconds,
            )
        }
        if (turnByTurnScreenOffDisabled) {
            item {
                GpsWarningText(
                    text = "Screen-off TBT GPS is off. Alerts will not work while the screen is off, and GPS may take a moment to catch up when you look at the watch.",
                )
            }
        }

        if (gpsDebugTelemetry) {
            item {
                ToggleChip(
                    checked = gpsPassiveLocationExperiment,
                    onCheckedChanged = { viewModel.setGpsPassiveLocationExperiment(it) },
                    label = "Use GPS from other apps",
                    secondaryLabel =
                        if (gpsPassiveLocationExperiment) {
                            "On during capture"
                        } else {
                            "Off during capture"
                        },
                    toggleControl = ToggleChipToggleControl.Switch,
                )
            }
        }
    }
}

@Composable
private fun GpsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun GpsWarningText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Suppress("FunctionName")
@Composable
private fun GpsIntervalSummary(
    primaryText: String,
    secondaryText: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = primaryText,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
        Text(
            text = secondaryText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

private val SCREEN_ON_OPTIONS_SECONDS = listOf(1, 2, 3, 5, 10, 15, 30, 60)

private val REC_SCREEN_ON_OPTIONS_SECONDS =
    listOf(SettingsRepository.RECORDING_SAMPLE_INTERVAL_DISABLED_SECONDS, 1, 2, 3, 5, 10, 15, 30, 60)

private val SCREEN_OFF_OPTIONS_SECONDS =
    listOf(
        SettingsRepository.GPS_INTERVAL_SAME_AS_SCREEN_ON_SECONDS,
        SettingsRepository.RECORDING_SAMPLE_INTERVAL_DISABLED_SECONDS,
        1,
        2,
        3,
        5,
        10,
        15,
        30,
        60,
    )

private val AUTO_PAUSE_OPTIONS =
    listOf(
        SettingsRepository.RECORDING_AUTO_PAUSE_OFF,
        SettingsRepository.RECORDING_AUTO_PAUSE_BIKE_ONLY,
        SettingsRepository.RECORDING_AUTO_PAUSE_ALWAYS,
    )

private fun autoPauseModeLabel(mode: String): String =
    when (mode) {
        SettingsRepository.RECORDING_AUTO_PAUSE_BIKE_ONLY -> "Bike only"
        SettingsRepository.RECORDING_AUTO_PAUSE_ALWAYS -> "Always"
        else -> "Off"
    }

private fun gpsScreenOffIntervalLabel(
    seconds: Int,
    screenOnSeconds: Int? = null,
): String =
    when (seconds) {
        SettingsRepository.GPS_INTERVAL_SAME_AS_SCREEN_ON_SECONDS ->
            screenOnSeconds?.let { "Same (${gpsIntervalLabel(it)})" } ?: "Same as screen on"

        else -> gpsIntervalLabel(seconds)
    }

private fun gpsIntervalLabel(seconds: Int): String =
    when {
        seconds == SettingsRepository.RECORDING_SAMPLE_INTERVAL_DISABLED_SECONDS -> "Off"
        seconds <= 1 -> "1 second"
        else -> "$seconds seconds"
    }
