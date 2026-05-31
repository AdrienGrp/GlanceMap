package com.glancemap.glancemapwearos.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults

@Composable
fun CompactIconHitTargetButton(
    onClick: () -> Unit,
    visualSize: Dp,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledContainerColor: Color = containerColor.copy(alpha = 0.5f),
    disabledContentColor: Color = contentColor.copy(alpha = 0.45f),
    content: @Composable BoxScope.() -> Unit,
) {
    val actualContainerColor = if (enabled) containerColor else disabledContainerColor

    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp),
        colors =
            IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = contentColor,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = disabledContentColor,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .size(visualSize)
                    .background(actualContainerColor, CircleShape),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}
