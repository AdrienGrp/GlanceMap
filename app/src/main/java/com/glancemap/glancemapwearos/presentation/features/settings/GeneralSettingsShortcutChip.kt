package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon

@OptIn(ExperimentalHorologistApi::class)
@Composable
internal fun GeneralSettingsShortcutChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Chip(
        modifier = modifier,
        label = "General Settings",
        secondaryLabel = "Back to settings menu",
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Shortcut,
                contentDescription = null,
                modifier = Modifier.size(ChipDefaults.IconSize)
            )
        },
        colors = ChipDefaults.secondaryChipColors(
            backgroundColor = Color(0xFF1F3554),
            contentColor = Color(0xFFF4F7FB),
            secondaryContentColor = Color(0xFFC9D7EA),
            iconColor = Color(0xFFF6C453)
        ),
        onClick = onClick
    )
}
