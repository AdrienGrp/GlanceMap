package com.glancemap.glancemapwearos.presentation.features.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.features.settings.SettingsToggleChip
import com.glancemap.glancemapwearos.presentation.features.settings.rememberSettingsListTokens
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScreenScaffold

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun DownloadSettingsScreen(viewModel: DownloadViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    val listTokens = rememberSettingsListTokens()

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
                Text(
                    text = "Download settings",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
                Text(
                    text = "Bundle: ${uiState.selection.label()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
