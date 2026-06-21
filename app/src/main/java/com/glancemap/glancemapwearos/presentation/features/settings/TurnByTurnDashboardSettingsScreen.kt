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
import com.glancemap.glancemapwearos.data.repository.TURN_BY_TURN_DASHBOARD_MAX_PAGE_COUNT
import com.glancemap.glancemapwearos.data.repository.TURN_BY_TURN_DASHBOARD_MIN_PAGE_COUNT
import com.glancemap.glancemapwearos.data.repository.TURN_BY_TURN_DASHBOARD_PAGE_SLOT_COUNT
import com.glancemap.glancemapwearos.data.repository.normalizeTurnByTurnDashboardMetricSlots
import com.glancemap.glancemapwearos.presentation.features.navigate.turnByTurnMetricLabel
import com.glancemap.glancemapwearos.presentation.features.navigate.turnByTurnMetricPickerOptions
import com.glancemap.glancemapwearos.presentation.ui.WearHelpDialog

@Composable
fun TurnByTurnDashboardSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenTurnByTurnSettings: () -> Unit,
) {
    val metricSlots by viewModel.turnByTurnDashboardMetricSlots.collectAsState()
    val slots = normalizeTurnByTurnDashboardMetricSlots(metricSlots)
    val pageCount = slots.size / TURN_BY_TURN_DASHBOARD_PAGE_SLOT_COUNT
    var selectedPage by remember { mutableStateOf(0) }
    var selectedSlot by remember { mutableStateOf<Int?>(null) }
    var pendingAddedPage by remember { mutableStateOf<Int?>(null) }
    var showInfo by remember { mutableStateOf(false) }

    LaunchedEffect(pageCount) {
        pendingAddedPage?.takeIf { it < pageCount }?.let {
            selectedPage = it
            pendingAddedPage = null
        }
        selectedPage = selectedPage.coerceIn(0, pageCount - 1)
    }

    WearSettingsListScreen(
        listTokens = rememberSettingsListTokens(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            SettingsInfoButton(
                contentDescription = "Turn-by-turn dashboard info",
                onClick = { showInfo = true },
            )
        }
        item {
            TurnByTurnSettingsShortcutChip(
                onClick = onOpenTurnByTurnSettings,
                applyTopPadding = false,
            )
        }
        item {
            RecordingDashboardPageSwitcher(
                pageIndex = selectedPage,
                pageCount = pageCount,
                onClick = { selectedPage = (selectedPage + 1) % pageCount },
            )
        }
        if (pageCount < TURN_BY_TURN_DASHBOARD_MAX_PAGE_COUNT) {
            item {
                SettingsSectionChip(
                    label = "Add dashboard page",
                    secondaryLabel = "$pageCount of $TURN_BY_TURN_DASHBOARD_MAX_PAGE_COUNT pages",
                    iconImageVector = Icons.Filled.Add,
                    onClick = {
                        pendingAddedPage = pageCount
                        viewModel.addTurnByTurnDashboardPage()
                    },
                )
            }
        }
        if (pageCount > TURN_BY_TURN_DASHBOARD_MIN_PAGE_COUNT) {
            item {
                SettingsPickerChip(
                    label = "Delete this page",
                    secondaryLabel = "Page ${selectedPage + 1}",
                    iconImageVector = Icons.Filled.Delete,
                    onClick = {
                        val pageToDelete = selectedPage
                        selectedPage = selectedPage.coerceAtMost(pageCount - 2)
                        viewModel.deleteTurnByTurnDashboardPage(pageToDelete)
                    },
                )
            }
        }
        DASHBOARD_SLOT_LABELS.forEachIndexed { pageSlot, label ->
            val slotIndex = selectedPage * TURN_BY_TURN_DASHBOARD_PAGE_SLOT_COUNT + pageSlot
            item {
                SettingsPickerChip(
                    label = label,
                    secondaryLabel = turnByTurnMetricLabel(slots[slotIndex]),
                    onClick = { selectedSlot = slotIndex },
                )
            }
        }
    }

    WearHelpDialog(
        visible = showInfo,
        title = "TBT dashboard",
        lines =
            listOf(
                "The first full-screen page keeps turn guidance.",
                "Use the crown or swipe vertically for route metric pages.",
                "Long press a metric in the TBT popup to change it.",
            ),
        onDismiss = { showInfo = false },
    )
    selectedSlot?.let { slotIndex ->
        OptionPickerDialog(
            visible = true,
            title = DASHBOARD_SLOT_LABELS[slotIndex % TURN_BY_TURN_DASHBOARD_PAGE_SLOT_COUNT],
            selectedValue = slots[slotIndex],
            options = turnByTurnMetricPickerOptions,
            onDismiss = { selectedSlot = null },
            onSelect = { metricId ->
                viewModel.setTurnByTurnDashboardMetricSlot(slotIndex, metricId)
                selectedSlot = null
            },
        )
    }
}

private val DASHBOARD_SLOT_LABELS = listOf("Top measure", "Left measure", "Right measure", "Bottom measure")
