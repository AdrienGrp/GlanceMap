package com.glancemap.glancemapwearos.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.roundToInt

enum class WearScreenSize {
    SMALL,
    MEDIUM,
    LARGE,
}

@Composable
fun rememberWearScreenSize(): WearScreenSize {
    val configuration = LocalConfiguration.current
    val minDimDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    val fontScale = configuration.fontScale.coerceIn(0.85f, 1.6f)
    val effectiveMinDimDp = (minDimDp / fontScale.coerceAtLeast(1f)).roundToInt()

    // Keep current 466x466 tuning in LARGE bucket and scale down only when text scale is high.
    return when {
        effectiveMinDimDp >= 225 -> WearScreenSize.LARGE
        effectiveMinDimDp >= 205 -> WearScreenSize.MEDIUM
        else -> WearScreenSize.SMALL
    }
}
