package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import com.glancemap.glancemapwearos.presentation.ui.WearWindowClass
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.material.Chip

@OptIn(ExperimentalHorologistApi::class)
@Composable
internal fun GeneralSettingsShortcutChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptive = rememberWearAdaptiveSpec()
    val useCompactLabels = adaptive.windowClass == WearWindowClass.COMPACT || adaptive.fontScale >= 1.25f
    val minHeight =
        when {
            useCompactLabels -> 84.dp
            else -> 52.dp
        }
    val topPadding = rememberSettingsFirstItemTopPadding()
    val label =
        if (useCompactLabels) {
            "General"
        } else {
            "General Settings"
        }
    val secondaryLabel =
        if (useCompactLabels) {
            "Settings menu"
        } else {
            "Back to settings menu"
        }
    val widthFraction =
        if (adaptive.isRound && useCompactLabels) {
            0.78f
        } else {
            1f
        }

    Chip(
        modifier =
            modifier
                .padding(top = topPadding)
                .fillMaxWidth(widthFraction)
                .heightIn(min = minHeight),
        label = label,
        secondaryLabel = secondaryLabel,
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Shortcut,
                contentDescription = null,
                modifier = Modifier.size(ChipDefaults.IconSize),
            )
        },
        colors =
            ChipDefaults.secondaryChipColors(
                backgroundColor = Color(0xFF1F3554),
                contentColor = Color(0xFFF4F7FB),
                secondaryContentColor = Color(0xFFC9D7EA),
                iconColor = Color(0xFFF6C453),
            ),
        onClick = onClick,
    )
}
