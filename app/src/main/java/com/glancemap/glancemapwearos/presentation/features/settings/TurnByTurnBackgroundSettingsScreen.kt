package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment

@Composable
fun TurnByTurnBackgroundSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenTurnByTurnSettings: () -> Unit,
) {
    val guidanceGpsInAmbient by viewModel.turnByTurnGpsInAmbientMode.collectAsState()

    WearSettingsListScreen(horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            TurnByTurnSettingsShortcutChip(onClick = onOpenTurnByTurnSettings)
        }
        item {
            SettingsToggleChip(
                checked = guidanceGpsInAmbient,
                onCheckedChanged = viewModel::setTurnByTurnGpsInAmbientMode,
                label = "Guidance GPS ambient",
                secondaryLabel =
                    if (guidanceGpsInAmbient) {
                        "Keep GPS for alerts while screen is off"
                    } else {
                        "Use normal GPS ambient setting"
                    },
            )
        }
    }
}
