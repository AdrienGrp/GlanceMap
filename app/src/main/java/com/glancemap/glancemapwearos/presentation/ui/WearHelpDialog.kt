package com.glancemap.glancemapwearos.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun WearHelpDialog(
    visible: Boolean,
    title: String,
    lines: List<String>,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    backgroundColor: Color = Color.Black.copy(alpha = 0.82f),
) {
    WearInfoDialog(
        visible = visible,
        title = title,
        onDismiss = onDismiss,
        dismissible = dismissible,
        backgroundColor = backgroundColor,
    ) {
        lines
            .flatMap { line -> line.lines() }
            .filter { line -> line.isNotBlank() }
            .forEach { line ->
                item {
                    WearInfoText(line)
                }
            }
    }
}
