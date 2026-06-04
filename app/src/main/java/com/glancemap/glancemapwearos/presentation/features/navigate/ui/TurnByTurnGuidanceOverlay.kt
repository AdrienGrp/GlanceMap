package com.glancemap.glancemapwearos.presentation.features.navigate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.AnchorType
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.basicCurvedText
import androidx.wear.compose.foundation.padding
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.GuidanceMode
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.RouteInstructionCommand
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.TurnByTurnGuidanceState
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import com.glancemap.glancemapwearos.presentation.ui.cappedFontScale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun BoxScope.TurnByTurnGuidanceOverlay(
    state: TurnByTurnGuidanceState,
    screenSize: WearScreenSize,
    isMetric: Boolean,
    compassHeadingDeg: Float,
    guideBackToRouteActive: Boolean,
    showGuideBackPrompt: Boolean,
    startDecisionPrompt: GuidanceDecisionPrompt?,
    onStop: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onGuideBackToRoute: () -> Unit,
    onDismissGuideBackPrompt: () -> Unit,
    onAcceptStartDecisionPrompt: () -> Unit,
    onDismissStartDecisionPrompt: () -> Unit,
) {
    LaunchedEffect(state.active) {
        if (!state.active) {
            onExpandedChange(false)
        }
    }
    if (!state.active) return

    var expanded by remember(state.trackTitle) { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        onExpandedChange(expanded)
    }
    DisposableEffect(Unit) {
        onDispose { onExpandedChange(false) }
    }
    val topPadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 54.dp
            WearScreenSize.MEDIUM -> 50.dp
            WearScreenSize.SMALL -> 46.dp
        }
    val compactWidth =
        when (screenSize) {
            WearScreenSize.LARGE -> 112.dp
            WearScreenSize.MEDIUM -> 104.dp
            WearScreenSize.SMALL -> 96.dp
        }
    val compactIconSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 17.dp
            WearScreenSize.MEDIUM -> 16.dp
            WearScreenSize.SMALL -> 15.dp
        }
    val titleFont =
        when (screenSize) {
            WearScreenSize.LARGE -> 11.sp
            WearScreenSize.MEDIUM -> 10.sp
            WearScreenSize.SMALL -> 10.sp
        }

    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.Center),
    ) {
        ExpandedGuidanceOverlay(
            state = state,
            screenSize = screenSize,
            isMetric = isMetric,
            compassHeadingDeg = compassHeadingDeg,
            guideBackToRouteActive = guideBackToRouteActive,
            onCollapse = { expanded = false },
        )
    }

    if (!expanded) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topPadding)
                    .widthIn(max = compactWidth)
                    .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            cappedFontScale(maxFontScale = 1f) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    GuidanceArrowIcon(
                        state = state,
                        compassHeadingDeg = compassHeadingDeg,
                        guideBackToRouteActive = guideBackToRouteActive,
                        modifier = Modifier.size(compactIconSize),
                    )
                    Text(
                        text = guidanceCompactText(state, isMetric, guideBackToRouteActive),
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = titleFont,
                        lineHeight = titleFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Expand guidance",
                        tint = Color.White.copy(alpha = 0.78f),
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
    }

    if (startDecisionPrompt != null) {
        GuidanceDecisionPromptCard(
            title = startDecisionPrompt.title,
            detail = startDecisionPrompt.detail,
            acceptText = startDecisionPrompt.acceptText,
            dismissText = startDecisionPrompt.dismissText,
            onAccept = onAcceptStartDecisionPrompt,
            onDismiss = onDismissStartDecisionPrompt,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp),
        )
    } else if (showGuideBackPrompt) {
        GuidanceDecisionPromptCard(
            title = "Off route",
            detail =
                state.distanceToRouteMeters?.let {
                    "${formatLiveDistanceLabel(it, isMetric)} from GPX"
                } ?: "Guide back?",
            acceptText = "Guide",
            dismissText = "Ignore",
            onAccept = onGuideBackToRoute,
            onDismiss = onDismissGuideBackPrompt,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp),
        )
    }
}

internal data class GuidanceDecisionPrompt(
    val title: String,
    val detail: String,
    val acceptText: String,
    val dismissText: String,
)

@Composable
private fun ExpandedGuidanceOverlay(
    state: TurnByTurnGuidanceState,
    screenSize: WearScreenSize,
    isMetric: Boolean,
    compassHeadingDeg: Float,
    guideBackToRouteActive: Boolean,
    onCollapse: () -> Unit,
) {
    val titleRadialPadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 16.dp
            WearScreenSize.MEDIUM -> 15.dp
            WearScreenSize.SMALL -> 14.dp
        }
    val contentWidthFraction =
        when (screenSize) {
            WearScreenSize.LARGE -> 0.70f
            WearScreenSize.MEDIUM -> 0.68f
            WearScreenSize.SMALL -> 0.66f
        }
    val arrowContainerSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 68.dp
            WearScreenSize.MEDIUM -> 64.dp
            WearScreenSize.SMALL -> 60.dp
        }
    val arrowIconSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 40.dp
            WearScreenSize.MEDIUM -> 38.dp
            WearScreenSize.SMALL -> 36.dp
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(state.mode, state.nextInstruction) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (abs(totalDrag) > POPUP_MINIMIZE_DRAG_THRESHOLD_PX) {
                                onCollapse()
                            }
                            totalDrag = 0f
                        },
                        onDragCancel = { totalDrag = 0f },
                    ) { _, dragAmount ->
                        totalDrag += dragAmount
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        RouteProgressRing(
            progress = state.routeProgressFraction,
            modifier = Modifier.fillMaxSize(),
        )

        if (!state.trackTitle.isNullOrBlank()) {
            CurvedLayout(
                modifier = Modifier.fillMaxSize(),
                anchor = 270f,
                anchorType = AnchorType.Center,
            ) {
                basicCurvedText(
                    text = state.trackTitle,
                    modifier = CurvedModifier.padding(titleRadialPadding),
                    overflow = TextOverflow.Ellipsis,
                    style = {
                        CurvedTextStyle(
                            color = Color.White.copy(alpha = 0.64f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                )
            }
        }

        cappedFontScale(maxFontScale = 1f) {
            Column(
                modifier = Modifier.fillMaxWidth(contentWidthFraction),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(arrowContainerSize)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    GuidanceArrowIcon(
                        state = state,
                        compassHeadingDeg = compassHeadingDeg,
                        guideBackToRouteActive = guideBackToRouteActive,
                        modifier = Modifier.size(arrowIconSize),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = guidancePrimaryText(state, guideBackToRouteActive),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = guidanceSecondaryText(state, isMetric, guideBackToRouteActive),
                    color = Color.White.copy(alpha = 0.82f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                state.distanceRemainingMeters?.let { remaining ->
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "${formatLiveDistanceLabel(remaining, isMetric)} remaining",
                        color = Color.White.copy(alpha = 0.64f),
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                state.routeProgressFraction?.let { progress ->
                    Spacer(modifier = Modifier.size(3.dp))
                    Text(
                        text = "${(progress * 100f).roundToInt()}% complete",
                        color = Color.White.copy(alpha = 0.46f),
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                if (state.offRoute) {
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "Off route",
                        color = Color(0xFFFFB74D),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        lineHeight = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuidanceDecisionPromptCard(
    title: String,
    detail: String,
    acceptText: String,
    dismissText: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .widthIn(max = 150.dp)
                .background(Color.Black.copy(alpha = 0.94f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        cappedFontScale(maxFontScale = 1f) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    color = Color(0xFFFFB74D),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = detail,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuideBackPromptButton(
                        text = acceptText,
                        selected = true,
                        onClick = onAccept,
                    )
                    GuideBackPromptButton(
                        text = dismissText,
                        selected = false,
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideBackPromptButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        modifier =
            Modifier
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f),
                    RoundedCornerShape(6.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 9.dp, vertical = 5.dp),
        color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 10.sp,
    )
}

@Composable
private fun RouteProgressRing(
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    val clampedProgress = progress?.coerceIn(0f, 1f) ?: return
    val progressColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val strokeWidth = 5.dp.toPx()
        val inset = strokeWidth / 2f + 5.dp.toPx()
        val side = min(size.width, size.height) - inset * 2f
        if (side <= 0f) return@Canvas
        val topLeft =
            Offset(
                x = (size.width - side) / 2f,
                y = (size.height - side) / 2f,
            )
        val arcSize = Size(side, side)
        drawArc(
            color = Color.White.copy(alpha = 0.12f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth),
        )
        if (clampedProgress > 0f) {
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * clampedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun GuidanceArrowIcon(
    state: TurnByTurnGuidanceState,
    compassHeadingDeg: Float,
    guideBackToRouteActive: Boolean,
    modifier: Modifier,
    tint: Color = Color.White,
) {
    Icon(
        imageVector = Icons.Default.Navigation,
        contentDescription = null,
        tint = tint,
        modifier =
            modifier.rotate(
                if (guideBackToRouteActive && state.bearingToRouteDegrees != null) {
                    state.bearingToRouteDegrees - compassHeadingDeg
                } else {
                    when (state.mode) {
                    GuidanceMode.TO_START ->
                        (state.bearingToStartDegrees ?: 0f) - compassHeadingDeg
                    GuidanceMode.FOLLOW_ROUTE ->
                        rotationForCommand(state.nextInstruction?.command)
                    GuidanceMode.FINISHED -> 0f
                    GuidanceMode.WAITING_FOR_LOCATION -> 0f
                    }
                },
            ),
    )
}

private fun guidancePrimaryText(
    state: TurnByTurnGuidanceState,
    guideBackToRouteActive: Boolean = false,
): String =
    if (guideBackToRouteActive) {
        "To route"
    } else {
        when (state.mode) {
        GuidanceMode.WAITING_FOR_LOCATION -> "Waiting GPS"
        GuidanceMode.TO_START -> "To start"
        GuidanceMode.FOLLOW_ROUTE -> state.nextInstruction?.message ?: "Continue"
        GuidanceMode.FINISHED -> "Finished"
        }
    }

private fun guidanceSecondaryText(
    state: TurnByTurnGuidanceState,
    isMetric: Boolean,
    guideBackToRouteActive: Boolean = false,
): String =
    if (guideBackToRouteActive) {
        state.distanceToRouteMeters?.let { formatLiveDistanceLabel(it, isMetric) } ?: "Find route"
    } else {
        when (state.mode) {
        GuidanceMode.WAITING_FOR_LOCATION -> state.trackTitle ?: "GPX guidance"
        GuidanceMode.TO_START ->
            state.distanceToStartMeters?.let { formatLiveDistanceLabel(it, isMetric) }
                ?: "Find the start"
        GuidanceMode.FOLLOW_ROUTE ->
            state.distanceToInstructionMeters?.let { formatLiveDistanceLabel(it, isMetric) }
                ?: "On route"
        GuidanceMode.FINISHED -> state.trackTitle ?: "Route complete"
        }
    }

private fun guidanceCompactText(
    state: TurnByTurnGuidanceState,
    isMetric: Boolean,
    guideBackToRouteActive: Boolean = false,
): String =
    if (guideBackToRouteActive) {
        state.distanceToRouteMeters?.let { "Route ${formatLiveDistanceLabel(it, isMetric)}" } ?: "To route"
    } else {
        when (state.mode) {
        GuidanceMode.WAITING_FOR_LOCATION -> "Waiting GPS"
        GuidanceMode.TO_START ->
            state.distanceToStartMeters?.let { "Start ${formatLiveDistanceLabel(it, isMetric)}" }
                ?: "To start"
        GuidanceMode.FOLLOW_ROUTE ->
            state.distanceToInstructionMeters?.let {
                "${guidancePrimaryText(state)} ${formatLiveDistanceLabel(it, isMetric)}"
            } ?: guidancePrimaryText(state)
        GuidanceMode.FINISHED -> "Finished"
        }
    }

private fun rotationForCommand(command: RouteInstructionCommand?): Float =
    when (command) {
        RouteInstructionCommand.SLIGHT_LEFT -> -45f
        RouteInstructionCommand.LEFT -> -90f
        RouteInstructionCommand.SHARP_LEFT -> -135f
        RouteInstructionCommand.SLIGHT_RIGHT -> 45f
        RouteInstructionCommand.RIGHT -> 90f
        RouteInstructionCommand.SHARP_RIGHT -> 135f
        RouteInstructionCommand.FINISH -> 0f
        RouteInstructionCommand.CONTINUE, null -> 0f
    }

private const val POPUP_MINIMIZE_DRAG_THRESHOLD_PX = 24f
