package com.glancemap.glancemapwearos.presentation.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.wearDialogWidth(
    roundFraction: Float = 0.88f,
    squareFraction: Float = 0.92f,
): Modifier {
    val adaptive = rememberWearAdaptiveSpec()
    return this.fillMaxWidth(
        if (adaptive.isRound) {
            roundFraction
        } else {
            squareFraction
        },
    )
}

@Composable
@Suppress("FunctionNaming")
fun WearDialogScrollBottomSpacer(extra: Dp = 28.dp) {
    val adaptive = rememberWearAdaptiveSpec()
    Spacer(modifier = Modifier.height(adaptive.dialogVerticalPadding + extra))
}
