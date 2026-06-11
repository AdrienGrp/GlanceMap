@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.glancemap.glancemapwearos.presentation.features.navigate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
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
import kotlin.math.min
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
    val recordingPageCount = (slots.size / RECORDING_DASHBOARD_PAGE_SLOT_COUNT).coerceAtLeast(1)
    val pageCount = recordingPageCount + 1
    LaunchedEffect(pageCount) {
        if (pageIndex >= pageCount) pageIndex = pageCount - 1
    }
    val recordingPageIndex = (pageIndex - 1).coerceIn(0, recordingPageCount - 1)
    val visibleSlots =
        slots
            .drop(recordingPageIndex * RECORDING_DASHBOARD_PAGE_SLOT_COUNT)
            .take(RECORDING_DASHBOARD_PAGE_SLOT_COUNT)
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
            slots = visibleSlots,
            pageIndex = pageIndex,
            pageCount = pageCount,
            recordingPageIndex = recordingPageIndex,
            snapshot = snapshot,
            screenSize = screenSize,
            isMetric = isMetric,
            compassHeadingDeg = compassHeadingDeg,
            guideBackToRouteActive = guideBackToRouteActive,
            onSlotLongPress = { slotIndex ->
                if (pageIndex > 0) {
                    metricPickerSlot = recordingPageIndex * RECORDING_DASHBOARD_PAGE_SLOT_COUNT + slotIndex
                }
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
    Box(
        modifier =
            modifier
                .padding(top = topPadding)
                .widthIn(max = compactWidth)
                .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = onExpand,
                    onLongClick = onShowActions,
                )
                .pointerInput(guidanceState.mode, guidanceState.nextInstruction, guidancePaused) {
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
                .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        cappedFontScale(maxFontScale = 1f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CombinedGuidanceIcon(
                    state = guidanceState,
                    compassHeadingDeg = compassHeadingDeg,
                    guideBackToRouteActive = guideBackToRouteActive,
                    modifier = Modifier.size(compactIconSize),
                )
                Text(
                    text =
                        if (guidancePaused) {
                            "Paused"
                        } else {
                            combinedGuidanceCompactText(guidanceState, isMetric, guideBackToRouteActive)
                        },
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = titleFont,
                    lineHeight = titleFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
    recordingPageIndex: Int,
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
    val focusRequester = remember { FocusRequester() }
    var rotaryAccumulator by remember(pageCount) { mutableFloatStateOf(0f) }

    LaunchedEffect(pageCount) {
        if (pageCount > 1) {
            focusRequester.requestFocus()
        }
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
                }
                .onPreRotaryScrollEvent { event ->
                    handleCombinedRotaryPageEvent(
                        delta = event.verticalScrollPixels,
                        pageCount = pageCount,
                        accumulator = rotaryAccumulator,
                        onAccumulatorChange = { rotaryAccumulator = it },
                        onPreviousPage = onPreviousPage,
                        onNextPage = onNextPage,
                    )
                }
                .focusRequester(focusRequester)
                .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        if (pageIndex == 0) {
            CombinedGuidancePage(
                state = guidanceState,
                paused = guidancePaused,
                screenSize = screenSize,
                isMetric = isMetric,
                compassHeadingDeg = compassHeadingDeg,
                guideBackToRouteActive = guideBackToRouteActive,
            )
        } else {
            CombinedRecordingPage(
                recordingState = recordingState,
                slots = slots,
                snapshot = snapshot,
                screenSize = screenSize,
                isMetric = isMetric,
                onSlotLongPress = onSlotLongPress,
                onShowActions = onShowActions,
            )
        }
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
private fun CombinedGuidancePage(
    state: TurnByTurnGuidanceState,
    paused: Boolean,
    screenSize: WearScreenSize,
    isMetric: Boolean,
    compassHeadingDeg: Float,
    guideBackToRouteActive: Boolean,
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

    CombinedRouteProgressRing(
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
                CombinedGuidanceIcon(
                    state = state,
                    compassHeadingDeg = compassHeadingDeg,
                    guideBackToRouteActive = guideBackToRouteActive,
                    modifier = Modifier.size(arrowIconSize),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text =
                    if (paused) {
                        "Paused"
                    } else {
                        combinedGuidancePrimaryText(state, guideBackToRouteActive)
                    },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = combinedGuidanceSecondaryText(state, isMetric, guideBackToRouteActive),
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

@Composable
private fun CombinedRecordingPage(
    recordingState: TraceRecordingUiState,
    slots: List<String>,
    snapshot: RecordingDashboardSnapshot,
    screenSize: WearScreenSize,
    isMetric: Boolean,
    onSlotLongPress: (Int) -> Unit,
    onShowActions: () -> Unit,
) {
    val tileSlots =
        List(RECORDING_DASHBOARD_PAGE_SLOT_COUNT) { index ->
            slots.getOrElse(index) { SettingsRepository.RECORDING_METRIC_DISTANCE }
        }
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
    val statusRowHeight =
        when (screenSize) {
            WearScreenSize.LARGE -> 48.dp
            WearScreenSize.MEDIUM -> 44.dp
            WearScreenSize.SMALL -> 40.dp
        }

    cappedFontScale(maxFontScale = 1f) {
        Column(
            modifier = Modifier.fillMaxWidth(contentWidthFraction),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(statusRowHeight),
                contentAlignment = Alignment.TopCenter,
            ) {
                RecordingStatusDot(
                    paused = recordingState.paused,
                    saving = recordingState.saving,
                    modifier = Modifier,
                    onClick = onShowActions,
                )
            }
            RecordingMiniTile(
                metric = formattedRecordingMetric(tileSlots[0], snapshot, isMetric),
                height = tileHeight,
                onLongPress = { onSlotLongPress(0) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RecordingMiniTile(
                    metric = formattedRecordingMetric(tileSlots[1], snapshot, isMetric),
                    height = tileHeight,
                    onLongPress = { onSlotLongPress(1) },
                    modifier = Modifier.weight(1f),
                )
                RecordingMiniTile(
                    metric = formattedRecordingMetric(tileSlots[2], snapshot, isMetric),
                    height = tileHeight,
                    onLongPress = { onSlotLongPress(2) },
                    modifier = Modifier.weight(1f),
                )
            }
            RecordingMiniTile(
                metric = formattedRecordingMetric(tileSlots[3], snapshot, isMetric),
                height = tileHeight,
                onLongPress = { onSlotLongPress(3) },
                modifier = Modifier.fillMaxWidth(0.86f),
            )
        }
    }
}

@Composable
private fun CombinedRouteProgressRing(
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
                .background(Color.Black.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
                .padding(horizontal = 2.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        cappedFontScale(maxFontScale = 1f) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactActionLabel("Guide")
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
                CompactActionLabel("REC")
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
                CompactCancelButton(onClick = onCancel)
            }
        }
    }
}

@Composable
private fun CompactActionLabel(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.66f),
        fontSize = 8.sp,
        lineHeight = 8.sp,
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(28.dp),
    )
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
                .size(48.dp)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.14f),
                        CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else tint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CompactCancelButton(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .width(56.dp)
                .height(48.dp)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .height(28.dp)
                    .width(50.dp)
                    .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Cancel",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 9.sp,
                lineHeight = 9.sp,
                maxLines = 1,
            )
        }
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
        modifier =
            modifier
                .size(12.dp)
                .rotate(180f),
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

private fun handleCombinedRotaryPageEvent(
    delta: Float,
    pageCount: Int,
    accumulator: Float,
    onAccumulatorChange: (Float) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
): Boolean {
    if (pageCount <= 1 || !delta.isFinite() || delta == 0f) return false
    var nextAccumulator =
        if (accumulator != 0f && (accumulator > 0f) != (delta > 0f)) {
            0f
        } else {
            accumulator
        }
    nextAccumulator += delta
    var consumed = false
    while (nextAccumulator >= COMBINED_POPUP_ROTARY_PAGE_THRESHOLD_PX) {
        onNextPage()
        nextAccumulator -= COMBINED_POPUP_ROTARY_PAGE_THRESHOLD_PX
        consumed = true
    }
    while (nextAccumulator <= -COMBINED_POPUP_ROTARY_PAGE_THRESHOLD_PX) {
        onPreviousPage()
        nextAccumulator += COMBINED_POPUP_ROTARY_PAGE_THRESHOLD_PX
        consumed = true
    }
    onAccumulatorChange(nextAccumulator)
    return consumed || nextAccumulator != 0f
}

private const val NO_SELECTED_SLOT = -1
private const val COMBINED_POPUP_DRAG_THRESHOLD_PX = 24f
private const val COMBINED_POPUP_ROTARY_PAGE_THRESHOLD_PX = 24f
