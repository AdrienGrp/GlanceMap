package com.glancemap.glancemapcompanionapp.livetracking

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactMail
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ColumnScope.SettingsContent(
    onBack: () -> Unit,
    userName: String,
    onUserNameChange: (String) -> Unit,
    notificationEmailInput: String,
    onNotificationEmailInputChange: (String) -> Unit,
    notificationEmailAddresses: List<String>,
    onNotificationEmailAdd: (String) -> Unit,
    onNotificationEmailRemove: (String) -> Unit,
    onPickNotificationEmailFromContacts: () -> Unit,
    alertEmailInput: String,
    onAlertEmailInputChange: (String) -> Unit,
    alertEmailAddresses: List<String>,
    onAlertEmailAdd: (String) -> Unit,
    onAlertEmailRemove: (String) -> Unit,
    onPickAlertEmailFromContacts: () -> Unit,
    stuckAlarmMinutes: String,
    onStuckAlarmMinutesChange: (String) -> Unit,
    updateIntervalSeconds: Int,
    onUpdateIntervalSecondsChange: (Int) -> Unit,
    isSavingSettings: Boolean,
    saveSettingsStatusMessage: String?,
    onSaveSettings: () -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
    contentSpacing: androidx.compose.ui.unit.Dp,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        FilledTonalIconButton(
            onClick = onBack,
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .size(36.dp),
            colors = companionTonalIconButtonColors(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.align(Alignment.Center),
        )
    }

    ScrollableScreenContent(
        scrollState = scrollState,
        contentSpacing = contentSpacing,
    ) {
        Button(
            onClick = onSaveSettings,
            enabled = !isSavingSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isSavingSettings) "Saving" else "Save")
        }
        saveSettingsStatusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (
                        message.startsWith("Save failed", ignoreCase = true) ||
                        message.contains("required", ignoreCase = true)
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }

        TrackingPanel(title = "Participant") {
            OutlinedTextField(
                value = userName,
                onValueChange = onUserNameChange,
                label = { Text("Participant name") },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        TrackingPanel(title = "Notifications") {
            Text(
                text = "GPS update frequency: ${formatUpdateInterval(updateIntervalSeconds)}",
                style = MaterialTheme.typography.labelMedium,
            )
            FrequencyPresetGrid(
                selectedSeconds = updateIntervalSeconds,
                onSelected = onUpdateIntervalSecondsChange,
            )
            EmailAddressInput(
                label = "Send tracking notifications & alerts",
                input = notificationEmailInput,
                onInputChange = onNotificationEmailInputChange,
                addresses = notificationEmailAddresses,
                onAdd = onNotificationEmailAdd,
                onRemove = onNotificationEmailRemove,
                onPickFromContacts = onPickNotificationEmailFromContacts,
            )
            EmailAddressInput(
                label = "Send only alerts to",
                input = alertEmailInput,
                onInputChange = onAlertEmailInputChange,
                addresses = alertEmailAddresses,
                onAdd = onAlertEmailAdd,
                onRemove = onAlertEmailRemove,
                onPickFromContacts = onPickAlertEmailFromContacts,
            )
            NoMovementAlertInput(
                minutes = stuckAlarmMinutes,
                onMinutesChange = onStuckAlarmMinutesChange,
            )
        }
    }
}

@Composable
private fun companionTonalIconButtonColors() =
    IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

@Composable
internal fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    isVisible: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        singleLine = true,
        visualTransformation =
            if (isVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Password,
            ),
        trailingIcon = {
            FilledTonalIconButton(
                onClick = { onVisibilityChange(!isVisible) },
                modifier = Modifier.size(36.dp),
                colors = companionTonalIconButtonColors(),
            ) {
                Icon(
                    imageVector =
                        if (isVisible) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                    contentDescription = if (isVisible) "Hide password" else "Show password",
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun NoMovementAlertInput(
    minutes: String,
    onMinutesChange: (String) -> Unit,
) {
    val isDisabled = minutes == "-1"

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isDisabled,
                onCheckedChange = { disabled ->
                    onMinutesChange(if (disabled) "-1" else "")
                },
            )
            Text(
                text = "Disable no-movement alerts",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = "Send alert email when no movement for",
            style = MaterialTheme.typography.labelMedium,
            color =
                if (isDisabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = if (isDisabled) "" else minutes,
                onValueChange = onMinutesChange,
                enabled = !isDisabled,
                placeholder = { Text("default") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "minutes",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EmailAddressInput(
    label: String,
    input: String,
    onInputChange: (String) -> Unit,
    addresses: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onPickFromContacts: () -> Unit,
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val email = input.trim().trimEnd(',', ';').lowercase()
    val isEmailValid = email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    val isDuplicate = addresses.any { it.equals(email, ignoreCase = true) }
    val canAddEmail = isEmailValid && !isDuplicate

    fun submitEmail(): Boolean {
        if (email.isBlank()) return false
        if (!isEmailValid) {
            errorMessage = "Enter a valid email address"
            return true
        }
        if (isDuplicate) {
            errorMessage = "Email already added"
            return true
        }

        onAdd(email)
        onInputChange("")
        errorMessage = null
        return true
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    onInputChange(it)
                    errorMessage = null
                },
                placeholder = { Text("email@example.com") },
                trailingIcon = {
                    IconButton(onClick = onPickFromContacts) {
                        Icon(
                            imageVector = Icons.Filled.ContactMail,
                            contentDescription = "Pick email from contacts",
                        )
                    }
                },
                supportingText = errorMessage?.let { message -> { Text(message) } },
                isError = errorMessage != null,
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { submitEmail() }),
                modifier =
                    Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { event ->
                            when {
                                event.key != Key.Enter -> false
                                event.type == KeyEventType.KeyDown -> submitEmail()
                                else -> true
                            }
                        },
            )
            Button(
                onClick = { submitEmail() },
                enabled = canAddEmail,
            ) {
                Text("Add")
            }
        }
        if (addresses.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                addresses.forEach { email ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(email) },
                        label = {
                            Text(
                                text = email,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove $email",
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FrequencyPresetGrid(
    selectedSeconds: Int,
    onSelected: (Int) -> Unit,
) {
    val presets =
        listOf(
            15 to "15s",
            30 to "30s",
            60 to "1 min",
            120 to "2 min",
            300 to "5 min",
            600 to "10 min",
        )
    presets.chunked(3).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { (seconds, label) ->
                val selected = selectedSeconds == seconds
                if (selected) {
                    Button(
                        onClick = { onSelected(seconds) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(label)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelected(seconds) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(label)
                    }
                }
            }
            repeat(3 - row.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

internal fun formatUpdateInterval(seconds: Int): String {
    if (seconds < 60) return "$seconds seconds"
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (remainingSeconds == 0) {
        "$minutes min"
    } else {
        "$minutes min $remainingSeconds sec"
    }
}
