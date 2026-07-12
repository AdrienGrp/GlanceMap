package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.data.repository.RECORDING_DASHBOARD_MAX_PAGE_COUNT
import com.glancemap.glancemapwearos.data.repository.RECORDING_DASHBOARD_MIN_PAGE_COUNT
import com.glancemap.glancemapwearos.data.repository.RECORDING_DASHBOARD_PAGE_SLOT_COUNT
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.data.repository.normalizeRecordingDashboardMetricSlots
import com.glancemap.glancemapwearos.presentation.features.recording.dashboard.recordingMetricDefinitions
import com.glancemap.glancemapwearos.presentation.features.recording.dashboard.recordingMetricPickerOptions
import com.glancemap.glancemapwearos.presentation.ui.WearHelpDialog

@Composable
fun RecordingDashboardSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenRecordingSettings: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()
    val dashboardMetricSlots by viewModel.recordingDashboardMetricSlots.collectAsState()
    val activityProfile by viewModel.activityProfile.collectAsState()
    val dashboardSlots = normalizeRecordingDashboardMetricSlots(dashboardMetricSlots)
    val dashboardPageCount = dashboardSlots.size / RECORDING_DASHBOARD_PAGE_SLOT_COUNT
    var selectedDashboardPage by remember { mutableStateOf(0) }
    var pendingAddedPage by remember { mutableStateOf<Int?>(null) }
    var selectedDashboardSlot by remember { mutableStateOf<Int?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(dashboardPageCount) {
        pendingAddedPage?.let { requestedPage ->
            if (requestedPage < dashboardPageCount) {
                selectedDashboardPage = requestedPage
                pendingAddedPage = null
            }
        }
        selectedDashboardPage = selectedDashboardPage.coerceIn(0, dashboardPageCount - 1)
    }

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
                applyTopPadding = false,
            )
        }
        item {
            RecordingDashboardPageSwitcher(
                pageIndex = selectedDashboardPage,
                pageCount = dashboardPageCount,
                dashboardLabel =
                    if (activityProfile == SettingsRepository.ACTIVITY_PROFILE_BIKE) {
                        "Bike"
                    } else {
                        "Hike"
                    },
                onClick = {
                    selectedDashboardPage = (selectedDashboardPage + 1) % dashboardPageCount
                },
            )
        }
        if (dashboardPageCount < RECORDING_DASHBOARD_MAX_PAGE_COUNT) {
            item {
                SettingsSectionChip(
                    label = "Add dashboard page",
                    secondaryLabel = "$dashboardPageCount of $RECORDING_DASHBOARD_MAX_PAGE_COUNT pages",
                    iconImageVector = Icons.Filled.Add,
                    onClick = {
                        pendingAddedPage = dashboardPageCount
                        viewModel.addRecordingDashboardPage()
                    },
                )
            }
        }
        if (dashboardPageCount > RECORDING_DASHBOARD_MIN_PAGE_COUNT) {
            item {
                SettingsPickerChip(
                    label = "Delete this page",
                    secondaryLabel = recordingDashboardPageLabel(selectedDashboardPage),
                    iconImageVector = Icons.Filled.Delete,
                    onClick = {
                        val pageToDelete = selectedDashboardPage
                        selectedDashboardPage =
                            selectedDashboardPage.coerceAtMost(dashboardPageCount - 2)
                        viewModel.deleteRecordingDashboardPage(pageToDelete)
                    },
                )
            }
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
        activityProfile = activityProfile,
        onDismiss = { showInfoDialog = false },
    )
    selectedDashboardSlot?.let { slotIndex ->
        OptionPickerDialog(
            visible = true,
            title = RECORDING_DASHBOARD_SLOT_LABELS[slotIndex % RECORDING_DASHBOARD_PAGE_SLOT_COUNT],
            selectedValue = dashboardSlots[slotIndex],
            options = recordingMetricPickerOptions,
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

@Suppress("FunctionNaming") // Compose functions use PascalCase by convention.
@Composable
private fun RecordingDashboardInfoDialog(
    visible: Boolean,
    activityProfile: String,
    onDismiss: () -> Unit,
) {
    WearHelpDialog(
        visible = visible,
        title =
            if (activityProfile == SettingsRepository.ACTIVITY_PROFILE_BIKE) {
                "Bike dashboard"
            } else {
                "Hike dashboard"
            },
        onDismiss = onDismiss,
        lines =
            listOf(
                "Hike and Bike keep separate dashboards.",
                "In the REC popup, long press any metric to change it.",
            ),
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

private val RECORDING_DASHBOARD_SLOT_LABELS = listOf("Top measure", "Left measure", "Right measure", "Bottom measure")

private fun recordingDashboardPageLabel(pageIndex: Int): String = "Page ${pageIndex + 1}"

private fun recordingMetricLabel(metricId: String): String = recordingMetricDefinitions.firstOrNull { it.id == metricId }?.label ?: "Distance"
