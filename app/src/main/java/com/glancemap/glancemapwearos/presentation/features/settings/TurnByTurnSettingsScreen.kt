package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.glancemap.glancemapwearos.data.repository.SettingsRepository

@Composable
fun TurnByTurnSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenGeneralSettings: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()
    val guidanceSource by viewModel.turnByTurnGuidanceSource.collectAsState()
    val useBrouterTiles by viewModel.turnByTurnUseBrouterTiles.collectAsState()
    var showGuidanceSourcePicker by remember { mutableStateOf(false) }

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            GeneralSettingsShortcutChip(onClick = onOpenGeneralSettings)
        }
        item {
            SettingsPickerChip(
                label = "Guidance source",
                secondaryLabel = guidanceSourceLabel(guidanceSource),
                onClick = { showGuidanceSourcePicker = true },
            )
        }
        item {
            SettingsToggleChip(
                checked = useBrouterTiles,
                onCheckedChanged = viewModel::setTurnByTurnUseBrouterTiles,
                label = "Use BRouter tiles",
                secondaryLabel =
                    if (useBrouterTiles) {
                        "Improve turns when routing data is available"
                    } else {
                        "Use GPX geometry only"
                    },
            )
        }
    }

    OptionPickerDialog(
        visible = showGuidanceSourcePicker,
        title = "Guidance source",
        selectedValue = guidanceSource,
        options =
            listOf(
                SettingsRepository.TURN_BY_TURN_SOURCE_AUTO to "Auto",
                SettingsRepository.TURN_BY_TURN_SOURCE_GPX_EXACT to "GPX exact",
                SettingsRepository.TURN_BY_TURN_SOURCE_BROUTER_ENHANCED to "BRouter enhanced",
            ),
        onDismiss = { showGuidanceSourcePicker = false },
        onSelect = viewModel::setTurnByTurnGuidanceSource,
    )
}

private fun guidanceSourceLabel(source: String): String =
    when (source) {
        SettingsRepository.TURN_BY_TURN_SOURCE_GPX_EXACT -> "GPX exact"
        SettingsRepository.TURN_BY_TURN_SOURCE_BROUTER_ENHANCED -> "BRouter enhanced"
        else -> "Auto"
    }
