package com.glancemap.glancemapwearos.presentation.features.routetools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Loop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.formatting.DurationFormatter
import com.glancemap.glancemapwearos.presentation.formatting.UnitFormatter
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize

@Composable
internal fun BoxScope.RouteCrosshairOverlay(
    session: RouteToolSession,
    screenSize: WearScreenSize,
    isMetric: Boolean,
    createPreview: RouteToolCreatePreview? = null,
    createPreviewInProgress: Boolean = false,
    createPreviewMessage: String? = null,
    busy: Boolean = false,
    busyMessage: String? = null,
    titleOverride: String? = null,
    instructionOverride: String? = null,
    showCapturedPoints: Boolean = true,
    onPickHere: () -> Unit,
    onCancel: () -> Unit,
    onUndoLastPoint: (() -> Unit)? = null,
    onSaveCreatePreview: (() -> Unit)? = null,
    onRefreshCreatePreview: (() -> Unit)? = null
) {
    val multiPointMode = session.isMultiPointCreate
    val loopPreviewMode = session.options.toolKind == RouteToolKind.CREATE &&
        session.options.createMode == RouteCreateMode.LOOP_AROUND_HERE &&
        session.isComplete
    val popupSpec = guidancePopupSpec(screenSize)
    val popupMaxWidth = if (multiPointMode) popupSpec.maxWidth + 14.dp else popupSpec.maxWidth
    val multiPointTitleFontSize = when (screenSize) {
        WearScreenSize.LARGE -> 10.5.sp
        WearScreenSize.MEDIUM -> 9.5.sp
        WearScreenSize.SMALL -> 8.5.sp
    }
    val multiPointTitleLineHeight = when (screenSize) {
        WearScreenSize.LARGE -> 10.sp
        WearScreenSize.MEDIUM -> 9.sp
        WearScreenSize.SMALL -> 8.sp
    }
    val multiPointStatusFontSize = when (screenSize) {
        WearScreenSize.LARGE -> 8.5.sp
        WearScreenSize.MEDIUM -> 8.sp
        WearScreenSize.SMALL -> 7.5.sp
    }
    val multiPointStatusLineHeight = when (screenSize) {
        WearScreenSize.LARGE -> 9.5.sp
        WearScreenSize.MEDIUM -> 9.sp
        WearScreenSize.SMALL -> 8.5.sp
    }
    val popupVerticalPadding = if (multiPointMode) {
        compactBannerVerticalPadding(screenSize)
    } else {
        popupSpec.verticalPadding
    }
    val popupRowSpacing = if (multiPointMode) 0.dp else popupSpec.rowSpacing
    val saveChipFontSize = when (screenSize) {
        WearScreenSize.LARGE -> 9.sp
        WearScreenSize.MEDIUM -> 8.5.sp
        WearScreenSize.SMALL -> 8.sp
    }
    val actionButtonSize = when (screenSize) {
        WearScreenSize.LARGE -> 28.dp
        WearScreenSize.MEDIUM -> 26.dp
        WearScreenSize.SMALL -> 24.dp
    }
    val actionIconSize = when (screenSize) {
        WearScreenSize.LARGE -> 15.dp
        WearScreenSize.MEDIUM -> 14.dp
        WearScreenSize.SMALL -> 13.dp
    }
    val actionsBottomPadding = when (screenSize) {
        WearScreenSize.LARGE -> 38.dp
        WearScreenSize.MEDIUM -> 34.dp
        WearScreenSize.SMALL -> 30.dp
    }
    if (session.usesCrosshair) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(46.dp),
            contentAlignment = Alignment.Center
        ) {
            if (busy) {
                RouteToolBusySpinner(size = 30.dp)
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val outerRadius = size.minDimension * 0.34f
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.45f),
                        radius = outerRadius + 6f,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFFF7C948),
                        radius = outerRadius,
                        center = center,
                        style = Stroke(width = 3f)
                    )
                    drawLine(
                        color = Color(0xFFF7C948),
                        start = Offset(center.x - 16f, center.y),
                        end = Offset(center.x + 16f, center.y),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFFF7C948),
                        start = Offset(center.x, center.y - 16f),
                        end = Offset(center.x, center.y + 16f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = Color(0xFFF7C948),
                        radius = 2.8f,
                        center = center
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = popupSpec.topPadding)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = popupMaxWidth)
                .background(
                    Color.Black.copy(alpha = 0.82f),
                    RoundedCornerShape(popupSpec.cornerRadius)
                )
                .padding(
                    horizontal = popupSpec.horizontalPadding,
                    vertical = popupVerticalPadding
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(popupRowSpacing)
        ) {
            Text(
                text = titleOverride ?: if (multiPointMode) {
                    "${session.modeTitle} • ${session.chainPoints.size}"
                } else {
                    session.modeTitle
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = if (multiPointMode) multiPointTitleFontSize else popupSpec.modeTitleFontSize,
                    lineHeight = if (multiPointMode) multiPointTitleLineHeight else popupSpec.modeTitleFontSize
                ),
                color = Color(0xFFF7C948),
                textAlign = TextAlign.Center
            )
            if (multiPointMode) {
                val previewLines = createPreview?.let {
                    formatCreatePreviewLines(preview = it, isMetric = isMetric)
                }
                if (previewLines != null && createPreviewMessage == null && !createPreviewInProgress) {
                    Text(
                        text = previewLines.primary,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = multiPointStatusFontSize),
                        textAlign = TextAlign.Center,
                        lineHeight = multiPointStatusLineHeight,
                        color = Color.White.copy(alpha = 0.92f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = previewLines.secondary,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = multiPointStatusFontSize),
                        textAlign = TextAlign.Center,
                        lineHeight = multiPointStatusLineHeight,
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = compactMultiPointBannerText(
                            session = session,
                            preview = createPreview,
                            createPreviewInProgress = createPreviewInProgress,
                            createPreviewMessage = createPreviewMessage,
                            isMetric = isMetric
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = multiPointStatusFontSize),
                        textAlign = TextAlign.Center,
                        lineHeight = multiPointStatusLineHeight,
                        color = if (createPreviewMessage != null) {
                            Color(0xFFFFCC80)
                        } else {
                            Color.White.copy(alpha = 0.90f)
                        },
                        maxLines = if (createPreviewMessage != null) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (createPreview != null && onSaveCreatePreview != null && session.chainPoints.size >= 2) {
                    Text(
                        text = "Save GPX",
                        modifier = Modifier
                            .background(
                                Color(0xFFF7C948).copy(alpha = 0.94f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(onClick = onSaveCreatePreview)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = saveChipFontSize),
                        color = Color.Black
                    )
                }
            } else if (loopPreviewMode) {
                val previewLines = createPreview?.let {
                    formatCreatePreviewLines(preview = it, isMetric = isMetric)
                }
                when {
                    createPreviewInProgress -> {
                        Text(
                            text = "Finding loop...",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = popupSpec.instructionFontSize),
                            textAlign = TextAlign.Center,
                            lineHeight = popupSpec.instructionLineHeight,
                            color = Color.White.copy(alpha = 0.90f)
                        )
                    }

                    createPreviewMessage != null -> {
                        Text(
                            text = createPreviewMessage,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = popupSpec.instructionFontSize),
                            textAlign = TextAlign.Center,
                            lineHeight = popupSpec.instructionLineHeight,
                            color = Color(0xFFFFCC80),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    previewLines != null -> {
                        Text(
                            text = previewLines.primary,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = popupSpec.instructionFontSize),
                            textAlign = TextAlign.Center,
                            lineHeight = popupSpec.instructionLineHeight,
                            color = Color.White.copy(alpha = 0.92f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = previewLines.secondary,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = popupSpec.instructionFontSize),
                            textAlign = TextAlign.Center,
                            lineHeight = popupSpec.instructionLineHeight,
                            color = Color.White.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    else -> {
                        Text(
                            text = "Check loop data, save it, or try another.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = popupSpec.instructionFontSize),
                            textAlign = TextAlign.Center,
                            lineHeight = popupSpec.instructionLineHeight,
                            color = Color.White.copy(alpha = 0.90f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Text(
                    text = busyMessage ?: instructionOverride ?: session.instructionText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = popupSpec.instructionFontSize),
                    textAlign = TextAlign.Center,
                    lineHeight = popupSpec.instructionLineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showCapturedPoints && session.hasCapturedPoints && !loopPreviewMode) {
                RouteCapturedPointsSummary(session = session)
            }
        }
    }

    if (session.usesCrosshair || loopPreviewMode) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = actionsBottomPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCancel,
                enabled = !busy,
                modifier = Modifier.size(actionButtonSize),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.74f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close route tool",
                    modifier = Modifier.size(actionIconSize)
                )
            }
            if (multiPointMode && onUndoLastPoint != null) {
                IconButton(
                    onClick = onUndoLastPoint,
                    enabled = session.chainPoints.isNotEmpty() && !createPreviewInProgress && !busy,
                    modifier = Modifier.size(actionButtonSize),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.74f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Remove last point",
                        modifier = Modifier.size(actionIconSize)
                    )
                }
            } else if (loopPreviewMode && onRefreshCreatePreview != null) {
                IconButton(
                    onClick = onRefreshCreatePreview,
                    enabled = !createPreviewInProgress && !busy,
                    modifier = Modifier.size(actionButtonSize),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.74f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Loop,
                        contentDescription = "Find another loop",
                        modifier = Modifier.size(actionIconSize)
                    )
                }
            }
            if (loopPreviewMode) {
                if (createPreview != null && onSaveCreatePreview != null) {
                    IconButton(
                        onClick = onSaveCreatePreview,
                        enabled = !createPreviewInProgress && !busy,
                        modifier = Modifier.size(actionButtonSize),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFFF7C948),
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save loop GPX",
                            modifier = Modifier.size(actionIconSize)
                        )
                    }
                }
            } else {
                IconButton(
                    onClick = onPickHere,
                    enabled = !createPreviewInProgress && !busy,
                    modifier = Modifier.size(actionButtonSize),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFFF7C948),
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Select position",
                        modifier = Modifier.size(actionIconSize)
                    )
                }
            }
        }
    }
}

private data class CreatePreviewLines(
    val primary: String,
    val secondary: String
)

private fun compactMultiPointBannerText(
    session: RouteToolSession,
    preview: RouteToolCreatePreview?,
    createPreviewInProgress: Boolean,
    createPreviewMessage: String?,
    isMetric: Boolean
): String {
    return when {
        createPreviewInProgress -> "Updating route..."
        createPreviewMessage != null -> createPreviewMessage
        preview != null -> formatCreatePreviewLines(preview, isMetric).primary
        session.chainPoints.isEmpty() -> "Start point"
        session.chainPoints.size == 1 -> "Next point"
        else -> "Add point"
    }
}

private fun formatCreatePreviewLines(
    preview: RouteToolCreatePreview,
    isMetric: Boolean
): CreatePreviewLines {
    val (distanceValue, distanceUnit) = UnitFormatter.formatDistance(preview.distanceMeters, isMetric)
    val etaText = DurationFormatter.formatDurationShort(preview.estimatedDurationSec)
    val gainText = compactElevationValue(preview.elevationGainMeters, isMetric)
    val lossText = compactElevationValue(preview.elevationLossMeters, isMetric)
    return CreatePreviewLines(
        primary = "$distanceValue $distanceUnit • $etaText",
        secondary = "↑$gainText  ↓$lossText"
    )
}

private fun compactElevationValue(
    meters: Double,
    isMetric: Boolean
): String {
    val (value, unit) = UnitFormatter.formatElevation(meters, isMetric)
    return if (isMetric) {
        value
    } else {
        "$value$unit"
    }
}

internal data class GuidancePopupSpec(
    val topPadding: androidx.compose.ui.unit.Dp,
    val maxWidth: androidx.compose.ui.unit.Dp,
    val horizontalPadding: androidx.compose.ui.unit.Dp,
    val verticalPadding: androidx.compose.ui.unit.Dp,
    val cornerRadius: androidx.compose.ui.unit.Dp,
    val rowSpacing: androidx.compose.ui.unit.Dp,
    val modeTitleFontSize: androidx.compose.ui.unit.TextUnit,
    val instructionFontSize: androidx.compose.ui.unit.TextUnit,
    val instructionLineHeight: androidx.compose.ui.unit.TextUnit
)

private fun compactBannerVerticalPadding(screenSize: WearScreenSize): Dp {
    return when (screenSize) {
        WearScreenSize.LARGE -> 2.dp
        WearScreenSize.MEDIUM -> 2.dp
        WearScreenSize.SMALL -> 1.5.dp
    }
}

internal fun guidancePopupSpec(screenSize: WearScreenSize): GuidancePopupSpec {
    return when (screenSize) {
        WearScreenSize.LARGE -> GuidancePopupSpec(
            topPadding = 23.dp,
            maxWidth = 112.dp,
            horizontalPadding = 6.dp,
            verticalPadding = 3.dp,
            cornerRadius = 7.dp,
            rowSpacing = 2.dp,
            modeTitleFontSize = 13.sp,
            instructionFontSize = 10.sp,
            instructionLineHeight = 11.sp
        )

        WearScreenSize.MEDIUM -> GuidancePopupSpec(
            topPadding = 21.dp,
            maxWidth = 106.dp,
            horizontalPadding = 6.dp,
            verticalPadding = 3.dp,
            cornerRadius = 7.dp,
            rowSpacing = 2.dp,
            modeTitleFontSize = 12.sp,
            instructionFontSize = 9.sp,
            instructionLineHeight = 10.sp
        )

        WearScreenSize.SMALL -> GuidancePopupSpec(
            topPadding = 19.dp,
            maxWidth = 100.dp,
            horizontalPadding = 5.dp,
            verticalPadding = 3.dp,
            cornerRadius = 6.dp,
            rowSpacing = 2.dp,
            modeTitleFontSize = 11.sp,
            instructionFontSize = 8.sp,
            instructionLineHeight = 9.sp
        )
    }
}

private val RouteToolSession.hasCapturedPoints: Boolean
    get() = if (
        options.toolKind == RouteToolKind.MODIFY &&
        options.modifyMode == RouteModifyMode.RESHAPE_ROUTE
    ) {
        false
    } else if (
        options.toolKind == RouteToolKind.CREATE &&
        options.createMode == RouteCreateMode.MULTI_POINT_CHAIN
    ) {
        false
    } else {
        pointA != null || pointB != null || destination != null || loopCenter != null
    }

@Composable
private fun RouteCapturedPointsSummary(session: RouteToolSession) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        session.pointA?.let {
            CapturedPointBadge(
                label = if (
                    session.options.toolKind == RouteToolKind.MODIFY &&
                    session.options.modifyMode == RouteModifyMode.RESHAPE_ROUTE
                ) {
                    "Route"
                } else if (
                    session.options.toolKind == RouteToolKind.CREATE &&
                    session.options.createMode == RouteCreateMode.LOOP_AROUND_HERE
                ) {
                    "Start"
                } else if (session.options.toolKind == RouteToolKind.CREATE) {
                    "Start"
                } else {
                    "A"
                }
            )
        }
        session.pointB?.let {
            CapturedPointBadge(
                label = if (session.options.toolKind == RouteToolKind.CREATE) "End" else "B"
            )
        }
        session.destination?.let {
            CapturedPointBadge(
                label = if (
                    session.options.toolKind == RouteToolKind.MODIFY &&
                    session.options.modifyMode == RouteModifyMode.RESHAPE_ROUTE
                ) {
                    "Bend"
                } else {
                    "Dest"
                }
            )
        }
        session.loopCenter?.let {
            CapturedPointBadge(label = "Loop")
        }
    }
}

@Composable
private fun CapturedPointBadge(label: String) {
    Box(
        modifier = Modifier
            .background(Color(0x33F7C948), CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFF7C948)
        )
    }
}
