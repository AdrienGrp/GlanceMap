package com.glancemap.glancemapwearos.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

enum class WearWindowClass {
    COMPACT,
    STANDARD,
    EXPANDED,
}

data class WearAdaptiveSpec(
    val screenSize: WearScreenSize,
    val windowClass: WearWindowClass,
    val widthDp: Int,
    val heightDp: Int,
    val minDimensionDp: Int,
    val fontScale: Float,
    val isRoundScreen: Boolean,
) {
    val isCompact: Boolean get() = windowClass == WearWindowClass.COMPACT
    val isExpanded: Boolean get() = windowClass == WearWindowClass.EXPANDED
    val isRound: Boolean get() = isRoundScreen
    val isSquare: Boolean get() = !isRoundScreen

    // Additional safe offset to protect top headers on round displays.
    val headerTopSafeInset: Dp
        get() =
            if (!isRoundScreen) {
                0.dp
            } else {
                when (windowClass) {
                    WearWindowClass.COMPACT -> 6.dp
                    WearWindowClass.STANDARD -> 0.dp
                    WearWindowClass.EXPANDED -> 0.dp
                }
            }

    // Dialog tokens keep current round defaults and apply square-safe variants.
    val dialogCornerRadius: Dp
        get() =
            if (isRoundScreen) {
                when (windowClass) {
                    WearWindowClass.COMPACT -> 14.dp
                    WearWindowClass.STANDARD -> 16.dp
                    WearWindowClass.EXPANDED -> 18.dp
                }
            } else {
                when (windowClass) {
                    WearWindowClass.COMPACT -> 12.dp
                    WearWindowClass.STANDARD -> 14.dp
                    WearWindowClass.EXPANDED -> 16.dp
                }
            }

    val dialogHorizontalPadding: Dp
        get() =
            if (isRoundScreen) {
                when (windowClass) {
                    WearWindowClass.COMPACT -> 10.dp
                    WearWindowClass.STANDARD -> 12.dp
                    WearWindowClass.EXPANDED -> 14.dp
                }
            } else {
                when (windowClass) {
                    WearWindowClass.COMPACT -> 12.dp
                    WearWindowClass.STANDARD -> 14.dp
                    WearWindowClass.EXPANDED -> 16.dp
                }
            }

    val dialogVerticalPadding: Dp
        get() =
            if (isRoundScreen) {
                when (windowClass) {
                    WearWindowClass.COMPACT -> 7.dp
                    WearWindowClass.STANDARD -> 8.dp
                    WearWindowClass.EXPANDED -> 9.dp
                }
            } else {
                when (windowClass) {
                    WearWindowClass.COMPACT -> 8.dp
                    WearWindowClass.STANDARD -> 9.dp
                    WearWindowClass.EXPANDED -> 10.dp
                }
            }

    val dialogBodyMaxHeight: Dp
        get() =
            if (isRoundScreen) {
                when (windowClass) {
                    WearWindowClass.COMPACT -> 104.dp
                    WearWindowClass.STANDARD -> 120.dp
                    WearWindowClass.EXPANDED -> 142.dp
                }
            } else {
                when (windowClass) {
                    WearWindowClass.COMPACT -> 116.dp
                    WearWindowClass.STANDARD -> 134.dp
                    WearWindowClass.EXPANDED -> 156.dp
                }
            }

    val helpDialogMaxHeight: Dp
        get() =
            if (isRoundScreen) {
                when (windowClass) {
                    WearWindowClass.COMPACT -> 110.dp
                    WearWindowClass.STANDARD -> 128.dp
                    WearWindowClass.EXPANDED -> 160.dp
                }
            } else {
                when (windowClass) {
                    WearWindowClass.COMPACT -> 122.dp
                    WearWindowClass.STANDARD -> 142.dp
                    WearWindowClass.EXPANDED -> 174.dp
                }
            }

    // Edge swipe tokens for dismiss/navigation affordance.
    val edgeSwipeWidth: Dp
        get() =
            when (windowClass) {
                WearWindowClass.COMPACT -> 18.dp
                WearWindowClass.STANDARD -> 20.dp
                WearWindowClass.EXPANDED -> 22.dp
            }

    val edgeSwipeHeightFraction: Float
        get() =
            when (windowClass) {
                WearWindowClass.COMPACT -> 0.26f
                WearWindowClass.STANDARD -> 0.22f
                WearWindowClass.EXPANDED -> 0.20f
            }

    val edgeSwipeThreshold: Dp
        get() =
            when (windowClass) {
                WearWindowClass.COMPACT -> 48.dp
                WearWindowClass.STANDARD -> 56.dp
                WearWindowClass.EXPANDED -> 60.dp
            }
}

@Composable
fun rememberWearAdaptiveSpec(): WearAdaptiveSpec {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp
    val minDimensionDp = minOf(widthDp, heightDp)
    val isRoundScreen = configuration.isScreenRound
    val fontScale = configuration.fontScale.coerceIn(0.85f, 1.6f)
    val normalizedMinDim = (minDimensionDp / fontScale.coerceAtLeast(1f)).roundToInt()
    val screenSize = rememberWearScreenSize()

    val windowClass =
        when {
            normalizedMinDim < 205 || fontScale >= 1.18f -> WearWindowClass.COMPACT
            normalizedMinDim >= 235 && fontScale <= 1.0f -> WearWindowClass.EXPANDED
            else -> WearWindowClass.STANDARD
        }

    return remember(
        screenSize,
        windowClass,
        widthDp,
        heightDp,
        minDimensionDp,
        fontScale,
        isRoundScreen,
    ) {
        WearAdaptiveSpec(
            screenSize = screenSize,
            windowClass = windowClass,
            widthDp = widthDp,
            heightDp = heightDp,
            minDimensionDp = minDimensionDp,
            fontScale = fontScale,
            isRoundScreen = isRoundScreen,
        )
    }
}
