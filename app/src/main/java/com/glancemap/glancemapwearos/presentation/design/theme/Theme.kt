package com.glancemap.glancemapwearos.presentation.design.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

@Composable
fun GlanceMapTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        content = content
    )
}