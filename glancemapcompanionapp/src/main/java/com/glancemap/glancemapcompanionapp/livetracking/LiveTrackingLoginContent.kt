@file:Suppress(
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
)

package com.glancemap.glancemapcompanionapp.livetracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ColumnScope.LoginJoinContent(
    onBack: () -> Unit,
    group: String,
    onGroupChange: (String) -> Unit,
    participantPassword: String,
    onParticipantPasswordChange: (String) -> Unit,
    isLoginJoinLoading: Boolean,
    loginJoinStatusMessage: String?,
    onLoginJoin: () -> Unit,
    showCreateGroupDialog: Boolean,
    createGroupPasswordConfirmation: String,
    onCreateGroupPasswordConfirmationChange: (String) -> Unit,
    onDismissCreateGroupDialog: () -> Unit,
    onConfirmCreateGroup: () -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
    contentSpacing: androidx.compose.ui.unit.Dp,
) {
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isPasswordConfirmationVisible by remember { mutableStateOf(false) }

    HeaderRow(onBack = onBack) {
        Text(
            text = "Live tracking setup",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
    }

    ScrollableScreenContent(
        scrollState = scrollState,
        contentSpacing = contentSpacing,
    ) {
        TrackingPanel(title = "Private group") {
            Text(
                text = "Connect to an existing group or create a new private group.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = group,
                onValueChange = onGroupChange,
                label = { Text("Group") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            PasswordField(
                value = participantPassword,
                onValueChange = onParticipantPasswordChange,
                label = { Text("Password") },
                isVisible = isPasswordVisible,
                onVisibilityChange = { isPasswordVisible = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onLoginJoin,
                enabled = !isLoginJoinLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        isLoginJoinLoading -> "Checking"
                        else -> "Connect"
                    },
                )
            }
            loginJoinStatusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (message.startsWith("Error", ignoreCase = true)) {
                            MaterialTheme.colorScheme.error
                        } else if (message.startsWith("Unable", ignoreCase = true)) {
                            MaterialTheme.colorScheme.error
                        } else if (message.contains("failed", ignoreCase = true)) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }

    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = onDismissCreateGroupDialog,
            title = { Text("Create group?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This group does not exist yet. Type the password again to create it.")
                    PasswordField(
                        value = createGroupPasswordConfirmation,
                        onValueChange = onCreateGroupPasswordConfirmationChange,
                        label = { Text("Password confirmation") },
                        isVisible = isPasswordConfirmationVisible,
                        onVisibilityChange = { isPasswordConfirmationVisible = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmCreateGroup,
                    enabled = createGroupPasswordConfirmation.isNotBlank(),
                ) {
                    Text("Create group")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissCreateGroupDialog) {
                    Text("Cancel")
                }
            },
        )
    }
}
