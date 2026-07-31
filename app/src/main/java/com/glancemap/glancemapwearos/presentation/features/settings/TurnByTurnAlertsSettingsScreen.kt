package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment

@Composable
fun TurnByTurnAlertsSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenTurnByTurnSettings: () -> Unit,
) {
    val turnAlertsMode by viewModel.turnByTurnTurnAlertsMode.collectAsState()
    val offRouteThresholdMeters by viewModel.turnByTurnOffRouteAlertThresholdMeters.collectAsState()
    val offRouteRepeatSeconds by viewModel.turnByTurnOffRouteRepeatSeconds.collectAsState()

    WearSettingsListScreen(horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            TurnByTurnSettingsShortcutChip(onClick = onOpenTurnByTurnSettings)
        }
        item {
            SettingsOptionPickerRow(
                label = "Turn alerts",
                selectedValue = turnAlertsMode,
                options = TurnByTurnAlertsOptions,
                secondaryLabel = turnAlertsLabel(turnAlertsMode),
                onSelect = viewModel::setTurnByTurnTurnAlertsMode,
            )
        }
        item {
            SettingsOptionPickerRow(
                label = "Off-route distance",
                selectedValue = offRouteThresholdMeters,
                options = TurnByTurnOffRouteThresholdOptions,
                secondaryLabel = "$offRouteThresholdMeters m",
                onSelect = viewModel::setTurnByTurnOffRouteAlertThresholdMeters,
            )
        }
        item {
            SettingsOptionPickerRow(
                label = "Repeat off-route",
                selectedValue = offRouteRepeatSeconds,
                options = TurnByTurnOffRouteRepeatOptions,
                secondaryLabel = "${offRouteRepeatSeconds}s",
                onSelect = viewModel::setTurnByTurnOffRouteRepeatSeconds,
            )
        }
    }
}
