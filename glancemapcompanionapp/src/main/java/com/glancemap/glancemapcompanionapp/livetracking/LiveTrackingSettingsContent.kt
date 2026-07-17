@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "ReturnCount",
)

package com.glancemap.glancemapcompanionapp.livetracking

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactMail
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    group: String,
    onChangeGroup: () -> Unit,
    userName: String,
    onUserNameChange: (String) -> Unit,
    notificationEmailInput: String,
    onNotificationEmailInputChange: (String) -> Unit,
    notificationEmailAddresses: List<String>,
    onNotificationEmailAdd: (String) -> Unit,
    onNotificationEmailRemove: (String) -> Unit,
    onPickNotificationEmailFromContacts: () -> Unit,
    alertRecipientInput: String,
    onAlertRecipientInputChange: (String) -> Unit,
    alertRecipients: List<String>,
    onAlertRecipientAdd: (AlertRecipient) -> Unit,
    onAlertRecipientRemove: (String) -> Unit,
    onPickAlertEmailFromContacts: () -> Unit,
    onPickAlertPhoneFromContacts: () -> Unit,
    isValidatingAlertRecipient: Boolean,
    alertRecipientStatusMessage: String?,
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
    HeaderRow(onBack = onBack) {
        Text(
            text = "Live tracking setup",
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }

    ScrollableScreenContent(
        scrollState = scrollState,
        contentSpacing = contentSpacing,
    ) {
        TrackingPanel(title = "Private group") {
            Text(
                text = "Connected to ${group.trim().ifBlank { "private group" }}",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Your tracking links and options are linked to this group.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onChangeGroup,
                enabled = !isSavingSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Change group")
            }
        }

        TrackingPanel(title = "Participant:") {
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
                label = "Send tracking notifications & safety alerts to:",
                input = notificationEmailInput,
                onInputChange = onNotificationEmailInputChange,
                addresses = notificationEmailAddresses,
                onAdd = onNotificationEmailAdd,
                onRemove = onNotificationEmailRemove,
                onPickFromContacts = onPickNotificationEmailFromContacts,
            )
            AlertRecipientInput(
                label = "Also send safety alerts to:",
                input = alertRecipientInput,
                onInputChange = onAlertRecipientInputChange,
                recipients = alertRecipients,
                onAdd = onAlertRecipientAdd,
                onRemove = onAlertRecipientRemove,
                onPickEmailFromContacts = onPickAlertEmailFromContacts,
                onPickPhoneFromContacts = onPickAlertPhoneFromContacts,
                isValidating = isValidatingAlertRecipient,
                statusMessage = alertRecipientStatusMessage,
            )
            NoMovementAlertInput(
                minutes = stuckAlarmMinutes,
                onMinutesChange = onStuckAlarmMinutesChange,
            )
        }

        Button(
            onClick = onSaveSettings,
            enabled = !isSavingSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isSavingSettings) "Saving" else "Save and return")
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
    }
}

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
            IconButton(
                onClick = { onVisibilityChange(!isVisible) },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector =
                        if (isVisible) {
                            Icons.Filled.Visibility
                        } else {
                            Icons.Filled.VisibilityOff
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
@Suppress("FunctionNaming", "LongMethod")
private fun NoMovementAlertInput(
    minutes: String,
    onMinutesChange: (String) -> Unit,
) {
    val isDisabled = minutes == "-1"
    val validationMessage = validateNoMovementAlertMinutes(minutes)
    var lastEnabledMinutes by remember {
        mutableStateOf(
            minutes.takeUnless { it == "-1" } ?: DEFAULT_NO_MOVEMENT_ALERT_MINUTES,
        )
    }

    LaunchedEffect(minutes) {
        if (minutes != "-1" && validateNoMovementAlertMinutes(minutes) == null) {
            lastEnabledMinutes = minutes
        }
    }

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
                    onMinutesChange(
                        if (disabled) {
                            "-1"
                        } else {
                            lastEnabledMinutes
                        },
                    )
                },
            )
            Text(
                text = "Disable no-movement alerts",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = "Send safety alert when no movement for:",
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
                onValueChange = { value ->
                    if (validateNoMovementAlertMinutes(value) == null && value != "-1") {
                        lastEnabledMinutes = value
                    }
                    onMinutesChange(value)
                },
                enabled = !isDisabled,
                placeholder = { Text(DEFAULT_NO_MOVEMENT_ALERT_MINUTES) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = !isDisabled && validationMessage != null,
                supportingText =
                    if (!isDisabled) {
                        {
                            Text(validationMessage ?: "Minimum 10 minutes")
                        }
                    } else {
                        null
                    },
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
        Text(text = label, style = MaterialTheme.typography.labelMedium)
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
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
            Button(onClick = { submitEmail() }, enabled = canAddEmail) {
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
private fun AlertRecipientInput(
    label: String,
    input: String,
    onInputChange: (String) -> Unit,
    recipients: List<String>,
    onAdd: (AlertRecipient) -> Unit,
    onRemove: (String) -> Unit,
    onPickEmailFromContacts: () -> Unit,
    onPickPhoneFromContacts: () -> Unit,
    isValidating: Boolean,
    statusMessage: String?,
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val recipient = normalizedAlertRecipient(input)
    val isDuplicate =
        recipient != null && recipients.any { it.equals(recipient.value, ignoreCase = true) }
    val canAddRecipient = recipient != null && !isDuplicate && !isValidating

    fun submitRecipient(): Boolean {
        if (input.isBlank()) return false
        if (recipient == null) {
            errorMessage = "Enter a valid email address or phone number starting with +"
            return true
        }
        if (isDuplicate) {
            errorMessage = "Recipient already added"
            return true
        }

        onAdd(recipient)
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
                placeholder = { Text("email@example.com or +33612345678") },
                trailingIcon = {
                    Row {
                        IconButton(
                            onClick = onPickEmailFromContacts,
                            enabled = !isValidating,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContactMail,
                                contentDescription = "Pick email from contacts",
                            )
                        }
                        IconButton(
                            onClick = onPickPhoneFromContacts,
                            enabled = !isValidating,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Phone,
                                contentDescription = "Pick phone number from contacts",
                            )
                        }
                    }
                },
                supportingText =
                    (errorMessage ?: statusMessage)?.let { message ->
                        { Text(message) }
                    },
                isError = errorMessage != null || statusMessage != null,
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { submitRecipient() }),
                modifier =
                    Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { event ->
                            when {
                                event.key != Key.Enter -> false
                                event.type == KeyEventType.KeyDown -> submitRecipient()
                                else -> true
                            }
                        },
            )
            Button(
                onClick = { submitRecipient() },
                enabled = canAddRecipient,
            ) {
                Text(if (isValidating) "Checking" else "Add")
            }
        }
        if (recipients.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                recipients.forEach { recipientValue ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(recipientValue) },
                        label = {
                            Text(
                                text = recipientValue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove $recipientValue",
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
