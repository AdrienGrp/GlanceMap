package com.glancemap.glancemapwearos.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun DeleteConfirmationDialog(
    visible: Boolean,
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "Delete",
    dismissText: String = "Cancel",
    messageTopPadding: Dp = 0.dp,
    messageBottomPadding: Dp = 0.dp,
) {
    if (!visible) return

    WearActionDialog(
        visible = visible,
        title = title,
        message = message,
        confirmText = confirmText,
        onConfirm = onConfirm,
        onDismissRequest = onDismiss,
        dismissText = dismissText,
        confirmRole = WearActionButtonRole.Destructive,
        messageTopPadding = messageTopPadding,
        messageBottomPadding = messageBottomPadding,
    )
}
