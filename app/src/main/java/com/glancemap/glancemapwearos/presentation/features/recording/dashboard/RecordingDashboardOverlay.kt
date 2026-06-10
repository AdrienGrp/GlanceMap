@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.glancemap.glancemapwearos.presentation.features.recording.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.features.recording.buildRecordingTitle
import com.glancemap.glancemapwearos.presentation.features.recording.TraceRecordingUiState
import com.glancemap.glancemapwearos.presentation.features.settings.OptionPickerDialog
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import com.glancemap.glancemapwearos.presentation.ui.cappedFontScale
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
internal fun BoxScope.RecordingDashboardOverlay(
    state: TraceRecordingUiState,
    metricSlots: List<String>,
    screenSize: WearScreenSize,
    isMetric: Boolean,
    suppressed: Boolean,
    toolButtonEdgePadding: Dp,
    toolButtonSize: Dp,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStopConfirmed: (String?) -> Unit,
    onDiscard: () -> Unit,
    onMetricSelected: (slotIndex: Int, metricId: String) -> Unit,
    expandRequestToken: Long,
    actionPromptRequestToken: Long,
    onExpandedChange: (Boolean) -> Unit,
) {
    if (!state.active && !state.saving) return

    var expanded by remember { mutableStateOf(false) }
    var showCompactControls by remember { mutableStateOf(false) }
    var showStopPrompt by remember { mutableStateOf(false) }
    var metricPickerSlot by remember { mutableIntStateOf(NO_SELECTED_SLOT) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(state.active, state.paused, state.saving) {
        while (isActive && (state.active || state.saving)) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    LaunchedEffect(suppressed) {
        if (suppressed) {
            expanded = false
            showCompactControls = false
            showStopPrompt = false
            metricPickerSlot = NO_SELECTED_SLOT
            onExpandedChange(false)
        }
    }
    LaunchedEffect(expanded) {
        onExpandedChange(expanded)
    }
    LaunchedEffect(actionPromptRequestToken) {
        if (actionPromptRequestToken != 0L && state.active && !state.saving) {
            showCompactControls = true
        }
    }
    LaunchedEffect(expandRequestToken) {
        if (expandRequestToken != 0L && state.active && !state.saving) {
            showCompactControls = false
            showStopPrompt = false
            expanded = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { onExpandedChange(false) }
    }
    if (suppressed) return

    val slots = normalizedSlots(metricSlots)
    val snapshot = buildRecordingDashboardSnapshot(state, nowMillis)

    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.Center),
    ) {
        ExpandedRecordingDashboard(
            state = state,
            slots = slots,
            snapshot = snapshot,
            screenSize = screenSize,
            isMetric = isMetric,
            onSlotLongPress = { slotIndex -> metricPickerSlot = slotIndex },
            onCollapse = { expanded = false },
            onShowActions = { showCompactControls = true },
        )
    }

    if (!expanded) {
        if (showCompactControls) {
            CompactRecordingControls(
                state = state,
                snapshot = snapshot,
                isMetric = isMetric,
                toolButtonEdgePadding = toolButtonEdgePadding,
                toolButtonSize = toolButtonSize,
                modifier = Modifier.align(Alignment.CenterEnd),
                onPauseResume = {
                    showCompactControls = false
                    if (state.paused) {
                        onResume()
                    } else {
                        onPause()
                    }
                },
                onStop = {
                    showStopPrompt = true
                },
                onExpand = {
                    showCompactControls = false
                    expanded = true
                },
            )
        }
    }

    if (showStopPrompt) {
        RecordingStopPromptCard(
            state = state,
            snapshot = snapshot,
            isMetric = isMetric,
            onDiscard = {
                showStopPrompt = false
                showCompactControls = false
                expanded = false
                onDiscard()
            },
            onSave = { title ->
                showStopPrompt = false
                showCompactControls = false
                expanded = false
                onStopConfirmed(title)
            },
            onCancel = { showStopPrompt = false },
            modifier =
                Modifier
                    .align(Alignment.Center),
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
                metricPickerSlot = NO_SELECTED_SLOT
            },
        )
    }
}

@Composable
private fun CompactRecordingControls(
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
    Column(
        modifier = modifier.width(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.ExpandLess,
            contentDescription = "Swipe up to expand recording",
            tint = Color.White.copy(alpha = 0.86f),
            modifier = Modifier.size(11.dp),
        )
        Icon(
            imageVector = Icons.Default.ExpandLess,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.58f),
            modifier = Modifier.size(9.dp),
        )
        Box(
            modifier =
                Modifier
                    .padding(top = 1.dp)
                    .width(12.dp)
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.42f), RoundedCornerShape(1.dp)),
        )
    }
}

@Composable
private fun CompactControlButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

@Composable
private fun RecordingStopPromptCard(
    state: TraceRecordingUiState,
    snapshot: RecordingDashboardSnapshot,
    isMetric: Boolean,
    onDiscard: () -> Unit,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val distance = formattedRecordingMetric(SettingsRepository.RECORDING_METRIC_DISTANCE, snapshot, isMetric)
    val duration = formattedRecordingMetric(SettingsRepository.RECORDING_METRIC_DURATION, snapshot, isMetric)
    val elevationGain = formattedRecordingMetric(SettingsRepository.RECORDING_METRIC_ELEVATION_GAIN, snapshot, isMetric)
    val elevationLoss = formattedRecordingMetric(SettingsRepository.RECORDING_METRIC_ELEVATION_LOSS, snapshot, isMetric)
    val defaultTitle =
        remember(state.startedAtMillis) {
            buildRecordingTitle(state.startedAtMillis ?: System.currentTimeMillis())
        }
    var draftTitle by remember(defaultTitle) { mutableStateOf(defaultTitle) }
    val shortRecording = isShortRecording(snapshot, state)
    Box(
        modifier =
            modifier
                .widthIn(max = 184.dp)
                .background(Color.Black.copy(alpha = 0.96f), RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        cappedFontScale(maxFontScale = 1f) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (shortRecording) "Short recording" else "Save recording",
                    color = Color(0xFFFFB74D),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    lineHeight = 13.sp,
                    textAlign = TextAlign.Center,
                )
                BasicTextField(
                    value = draftTitle,
                    onValueChange = { draftTitle = it.take(MAX_RECORDING_TITLE_LENGTH) },
                    singleLine = true,
                    textStyle =
                        TextStyle(
                            color = Color.White,
                            fontSize = 11.sp,
                            lineHeight = 11.sp,
                            textAlign = TextAlign.Center,
                        ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    decorationBox = { innerTextField ->
                        if (draftTitle.isBlank()) {
                            Text(
                                text = "Name",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 11.sp,
                                lineHeight = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        innerTextField()
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RecordingMiniStat("${distance.value} ${distance.unit.orEmpty()}".trim(), "Dist")
                    RecordingMiniStat(duration.value, "Time")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RecordingMiniStat("${elevationGain.value} ${elevationGain.unit.orEmpty()}".trim(), "Up")
                    RecordingMiniStat("${elevationLoss.value} ${elevationLoss.unit.orEmpty()}".trim(), "Down")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    ShortPromptButton(text = "Discard", selected = false, onClick = onDiscard, width = 56.dp)
                    ShortPromptButton(text = "Save", selected = true, onClick = { onSave(draftTitle) }, width = 48.dp)
                    ShortPromptButton(text = "Cancel", selected = false, onClick = onCancel, width = 54.dp)
                }
            }
        }
    }
}

@Composable
private fun ShortPromptButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    width: Dp,
) {
    Box(
        modifier =
            Modifier
                .width(width)
                .height(48.dp)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .width(width)
                    .height(32.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f),
                        RoundedCornerShape(7.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                lineHeight = 10.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ExpandedRecordingDashboard(
    state: TraceRecordingUiState,
    slots: List<String>,
    snapshot: RecordingDashboardSnapshot,
    screenSize: WearScreenSize,
    isMetric: Boolean,
    onSlotLongPress: (Int) -> Unit,
    onCollapse: () -> Unit,
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
                .pointerInput(state.active, state.paused) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (totalDrag > POPUP_MINIMIZE_DRAG_THRESHOLD_PX) onCollapse()
                            totalDrag = 0f
                        },
                        onDragCancel = { totalDrag = 0f },
                    ) { _, dragAmount ->
                        totalDrag += dragAmount
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
private fun RecordingActionPromptCard(
    state: TraceRecordingUiState,
    snapshot: RecordingDashboardSnapshot,
    isMetric: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStopConfirmed: (String?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val distance = formattedRecordingMetric(SettingsRepository.RECORDING_METRIC_DISTANCE, snapshot, isMetric)
    val duration = formattedRecordingMetric(SettingsRepository.RECORDING_METRIC_DURATION, snapshot, isMetric)
    val elevationGain = formattedRecordingMetric(SettingsRepository.RECORDING_METRIC_ELEVATION_GAIN, snapshot, isMetric)
    val elevationLoss = formattedRecordingMetric(SettingsRepository.RECORDING_METRIC_ELEVATION_LOSS, snapshot, isMetric)
    Box(
        modifier =
            modifier
                .widthIn(max = 174.dp)
                .background(Color.Black.copy(alpha = 0.95f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        cappedFontScale(maxFontScale = 1f) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (state.paused) "Recording paused" else "Recording",
                    color = Color(0xFFFFB74D),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RecordingMiniStat("${distance.value} ${distance.unit.orEmpty()}".trim(), "Dist")
                    RecordingMiniStat(duration.value, "Time")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RecordingMiniStat("${elevationGain.value} ${elevationGain.unit.orEmpty()}".trim(), "Up")
                    RecordingMiniStat("${elevationLoss.value} ${elevationLoss.unit.orEmpty()}".trim(), "Down")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    PromptButton(
                        text = if (state.paused) "Resume" else "Pause",
                        selected = true,
                        onClick = if (state.paused) onResume else onPause,
                        icon = if (state.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    )
                    HoldStopButton(onConfirmed = { onStopConfirmed(null) })
                    PromptButton(text = "Cancel", selected = false, onClick = onCancel)
                }
            }
        }
    }
}

@Composable
private fun RecordingMiniStat(
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            maxLines = 1,
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.56f),
            fontSize = 8.sp,
            lineHeight = 8.sp,
        )
    }
}

@Composable
private fun PromptButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier =
            Modifier
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f),
                    RoundedCornerShape(6.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
            )
        }
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            lineHeight = 10.sp,
        )
    }
}

@Composable
private fun HoldStopButton(onConfirmed: () -> Unit) {
    var holding by remember { mutableStateOf(false) }
    Box(
        modifier =
            Modifier
                .background(
                    if (holding) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.12f),
                    RoundedCornerShape(6.dp),
                )
                .pointerInput(Unit) {
                    coroutineScope {
                        awaitEachGesture {
                            awaitFirstDown()
                            holding = true
                            val holdJob =
                                launch {
                                    delay(STOP_CONFIRM_HOLD_MS)
                                    if (holding) {
                                        holding = false
                                        onConfirmed()
                                    }
                                }
                            waitForUpOrCancellation()
                            holdJob.cancel()
                            holding = false
                        }
                    }
                }
                .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (holding) "Hold..." else "Stop",
            color = if (holding) MaterialTheme.colorScheme.onError else Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            lineHeight = 10.sp,
        )
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

private fun normalizedSlots(metricSlots: List<String>): List<String> =
    (metricSlots + SettingsRepository.DEFAULT_RECORDING_DASHBOARD_METRICS).take(RECORDING_DASHBOARD_SLOT_COUNT)

private fun isShortRecording(
    snapshot: RecordingDashboardSnapshot,
    state: TraceRecordingUiState,
): Boolean =
    state.points.size < MIN_SAVE_POINT_COUNT ||
        snapshot.distanceMeters < SHORT_RECORDING_DISTANCE_METERS ||
        snapshot.durationSeconds < SHORT_RECORDING_DURATION_SECONDS

private const val NO_SELECTED_SLOT = -1
private const val RECORDING_DASHBOARD_SLOT_COUNT = 4
private const val POPUP_MINIMIZE_DRAG_THRESHOLD_PX = 24f
private const val POPUP_EXPAND_DRAG_THRESHOLD_PX = 24f
private const val STOP_CONFIRM_HOLD_MS = 3_000L
private const val MIN_SAVE_POINT_COUNT = 2
private const val SHORT_RECORDING_DISTANCE_METERS = 20.0
private const val SHORT_RECORDING_DURATION_SECONDS = 10.0
private const val MAX_RECORDING_TITLE_LENGTH = 64
