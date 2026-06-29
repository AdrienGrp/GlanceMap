package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.glancemap.glancemapwearos.presentation.features.routetools.routeStyleSettingsOptions
import com.glancemap.glancemapwearos.presentation.features.routetools.routeStyleTitleForSettingsValue
import com.google.android.horologist.annotations.ExperimentalHorologistApi

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun GpxToolsSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenGpxSettings: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()
    val routeStyle by viewModel.gpxToolRouteStyle.collectAsState()
    val useElevation by viewModel.gpxToolUseElevation.collectAsState()
    val allowFerries by viewModel.gpxToolAllowFerries.collectAsState()

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            GpxSettingsShortcutChip(
                onClick = onOpenGpxSettings,
            )
        }

        item {
            SettingsOptionPickerRow(
                label = "Route style",
                selectedValue = routeStyle,
                options = routeStyleSettingsOptions,
                onSelect = viewModel::setGpxToolRouteStyle,
                secondaryLabel = routeStyleTitleForSettingsValue(routeStyle),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            SettingsToggleChip(
                checked = useElevation,
                onCheckedChanged = viewModel::setGpxToolUseElevation,
                label = "Use elevation",
                secondaryLabel = if (useElevation) "Prefer realistic climbs" else "Ignore elevation",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            SettingsToggleChip(
                checked = allowFerries,
                onCheckedChanged = viewModel::setGpxToolAllowFerries,
                label = "Allow ferries",
                secondaryLabel = if (allowFerries) "Ferry routes allowed" else "Avoid ferries",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GpxSettingsShortcutChip(
    onClick: () -> Unit,
) {
    AdaptiveSettingsShortcutChip(
        standardLabel = "GPX settings",
        compactLabel = "GPX",
        standardSecondaryLabel = "Back to GPX settings",
        compactSecondaryLabel = "Back",
        iconImageVector = Icons.Filled.Folder,
        applyTopPadding = true,
        compactRoundWidthFraction = 0.78f,
        onClick = onClick,
    )
}
