package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.data.repository.RECORDING_DASHBOARD_PAGE_SLOT_COUNT
import com.glancemap.glancemapwearos.data.repository.normalizeRecordingDashboardMetricSlots
import com.glancemap.glancemapwearos.presentation.features.recording.dashboard.recordingMetricDefinitions
import com.glancemap.glancemapwearos.presentation.ui.WearHelpDialog

@Composable
fun RecordingDashboardSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenRecordingSettings: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()
    val dashboardMetricSlots by viewModel.recordingDashboardMetricSlots.collectAsState()
    val dashboardSlots = normalizeRecordingDashboardMetricSlots(dashboardMetricSlots)
    var selectedDashboardPage by remember { mutableStateOf(0) }
    var selectedDashboardSlot by remember { mutableStateOf<Int?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            SettingsInfoButton(
                contentDescription = "Dashboard info",
                onClick = { showInfoDialog = true },
            )
        }
        item {
            RecordingSettingsShortcutChip(
                onClick = onOpenRecordingSettings,
            )
        }
        item {
            SettingsOptionPickerRow(
                label = "Dashboard page",
                selectedValue = selectedDashboardPage,
                options = RECORDING_DASHBOARD_PAGE_OPTIONS.map { it to recordingDashboardPageLabel(it) },
                secondaryLabel = recordingDashboardPageLabel(selectedDashboardPage),
                onSelect = { selectedDashboardPage = it },
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
    RecordingDashboardInfoDialog(
        visible = showInfoDialog,
        onDismiss = { showInfoDialog = false },
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
private fun RecordingDashboardInfoDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    WearHelpDialog(
        visible = visible,
        title = "Dashboard",
        onDismiss = onDismiss,
        lines = listOf("In the REC popup, long press any metric to change it."),
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

private fun recordingDashboardPageLabel(pageIndex: Int): String = "Page ${pageIndex + 1}"

private fun recordingMetricLabel(metricId: String): String =
    recordingMetricDefinitions.firstOrNull { it.id == metricId }?.label ?: "Distance"
