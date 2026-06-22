package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.glancemap.glancemapwearos.presentation.ui.WearHelpDialog

@Composable
fun TurnByTurnSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenGeneralSettings: () -> Unit,
    onOpenGuidanceSettings: () -> Unit,
    onOpenAlertsSettings: () -> Unit,
    onOpenDashboardSettings: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()
    val hapticsEnabled by viewModel.turnByTurnHapticsEnabled.collectAsState()
    val voiceGuidanceEnabled by viewModel.turnByTurnVoiceGuidanceEnabled.collectAsState()
    val offRouteAlertsEnabled by viewModel.turnByTurnOffRouteAlertsEnabled.collectAsState()
    val guidanceGpsInAmbient by viewModel.turnByTurnGpsInAmbientMode.collectAsState()
    var showInfoDialog by remember { mutableStateOf(false) }

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            SettingsInfoButton(
                contentDescription = "Turn-by-turn info",
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
                label = "Dashboard",
                secondaryLabel = "Route metrics and pages",
                onClick = onOpenDashboardSettings,
            )
        }
        item {
            SettingsSectionChip(
                label = "Advanced guidance",
                secondaryLabel = "Start, reverse and route back",
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
    WearHelpDialog(
        visible = showInfoDialog,
        title = "Turn-by-turn",
        lines =
            listOf(
                "Tap the small guidance popup to open the full turn view.",
                "Long press the popup to pause or stop guidance.",
                "Use the crown or swipe vertically to move between guidance, route metrics and REC pages.",
                "Tap the speaker icon in the full turn view to switch voice guidance on or off.",
                "Amber guidance means you are off route. The distance shows how far you are from the GPX.",
                "Screen-off guidance keeps dedicated GPS updates active for reliable alerts, but uses more battery.",
                "Turn instructions depend on the GPX geometry or routing hints available in the file.",
            ),
        onDismiss = { showInfoDialog = false },
    )
}
