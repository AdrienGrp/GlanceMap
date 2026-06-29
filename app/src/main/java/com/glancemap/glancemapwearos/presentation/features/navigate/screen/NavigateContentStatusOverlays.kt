package com.glancemap.glancemapwearos.presentation.features.navigate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.core.service.location.model.GpsEnvironmentWarning
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize

@Composable
internal fun RecordingStatusMessageChip(
    message: String?,
    modifier: Modifier = Modifier,
) {
    if (message.isNullOrBlank()) return

    Text(
        text = message,
        modifier =
            modifier
                .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        color = Color.White,
        fontSize = 14.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Suppress("FunctionName")
@Composable
internal fun BoxScope.GpsEnvironmentWarningOverlay(
    warning: GpsEnvironmentWarning,
    visible: Boolean,
) {
    val message =
        when (warning) {
            GpsEnvironmentWarning.NONE -> null
            GpsEnvironmentWarning.LOCATION_SETTINGS_UNSATISFIED -> "Turn on watch Location"
            GpsEnvironmentWarning.WATCH_GPS_UNAVAILABLE -> null
            GpsEnvironmentWarning.AUTO_PHONE_DISCONNECTED_NO_WATCH_GPS,
            GpsEnvironmentWarning.AUTO_PHONE_DISCONNECTED_USING_WATCH_GPS,
            -> null
        }
    if (!visible || message == null) return

    Text(
        text = message,
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 38.dp, vertical = 10.dp)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        color = Color.White,
        fontSize = 11.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 2,
    )
}

@Suppress("FunctionName")
@Composable
internal fun BoxScope.MarkerMotionDebugOverlay(
    label: String?,
    screenSize: WearScreenSize,
    recordingStatusChipVisible: Boolean,
    onHeadingLooksWrong: () -> Unit,
) {
    if (label.isNullOrBlank()) return

    val overlayPadding =
        if (recordingStatusChipVisible) {
            when (screenSize) {
                WearScreenSize.LARGE -> 52.dp
                WearScreenSize.MEDIUM -> 50.dp
                WearScreenSize.SMALL -> 48.dp
            }
        } else {
            when (screenSize) {
                WearScreenSize.LARGE -> 24.dp
                WearScreenSize.MEDIUM -> 22.dp
                WearScreenSize.SMALL -> 20.dp
            }
        }
    val overlayTextSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 10.sp
            WearScreenSize.MEDIUM -> 10.sp
            WearScreenSize.SMALL -> 10.sp
        }

    Column(
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = overlayPadding)
                .padding(horizontal = overlayPadding)
                .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = overlayTextSize,
            lineHeight = overlayTextSize,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Report heading",
            modifier =
                Modifier
                    .padding(top = 3.dp)
                    .background(Color(0xFF6D2430), RoundedCornerShape(5.dp))
                    .clickable(onClick = onHeadingLooksWrong)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            color = Color.White,
            fontSize = overlayTextSize,
            lineHeight = overlayTextSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}
