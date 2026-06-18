package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment

@Composable
fun TurnByTurnSettingsScreen(
    onOpenGeneralSettings: () -> Unit,
    onOpenGuidanceSettings: () -> Unit,
    onOpenAlertsSettings: () -> Unit,
    onOpenFeedbackSettings: () -> Unit,
    onOpenBackgroundSettings: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            GeneralSettingsShortcutChip(onClick = onOpenGeneralSettings)
        }
        item {
            SettingsSectionChip(
                label = "Guidance",
                secondaryLabel = "Route source and start",
                onClick = onOpenGuidanceSettings,
            )
        }
        item {
            SettingsSectionChip(
                label = "Alerts",
                secondaryLabel = "Turns and off-route",
                onClick = onOpenAlertsSettings,
            )
        }
        item {
            SettingsSectionChip(
                label = "Feedback",
                secondaryLabel = "Haptics and voice",
                onClick = onOpenFeedbackSettings,
            )
        }
        item {
            SettingsSectionChip(
                label = "Background",
                secondaryLabel = "Screen-off GPS",
                onClick = onOpenBackgroundSettings,
            )
        }
    }
}
