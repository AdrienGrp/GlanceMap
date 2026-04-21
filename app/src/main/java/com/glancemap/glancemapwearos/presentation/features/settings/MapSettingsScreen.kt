package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.data.repository.maps.theme.ThemeRepositoryImpl
import com.glancemap.glancemapwearos.domain.model.maps.theme.ThemeListItem
import com.glancemap.glancemapwearos.presentation.features.maps.DemSetupBottomSheet
import com.glancemap.glancemapwearos.presentation.features.maps.DemSetupReason
import com.glancemap.glancemapwearos.presentation.features.maps.theme.ThemeViewModel
import com.glancemap.glancemapwearos.presentation.navigation.WatchRoutes
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScreenScaffold
import kotlinx.coroutines.launch

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun MapSettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel,
    themeViewModel: ThemeViewModel,
    onOpenGeneralSettings: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()
    val autoRecenterEnabled by viewModel.autoRecenterEnabled.collectAsState()
    val autoRecenterDelay by viewModel.autoRecenterDelay.collectAsState(initial = 5)
    val liveElevation by viewModel.liveElevation.collectAsState()
    val liveDistance by viewModel.liveDistance.collectAsState()
    val themeItems by themeViewModel.themeItems.collectAsState()
    val selectedMapPath by viewModel.selectedMapPath.collectAsState()
    val scope = rememberCoroutineScope()
    var showDemSetupDialog by remember { mutableStateOf(false) }
    var demSetupReason by remember { mutableStateOf(DemSetupReason.GENERIC) }
    val hillShadingEnabled =
        remember(themeItems) {
            themeItems
                .filterIsInstance<ThemeListItem.GlobalToggle>()
                .firstOrNull { it.id == ThemeRepositoryImpl.GLOBAL_HILL_SHADING_ID }
                ?.enabled
                ?: false
        }
    val hillShadingSupported =
        remember(themeItems) {
            themeItems
                .filterIsInstance<ThemeListItem.GlobalToggle>()
                .firstOrNull { it.id == ThemeRepositoryImpl.GLOBAL_HILL_SHADING_ID }
                ?.supported
                ?: false
        }
    val reliefOverlayEnabled =
        remember(themeItems) {
            themeItems
                .filterIsInstance<ThemeListItem.GlobalToggle>()
                .firstOrNull { it.id == ThemeRepositoryImpl.GLOBAL_RELIEF_OVERLAY_ID }
                ?.enabled
                ?: false
        }
    val hillShadingChecked = hillShadingEnabled && hillShadingSupported
    val hillShadingSecondaryLabel =
        when {
            !hillShadingSupported -> "Not supported by this theme"
            hillShadingEnabled -> "On"
            else -> "Off"
        }
    val reliefOverlaySecondaryLabel = if (reliefOverlayEnabled) "On" else "Off"

    val listState = rememberScalingLazyListState()

    DemSetupBottomSheet(
        visible = showDemSetupDialog,
        reason = demSetupReason,
        onDismiss = {
            showDemSetupDialog = false
            demSetupReason = DemSetupReason.GENERIC
        },
    )

    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding =
                PaddingValues(
                    start = listTokens.horizontalPadding,
                    end = listTokens.horizontalPadding,
                    top = listTokens.topPadding,
                    bottom = listTokens.bottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(listTokens.itemSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                GeneralSettingsShortcutChip(onClick = onOpenGeneralSettings)
            }
            item {
                SettingsToggleChip(
                    checked = autoRecenterEnabled,
                    onCheckedChanged = {
                        viewModel.setAutoRecenterEnabled(it)
                    },
                    label = "Auto recenter",
                )
            }
            if (autoRecenterEnabled) {
                item {
                    RecenterDelaySetting(autoRecenterDelay) { newDelay ->
                        viewModel.setAutoRecenterDelay(newDelay)
                    }
                }
            }
            item {
                SettingsToggleChip(
                    checked = liveElevation,
                    onCheckedChanged = { enabled ->
                        if (!enabled) {
                            viewModel.setLiveElevation(false)
                        } else {
                            scope.launch {
                                val demReady = themeViewModel.isDemReadyForMap(selectedMapPath)
                                if (demReady) {
                                    viewModel.setLiveElevation(true)
                                } else {
                                    viewModel.setLiveElevation(false)
                                    demSetupReason = DemSetupReason.LIVE_ELEVATION
                                    showDemSetupDialog = true
                                }
                            }
                        }
                    },
                    label = "Live elevation",
                    secondaryLabel = if (liveElevation) "On" else "Off",
                )
            }
            item {
                SettingsToggleChip(
                    checked = liveDistance,
                    onCheckedChanged = { viewModel.setLiveDistance(it) },
                    label = "Live distance",
                    secondaryLabel = if (liveDistance) "On" else "Off",
                )
            }
            item {
                SettingsToggleChip(
                    checked = hillShadingChecked,
                    enabled = hillShadingSupported,
                    onCheckedChanged = { enabled ->
                        if (!enabled) {
                            themeViewModel.setGlobalToggle(ThemeRepositoryImpl.GLOBAL_HILL_SHADING_ID, false)
                        } else {
                            scope.launch {
                                val demReady = themeViewModel.isDemReadyForMap(selectedMapPath)
                                if (demReady) {
                                    themeViewModel.setGlobalToggle(ThemeRepositoryImpl.GLOBAL_HILL_SHADING_ID, true)
                                } else {
                                    themeViewModel.setGlobalToggle(ThemeRepositoryImpl.GLOBAL_HILL_SHADING_ID, false)
                                    demSetupReason = DemSetupReason.GENERIC
                                    showDemSetupDialog = true
                                }
                            }
                        }
                    },
                    label = "Hill shading",
                    secondaryLabel = hillShadingSecondaryLabel,
                )
            }
            item {
                SettingsToggleChip(
                    checked = reliefOverlayEnabled,
                    onCheckedChanged = { enabled ->
                        if (!enabled) {
                            themeViewModel.setGlobalToggle(ThemeRepositoryImpl.GLOBAL_RELIEF_OVERLAY_ID, false)
                        } else {
                            scope.launch {
                                val demReady = themeViewModel.isDemReadyForMap(selectedMapPath)
                                if (demReady) {
                                    themeViewModel.setGlobalToggle(ThemeRepositoryImpl.GLOBAL_RELIEF_OVERLAY_ID, true)
                                } else {
                                    themeViewModel.setGlobalToggle(ThemeRepositoryImpl.GLOBAL_RELIEF_OVERLAY_ID, false)
                                    demSetupReason = DemSetupReason.SLOPE_OVERLAY
                                    showDemSetupDialog = true
                                }
                            }
                        }
                    },
                    label = "Slope overlay",
                    secondaryLabel = reliefOverlaySecondaryLabel,
                )
            }
            item {
                SettingsSectionChip(
                    label = "Theme",
                    secondaryLabel = "Open theme settings",
                    onClick = { navController.navigate(WatchRoutes.THEME_SETTINGS) },
                )
            }
            item {
                SettingsSectionChip(
                    label = "Display",
                    secondaryLabel = "Open display settings",
                    onClick = { navController.navigate(WatchRoutes.MAP_DISPLAY_SETTINGS) },
                )
            }
            item {
                SettingsSectionChip(
                    label = "Zoom",
                    secondaryLabel = "Open zoom settings",
                    onClick = { navController.navigate(WatchRoutes.MAP_ZOOM_SETTINGS) },
                )
            }
        }
    }
}

@Composable
private fun RecenterDelaySetting(
    delay: Int,
    onValueChange: (Int) -> Unit,
) {
    var internalValue by remember(delay) { mutableStateOf(delay.toFloat()) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Recenter Delay: ${internalValue.toInt()}s",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Slider(
            value = internalValue,
            onValueChange = {
                internalValue = it
                onValueChange(it.toInt())
            },
            valueRange = 1f..30f,
            steps = 28,
            increaseIcon = { Icon(Icons.Filled.Add, contentDescription = "Increase") },
            decreaseIcon = { Icon(Icons.Filled.Remove, contentDescription = "Decrease") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
