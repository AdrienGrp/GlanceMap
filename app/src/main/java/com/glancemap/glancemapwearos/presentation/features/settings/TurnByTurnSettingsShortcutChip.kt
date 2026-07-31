package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun TurnByTurnSettingsShortcutChip(
    onClick: () -> Unit,
    applyTopPadding: Boolean = true,
    modifier: Modifier = Modifier,
) {
    AdaptiveSettingsShortcutChip(
        standardLabel = "Turn-by-turn settings",
        compactLabel = "TBT settings",
        standardSecondaryLabel = "Back to turn-by-turn",
        compactSecondaryLabel = "Back",
        iconImageVector = Icons.Filled.Folder,
        applyTopPadding = applyTopPadding,
        compactRoundWidthFraction = 0.78f,
        modifier = modifier,
        onClick = onClick,
    )
}
