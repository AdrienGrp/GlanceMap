@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.glancemap.glancemapwearos.presentation.features.navigate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.GuidanceMode
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.RouteInstructionCommand
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.TurnByTurnGuidanceState
import com.glancemap.glancemapwearos.presentation.features.recording.TraceRecordingUiState
import com.glancemap.glancemapwearos.presentation.features.recording.dashboard.RECORDING_DASHBOARD_PAGE_SLOT_COUNT
import com.glancemap.glancemapwearos.presentation.features.recording.dashboard.RecordingDashboardSnapshot
import com.glancemap.glancemapwearos.presentation.features.recording.dashboard.RecordingStopPromptCard
import com.glancemap.glancemapwearos.presentation.features.recording.dashboard.buildRecordingDashboardSnapshot
import com.glancemap.glancemapwearos.presentation.features.recording.dashboard.floorMod
import com.glancemap.glancemapwearos.presentation.features.recording.dashboard.formattedRecordingMetric
import com.glancemap.glancemapwearos.presentation.features.recording.dashboard.logRecordingDashboardPageChange
import com.glancemap.glancemapwearos.presentation.features.recording.dashboard.normalizedRecordingDashboardSlots
import com.glancemap.glancemapwearos.presentation.features.recording.dashboard.recordingMetricDefinitions
import com.glancemap.glancemapwearos.presentation.features.settings.OptionPickerDialog
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import com.glancemap.glancemapwearos.presentation.ui.cappedFontScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun BoxScope.CombinedGuidanceRecordingOverlay(
    guidanceState: TurnByTurnGuidanceState,
    guidancePaused: Boolean,
    recordingState: TraceRecordingUiState,
    metricSlots: List<String>,
    userWeightKg: Float,
    backpackWeightKg: Float,
    screenSize: WearScreenSize,
    isMetric: Boolean,
    compassHeadingDeg: Float,
    guideBackToRouteActive: Boolean,
    expandRequestToken: Long,
    actionPromptRequestToken: Long,
    suppressed: Boolean,
    onPauseGuidance: () -> Unit,
    onResumeGuidance: () -> Unit,
    onStopGuidance: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onFinishRecording: (String?) -> Unit,
    onDiscardRecording: () -> Unit,
    onMetricSelected: (Int, String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    if ((!guidanceState.active && !guidancePaused) || (!recordingState.active && !recordingState.saving)) return

    var expanded by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var showStopPrompt by remember { mutableStateOf(false) }
    var metricPickerSlot by remember { mutableIntStateOf(NO_SELECTED_SLOT) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(recordingState.active, recordingState.paused, recordingState.saving) {
        while (isActive && (recordingState.active || recordingState.saving)) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    LaunchedEffect(suppressed) {
        if (suppressed) {
            expanded = false
            showActions = false
            showStopPrompt = false
            metricPickerSlot = NO_SELECTED_SLOT
            onExpandedChange(false)
        }
    }
    LaunchedEffect(expanded) {
        onExpandedChange(expanded)
    }
    LaunchedEffect(actionPromptRequestToken) {
        if (actionPromptRequestToken != 0L && recordingState.active && !recordingState.saving) {
            showActions = true
        }
    }
    LaunchedEffect(expandRequestToken) {
        if (expandRequestToken != 0L && recordingState.active && !recordingState.saving) {
            showActions = false
            showStopPrompt = false
            expanded = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { onExpandedChange(false) }
    }
    if (suppressed) return

    val slots = normalizedRecordingDashboardSlots(metricSlots)
    val pageCount = (slots.size / RECORDING_DASHBOARD_PAGE_SLOT_COUNT).coerceAtLeast(1)
    LaunchedEffect(pageCount) {
        if (pageIndex >= pageCount) pageIndex = pageCount - 1
    }
    val snapshot =
        buildRecordingDashboardSnapshot(
            state = recordingState,
            nowMillis = nowMillis,
            userWeightKg = userWeightKg,
            backpackWeightKg = backpackWeightKg,
        )

    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier =
            Modifier
                .align(Alignment.Center)
                .fillMaxSize(),
    ) {
        CombinedFullscreenDashboard(
            guidanceState = guidanceState,
            guidancePaused = guidancePaused,
            recordingState = recordingState,
            slots =
                slots
                    .drop(pageIndex * RECORDING_DASHBOARD_PAGE_SLOT_COUNT)
                    .take(RECORDING_DASHBOARD_PAGE_SLOT_COUNT),
            pageIndex = pageIndex,
            pageCount = pageCount,
            snapshot = snapshot,
            screenSize = screenSize,
            isMetric = isMetric,
            compassHeadingDeg = compassHeadingDeg,
            guideBackToRouteActive = guideBackToRouteActive,
            onSlotLongPress = { slotIndex ->
                metricPickerSlot = pageIndex * RECORDING_DASHBOARD_PAGE_SLOT_COUNT + slotIndex
            },
            onCollapse = { expanded = false },
            onPreviousPage = {
                val nextPageIndex = (pageIndex - 1).floorMod(pageCount)
                pageIndex = nextPageIndex
                logRecordingDashboardPageChange(nextPageIndex, pageCount, "combined_swipe_down")
            },
            onNextPage = {
                val nextPageIndex = (pageIndex + 1).floorMod(pageCount)
                pageIndex = nextPageIndex
                logRecordingDashboardPageChange(nextPageIndex, pageCount, "combined_swipe_up")
            },
            onShowActions = { showActions = true },
        )
    }

    if (!expanded) {
        CombinedCompactPopup(
            guidanceState = guidanceState,
            guidancePaused = guidancePaused,
            recordingState = recordingState,
            snapshot = snapshot,
            isMetric = isMetric,
            compassHeadingDeg = compassHeadingDeg,
            guideBackToRouteActive = guideBackToRouteActive,
            screenSize = screenSize,
            modifier = Modifier.align(Alignment.TopCenter),
            onExpand = {
                showActions = false
                expanded = true
            },
            onShowActions = { showActions = true },
        )
    }

    if (showActions) {
        CombinedActionPrompt(
            guidancePaused = guidancePaused,
            recordingPaused = recordingState.paused,
            modifier =
                Modifier
                    .align(if (expanded) Alignment.BottomCenter else Alignment.TopCenter)
                    .padding(top = if (expanded) 0.dp else 104.dp, bottom = if (expanded) 28.dp else 0.dp),
            onPauseResumeGuidance = {
                showActions = false
                if (guidancePaused) onResumeGuidance() else onPauseGuidance()
            },
            onStopGuidance = {
                showActions = false
                onStopGuidance()
            },
            onPauseResumeRecording = {
                showActions = false
                if (recordingState.paused) onResumeRecording() else onPauseRecording()
            },
            onStopRecording = { showStopPrompt = true },
            onCancel = { showActions = false },
        )
    }

    if (showStopPrompt) {
        RecordingStopPromptCard(
            state = recordingState,
            snapshot = snapshot,
            isMetric = isMetric,
            onDiscard = {
                showStopPrompt = false
                showActions = false
                expanded = false
                onDiscardRecording()
            },
            onSave = { title ->
                showStopPrompt = false
                showActions = false
                expanded = false
                onFinishRecording(title)
            },
            onCancel = { showStopPrompt = false },
        )
    }

    if (metricPickerSlot != NO_SELECTED_SLOT) {
        val currentMetric = slots.getOrElse(metricPickerSlot) { SettingsRepository.RECORDING_METRIC_DISTANCE }
        OptionPickerDialog(
            visible = true,
            title = "Choose measure",
            selectedValue = currentMetric,
            options = recordingMetricDefinitions.map { it.id to it.label },
            onDismiss = { metricPickerSlot = NO_SELECTED_SLOT },
            onSelect = { metricId ->
                onMetricSelected(metricPickerSlot, metricId)
                DebugTelemetry.log(
                    "TraceRecording",
                    "event=dashboard_metric_selected slot=$metricPickerSlot metric=$metricId source=combined_fullscreen",
                )
                metricPickerSlot = NO_SELECTED_SLOT
            },
        )
    }
}

@Composable
private fun CombinedCompactPopup(
    guidanceState: TurnByTurnGuidanceState,
    guidancePaused: Boolean,
    recordingState: TraceRecordingUiState,
    snapshot: RecordingDashboardSnapshot,
    isMetric: Boolean,
    compassHeadingDeg: Float,
    guideBackToRouteActive: Boolean,
    screenSize: WearScreenSize,
    modifier: Modifier,
    onExpand: () -> Unit,
    onShowActions: () -> Unit,
) {
    val topPadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 54.dp
            WearScreenSize.MEDIUM -> 50.dp
            WearScreenSize.SMALL -> 46.dp
        }
    val width =
        when (screenSize) {
            WearScreenSize.LARGE -> 184.dp
            WearScreenSize.MEDIUM -> 172.dp
            WearScreenSize.SMALL -> 158.dp
        }
    val distance = formattedRecordingMetric(SettingsRepository.RECORDING_METRIC_DISTANCE, snapshot, isMetric)
    Box(
        modifier =
            modifier
                .padding(top = topPadding)
                .widthIn(max = width)
                .height(44.dp)
                .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
                .combinedClickable(
                    onClick = onExpand,
                    onLongClick = onShowActions,
                )
                .pointerInput(guidanceState.mode, guidanceState.nextInstruction, recordingState.paused) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (totalDrag < -COMBINED_POPUP_DRAG_THRESHOLD_PX) onExpand()
                            totalDrag = 0f
                        },
                        onDragCancel = { totalDrag = 0f },
                    ) { _, dragAmount ->
                        totalDrag += dragAmount
                    }
                }
                .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        cappedFontScale(maxFontScale = 1f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                CombinedGuidanceIcon(
                    state = guidanceState,
                    compassHeadingDeg = compassHeadingDeg,
                    guideBackToRouteActive = guideBackToRouteActive,
                    modifier = Modifier.size(16.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        text =
                            if (guidancePaused) {
                                "Paused"
                            } else {
                                combinedGuidanceCompactText(guidanceState, isMetric, guideBackToRouteActive)
                            },
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "REC ${distance.value}${distance.unit?.let { " $it" }.orEmpty()}",
                        color =
                            if (recordingState.paused) {
                                Color(0xFFFFB74D)
                            } else {
                                Color(0xFFFF5252)
                            },
                        fontSize = 8.sp,
                        lineHeight = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                SwipeExpandCue()
            }
        }
    }
}

@Composable
private fun CombinedFullscreenDashboard(
    guidanceState: TurnByTurnGuidanceState,
    guidancePaused: Boolean,
    recordingState: TraceRecordingUiState,
    slots: List<String>,
    pageIndex: Int,
    pageCount: Int,
    snapshot: RecordingDashboardSnapshot,
    screenSize: WearScreenSize,
    isMetric: Boolean,
    compassHeadingDeg: Float,
    guideBackToRouteActive: Boolean,
    onSlotLongPress: (Int) -> Unit,
    onCollapse: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onShowActions: () -> Unit,
) {
    val contentWidthFraction =
        when (screenSize) {
            WearScreenSize.LARGE -> 0.74f
            WearScreenSize.MEDIUM -> 0.70f
            WearScreenSize.SMALL -> 0.66f
        }
    val tileHeight =
        when (screenSize) {
            WearScreenSize.LARGE -> 36.dp
            WearScreenSize.MEDIUM -> 34.dp
            WearScreenSize.SMALL -> 31.dp
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
                .pointerInput(recordingState.active, recordingState.paused, pageIndex, pageCount) {
                    var totalDragX = 0f
                    var totalDragY = 0f
                    detectDragGestures(
                        onDragEnd = {
                            val horizontalDominates = abs(totalDragX) > abs(totalDragY)
                            val verticalDominates = abs(totalDragY) > abs(totalDragX)
                            when {
                                horizontalDominates && totalDragX > COMBINED_POPUP_DRAG_THRESHOLD_PX -> onCollapse()
                                verticalDominates && totalDragY < -COMBINED_POPUP_DRAG_THRESHOLD_PX -> onNextPage()
                                verticalDominates && totalDragY > COMBINED_POPUP_DRAG_THRESHOLD_PX -> onPreviousPage()
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(42.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        CombinedGuidanceIcon(
                            state = guidanceState,
                            compassHeadingDeg = compassHeadingDeg,
                            guideBackToRouteActive = guideBackToRouteActive,
                            modifier = Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text =
                                if (guidancePaused) {
                                    "Paused"
                                } else {
                                    combinedGuidancePrimaryText(guidanceState, guideBackToRouteActive)
                                },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            lineHeight = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = combinedGuidanceSecondaryText(guidanceState, isMetric, guideBackToRouteActive),
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 10.sp,
                            lineHeight = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                RecordingMiniTile(
                    metric = formattedRecordingMetric(slots[0], snapshot, isMetric),
                    height = tileHeight,
                    onLongPress = { onSlotLongPress(0) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RecordingMiniTile(
                        metric = formattedRecordingMetric(slots[1], snapshot, isMetric),
                        height = tileHeight,
                        onLongPress = { onSlotLongPress(1) },
                        modifier = Modifier.weight(1f),
                    )
                    RecordingMiniTile(
                        metric = formattedRecordingMetric(slots[2], snapshot, isMetric),
                        height = tileHeight,
                        onLongPress = { onSlotLongPress(2) },
                        modifier = Modifier.weight(1f),
                    )
                }
                RecordingMiniTile(
                    metric = formattedRecordingMetric(slots[3], snapshot, isMetric),
                    height = tileHeight,
                    onLongPress = { onSlotLongPress(3) },
                    modifier = Modifier.fillMaxWidth(0.86f),
                )
            }
        }
        RecordingStatusDot(
            paused = recordingState.paused,
            saving = recordingState.saving,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 15.dp),
            onClick = onShowActions,
        )
        CombinedPageIndicator(
            pageIndex = pageIndex,
            pageCount = pageCount,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp),
        )
        SwipeMinimizeHandle(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp),
        )
    }
}

@Composable
private fun RecordingMiniTile(
    metric: com.glancemap.glancemapwearos.presentation.features.recording.dashboard.RecordingMetricValue,
    height: androidx.compose.ui.unit.Dp,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .height(height)
                .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = metric.label,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 8.sp,
            lineHeight = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = metric.value,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            metric.unit?.let {
                Text(
                    text = it,
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CombinedActionPrompt(
    guidancePaused: Boolean,
    recordingPaused: Boolean,
    modifier: Modifier,
    onPauseResumeGuidance: () -> Unit,
    onStopGuidance: () -> Unit,
    onPauseResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .background(Color.Black.copy(alpha = 0.92f), RoundedCornerShape(14.dp))
                .padding(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CompactActionButton(
                    icon = if (guidancePaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (guidancePaused) "Resume guidance" else "Pause guidance",
                    onClick = onPauseResumeGuidance,
                    selected = true,
                )
                CompactActionButton(
                    icon = Icons.Default.Stop,
                    contentDescription = "Stop guidance",
                    onClick = onStopGuidance,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "Guide",
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    modifier = Modifier.width(38.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CompactActionButton(
                    icon = if (recordingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (recordingPaused) "Resume recording" else "Pause recording",
                    onClick = onPauseResumeRecording,
                    selected = true,
                )
                CompactActionButton(
                    icon = Icons.Default.Stop,
                    contentDescription = "Stop recording",
                    onClick = onStopRecording,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "REC",
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    modifier = Modifier.width(38.dp),
                )
            }
            Text(
                text = "Cancel",
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 10.sp,
                lineHeight = 10.sp,
                modifier =
                    Modifier
                        .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 14.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun CompactActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    tint: Color = Color.White,
) {
    Box(
        modifier =
            Modifier
                .size(34.dp)
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
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun RecordingStatusDot(
    paused: Boolean,
    saving: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(48.dp)
                .combinedClickable(onClick = onClick, onLongClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(20.dp)
                    .background(Color.White.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(12.dp)
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
}

@Composable
private fun CombinedGuidanceIcon(
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
                        GuidanceMode.TO_START -> (state.bearingToStartDegrees ?: 0f) - compassHeadingDeg
                        GuidanceMode.FOLLOW_ROUTE -> rotationForCommand(state.nextInstruction?.command)
                        GuidanceMode.FINISHED,
                        GuidanceMode.WAITING_FOR_LOCATION,
                        -> 0f
                    }
                },
            ),
    )
}

@Composable
private fun SwipeExpandCue(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.ExpandLess,
        contentDescription = "Swipe up to expand",
        tint = Color.White.copy(alpha = 0.62f),
        modifier = modifier.size(12.dp),
    )
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
private fun CombinedPageIndicator(
    pageIndex: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 1) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(pageCount) { index ->
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .height(if (index == pageIndex) 18.dp else 6.dp)
                        .background(
                            Color.White.copy(alpha = if (index == pageIndex) 0.72f else 0.28f),
                            RoundedCornerShape(4.dp),
                        ),
            )
        }
    }
}

private fun combinedGuidancePrimaryText(
    state: TurnByTurnGuidanceState,
    guideBackToRouteActive: Boolean,
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

private fun combinedGuidanceSecondaryText(
    state: TurnByTurnGuidanceState,
    isMetric: Boolean,
    guideBackToRouteActive: Boolean,
): String =
    if (guideBackToRouteActive) {
        state.distanceToRouteMeters?.let { formatLiveDistanceLabel(it, isMetric) } ?: "Find route"
    } else {
        when (state.mode) {
            GuidanceMode.WAITING_FOR_LOCATION -> state.trackTitle ?: "GPX guidance"
            GuidanceMode.TO_START ->
                state.distanceToStartMeters?.let { formatLiveDistanceLabel(it, isMetric) } ?: "Find the start"
            GuidanceMode.FOLLOW_ROUTE ->
                state.distanceToInstructionMeters?.let { formatLiveDistanceLabel(it, isMetric) } ?: "On route"
            GuidanceMode.FINISHED -> state.trackTitle ?: "Route complete"
        }
    }

private fun combinedGuidanceCompactText(
    state: TurnByTurnGuidanceState,
    isMetric: Boolean,
    guideBackToRouteActive: Boolean,
): String =
    if (guideBackToRouteActive) {
        state.distanceToRouteMeters?.let { "Route ${formatLiveDistanceLabel(it, isMetric)}" } ?: "To route"
    } else {
        when (state.mode) {
            GuidanceMode.WAITING_FOR_LOCATION -> "Waiting GPS"
            GuidanceMode.TO_START ->
                state.distanceToStartMeters?.let { "Start ${formatLiveDistanceLabel(it, isMetric)}" } ?: "To start"
            GuidanceMode.FOLLOW_ROUTE ->
                state.distanceToInstructionMeters?.let {
                    "${combinedGuidancePrimaryText(state, guideBackToRouteActive)} ${formatLiveDistanceLabel(it, isMetric)}"
                } ?: combinedGuidancePrimaryText(state, guideBackToRouteActive)
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
        RouteInstructionCommand.CONTINUE,
        RouteInstructionCommand.FINISH,
        null,
        -> 0f
    }

private const val NO_SELECTED_SLOT = -1
private const val COMBINED_POPUP_DRAG_THRESHOLD_PX = 24f
