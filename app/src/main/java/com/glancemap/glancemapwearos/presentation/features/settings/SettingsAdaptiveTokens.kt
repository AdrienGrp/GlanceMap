package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
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
    val adaptive = rememberWearAdaptiveSpec()
    val windowClass = adaptive.windowClass
    val largeTextTopExtra =
        if (adaptive.isRound && adaptive.fontScale >= 1.25f) {
            24.dp
        } else {
            0.dp
        }
    return remember(
        windowClass,
        largeTextTopExtra,
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
                    topPadding = compactTop + largeTextTopExtra,
                    bottomPadding = compactBottom,
                    itemSpacing = compactItemSpacing,
                )

            WearWindowClass.STANDARD ->
                SettingsListTokens(
                    horizontalPadding = standardHorizontal,
                    topPadding = standardTop + largeTextTopExtra,
                    bottomPadding = standardBottom,
                    itemSpacing = standardItemSpacing,
                )

            WearWindowClass.EXPANDED ->
                SettingsListTokens(
                    horizontalPadding = expandedHorizontal,
                    topPadding = expandedTop + largeTextTopExtra,
                    bottomPadding = expandedBottom,
                    itemSpacing = expandedItemSpacing,
                )
        }
    }
}

@Composable
internal fun rememberSettingsScalingLazyListState(): ScalingLazyListState =
    rememberScalingLazyListState(initialCenterItemIndex = 0)
