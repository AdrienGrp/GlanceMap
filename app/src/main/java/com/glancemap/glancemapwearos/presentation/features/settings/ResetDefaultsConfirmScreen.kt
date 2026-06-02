@file:Suppress("FunctionNaming")

package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.ui.WearActionButtonRole
import com.glancemap.glancemapwearos.presentation.ui.WearActionDialogButton
import com.glancemap.glancemapwearos.presentation.ui.WearActionScreen

@Composable
fun ResetDefaultsConfirmScreen(
    onCancel: () -> Unit,
    onConfirmReset: () -> Unit,
) {
    WearActionScreen(
        title = "Reset to defaults",
        buttons =
            listOf(
                WearActionDialogButton(
                    text = "Reset now",
                    onClick = onConfirmReset,
                    role = WearActionButtonRole.Destructive,
                ),
                WearActionDialogButton(
                    text = "Cancel",
                    onClick = onCancel,
                    role = WearActionButtonRole.Secondary,
                ),
            ),
    ) {
        Text(
            text = "This resets settings and theme preferences.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
