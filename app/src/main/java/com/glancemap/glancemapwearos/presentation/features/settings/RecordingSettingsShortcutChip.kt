package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun RecordingSettingsShortcutChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    applyTopPadding: Boolean = true,
) {
    AdaptiveSettingsShortcutChip(
        standardLabel = "Recording settings",
        compactLabel = "REC settings",
        standardSecondaryLabel = "Back to REC settings",
        compactSecondaryLabel = "Back",
        iconImageVector = Icons.Filled.Folder,
        applyTopPadding = applyTopPadding,
        compactRoundWidthFraction = 0.78f,
        modifier = modifier,
        onClick = onClick,
    )
}
