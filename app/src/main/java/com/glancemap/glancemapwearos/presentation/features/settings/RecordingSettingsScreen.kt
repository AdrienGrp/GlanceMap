package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment

@Composable
fun RecordingSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenGeneralSettings: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()
    val sampleIntervalSeconds by viewModel.recordingSampleIntervalSeconds.collectAsState()
    var showIntervalPicker by remember { mutableStateOf(false) }

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            GeneralSettingsShortcutChip(onClick = onOpenGeneralSettings)
        }
        item {
            SettingsPickerChip(
                label = "Record GPS every",
                secondaryLabel = recordingIntervalLabel(sampleIntervalSeconds),
                onClick = { showIntervalPicker = true },
            )
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
}

private val RECORDING_INTERVAL_OPTIONS_SECONDS = listOf(1, 2, 5, 10, 15, 30, 60)

private fun recordingIntervalLabel(seconds: Int): String =
    if (seconds <= 1) {
        "1 second"
    } else {
        "$seconds seconds"
    }
