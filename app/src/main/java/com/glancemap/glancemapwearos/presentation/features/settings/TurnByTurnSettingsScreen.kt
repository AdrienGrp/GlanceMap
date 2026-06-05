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
    val hapticsEnabled by viewModel.turnByTurnHapticsEnabled.collectAsState()
    val turnAlertsMode by viewModel.turnByTurnTurnAlertsMode.collectAsState()
    val offRouteAlertsEnabled by viewModel.turnByTurnOffRouteAlertsEnabled.collectAsState()
    val offRouteThresholdMeters by viewModel.turnByTurnOffRouteAlertThresholdMeters.collectAsState()
    val offRouteRepeatSeconds by viewModel.turnByTurnOffRouteRepeatSeconds.collectAsState()
    val guidanceGpsInAmbient by viewModel.turnByTurnGpsInAmbientMode.collectAsState()
    val routeStartBehavior by viewModel.turnByTurnRouteStartBehavior.collectAsState()
    val reverseSuggestionMode by viewModel.turnByTurnReverseSuggestionMode.collectAsState()
    var showGuidanceSourcePicker by remember { mutableStateOf(false) }
    var showRouteStartPicker by remember { mutableStateOf(false) }
    var showReverseSuggestionPicker by remember { mutableStateOf(false) }
    var showTurnAlertsPicker by remember { mutableStateOf(false) }
    var showOffRouteThresholdPicker by remember { mutableStateOf(false) }
    var showOffRouteRepeatPicker by remember { mutableStateOf(false) }

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
            SettingsPickerChip(
                label = "Route start behavior",
                secondaryLabel = routeStartBehaviorLabel(routeStartBehavior),
                onClick = { showRouteStartPicker = true },
            )
        }
        item {
            SettingsPickerChip(
                label = "Reverse suggestion",
                secondaryLabel = reverseSuggestionLabel(reverseSuggestionMode),
                onClick = { showReverseSuggestionPicker = true },
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
            SettingsPickerChip(
                label = "Turn alerts",
                secondaryLabel = turnAlertsLabel(turnAlertsMode),
                onClick = { showTurnAlertsPicker = true },
            )
        }
        item {
            SettingsToggleChip(
                checked = offRouteAlertsEnabled,
                onCheckedChanged = viewModel::setTurnByTurnOffRouteAlertsEnabled,
                label = "Off-route alerts",
                secondaryLabel = if (offRouteAlertsEnabled) "Warn when leaving the GPX" else "Show only on screen",
            )
        }
        item {
            SettingsPickerChip(
                label = "Off-route distance",
                secondaryLabel = "$offRouteThresholdMeters m",
                onClick = { showOffRouteThresholdPicker = true },
            )
        }
        item {
            SettingsPickerChip(
                label = "Repeat off-route",
                secondaryLabel = "${offRouteRepeatSeconds}s",
                onClick = { showOffRouteRepeatPicker = true },
            )
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

    OptionPickerDialog(
        visible = showGuidanceSourcePicker,
        title = "Guidance source",
        selectedValue = guidanceSource,
        options =
            listOf(
                SettingsRepository.TURN_BY_TURN_SOURCE_AUTO to "Auto",
                SettingsRepository.TURN_BY_TURN_SOURCE_GPX_EXACT to "GPX route",
                SettingsRepository.TURN_BY_TURN_SOURCE_BROUTER_ENHANCED to "BRouter enhanced",
            ),
        onDismiss = { showGuidanceSourcePicker = false },
        onSelect = viewModel::setTurnByTurnGuidanceSource,
    )
    OptionPickerDialog(
        visible = showRouteStartPicker,
        title = "Route start behavior",
        selectedValue = routeStartBehavior,
        options =
            listOf(
                SettingsRepository.TURN_BY_TURN_ROUTE_START_GO_TO_START to "Go to GPX start",
                SettingsRepository.TURN_BY_TURN_ROUTE_START_NEAREST_POINT to "Start nearest",
                SettingsRepository.TURN_BY_TURN_ROUTE_START_ASK to "Ask each time",
            ),
        onDismiss = { showRouteStartPicker = false },
        onSelect = viewModel::setTurnByTurnRouteStartBehavior,
    )
    OptionPickerDialog(
        visible = showReverseSuggestionPicker,
        title = "Reverse suggestion",
        selectedValue = reverseSuggestionMode,
        options =
            listOf(
                SettingsRepository.TURN_BY_TURN_REVERSE_SUGGESTION_ASK to "Ask",
                SettingsRepository.TURN_BY_TURN_REVERSE_SUGGESTION_NEVER to "Never",
            ),
        onDismiss = { showReverseSuggestionPicker = false },
        onSelect = viewModel::setTurnByTurnReverseSuggestionMode,
    )
    OptionPickerDialog(
        visible = showTurnAlertsPicker,
        title = "Turn alerts",
        selectedValue = turnAlertsMode,
        options =
            listOf(
                SettingsRepository.TURN_BY_TURN_TURN_ALERTS_IMPORTANT to "Important",
                SettingsRepository.TURN_BY_TURN_TURN_ALERTS_ALL to "All turns",
                SettingsRepository.TURN_BY_TURN_TURN_ALERTS_OFF to "Off",
            ),
        onDismiss = { showTurnAlertsPicker = false },
        onSelect = viewModel::setTurnByTurnTurnAlertsMode,
    )
    OptionPickerDialog(
        visible = showOffRouteThresholdPicker,
        title = "Off-route distance",
        selectedValue = offRouteThresholdMeters,
        options = listOf(20, 40, 60, 80, 100).map { it to "$it m" },
        onDismiss = { showOffRouteThresholdPicker = false },
        onSelect = viewModel::setTurnByTurnOffRouteAlertThresholdMeters,
    )
    OptionPickerDialog(
        visible = showOffRouteRepeatPicker,
        title = "Repeat off-route",
        selectedValue = offRouteRepeatSeconds,
        options = listOf(30, 60, 120).map { it to "${it}s" },
        onDismiss = { showOffRouteRepeatPicker = false },
        onSelect = viewModel::setTurnByTurnOffRouteRepeatSeconds,
    )
}

private fun guidanceSourceLabel(source: String): String =
    when (source) {
        SettingsRepository.TURN_BY_TURN_SOURCE_GPX_EXACT -> "GPX route"
        SettingsRepository.TURN_BY_TURN_SOURCE_BROUTER_ENHANCED -> "BRouter enhanced"
        else -> "Auto"
    }

private fun turnAlertsLabel(mode: String): String =
    when (mode) {
        SettingsRepository.TURN_BY_TURN_TURN_ALERTS_OFF -> "Off"
        SettingsRepository.TURN_BY_TURN_TURN_ALERTS_ALL -> "All turns"
        else -> "Important"
    }

private fun routeStartBehaviorLabel(behavior: String): String =
    when (behavior) {
        SettingsRepository.TURN_BY_TURN_ROUTE_START_NEAREST_POINT -> "Start nearest"
        SettingsRepository.TURN_BY_TURN_ROUTE_START_ASK -> "Ask each time"
        else -> "Go to GPX start"
    }

private fun reverseSuggestionLabel(mode: String): String =
    when (mode) {
        SettingsRepository.TURN_BY_TURN_REVERSE_SUGGESTION_NEVER -> "Never"
        else -> "Ask"
    }
