package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glancemap.glancemapwearos.presentation.ui.WearWindowClass
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec

internal data class SettingsListTokens(
    val horizontalPadding: Dp,
    val topPadding: Dp,
    val bottomPadding: Dp,
    val itemSpacing: Dp,
)

@Composable
internal fun rememberSettingsListTokens(
    compactHorizontal: Dp = 12.dp,
    standardHorizontal: Dp = 14.dp,
    expandedHorizontal: Dp = 16.dp,
    compactTop: Dp = 16.dp,
    standardTop: Dp = 20.dp,
    expandedTop: Dp = 24.dp,
    compactBottom: Dp = 48.dp,
    standardBottom: Dp = 56.dp,
    expandedBottom: Dp = 64.dp,
    compactItemSpacing: Dp = 6.dp,
    standardItemSpacing: Dp = 7.dp,
    expandedItemSpacing: Dp = 8.dp,
): SettingsListTokens {
    val windowClass = rememberWearAdaptiveSpec().windowClass
    return remember(
        windowClass,
        compactHorizontal,
        standardHorizontal,
        expandedHorizontal,
        compactTop,
        standardTop,
        expandedTop,
        compactBottom,
        standardBottom,
        expandedBottom,
        compactItemSpacing,
        standardItemSpacing,
        expandedItemSpacing,
    ) {
        when (windowClass) {
            WearWindowClass.COMPACT ->
                SettingsListTokens(
                    horizontalPadding = compactHorizontal,
                    topPadding = compactTop,
                    bottomPadding = compactBottom,
                    itemSpacing = compactItemSpacing,
                )

            WearWindowClass.STANDARD ->
                SettingsListTokens(
                    horizontalPadding = standardHorizontal,
                    topPadding = standardTop,
                    bottomPadding = standardBottom,
                    itemSpacing = standardItemSpacing,
                )

            WearWindowClass.EXPANDED ->
                SettingsListTokens(
                    horizontalPadding = expandedHorizontal,
                    topPadding = expandedTop,
                    bottomPadding = expandedBottom,
                    itemSpacing = expandedItemSpacing,
                )
        }
    }
}
