package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment

@Composable
fun TurnByTurnGuidanceSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenTurnByTurnSettings: () -> Unit,
) {
    val routeStartBehavior by viewModel.turnByTurnRouteStartBehavior.collectAsState()
    val reverseSuggestionMode by viewModel.turnByTurnReverseSuggestionMode.collectAsState()

    WearSettingsListScreen(horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            TurnByTurnSettingsShortcutChip(onClick = onOpenTurnByTurnSettings)
        }
        item {
            SettingsOptionPickerRow(
                label = "Route start behavior",
                selectedValue = routeStartBehavior,
                options = TurnByTurnRouteStartOptions,
                secondaryLabel = routeStartBehaviorLabel(routeStartBehavior),
                onSelect = viewModel::setTurnByTurnRouteStartBehavior,
            )
        }
        item {
            SettingsOptionPickerRow(
                label = "Suggest reverse route",
                selectedValue = reverseSuggestionMode,
                options = TurnByTurnReverseSuggestionOptions,
                secondaryLabel = reverseSuggestionLabel(reverseSuggestionMode),
                onSelect = viewModel::setTurnByTurnReverseSuggestionMode,
            )
        }
    }
}
