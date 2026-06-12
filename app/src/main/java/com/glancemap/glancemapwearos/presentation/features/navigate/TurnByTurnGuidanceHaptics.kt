package com.glancemap.glancemapwearos.presentation.features.navigate

import android.content.Context
import android.os.VibrationEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.GuidanceMode
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.RouteInstructionCommand
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.TurnByTurnGuidanceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun TurnByTurnGuidanceHapticEffect(
    context: Context,
    state: TurnByTurnGuidanceState,
    currentSpeedMps: Float?,
    hapticsEnabled: Boolean,
    turnAlertsMode: String,
    offRouteAlertsEnabled: Boolean,
    offRouteRepeatSeconds: Int,
) {
    val vibrator = remember { vibratorFrom(context) }
    var alertedInstructionKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.active, state.trackTitle) {
        if (!state.active) {
            alertedInstructionKey = null
        }
    }

    LaunchedEffect(
        state.active,
        state.mode,
        state.nextInstruction?.trackPointIndex,
        state.distanceToInstructionMeters,
        currentSpeedMps,
        hapticsEnabled,
        turnAlertsMode,
    ) {
        val instruction = state.nextInstruction ?: return@LaunchedEffect
        if (!hapticsEnabled || !shouldAlertForTurn(turnAlertsMode, instruction.command)) return@LaunchedEffect
        if (state.mode != GuidanceMode.FOLLOW_ROUTE) return@LaunchedEffect
        val distanceMeters = state.distanceToInstructionMeters ?: return@LaunchedEffect
        val alertDistanceMeters = turnAlertDistanceMeters(currentSpeedMps)
        if (distanceMeters > alertDistanceMeters) return@LaunchedEffect

        val instructionKey = "${state.trackTitle}:${instruction.trackPointIndex}:${instruction.command}"
        if (alertedInstructionKey == instructionKey) return@LaunchedEffect
        alertedInstructionKey = instructionKey
        DebugTelemetry.log(
            "TurnByTurn",
            "haptic=turn command=${instruction.command} index=${instruction.trackPointIndex} " +
                "distanceM=${distanceMeters.toInt()} alertDistanceM=${alertDistanceMeters.toInt()} " +
                "speedMps=${currentSpeedMps?.takeIf { it.isFinite() }?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "na"} " +
                "mode=$turnAlertsMode",
        )
        vibrator?.vibrate(turnAlertEffect(instruction.command))
    }

    LaunchedEffect(
        state.active,
        state.mode,
        state.offRoute,
        hapticsEnabled,
        offRouteAlertsEnabled,
        offRouteRepeatSeconds,
    ) {
        if (!hapticsEnabled || !offRouteAlertsEnabled) return@LaunchedEffect
        if (!state.active || state.mode != GuidanceMode.FOLLOW_ROUTE || !state.offRoute) return@LaunchedEffect

        while (isActive) {
            DebugTelemetry.log(
                "TurnByTurn",
                "haptic=off_route distanceToRouteM=${state.distanceToRouteMeters?.toInt() ?: "na"} repeatSeconds=$offRouteRepeatSeconds",
            )
            vibrator?.vibrate(OFF_ROUTE_ALERT_EFFECT)
            delay(offRouteRepeatSeconds.coerceAtLeast(OFF_ROUTE_MIN_REPEAT_SECONDS) * 1_000L)
        }
    }
}

private fun shouldAlertForTurn(
    mode: String,
    command: RouteInstructionCommand,
): Boolean =
    when (mode) {
        SettingsRepository.TURN_BY_TURN_TURN_ALERTS_OFF -> false
        SettingsRepository.TURN_BY_TURN_TURN_ALERTS_ALL -> true
        else ->
            command != RouteInstructionCommand.CONTINUE &&
                command != RouteInstructionCommand.SLIGHT_LEFT &&
                command != RouteInstructionCommand.SLIGHT_RIGHT
    }

private fun turnAlertEffect(command: RouteInstructionCommand): VibrationEffect =
    when (command) {
        RouteInstructionCommand.SHARP_LEFT,
        RouteInstructionCommand.SHARP_RIGHT,
        RouteInstructionCommand.FINISH,
        -> VibrationEffect.createWaveform(longArrayOf(0L, 70L, 55L, 70L), -1)
        else -> VibrationEffect.createOneShot(65L, VibrationEffect.DEFAULT_AMPLITUDE)
    }

private fun turnAlertDistanceMeters(speedMps: Float?): Double {
    val speed = speedMps?.takeIf { it.isFinite() && it > 0f }?.toDouble() ?: return TURN_ALERT_DEFAULT_DISTANCE_METERS
    return (speed * TURN_ALERT_LOOKAHEAD_SECONDS)
        .coerceIn(TURN_ALERT_MIN_DISTANCE_METERS, TURN_ALERT_MAX_DISTANCE_METERS)
}

private const val TURN_ALERT_DEFAULT_DISTANCE_METERS = 35.0
private const val TURN_ALERT_MIN_DISTANCE_METERS = 25.0
private const val TURN_ALERT_MAX_DISTANCE_METERS = 90.0
private const val TURN_ALERT_LOOKAHEAD_SECONDS = 8.0
private const val OFF_ROUTE_MIN_REPEAT_SECONDS = 15

private val OFF_ROUTE_ALERT_EFFECT: VibrationEffect =
    VibrationEffect.createWaveform(longArrayOf(0L, 120L, 80L, 120L, 80L, 120L), -1)
