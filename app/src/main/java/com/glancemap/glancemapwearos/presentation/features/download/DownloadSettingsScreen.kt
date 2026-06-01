@file:Suppress("FunctionName", "FunctionNaming", "LongMethod")

package com.glancemap.glancemapwearos.presentation.features.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.SplitSwitchButton
import androidx.wear.compose.material3.SwitchButtonDefaults
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.core.maps.DemSource
import com.glancemap.glancemapwearos.presentation.features.settings.OptionPickerDialog
import com.glancemap.glancemapwearos.presentation.features.settings.SettingsListAnchorType
import com.glancemap.glancemapwearos.presentation.features.settings.SettingsListAutoCentering
import com.glancemap.glancemapwearos.presentation.features.settings.SettingsToggleChip
import com.glancemap.glancemapwearos.presentation.features.settings.rememberSettingsListTokens
import com.glancemap.glancemapwearos.presentation.features.settings.rememberSettingsScalingLazyListState
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScreenScaffold

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun DownloadSettingsScreen(viewModel: DownloadViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val listTokens =
        rememberSettingsListTokens(
            compactTop = 40.dp,
            standardTop = 44.dp,
            expandedTop = 48.dp,
        )
    val listState = rememberSettingsScalingLazyListState(topPadding = listTokens.topPadding)
    var showDemSourcePicker by remember { mutableStateOf(false) }

    OptionPickerDialog(
        visible = showDemSourcePicker,
        title = "Elevation quality",
        selectedValue = uiState.selection.demSource,
        options = DemSource.entries.map { source -> source to source.displayName },
        onDismiss = { showDemSourcePicker = false },
        onSelect = viewModel::setDemSource,
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
            anchorType = SettingsListAnchorType,
            autoCentering = SettingsListAutoCentering,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                SettingsToggleChip(
                    checked = uiState.selection.includeMap,
                    onCheckedChanged = viewModel::setIncludeMap,
                    label = "Maps",
                    secondaryLabel = "OpenAndroMaps",
                )
            }
            item {
                SettingsToggleChip(
                    checked = uiState.selection.includePoi,
                    onCheckedChanged = viewModel::setIncludePoi,
                    label = "POI",
                    secondaryLabel = "Mapsforge POI",
                )
            }
            item {
                SettingsToggleChip(
                    checked = uiState.selection.includeRouting,
                    onCheckedChanged = viewModel::setIncludeRouting,
                    label = "Routing",
                    secondaryLabel = "BRouter RD5",
                )
            }
            item {
                ElevationDownloadSetting(
                    checked = uiState.selection.includeDem,
                    source = uiState.selection.demSource,
                    onCheckedChanged = viewModel::setIncludeDem,
                    onPickSource = { showDemSourcePicker = true },
                )
            }
            item {
                SettingsToggleChip(
                    checked = uiState.selection.includeRefugesInfo,
                    onCheckedChanged = viewModel::setIncludeRefugesInfo,
                    label = "Refuges.info",
                    secondaryLabel = "All refuge POIs",
                )
            }
        }
    }
}

@Composable
private fun ElevationDownloadSetting(
    checked: Boolean,
    source: DemSource,
    onCheckedChanged: (Boolean) -> Unit,
    onPickSource: () -> Unit,
) {
    SplitSwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChanged,
        toggleContentDescription = "Include elevation",
        onContainerClick = onPickSource,
        containerClickLabel = "Choose elevation quality",
        modifier = Modifier.fillMaxWidth(),
        colors =
            SwitchButtonDefaults.splitSwitchButtonColors(
                checkedContainerColor = Color(0xFF5E6B7F),
                checkedContentColor = Color.White,
                checkedSecondaryContentColor = Color(0xFFE5E7EB),
                checkedSplitContainerColor = Color.Black.copy(alpha = 0.10f),
                uncheckedContainerColor = Color(0xFF2B2F36),
                uncheckedContentColor = Color(0xFFF1F5FB),
                uncheckedSecondaryContentColor = Color(0xFFBAC5D4),
                uncheckedSplitContainerColor = Color.Black.copy(alpha = 0.18f),
            ),
        label = {
            Text(
                text = "Elevation",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Filled.UnfoldMore,
                contentDescription = "Choose elevation quality",
                modifier = Modifier.size(18.dp),
            )
        },
        secondaryLabel = {
            Text(
                text = source.shortLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
