package com.glancemap.glancemapwearos.presentation.features.recording.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.unit.Dp
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.features.recording.TraceRecordingUiState
import com.glancemap.glancemapwearos.presentation.features.settings.OptionPickerDialog
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
    var dashboardPageIndex by remember { mutableIntStateOf(0) }
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
    val pageCount = (slots.size / RECORDING_DASHBOARD_PAGE_SLOT_COUNT).coerceAtLeast(1)
    LaunchedEffect(pageCount) {
        if (dashboardPageIndex >= pageCount) {
            dashboardPageIndex = pageCount - 1
        }
    }
    val snapshot = buildRecordingDashboardSnapshot(state, nowMillis)

    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.Center),
    ) {
        ExpandedRecordingDashboard(
            state = state,
            slots =
                slots
                    .drop(dashboardPageIndex * RECORDING_DASHBOARD_PAGE_SLOT_COUNT)
                    .take(RECORDING_DASHBOARD_PAGE_SLOT_COUNT),
            pageIndex = dashboardPageIndex,
            pageCount = pageCount,
            snapshot = snapshot,
            screenSize = screenSize,
            isMetric = isMetric,
            onSlotLongPress = { slotIndex ->
                metricPickerSlot = dashboardPageIndex * RECORDING_DASHBOARD_PAGE_SLOT_COUNT + slotIndex
            },
            onCollapse = { expanded = false },
            onPreviousPage = {
                val nextPageIndex = (dashboardPageIndex - 1).floorMod(pageCount)
                dashboardPageIndex = nextPageIndex
                logRecordingDashboardPageChange(nextPageIndex, pageCount, "swipe_down")
            },
            onNextPage = {
                val nextPageIndex = (dashboardPageIndex + 1).floorMod(pageCount)
                dashboardPageIndex = nextPageIndex
                logRecordingDashboardPageChange(nextPageIndex, pageCount, "swipe_up")
            },
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
                    "event=dashboard_metric_selected slot=$metricPickerSlot metric=$metricId source=fullscreen",
                )
                metricPickerSlot = NO_SELECTED_SLOT
            },
        )
    }
}

internal fun normalizedRecordingDashboardSlots(metricSlots: List<String>): List<String> =
    normalizeRecordingDashboardSlotList(metricSlots)

private fun normalizedSlots(metricSlots: List<String>): List<String> =
    normalizedRecordingDashboardSlots(metricSlots)

private fun normalizeRecordingDashboardSlotList(metricSlots: List<String>): List<String> {
    val migratedSlots =
        if (metricSlots.take(RECORDING_DASHBOARD_PAGE_SLOT_COUNT) == LEGACY_RECORDING_DASHBOARD_PAGE_ONE_METRICS) {
            SettingsRepository.DEFAULT_RECORDING_DASHBOARD_METRICS +
                metricSlots.drop(RECORDING_DASHBOARD_PAGE_SLOT_COUNT)
        } else {
            metricSlots
        }
    return (
        migratedSlots.take(RECORDING_DASHBOARD_TOTAL_SLOT_COUNT) +
            SettingsRepository.DEFAULT_RECORDING_DASHBOARD_ALL_METRICS.drop(migratedSlots.size)
    ).take(RECORDING_DASHBOARD_TOTAL_SLOT_COUNT)
}

private const val NO_SELECTED_SLOT = -1
private val LEGACY_RECORDING_DASHBOARD_PAGE_ONE_METRICS =
    listOf(
        SettingsRepository.RECORDING_METRIC_DISTANCE,
        SettingsRepository.RECORDING_METRIC_DURATION,
        SettingsRepository.RECORDING_METRIC_ELEVATION_GAIN,
        SettingsRepository.RECORDING_METRIC_ELEVATION_LOSS,
    )
