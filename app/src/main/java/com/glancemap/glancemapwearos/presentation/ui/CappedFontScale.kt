package com.glancemap.glancemapwearos.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

@Composable
fun CappedFontScale(
    maxFontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val cappedFontScale = density.fontScale.coerceAtMost(maxFontScale)

    if (cappedFontScale == density.fontScale) {
        content()
    } else {
        CompositionLocalProvider(
            LocalDensity provides Density(density = density.density, fontScale = cappedFontScale),
            content = content,
        )
    }
}
