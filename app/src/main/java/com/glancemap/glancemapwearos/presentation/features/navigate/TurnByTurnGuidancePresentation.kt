package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.GuidanceMode
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.RouteInstructionCommand
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.TurnByTurnGuidanceState

internal fun guidanceShowsCurrentStraight(state: TurnByTurnGuidanceState): Boolean {
    if (state.mode != GuidanceMode.FOLLOW_ROUTE) return false
    val command = state.nextInstruction?.command ?: return false
    val distanceMeters = state.distanceToInstructionMeters ?: return false
    return command != RouteInstructionCommand.CONTINUE &&
        command != RouteInstructionCommand.FINISH &&
        distanceMeters > MANEUVER_PREPARATION_DISTANCE_METERS
}

internal fun guidanceInstructionPrimaryText(state: TurnByTurnGuidanceState): String =
    if (guidanceShowsCurrentStraight(state)) {
        "Continue straight"
    } else {
        state.nextInstruction?.message ?: "Continue"
    }

internal fun guidanceInstructionDistanceText(
    state: TurnByTurnGuidanceState,
    isMetric: Boolean,
): String? =
    state.distanceToInstructionMeters?.let { distanceMeters ->
        if (distanceMeters < MANEUVER_NOW_DISTANCE_METERS) {
            "Now"
        } else {
            formatLiveDistanceLabel(distanceMeters, isMetric)
        }
    }

internal fun guidanceCompactInstructionText(
    state: TurnByTurnGuidanceState,
    isMetric: Boolean,
): String {
    val distance = guidanceInstructionDistanceText(state, isMetric)
    return if (guidanceShowsCurrentStraight(state)) {
        val current = listOfNotNull("Continue", distance).joinToString(" ")
        val next = state.nextInstruction?.message?.lowercase()
        if (next == null) current else "$current\nThen $next"
    } else {
        listOfNotNull(guidanceInstructionPrimaryText(state), distance).joinToString("\n")
    }
}

internal const val MANEUVER_PREPARATION_DISTANCE_METERS = 60.0
private const val MANEUVER_NOW_DISTANCE_METERS = 5.0
