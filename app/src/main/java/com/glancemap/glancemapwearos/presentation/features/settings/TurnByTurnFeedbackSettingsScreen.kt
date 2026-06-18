package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment

@Composable
fun TurnByTurnFeedbackSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenTurnByTurnSettings: () -> Unit,
) {
    val hapticsEnabled by viewModel.turnByTurnHapticsEnabled.collectAsState()
    val voiceGuidanceEnabled by viewModel.turnByTurnVoiceGuidanceEnabled.collectAsState()

    WearSettingsListScreen(horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            TurnByTurnSettingsShortcutChip(onClick = onOpenTurnByTurnSettings)
        }
        item {
            SettingsToggleChip(
                checked = hapticsEnabled,
                onCheckedChanged = viewModel::setTurnByTurnHapticsEnabled,
                label = "Guidance haptics",
                secondaryLabel = if (hapticsEnabled) "Vibrate for guidance cues" else "Silent guidance",
            )
        }
        item {
            SettingsToggleChip(
                checked = voiceGuidanceEnabled,
                onCheckedChanged = viewModel::setTurnByTurnVoiceGuidanceEnabled,
                label = "Voice guidance",
                secondaryLabel = if (voiceGuidanceEnabled) "Speak turn cues" else "Off by default",
            )
        }
    }
}
