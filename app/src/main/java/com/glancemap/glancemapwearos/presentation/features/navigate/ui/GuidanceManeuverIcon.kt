package com.glancemap.glancemapwearos.presentation.features.navigate

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.wear.compose.material3.Icon
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.GuidanceMode
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.RouteInstructionCommand
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.TurnByTurnGuidanceState
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun GuidanceManeuverIcon(
    state: TurnByTurnGuidanceState,
    compassHeadingDeg: Float,
    guideBackToRouteActive: Boolean,
    modifier: Modifier,
    tint: Color = Color.White,
) {
    val bearingRotation =
        when {
            (guideBackToRouteActive || state.offRoute) && state.bearingToRouteDegrees != null ->
                state.bearingToRouteDegrees - compassHeadingDeg
            state.mode == GuidanceMode.TO_START ->
                (state.bearingToStartDegrees ?: 0f) - compassHeadingDeg
            else -> null
        }
    when {
        state.mode == GuidanceMode.FINISHED ->
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Route complete",
                tint = tint,
                modifier = modifier,
            )
        bearingRotation != null ->
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = if (state.mode == GuidanceMode.TO_START) "Direction to route start" else "Direction to route",
                tint = tint,
                modifier = modifier.rotate(bearingRotation),
            )
        else ->
            ManeuverArrow(
                command = state.nextInstruction?.command,
                tint = tint,
                modifier = modifier,
            )
    }
}

@Composable
private fun ManeuverArrow(
    command: RouteInstructionCommand?,
    tint: Color,
    modifier: Modifier,
) {
    val angleDegrees =
        when (command) {
            RouteInstructionCommand.SLIGHT_LEFT -> -45f
            RouteInstructionCommand.LEFT -> -90f
            RouteInstructionCommand.SHARP_LEFT -> -135f
            RouteInstructionCommand.SLIGHT_RIGHT -> 45f
            RouteInstructionCommand.RIGHT -> 90f
            RouteInstructionCommand.SHARP_RIGHT -> 135f
            RouteInstructionCommand.CONTINUE,
            RouteInstructionCommand.FINISH,
            null,
            -> 0f
        }
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.14f
        val pivot = Offset(size.width * 0.5f, size.height * 0.52f)
        val start = Offset(size.width * 0.5f, size.height * 0.88f)
        val angleRadians = Math.toRadians(angleDegrees.toDouble())
        val direction = Offset(sin(angleRadians).toFloat(), -cos(angleRadians).toFloat())
        val end = pivot + direction * (size.minDimension * 0.34f)
        val path =
            Path().apply {
                moveTo(start.x, start.y)
                lineTo(pivot.x, pivot.y)
                quadraticTo(
                    pivot.x,
                    pivot.y - size.height * 0.16f,
                    end.x,
                    end.y,
                )
            }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        val back = direction * (-size.minDimension * 0.20f)
        val perpendicular = Offset(-direction.y, direction.x) * (size.minDimension * 0.12f)
        drawLine(
            color = tint,
            start = end + back + perpendicular,
            end = end,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = end + back - perpendicular,
            end = end,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
