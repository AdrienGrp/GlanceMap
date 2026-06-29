package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RecordingBikeSensorSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenRecordingSourceSettings: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()
    val wheelCircumferenceMeters by viewModel.cyclingWheelCircumferenceMeters.collectAsState()
    val linkedRunPodAddress by viewModel.recordingExternalRunPodAddress.collectAsState()
    val linkedRunPodName by viewModel.recordingExternalRunPodName.collectAsState()
    val selectedWheelCircumferenceMm = (wheelCircumferenceMeters * 1000f).roundToInt()

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            AdaptiveSettingsShortcutChip(
                standardLabel = "Recording Sources",
                compactLabel = "Sources",
                standardSecondaryLabel = "Back to sensor sources",
                compactSecondaryLabel = "Sensor sources",
                iconImageVector = Icons.Filled.Folder,
                applyTopPadding = true,
                compactRoundWidthFraction = 0.82f,
                onClick = onOpenRecordingSourceSettings,
            )
        }
        item {
            SettingsOptionPickerRow(
                label = "Wheel size",
                dialogTitle = "Wheel circumference",
                selectedValue = selectedWheelCircumferenceMm,
                options = CYCLING_WHEEL_CIRCUMFERENCE_OPTIONS,
                secondaryLabel = formatWheelCircumference(wheelCircumferenceMeters),
                iconImageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                onSelect = { wheelMm ->
                    viewModel.setCyclingWheelCircumferenceMeters(wheelMm / 1000f)
                },
            )
        }
        item {
            SettingsPickerChip(
                label = "Linked bike sensor",
                secondaryLabel = linkedBikeSensorLabel(linkedRunPodName, linkedRunPodAddress),
                iconImageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                onClick = onOpenRecordingSourceSettings,
            )
        }
    }
}

private val CYCLING_WHEEL_CIRCUMFERENCE_OPTIONS =
    listOf(
        1935 to "26×1.95 · 1.935 m",
        2070 to "700×23 · 2.070 m",
        (SettingsRepository.DEFAULT_CYCLING_WHEEL_CIRCUMFERENCE_METERS * 1000f).roundToInt() to "700×25 · 2.105 m",
        2136 to "700×28 · 2.136 m",
        2155 to "700×32 · 2.155 m",
        2230 to "29×2.2 · 2.230 m",
    )

private fun formatWheelCircumference(meters: Float): String =
    "${(meters * 1000f).roundToInt()} mm · ${String.format(Locale.US, "%.3f", meters)} m"

private fun linkedBikeSensorLabel(
    name: String?,
    address: String?,
): String {
    val cleanAddress = address?.takeIf(String::isNotBlank) ?: return "No bike sensor linked"
    val cleanName = name?.takeIf(String::isNotBlank) ?: "Bike sensor"
    return "$cleanName · ${cleanAddress.takeLast(5)}"
}
