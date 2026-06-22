package com.glancemap.glancemapwearos.presentation.features.settings

import com.glancemap.glancemapwearos.data.repository.SettingsRepository

internal val TurnByTurnGuidanceSourceOptions =
    listOf(
        SettingsRepository.TURN_BY_TURN_SOURCE_AUTO to "Auto",
        SettingsRepository.TURN_BY_TURN_SOURCE_GPX_EXACT to "GPX route",
        SettingsRepository.TURN_BY_TURN_SOURCE_BROUTER_ENHANCED to "BRouter enhanced",
    )

internal val TurnByTurnRouteStartOptions =
    listOf(
        SettingsRepository.TURN_BY_TURN_ROUTE_START_GO_TO_START to "Go to GPX start",
        SettingsRepository.TURN_BY_TURN_ROUTE_START_NEAREST_POINT to "Start nearest",
        SettingsRepository.TURN_BY_TURN_ROUTE_START_ASK to "Ask each time",
    )

internal val TurnByTurnReverseSuggestionOptions =
    listOf(
        SettingsRepository.TURN_BY_TURN_REVERSE_SUGGESTION_ASK to "When near the end",
        SettingsRepository.TURN_BY_TURN_REVERSE_SUGGESTION_NEVER to "Never",
    )

internal val TurnByTurnAlertsOptions =
    listOf(
        SettingsRepository.TURN_BY_TURN_TURN_ALERTS_IMPORTANT to "Important",
        SettingsRepository.TURN_BY_TURN_TURN_ALERTS_ALL to "All turns",
        SettingsRepository.TURN_BY_TURN_TURN_ALERTS_OFF to "Off",
    )

internal val TurnByTurnOffRouteThresholdOptions = listOf(20, 40, 60, 80, 100).map { it to "$it m" }
internal val TurnByTurnOffRouteRepeatOptions = listOf(30, 60, 120).map { it to "${it}s" }

internal fun guidanceSourceLabel(source: String): String =
    when (source) {
        SettingsRepository.TURN_BY_TURN_SOURCE_GPX_EXACT -> "GPX route"
        SettingsRepository.TURN_BY_TURN_SOURCE_BROUTER_ENHANCED -> "BRouter enhanced"
        else -> "Auto"
    }

internal fun routeStartBehaviorLabel(behavior: String): String =
    when (behavior) {
        SettingsRepository.TURN_BY_TURN_ROUTE_START_NEAREST_POINT -> "Start nearest"
        SettingsRepository.TURN_BY_TURN_ROUTE_START_ASK -> "Ask each time"
        else -> "Go to GPX start"
    }

internal fun reverseSuggestionLabel(mode: String): String =
    when (mode) {
        SettingsRepository.TURN_BY_TURN_REVERSE_SUGGESTION_NEVER -> "Never"
        else -> "When near the end"
    }

internal fun turnAlertsLabel(mode: String): String =
    when (mode) {
        SettingsRepository.TURN_BY_TURN_TURN_ALERTS_OFF -> "Off"
        SettingsRepository.TURN_BY_TURN_TURN_ALERTS_ALL -> "All turns"
        else -> "Important"
    }
