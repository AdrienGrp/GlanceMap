package com.glancemap.glancemapwearos.presentation.features.navigate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec
import com.glancemap.glancemapwearos.presentation.ui.rememberWearScreenSize

@Composable
fun AmbientScreen(
    ambientTick: Long,
    timeFormat: String,
    burnInProtectionRequired: Boolean = true,
) {
    val context = LocalContext.current
    val screenSize = rememberWearScreenSize()
    val adaptive = rememberWearAdaptiveSpec()
    val timeFontSize =
        if (adaptive.isRound) {
            when (screenSize) {
                WearScreenSize.LARGE -> 42.sp
                WearScreenSize.MEDIUM -> 38.sp
                WearScreenSize.SMALL -> 34.sp
            }
        } else {
            when (screenSize) {
                WearScreenSize.LARGE -> 44.sp
                WearScreenSize.MEDIUM -> 40.sp
                WearScreenSize.SMALL -> 36.sp
            }
        }
    val burnInShiftRange =
        if (adaptive.isRound) {
            when (screenSize) {
                WearScreenSize.LARGE -> 5
                WearScreenSize.MEDIUM -> 4
                WearScreenSize.SMALL -> 3
            }
        } else {
            when (screenSize) {
                WearScreenSize.LARGE -> 4
                WearScreenSize.MEDIUM -> 3
                WearScreenSize.SMALL -> 2
            }
        }
    val time =
        remember(ambientTick, timeFormat) {
            formatNavigateClockTime(context, ambientTick, timeFormat)
        }

    // Offset position slightly every minute to prevent burn-in
    val offset =
        if (burnInProtectionRequired) {
            remember(ambientTick, burnInShiftRange) {
                val span = burnInShiftRange * 2 + 1
                val offsetX = (ambientTick % span).toInt() - burnInShiftRange
                val offsetY = ((ambientTick / span) % span).toInt() - burnInShiftRange
                androidx.compose.ui.geometry.Offset(
                    offsetX.toFloat(),
                    offsetY.toFloat(),
                )
            }
        } else {
            androidx.compose.ui.geometry.Offset.Zero
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = time,
            fontSize = timeFontSize,
            fontWeight = FontWeight.Light,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.offset(
                    x = offset.x.dp,
                    y = offset.y.dp,
                ),
        )
    }
}
