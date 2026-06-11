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
fun RecordingSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenGeneralSettings: () -> Unit,
    onOpenExternalSensors: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()
    val sampleIntervalSeconds by viewModel.recordingSampleIntervalSeconds.collectAsState()
    val elevationSource by viewModel.recordingElevationSource.collectAsState()
    val dashboardMetricSlots by viewModel.recordingDashboardMetricSlots.collectAsState()
    val showSavedGpxOnMap by viewModel.recordingShowSavedGpxOnMap.collectAsState()
    val startWithTurnByTurn by viewModel.recordingStartWithTurnByTurn.collectAsState()
    val dashboardSlots = normalizedDashboardSlots(dashboardMetricSlots)
    var showIntervalPicker by remember { mutableStateOf(false) }
    var showElevationSourcePicker by remember { mutableStateOf(false) }
    var showDashboardPagePicker by remember { mutableStateOf(false) }
    var selectedDashboardPage by remember { mutableStateOf(0) }
    var selectedDashboardSlot by remember { mutableStateOf<Int?>(null) }

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            GeneralSettingsShortcutChip(onClick = onOpenGeneralSettings)
        }
        item {
            RecordingGpsIntervalSetting(
                sampleIntervalSeconds = sampleIntervalSeconds,
                onClick = { showIntervalPicker = true },
            )
        }
        item {
            RecordingElevationSourceSetting(
                elevationSource = elevationSource,
                onClick = { showElevationSourcePicker = true },
            )
        }
        item {
            SettingsToggleChip(
                checked = showSavedGpxOnMap,
                onCheckedChanged = viewModel::setRecordingShowSavedGpxOnMap,
                label = "Show saved activity",
                secondaryLabel = if (showSavedGpxOnMap) "On map after save" else "Saved but hidden",
            )
        }
        item {
            SettingsToggleChip(
                checked = startWithTurnByTurn,
                onCheckedChanged = viewModel::setRecordingStartWithTurnByTurn,
                label = "REC with guidance",
                secondaryLabel = if (startWithTurnByTurn) "Starts with turn-by-turn" else "Manual start",
            )
        }
        item {
            RecordingExternalSensorsSetting(onClick = onOpenExternalSensors)
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
        visible = showIntervalPicker,
        title = "Record GPS every",
        selectedValue = sampleIntervalSeconds,
        options = RECORDING_INTERVAL_OPTIONS_SECONDS.map { it to recordingIntervalLabel(it) },
        onDismiss = { showIntervalPicker = false },
        onSelect = viewModel::setRecordingSampleIntervalSeconds,
    )
    OptionPickerDialog(
        visible = showElevationSourcePicker,
        title = "Elevation source",
        selectedValue = elevationSource,
        options = RECORDING_ELEVATION_SOURCE_OPTIONS.map { it to recordingElevationSourceLabel(it) },
        onDismiss = { showElevationSourcePicker = false },
        onSelect = viewModel::setRecordingElevationSource,
    )
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
                    "event=dashboard_metric_selected slot=$slotIndex metric=$metricId source=settings",
                )
                selectedDashboardSlot = null
            },
        )
    }
}

@Composable
private fun RecordingGpsIntervalSetting(
    sampleIntervalSeconds: Int,
    onClick: () -> Unit,
) {
    SettingsPickerChip(
        label = "Record GPS every",
        secondaryLabel = recordingIntervalLabel(sampleIntervalSeconds),
        onClick = onClick,
    )
}

@Composable
private fun RecordingElevationSourceSetting(
    elevationSource: String,
    onClick: () -> Unit,
) {
    SettingsPickerChip(
        label = "Elevation source",
        secondaryLabel = recordingElevationSourceLabel(elevationSource),
        onClick = onClick,
    )
}

@Composable
private fun RecordingExternalSensorsSetting(onClick: () -> Unit) {
    SettingsSectionChip(
        label = "External sensors",
        secondaryLabel = "Heart, steps, pods",
        onClick = onClick,
    )
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

private val RECORDING_INTERVAL_OPTIONS_SECONDS = listOf(1, 2, 5, 10, 15, 30, 60)
private val RECORDING_ELEVATION_SOURCE_OPTIONS =
    listOf(
        SettingsRepository.RECORDING_ELEVATION_SOURCE_GPS,
        SettingsRepository.RECORDING_ELEVATION_SOURCE_DEM,
        SettingsRepository.RECORDING_ELEVATION_SOURCE_AUTO,
    )
private val RECORDING_DASHBOARD_PAGE_OPTIONS = listOf(0, 1)
private val RECORDING_DASHBOARD_SLOT_LABELS = listOf("Top measure", "Left measure", "Right measure", "Bottom measure")
private const val RECORDING_DASHBOARD_PAGE_SLOT_COUNT = 4
private const val RECORDING_DASHBOARD_TOTAL_SLOT_COUNT = 8

private fun recordingIntervalLabel(seconds: Int): String =
    if (seconds <= 1) {
        "1 second"
    } else {
        "$seconds seconds"
    }

private fun recordingElevationSourceLabel(source: String): String =
    when (source) {
        SettingsRepository.RECORDING_ELEVATION_SOURCE_DEM -> "DEM"
        SettingsRepository.RECORDING_ELEVATION_SOURCE_AUTO -> "Auto"
        else -> "GPS altitude"
    }

private fun recordingDashboardPageLabel(pageIndex: Int): String = "Page ${pageIndex + 1}"

private fun recordingMetricLabel(metricId: String): String =
    recordingMetricDefinitions.firstOrNull { it.id == metricId }?.label ?: "Distance"

private fun normalizedDashboardSlots(slots: List<String>): List<String> =
    normalizeDashboardSlotList(slots)

private fun normalizeDashboardSlotList(slots: List<String>): List<String> {
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
