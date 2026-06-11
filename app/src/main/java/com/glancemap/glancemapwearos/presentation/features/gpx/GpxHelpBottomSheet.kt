package com.glancemap.glancemapwearos.presentation.features.gpx

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.R
import com.glancemap.glancemapwearos.presentation.ui.WearInfoDialog
import com.glancemap.glancemapwearos.presentation.ui.cappedFontScale

@Composable
fun GpxHelpBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    WearInfoDialog(
        visible = visible,
        title = "GPX Actions",
        onDismiss = onDismiss,
    ) {
        item {
            GpxHelpText("Toggle tracks on the map.")
        }
        item {
            GpxHelpText("Switch GPX and activities with the route/activity icon.")
        }
        item {
            GpxHelpText("Use the guidance icon to start turn-by-turn.")
        }
        item {
            GpxHelpText("Long press for elevation.")
        }
        item {
            cappedFontScale(maxFontScale = 1.08f) {
                Text(
                    text =
                        buildAnnotatedString {
                            append("Use ")
                            appendInlineContent("sendToPhone", "[send]")
                            append(" to send GPX to phone.")
                        },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    inlineContent =
                        mapOf(
                            "sendToPhone" to
                                InlineTextContent(
                                    placeholder =
                                        Placeholder(
                                            width = 16.sp,
                                            height = 16.sp,
                                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                                        ),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_mobile_arrow_right),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                        ),
                )
            }
        }
        item {
            GpxHelpText("Edit or delete tracks with mode buttons.")
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun GpxHelpText(text: String) {
    cappedFontScale(maxFontScale = 1.08f) {
        Text(
            text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
