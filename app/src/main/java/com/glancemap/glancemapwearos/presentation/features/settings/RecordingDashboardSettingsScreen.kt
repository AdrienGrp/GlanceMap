package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.features.recording.dashboard.recordingMetricDefinitions

@Composable
fun RecordingDashboardSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenRecordingSettings: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()
    val dashboardMetricSlots by viewModel.recordingDashboardMetricSlots.collectAsState()
    val dashboardSlots = normalizedDashboardSlots(dashboardMetricSlots)
    var showDashboardPagePicker by remember { mutableStateOf(false) }
    var selectedDashboardPage by remember { mutableStateOf(0) }
    var selectedDashboardSlot by remember { mutableStateOf<Int?>(null) }

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            SettingsSectionChip(
                label = "Recording settings",
                secondaryLabel = "Back to REC settings",
                onClick = onOpenRecordingSettings,
            )
        }
        item {
            RecordingDashboardPageSetting(
                selectedDashboardPage = selectedDashboardPage,
                onClick = { showDashboardPagePicker = true },
            )
        }
        RECORDING_DASHBOARD_SLOT_LABELS.forEachIndexed { pageSlotIndex, label ->
            val absoluteSlotIndex = selectedDashboardPage * RECORDING_DASHBOARD_PAGE_SLOT_COUNT + pageSlotIndex
            item {
                RecordingMetricSlotSetting(
                    label = label,
                    metricId = dashboardSlots[absoluteSlotIndex],
                    onClick = { selectedDashboardSlot = absoluteSlotIndex },
                )
            }
        }
    }

    OptionPickerDialog(
        visible = showDashboardPagePicker,
        title = "Dashboard page",
        selectedValue = selectedDashboardPage,
        options = RECORDING_DASHBOARD_PAGE_OPTIONS.map { it to recordingDashboardPageLabel(it) },
        onDismiss = { showDashboardPagePicker = false },
        onSelect = { page ->
            selectedDashboardPage = page
            showDashboardPagePicker = false
        },
    )
    selectedDashboardSlot?.let { slotIndex ->
        OptionPickerDialog(
            visible = true,
            title = RECORDING_DASHBOARD_SLOT_LABELS[slotIndex % RECORDING_DASHBOARD_PAGE_SLOT_COUNT],
            selectedValue = dashboardSlots[slotIndex],
            options = recordingMetricDefinitions.map { it.id to it.label },
            onDismiss = { selectedDashboardSlot = null },
            onSelect = { metricId ->
                viewModel.setRecordingDashboardMetricSlot(slotIndex, metricId)
                DebugTelemetry.log(
                    "TraceRecording",
                    "event=dashboard_metric_selected slot=$slotIndex metric=$metricId source=settings_dashboard",
                )
                selectedDashboardSlot = null
            },
        )
    }
}

@Composable
private fun RecordingDashboardPageSetting(
    selectedDashboardPage: Int,
    onClick: () -> Unit,
) {
    SettingsPickerChip(
        label = "Dashboard page",
        secondaryLabel = recordingDashboardPageLabel(selectedDashboardPage),
        onClick = onClick,
    )
}

@Composable
private fun RecordingMetricSlotSetting(
    label: String,
    metricId: String,
    onClick: () -> Unit,
) {
    SettingsPickerChip(
        label = label,
        secondaryLabel = recordingMetricLabel(metricId),
        onClick = onClick,
    )
}

private val RECORDING_DASHBOARD_PAGE_OPTIONS = listOf(0, 1)
private val RECORDING_DASHBOARD_SLOT_LABELS = listOf("Top measure", "Left measure", "Right measure", "Bottom measure")
private const val RECORDING_DASHBOARD_PAGE_SLOT_COUNT = 4
private const val RECORDING_DASHBOARD_TOTAL_SLOT_COUNT = 8

private fun recordingDashboardPageLabel(pageIndex: Int): String = "Page ${pageIndex + 1}"

private fun recordingMetricLabel(metricId: String): String =
    recordingMetricDefinitions.firstOrNull { it.id == metricId }?.label ?: "Distance"

private fun normalizedDashboardSlots(slots: List<String>): List<String> {
    val migratedSlots =
        if (slots.take(RECORDING_DASHBOARD_PAGE_SLOT_COUNT) == LEGACY_RECORDING_DASHBOARD_PAGE_ONE_METRICS) {
            SettingsRepository.DEFAULT_RECORDING_DASHBOARD_METRICS +
                slots.drop(RECORDING_DASHBOARD_PAGE_SLOT_COUNT)
        } else {
            slots
        }
    return (
        migratedSlots.take(RECORDING_DASHBOARD_TOTAL_SLOT_COUNT) +
            SettingsRepository.DEFAULT_RECORDING_DASHBOARD_ALL_METRICS.drop(migratedSlots.size)
    ).take(RECORDING_DASHBOARD_TOTAL_SLOT_COUNT)
}

private val LEGACY_RECORDING_DASHBOARD_PAGE_ONE_METRICS =
    listOf(
        SettingsRepository.RECORDING_METRIC_DISTANCE,
        SettingsRepository.RECORDING_METRIC_DURATION,
        SettingsRepository.RECORDING_METRIC_ELEVATION_GAIN,
        SettingsRepository.RECORDING_METRIC_ELEVATION_LOSS,
    )
