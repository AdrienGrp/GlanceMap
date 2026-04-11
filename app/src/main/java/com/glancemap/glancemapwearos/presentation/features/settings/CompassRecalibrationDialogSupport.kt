package com.glancemap.glancemapwearos.presentation.features.settings

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glancemap.glancemapwearos.presentation.ui.WearWindowClass
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec

internal enum class CalibrationPhase { MEASURING, HOLD_STILL, RESULT, UNSUPPORTED }

internal const val CALIBRATION_TIMEOUT_MS = 12_000L
internal const val HOLD_STILL_TIMEOUT_MS = 3_000L
internal const val COUNTDOWN_AFTER_QUALITY_VISIBLE_MS = 600L
internal const val COMPASS_SOURCE_RESOLUTION_TIMEOUT_MS = 1_500L

internal data class CompassRecalibrationDialogTokens(
    val viewportVerticalInset: Dp,
    val titleTopPadding: Dp,
    val dialogWidthFraction: Float,
    val dialogContentSpacing: Dp,
    val dialogHorizontalPadding: Dp,
    val dialogVerticalPadding: Dp,
    val titleFontSize: TextUnit,
    val bodyFontSize: TextUnit,
    val bodyLineHeight: TextUnit,
    val qualityFontSize: TextUnit,
    val timerFontSize: TextUnit,
    val qualityMeterWidthFraction: Float,
    val qualityMeterSpacing: Dp,
    val qualityBarWidth: Dp,
    val qualityBarHeight: Dp,
    val qualityBarCornerRadius: Dp,
    val actionTextFontSize: TextUnit,
    val skipButtonWidthFraction: Float,
    val singleActionWidthFraction: Float
)

@Composable
internal fun rememberCompassRecalibrationDialogTokens(): CompassRecalibrationDialogTokens {
    val adaptive = rememberWearAdaptiveSpec()
    return remember(adaptive.windowClass, adaptive.isRound) {
        val windowClass = adaptive.windowClass
        when (windowClass) {
            WearWindowClass.EXPANDED -> CompassRecalibrationDialogTokens(
                viewportVerticalInset = 0.dp,
                titleTopPadding = 0.dp,
                dialogWidthFraction = if (adaptive.isRound) 0.84f else 0.88f,
                dialogContentSpacing = 6.dp,
                dialogHorizontalPadding = 10.dp,
                dialogVerticalPadding = 8.dp,
                titleFontSize = 14.sp,
                bodyFontSize = 11.sp,
                bodyLineHeight = 13.sp,
                qualityFontSize = 11.sp,
                timerFontSize = 10.sp,
                qualityMeterWidthFraction = 0.70f,
                qualityMeterSpacing = 6.dp,
                qualityBarWidth = 20.dp,
                qualityBarHeight = 5.dp,
                qualityBarCornerRadius = 3.dp,
                actionTextFontSize = 11.sp,
                skipButtonWidthFraction = 0.58f,
                singleActionWidthFraction = 0.65f
            )

            WearWindowClass.STANDARD -> CompassRecalibrationDialogTokens(
                viewportVerticalInset = 0.dp,
                titleTopPadding = 0.dp,
                dialogWidthFraction = if (adaptive.isRound) 0.88f else 0.92f,
                dialogContentSpacing = 5.dp,
                dialogHorizontalPadding = 9.dp,
                dialogVerticalPadding = 7.dp,
                titleFontSize = 13.sp,
                bodyFontSize = 10.sp,
                bodyLineHeight = 12.sp,
                qualityFontSize = 10.sp,
                timerFontSize = 9.sp,
                qualityMeterWidthFraction = 0.76f,
                qualityMeterSpacing = 5.dp,
                qualityBarWidth = 18.dp,
                qualityBarHeight = 4.dp,
                qualityBarCornerRadius = 2.5.dp,
                actionTextFontSize = 10.sp,
                skipButtonWidthFraction = 0.64f,
                singleActionWidthFraction = 0.72f
            )

            WearWindowClass.COMPACT -> CompassRecalibrationDialogTokens(
                viewportVerticalInset = if (adaptive.isRound) 10.dp else 0.dp,
                titleTopPadding = if (adaptive.isRound) 6.dp else 0.dp,
                dialogWidthFraction = if (adaptive.isRound) 0.88f else 0.94f,
                dialogContentSpacing = 4.dp,
                dialogHorizontalPadding = 8.dp,
                dialogVerticalPadding = 6.dp,
                titleFontSize = if (adaptive.isRound) 11.sp else 12.sp,
                bodyFontSize = 9.sp,
                bodyLineHeight = 11.sp,
                qualityFontSize = 9.sp,
                timerFontSize = 8.sp,
                qualityMeterWidthFraction = 0.82f,
                qualityMeterSpacing = 4.dp,
                qualityBarWidth = 16.dp,
                qualityBarHeight = 4.dp,
                qualityBarCornerRadius = 2.dp,
                actionTextFontSize = 9.sp,
                skipButtonWidthFraction = 0.72f,
                singleActionWidthFraction = 0.78f
            )
        }
    }
}

internal fun vibratorFrom(context: Context): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
}
