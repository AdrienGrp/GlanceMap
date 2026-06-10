package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment

@Composable
fun RecordingExternalSensorsScreen(onOpenRecordingSettings: () -> Unit) {
    val listTokens = rememberSettingsListTokens()

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            SettingsSectionChip(
                label = "Recording settings",
                secondaryLabel = "Back to REC settings",
                onClick = onOpenRecordingSettings,
            )
        }
        item {
            SettingsSectionChip(
                label = "Bluetooth devices",
                secondaryLabel = "Not linked",
                iconImageVector = Icons.Default.Bluetooth,
                onClick = {},
            )
        }
        item {
            SettingsSectionChip(
                label = "Heart rate",
                secondaryLabel = "Watch sensor",
                iconImageVector = Icons.Default.Favorite,
                onClick = {},
            )
        }
        item {
            SettingsSectionChip(
                label = "Pods",
                secondaryLabel = "Cadence, power",
                iconImageVector = Icons.Default.Sensors,
                onClick = {},
            )
        }
    }
}
