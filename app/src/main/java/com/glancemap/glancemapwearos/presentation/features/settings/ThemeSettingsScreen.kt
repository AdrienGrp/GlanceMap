package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.glancemap.glancemapwearos.data.repository.maps.theme.ThemeRepositoryImpl
import com.glancemap.glancemapwearos.domain.model.maps.theme.ThemeListItem
import com.glancemap.glancemapwearos.domain.model.maps.theme.mapsforge.MapsforgeThemeCatalog
import com.glancemap.glancemapwearos.presentation.features.maps.MapViewModel
import com.glancemap.glancemapwearos.presentation.features.maps.theme.ThemeViewModel
import java.util.Locale

internal object ThemeOverlayGrouping {
    fun groupIdForOverlay(layerId: String): String {
        val id = layerId.lowercase(Locale.ROOT)
        return when {
            id.contains("skipiste") ||
                id.contains("ski") ||
                id.contains("skitour") ||
                id.contains("skiloipe") ||
                id.contains("schneeschuh") ||
                id.contains("rodeln") ||
                id.contains("hundeschlitten") ||
                id.contains("eislaufen") ||
                id.contains("schneepark") ||
                id.contains("winter_") -> "winter"

            id.contains("routes") ||
                id.contains("hiking") ||
                id.contains("cycling") ||
                id.contains("mtb") ||
                id.contains("guidepost") ||
                id.contains("placenames") ||
                id.contains("roadnames") ||
                id.contains("roadnumbers") ||
                id.contains("elevations") ||
                id.contains("topography") ||
                id.contains("tracks") ||
                id.contains("waymarks") ||
                id.contains("pt_network") ||
                id.contains("reference") ||
                id.contains("symbol") ||
                id.contains("namen") ||
                id.contains("tms_hknw") ||
                id.contains("tms_cynw") ||
                id.contains("tms_mtbnw") ||
                id.contains("tms_paths") ||
                id.contains("tms_tracks") -> "routes"

            id.contains("amenities") ||
                id.contains("accommodation") ||
                id.contains("restaurants") ||
                id.contains("shops") ||
                id.contains("publictrans") ||
                id.contains("camping") ||
                id.contains("hotel") ||
                id.contains("hut") ||
                id.contains("health") ||
                id.contains("education") ||
                id.contains("eating") ||
                id.contains("fun") ||
                id.contains("urban_equipment") ||
                id.contains("nature_equipment") ||
                id.contains("infrastructure") ||
                id.contains("tourism") ||
                id.contains("sports") ||
                id.contains("emergency") ||
                id.contains("pubtrans") ||
                id.contains("car") ||
                id.contains("barriers") ||
                id.contains("buildings") ||
                id.contains("shop") ||
                id.contains("tms_huts") ||
                id.contains("tms_acco") ||
                id.contains("tms_picnic") ||
                id.contains("tms_tourism") ||
                id.contains("tms_trans-priv") ||
                id.contains("tms_trans-pub") ||
                id.contains("tms_culture") ||
                id.contains("tms_freetime") ||
                id.contains("tms_food") ||
                id.contains("tms_shops") ||
                id.contains("tms_religion") ||
                id.contains("tms_emergency") -> "poi"

            else -> "map"
        }
    }
}

private data class OverlayGroupUi(
    val id: String,
    val title: String,
    val overlays: List<ThemeListItem.Overlay>
)

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun ThemeSettingsScreen(
    themeViewModel: ThemeViewModel,
    mapViewModel: MapViewModel,
    @Suppress("UNUSED_PARAMETER")
    onOpenMaps: () -> Unit
) {
    val listTokens = rememberSettingsListTokens(
        compactTop = 12.dp,
        standardTop = 14.dp,
        expandedTop = 16.dp,
        compactBottom = 12.dp,
        standardBottom = 14.dp,
        expandedBottom = 16.dp
    )
    val themeItems by themeViewModel.themeItems.collectAsState()
    val listState = rememberScalingLazyListState()
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    var showThemePicker by remember { mutableStateOf(false) }
    var showStylePicker by remember { mutableStateOf(false) }

    val selectedStyle = themeItems
        .filterIsInstance<ThemeListItem.Style>()
        .firstOrNull { it.selected }
    val selectedStyleId = selectedStyle?.id

    val selectedTheme = themeItems
        .filterIsInstance<ThemeListItem.ThemeOption>()
        .firstOrNull { it.selected }
    val selectedThemeId = selectedTheme?.id

    val themeOptions = themeItems.filterIsInstance<ThemeListItem.ThemeOption>()
    val styleOptions = themeItems.filterIsInstance<ThemeListItem.Style>()
    val overlayOptions = themeItems.filterIsInstance<ThemeListItem.Overlay>()
    val overlayGroups = remember(overlayOptions) { buildOverlayGroups(overlayOptions) }
    val themePickerOptions = remember(themeOptions) {
        themeOptions.map { option -> option.id to option.name.ifBlank { option.id } }
    }
    val stylePickerOptions = remember(styleOptions) {
        styleOptions.map { option -> option.id to option.name.ifBlank { option.id } }
    }

    val bundledThemeSelected = MapsforgeThemeCatalog.isBundledAssetTheme(selectedThemeId)
    val styleSelectionAvailable = hasMeaningfulStyleSelection(styleOptions)
    val canToggleOverlays = bundledThemeSelected && selectedStyleId != null

    DisposableEffect(mapViewModel) {
        mapViewModel.setThemeRenderingDeferred(true)
        onDispose {
            mapViewModel.setThemeRenderingDeferred(false)
        }
    }

    LaunchedEffect(overlayGroups.map { it.id }) {
        val validKeys = overlayGroups.map { it.id }.toSet()
        expandedGroups.keys
            .toList()
            .filter { it !in validKeys }
            .forEach { expandedGroups.remove(it) }
        validKeys.forEach { key ->
            if (expandedGroups[key] == null) expandedGroups[key] = false
        }
    }

    val overlayRows = remember(overlayGroups, expandedGroups.toMap()) {
        buildOverlayRows(overlayGroups, expandedGroups)
    }

    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = listTokens.horizontalPadding,
                end = listTokens.horizontalPadding,
                top = listTokens.topPadding,
                bottom = listTokens.bottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(listTokens.itemSpacing)
        ) {
            item {
                Button(onClick = { themeViewModel.resetToDefaults() }) {
                    Text(
                        text = "Reset defaults",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (themeOptions.isNotEmpty()) {
                item {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                item {
                    SettingsPickerChip(
                        label = "Map theme",
                        secondaryLabel = selectedTheme?.name?.ifBlank { selectedTheme.id } ?: "-",
                        iconImageVector = Icons.Filled.UnfoldMore,
                        onClick = { showThemePicker = true }
                    )
                }
            }

            if (styleSelectionAvailable) {
                item {
                    SettingsPickerChip(
                        label = "Style",
                        secondaryLabel = selectedStyle?.name?.ifBlank { selectedStyle.id } ?: "-",
                        iconImageVector = Icons.Filled.UnfoldMore,
                        onClick = { showStylePicker = true }
                    )
                }
            }

            if (themeOptions.isNotEmpty()) {
                item {
                    Text(
                        text = "Themes can misread terrain or paths. Verify with local conditions and other sources.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (bundledThemeSelected && overlayOptions.isNotEmpty()) {
                item {
                    Text(
                        text = "Overlays (tap group to open)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                items(overlayRows) { row ->
                    when (row) {
                        is OverlayRow.Group -> {
                            val enabledCount = row.group.overlays.count { it.enabled }
                            val totalCount = row.group.overlays.size
                            SettingsPickerChip(
                                label = if (row.expanded) "▾ ${row.group.title}" else "▸ ${row.group.title}",
                                secondaryLabel = "$enabledCount/$totalCount",
                                iconImageVector = null,
                                onClick = {
                                    expandedGroups[row.group.id] = !row.expanded
                                }
                            )
                        }

                        is OverlayRow.Item -> {
                            SettingsToggleChip(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = canToggleOverlays,
                                checked = row.overlay.enabled,
                                onCheckedChanged = { _ ->
                                    if (canToggleOverlays) {
                                        themeViewModel.toggleOverlay(row.overlay.layerId)
                                    }
                                },
                                label = row.overlay.name.ifBlank { row.overlay.layerId }
                            )
                        }
                    }
                }
            }
        }
    }

    val selectedThemeValue = selectedTheme?.id ?: themeOptions.firstOrNull()?.id
    if (selectedThemeValue != null && themePickerOptions.isNotEmpty()) {
        OptionPickerDialog(
            visible = showThemePicker,
            title = "Theme",
            selectedValue = selectedThemeValue,
            options = themePickerOptions,
            onDismiss = { showThemePicker = false },
            onSelect = { selected -> themeViewModel.setTheme(selected) }
        )
    }

    val selectedStyleValue = selectedStyleId ?: styleOptions.firstOrNull()?.id
    if (selectedStyleValue != null && styleSelectionAvailable && stylePickerOptions.isNotEmpty()) {
        OptionPickerDialog(
            visible = showStylePicker,
            title = "Theme style",
            selectedValue = selectedStyleValue,
            options = stylePickerOptions,
            onDismiss = { showStylePicker = false },
            onSelect = { selected -> themeViewModel.setMapStyle(selected) }
        )
    }
}

private sealed interface OverlayRow {
    data class Group(
        val group: OverlayGroupUi,
        val expanded: Boolean
    ) : OverlayRow

    data class Item(
        val groupId: String,
        val overlay: ThemeListItem.Overlay
    ) : OverlayRow
}

private fun buildOverlayRows(
    groups: List<OverlayGroupUi>,
    expandedState: Map<String, Boolean>
): List<OverlayRow> {
    val rows = mutableListOf<OverlayRow>()
    groups.forEach { group ->
        val expanded = expandedState[group.id] == true
        rows += OverlayRow.Group(group = group, expanded = expanded)
        if (expanded) {
            group.overlays.forEach { overlay ->
                rows += OverlayRow.Item(groupId = group.id, overlay = overlay)
            }
        }
    }
    return rows
}

private fun buildOverlayGroups(overlays: List<ThemeListItem.Overlay>): List<OverlayGroupUi> {
    val buckets = linkedMapOf(
        "winter" to mutableListOf<ThemeListItem.Overlay>(),
        "routes" to mutableListOf<ThemeListItem.Overlay>(),
        "poi" to mutableListOf<ThemeListItem.Overlay>(),
        "map" to mutableListOf<ThemeListItem.Overlay>()
    )

    overlays.forEach { overlay ->
        buckets[ThemeOverlayGrouping.groupIdForOverlay(overlay.layerId)]?.add(overlay)
    }

    val titles = mapOf(
        "winter" to "Winter activities",
        "routes" to "Routes & labels",
        "poi" to "POI & services",
        "map" to "Map details"
    )

    return buckets
        .mapNotNull { (id, list) ->
            if (list.isEmpty()) return@mapNotNull null
            OverlayGroupUi(
                id = id,
                title = titles[id] ?: id,
                overlays = list.sortedBy { it.name.lowercase(Locale.ROOT) }
            )
        }
}

private fun hasMeaningfulStyleSelection(styles: List<ThemeListItem.Style>): Boolean {
    val nonDefaultStyleCount = styles.count {
        it.id != ThemeRepositoryImpl.DEFAULT_STYLE_ID
    }
    return nonDefaultStyleCount > 1
}
