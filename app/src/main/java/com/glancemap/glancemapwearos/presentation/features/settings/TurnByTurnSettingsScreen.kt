package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment

@Composable
fun TurnByTurnSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenGeneralSettings: () -> Unit,
    onOpenGuidanceSettings: () -> Unit,
    onOpenAlertsSettings: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()
    val hapticsEnabled by viewModel.turnByTurnHapticsEnabled.collectAsState()
    val voiceGuidanceEnabled by viewModel.turnByTurnVoiceGuidanceEnabled.collectAsState()
    val offRouteAlertsEnabled by viewModel.turnByTurnOffRouteAlertsEnabled.collectAsState()
    val guidanceGpsInAmbient by viewModel.turnByTurnGpsInAmbientMode.collectAsState()

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            GeneralSettingsShortcutChip(onClick = onOpenGeneralSettings)
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
                secondaryLabel = if (voiceGuidanceEnabled) "Speak turn cues" else "Voice cues off",
            )
        }
        item {
            SettingsToggleChip(
                checked = offRouteAlertsEnabled,
                onCheckedChanged = viewModel::setTurnByTurnOffRouteAlertsEnabled,
                label = "Off-route alerts",
                secondaryLabel =
                    if (offRouteAlertsEnabled) {
                        "Warn when leaving the GPX"
                    } else {
                        "Show off-route status only"
                    },
            )
        }
        item {
            SettingsToggleChip(
                checked = guidanceGpsInAmbient,
                onCheckedChanged = viewModel::setTurnByTurnGpsInAmbientMode,
                label = "Screen-off guidance",
                secondaryLabel =
                    if (guidanceGpsInAmbient) {
                        "Reliable alerts; uses more battery"
                    } else {
                        "Pause dedicated guidance GPS"
                    },
            )
        }
        item {
            SettingsSectionChip(
                label = "Advanced guidance",
                secondaryLabel = "Route source, start and guide back",
                onClick = onOpenGuidanceSettings,
            )
        }
        item {
            SettingsSectionChip(
                label = "Advanced alerts",
                secondaryLabel = "Turn timing and off-route tuning",
                onClick = onOpenAlertsSettings,
            )
        }
    }
}
