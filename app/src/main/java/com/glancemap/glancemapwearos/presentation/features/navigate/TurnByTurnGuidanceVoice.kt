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
) {
    if (!voiceEnabled) return

    val appContext = context.applicationContext
    var ttsReady by remember { mutableStateOf(false) }
    var alertedInstructionKey by remember { mutableStateOf<String?>(null) }
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
        val spokenText = spokenInstructionText(instruction, distanceMeters)
        DebugTelemetry.log(
            "TurnByTurn",
            "voice=turn command=${instruction.command} index=${instruction.trackPointIndex} " +
                "distanceM=${distanceMeters.toInt()} alertDistanceM=${alertDistanceMeters.toInt()}",
        )
        tts.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, instructionKey)
    }
}

private fun spokenInstructionText(
    instruction: RouteInstruction,
    distanceMeters: Double,
): String {
    val action =
        when (instruction.command) {
            RouteInstructionCommand.SLIGHT_LEFT -> "slight left"
            RouteInstructionCommand.LEFT -> "turn left"
            RouteInstructionCommand.SHARP_LEFT -> "sharp left"
            RouteInstructionCommand.SLIGHT_RIGHT -> "slight right"
            RouteInstructionCommand.RIGHT -> "turn right"
            RouteInstructionCommand.SHARP_RIGHT -> "sharp right"
            RouteInstructionCommand.CONTINUE -> "continue"
            RouteInstructionCommand.FINISH -> "finish"
        }
    val distance = spokenDistancePrefix(distanceMeters)
    return if (distance == null) {
        action
    } else {
        "$distance, $action"
    }
}

private fun spokenDistancePrefix(distanceMeters: Double): String? {
    if (!distanceMeters.isFinite()) return null
    if (distanceMeters < 15.0) return "now"
    val roundedMeters = ((distanceMeters / 5.0).roundToInt() * 5).coerceAtLeast(15)
    return "in $roundedMeters meters"
}
