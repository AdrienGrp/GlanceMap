@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.glancemap.glancemapwearos.presentation.features.recording.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.features.recording.TraceRecordingUiState
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import com.glancemap.glancemapwearos.presentation.ui.cappedFontScale
import kotlin.math.abs

@Composable
internal fun ExpandedRecordingDashboard(
    state: TraceRecordingUiState,
    slots: List<String>,
    pageIndex: Int,
    pageCount: Int,
    snapshot: RecordingDashboardSnapshot,
    screenSize: WearScreenSize,
    isMetric: Boolean,
    onSlotLongPress: (Int) -> Unit,
    onCollapse: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onShowActions: () -> Unit,
) {
    val contentWidthFraction =
        when (screenSize) {
            WearScreenSize.LARGE -> 0.72f
            WearScreenSize.MEDIUM -> 0.68f
            WearScreenSize.SMALL -> 0.64f
        }
    val tileHeight =
        when (screenSize) {
            WearScreenSize.LARGE -> 46.dp
            WearScreenSize.MEDIUM -> 42.dp
            WearScreenSize.SMALL -> 38.dp
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onShowActions,
                )
                .pointerInput(state.active, state.paused, pageIndex, pageCount) {
                    var totalDragX = 0f
                    var totalDragY = 0f
                    detectDragGestures(
                        onDragEnd = {
                            val horizontalDominates = abs(totalDragX) > abs(totalDragY)
                            val verticalDominates = abs(totalDragY) > abs(totalDragX)
                            when {
                                horizontalDominates && totalDragX > POPUP_MINIMIZE_DRAG_THRESHOLD_PX -> onCollapse()
                                verticalDominates && totalDragY < -POPUP_PAGE_DRAG_THRESHOLD_PX -> onNextPage()
                                verticalDominates && totalDragY > POPUP_PAGE_DRAG_THRESHOLD_PX -> onPreviousPage()
                            }
                            totalDragX = 0f
                            totalDragY = 0f
                        },
                        onDragCancel = {
                            totalDragX = 0f
                            totalDragY = 0f
                        },
                    ) { _, dragAmount ->
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        cappedFontScale(maxFontScale = 1f) {
            Column(
                modifier = Modifier.fillMaxWidth(contentWidthFraction),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                RecordingDot(paused = state.paused, saving = state.saving, size = 26.dp)
                RecordingMetricTile(
                    metric = formattedRecordingMetric(slots[0], snapshot, isMetric),
                    height = tileHeight,
                    onLongPress = { onSlotLongPress(0) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RecordingMetricTile(
                        metric = formattedRecordingMetric(slots[1], snapshot, isMetric),
                        height = tileHeight,
                        onLongPress = { onSlotLongPress(1) },
                        modifier = Modifier.weight(1f),
                    )
                    RecordingMetricTile(
                        metric = formattedRecordingMetric(slots[2], snapshot, isMetric),
                        height = tileHeight,
                        onLongPress = { onSlotLongPress(2) },
                        modifier = Modifier.weight(1f),
                    )
                }
                RecordingMetricTile(
                    metric = formattedRecordingMetric(slots[3], snapshot, isMetric),
                    height = tileHeight,
                    onLongPress = { onSlotLongPress(3) },
                    modifier = Modifier.fillMaxWidth(0.86f),
                )
            }
        }

        SwipeMinimizeHandle(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp),
        )
        RecordingPageIndicator(
            pageIndex = pageIndex,
            pageCount = pageCount,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp),
        )
    }
}

@Composable
private fun RecordingMetricTile(
    metric: RecordingMetricValue,
    height: Dp,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .height(height)
                .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress,
                )
                .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = metric.label,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 9.sp,
            lineHeight = 9.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = metric.value,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            metric.unit?.let { unit ->
                Text(
                    text = unit,
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RecordingDot(
    paused: Boolean,
    saving: Boolean,
    size: Dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .background(Color.White.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(size * 0.55f)
                    .background(
                        when {
                            saving -> Color(0xFFFFD54F)
                            paused -> Color(0xFFFFB74D)
                            else -> Color(0xFFFF1744)
                        },
                        CircleShape,
                    ),
        )
    }
}

@Composable
private fun SwipeMinimizeHandle(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .width(28.dp)
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(2.dp)),
    )
}

@Composable
private fun RecordingPageIndicator(
    pageIndex: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 1) return
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .height(if (index == pageIndex) 18.dp else 6.dp)
                        .background(
                            color = Color.White.copy(alpha = if (index == pageIndex) 0.72f else 0.28f),
                            shape = RoundedCornerShape(percent = 50),
                        ),
            )
        }
    }
}
