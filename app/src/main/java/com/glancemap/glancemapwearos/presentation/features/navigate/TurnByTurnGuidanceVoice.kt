package com.glancemap.glancemapwearos.presentation.features.navigate

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.GuidanceMode
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.RouteInstruction
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.RouteInstructionCommand
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.TurnByTurnGuidanceState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun TurnByTurnGuidanceVoiceEffect(
    context: Context,
    state: TurnByTurnGuidanceState,
    currentSpeedMps: Float?,
    voiceEnabled: Boolean,
    turnAlertsMode: String,
    paused: Boolean,
    isMetric: Boolean,
) {
    if (!voiceEnabled) return

    val appContext = context.applicationContext
    var ttsReady by remember { mutableStateOf(false) }
    var alertedInstructionKey by remember { mutableStateOf<String?>(null) }
    var arrivalAlertedTrack by remember { mutableStateOf<String?>(null) }
    val tts =
        remember(appContext) {
            TextToSpeech(appContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                DebugTelemetry.log("TurnByTurn", "voice=init status=$status ready=$ttsReady")
            }
        }

    DisposableEffect(tts) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    LaunchedEffect(ttsReady) {
        if (ttsReady) {
            val result = tts.setLanguage(Locale.getDefault())
            tts.setSpeechRate(1.0f)
            DebugTelemetry.log("TurnByTurn", "voice=language result=$result locale=${Locale.getDefault()}")
        }
    }

    LaunchedEffect(state.active, state.trackTitle) {
        if (!state.active) {
            alertedInstructionKey = null
            arrivalAlertedTrack = null
            tts.stop()
        }
    }

    LaunchedEffect(
        ttsReady,
        state.active,
        state.mode,
        state.nextInstruction?.trackPointIndex,
        state.distanceToInstructionMeters,
        currentSpeedMps,
        voiceEnabled,
        turnAlertsMode,
        paused,
    ) {
        if (!ttsReady || paused) return@LaunchedEffect
        val instruction = state.nextInstruction ?: return@LaunchedEffect
        if (!shouldAlertForTurn(turnAlertsMode, instruction.command)) return@LaunchedEffect
        if (state.mode != GuidanceMode.FOLLOW_ROUTE) return@LaunchedEffect
        val distanceMeters = state.distanceToInstructionMeters ?: return@LaunchedEffect
        val alertDistanceMeters = turnAlertDistanceMeters(currentSpeedMps)
        if (distanceMeters > alertDistanceMeters) return@LaunchedEffect

        val instructionKey = "${state.trackTitle}:${instruction.trackPointIndex}:${instruction.command}"
        if (alertedInstructionKey == instructionKey) return@LaunchedEffect
        alertedInstructionKey = instructionKey
        val spokenText =
            spokenInstructionText(
                instruction = instruction,
                distanceMeters = distanceMeters,
                followingInstruction = state.followingInstruction,
                distanceToFollowingInstructionMeters = state.distanceToFollowingInstructionMeters,
                isMetric = isMetric,
            )
        DebugTelemetry.log(
            "TurnByTurn",
            "voice=turn command=${instruction.command} index=${instruction.trackPointIndex} " +
                "distanceM=${distanceMeters.toInt()} alertDistanceM=${alertDistanceMeters.toInt()}",
        )
        tts.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, instructionKey)
    }

    LaunchedEffect(ttsReady, state.active, state.mode, state.trackTitle, paused) {
        if (!ttsReady || paused || !state.active || state.mode != GuidanceMode.FINISHED) {
            return@LaunchedEffect
        }
        val trackKey = state.trackTitle ?: "active_route"
        if (arrivalAlertedTrack == trackKey) return@LaunchedEffect
        arrivalAlertedTrack = trackKey
        DebugTelemetry.log("TurnByTurn", "voice=arrival track=${state.trackTitle ?: "unknown"}")
        tts.speak("You have arrived", TextToSpeech.QUEUE_FLUSH, null, "arrival:$trackKey")
    }
}

private fun spokenInstructionText(
    instruction: RouteInstruction,
    distanceMeters: Double,
    followingInstruction: RouteInstruction?,
    distanceToFollowingInstructionMeters: Double?,
    isMetric: Boolean,
): String {
    val action = spokenTurnAction(instruction.command)
    val distance = spokenDistancePrefix(distanceMeters, isMetric)
    val primary = if (distance == null) action else "$distance, $action"
    val followingGapMeters =
        distanceToFollowingInstructionMeters
            ?.let { it - distanceMeters }
            ?.takeIf { it in 0.0..VOICE_FOLLOWING_TURN_MAX_GAP_METERS }
    val followingAction =
        if (followingGapMeters != null && followingInstruction != null) {
            spokenTurnAction(followingInstruction.command)
        } else {
            null
        }
    return if (followingAction != null) "$primary, then $followingAction" else primary
}

private fun spokenTurnAction(command: RouteInstructionCommand): String =
    when (command) {
        RouteInstructionCommand.SLIGHT_LEFT -> "slight left"
        RouteInstructionCommand.LEFT -> "turn left"
        RouteInstructionCommand.SHARP_LEFT -> "sharp left"
        RouteInstructionCommand.SLIGHT_RIGHT -> "slight right"
        RouteInstructionCommand.RIGHT -> "turn right"
        RouteInstructionCommand.SHARP_RIGHT -> "sharp right"
        RouteInstructionCommand.CONTINUE -> "continue"
        RouteInstructionCommand.FINISH -> "finish"
    }

private fun spokenDistancePrefix(
    distanceMeters: Double,
    isMetric: Boolean,
): String? {
    if (!distanceMeters.isFinite()) return null
    if (distanceMeters < 15.0) return "now"
    if (!isMetric) {
        val feet = distanceMeters * METERS_TO_FEET
        return if (feet < FEET_PER_HALF_MILE) {
            val roundedFeet = ((feet / 25.0).roundToInt() * 25).coerceAtLeast(50)
            "in $roundedFeet feet"
        } else {
            val miles = distanceMeters * METERS_TO_MILES
            val roundedTenths = (miles * 10.0).roundToInt().coerceAtLeast(1)
            val spokenMiles = String.format(Locale.US, "%.1f", roundedTenths / 10.0)
            "in $spokenMiles miles"
        }
    }
    val roundedMeters = ((distanceMeters / 5.0).roundToInt() * 5).coerceAtLeast(15)
    return "in $roundedMeters meters"
}

private const val VOICE_FOLLOWING_TURN_MAX_GAP_METERS = 120.0
private const val METERS_TO_FEET = 3.28084
private const val METERS_TO_MILES = 0.000621371
private const val FEET_PER_HALF_MILE = 2_640.0
