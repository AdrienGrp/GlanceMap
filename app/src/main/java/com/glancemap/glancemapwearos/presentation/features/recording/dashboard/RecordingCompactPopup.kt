package com.glancemap.glancemapwearos.presentation.features.recording.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.features.recording.TraceRecordingUiState
import com.glancemap.glancemapwearos.presentation.ui.cappedFontScale

@Composable
internal fun CompactRecordingControls(
    state: TraceRecordingUiState,
    snapshot: RecordingDashboardSnapshot,
    isMetric: Boolean,
    toolButtonEdgePadding: Dp,
    toolButtonSize: Dp,
    modifier: Modifier,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    onExpand: () -> Unit,
) {
    val distance = formattedRecordingMetric(SettingsRepository.RECORDING_METRIC_DISTANCE, snapshot, isMetric)
    Box(
        modifier =
            modifier
                .padding(end = toolButtonEdgePadding + toolButtonSize + 8.dp)
                .width(128.dp)
                .height(48.dp)
                .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(percent = 50))
                .pointerInput(state.active, state.paused) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (totalDrag < -POPUP_EXPAND_DRAG_THRESHOLD_PX) onExpand()
                            totalDrag = 0f
                        },
                        onDragCancel = { totalDrag = 0f },
                    ) { _, dragAmount ->
                        totalDrag += dragAmount
                    }
                }
                .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        cappedFontScale(maxFontScale = 1f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompactControlButton(
                    selected = true,
                    onClick = onPauseResume,
                    icon = if (state.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (state.paused) "Resume recording" else "Pause recording",
                )
                CompactControlButton(
                    selected = false,
                    onClick = onStop,
                    icon = Icons.Default.Stop,
                    contentDescription = "Stop recording",
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "${distance.value}${distance.unit?.let { " $it" }.orEmpty()}",
                    color = Color.White.copy(alpha = 0.74f),
                    fontSize = 9.sp,
                    lineHeight = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SwipeExpandCue()
            }
        }
    }
}

@Composable
private fun SwipeExpandCue(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.width(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.ExpandLess,
            contentDescription = "Swipe up to expand recording",
            tint = Color.White.copy(alpha = 0.62f),
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun CompactControlButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    tint: Color = Color.White,
) {
    Box(
        modifier =
            Modifier
                .size(30.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.14f),
                    CircleShape,
                )
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.onPrimary else tint,
            modifier = Modifier.size(18.dp),
        )
    }
}
