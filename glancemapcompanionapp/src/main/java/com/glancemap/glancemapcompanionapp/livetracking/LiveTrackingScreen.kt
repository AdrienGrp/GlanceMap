package com.glancemap.glancemapcompanionapp.livetracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpCenter
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.glancemap.glancemapcompanionapp.resolveUriDisplayName
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private enum class LiveTrackingPage {
    MAIN,
    SETTINGS,
    ABOUT,
}

@Composable
fun LiveTrackingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionState by LiveTrackingSessionStore.state.collectAsState()
    var page by remember { mutableStateOf(LiveTrackingPage.MAIN) }
    var group by remember { mutableStateOf("") }
    var participantPassword by remember { mutableStateOf("") }
    var followerPassword by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var notificationEmailInput by remember { mutableStateOf("") }
    var notificationEmailAddresses by remember { mutableStateOf<List<String>>(emptyList()) }
    var alertEmailInput by remember { mutableStateOf("") }
    var alertEmailAddresses by remember { mutableStateOf<List<String>>(emptyList()) }
    var stuckAlarmMinutes by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf("") }
    var trackingEndpoint by remember { mutableStateOf(ArkluzTrackingEndpoint.PRODUCTION) }
    var updateIntervalSeconds by remember { mutableStateOf(60) }
    var customUpdateIntervalSeconds by remember { mutableStateOf("") }
    var selectedGpxUri by remember { mutableStateOf<Uri?>(null) }
    var selectedGpxName by remember { mutableStateOf("") }
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var sendStatusMessage by remember { mutableStateOf<String?>(null) }
    var isSendingPlan by remember { mutableStateOf(false) }
    var planSent by remember { mutableStateOf(false) }

    val gpxPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            selectedGpxUri = uri
            selectedGpxName = resolveUriDisplayName(context, uri).ifBlank { "Selected GPX" }
            planSent = false
            sendStatusMessage = null
        }
    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { result ->
            hasLocationPermission =
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                hasLocationPermission(context)
        }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
    ) {
        val compact = maxWidth < 420.dp
        val contentSpacing = if (compact) 8.dp else 10.dp
        val scrollState = rememberScrollState()
        val settings =
            remember(
                group,
                participantPassword,
                followerPassword,
                userName,
                notificationEmailAddresses,
                alertEmailAddresses,
                stuckAlarmMinutes,
                comments,
                selectedGpxUri,
                selectedGpxName,
                planSent,
                trackingEndpoint,
                updateIntervalSeconds,
            ) {
                LiveTrackingSettings(
                    trackingUrl = trackingEndpoint.url,
                    updateIntervalSeconds = updateIntervalSeconds,
                    group = group,
                    participantPassword = participantPassword,
                    followerPassword = followerPassword,
                    userName = userName,
                    notificationEmails = notificationEmailAddresses.joinToString(","),
                    alertEmails = alertEmailAddresses.joinToString(","),
                    stuckAlarmMinutes = stuckAlarmMinutes,
                    comments = if (planSent) "" else comments,
                    gpxUri = if (planSent) null else selectedGpxUri,
                    gpxName = selectedGpxName,
                )
            }
        val groupTrackUrl =
            remember(group, followerPassword, userName, trackingEndpoint) {
                arkluzTrackUrl(
                    baseUrl = trackingEndpoint.url,
                    group = group,
                    followerPassword = followerPassword,
                    user = null,
                    selectedUser = userName,
                )
            }
        val userTrackUrl =
            remember(group, followerPassword, userName, trackingEndpoint) {
                arkluzTrackUrl(
                    baseUrl = trackingEndpoint.url,
                    group = group,
                    followerPassword = followerPassword,
                    user = userName,
                    selectedUser = null,
                )
            }
        val canSendPlan = selectedGpxUri != null || comments.isNotBlank()
        val canStart =
            group.isNotBlank() &&
                participantPassword.isNotBlank() &&
                userName.isNotBlank()

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
        ) {
            when (page) {
                LiveTrackingPage.MAIN -> {
                    MainTrackingContent(
                        onBack = onBack,
                        onOpenSettings = { page = LiveTrackingPage.SETTINGS },
                        onOpenAbout = { page = LiveTrackingPage.ABOUT },
                        selectedGpxName = selectedGpxName,
                        comments = comments,
                        onCommentsChange = {
                            comments = it
                            planSent = false
                            sendStatusMessage = null
                        },
                        onPickGpx = {
                            gpxPicker.launch(arrayOf("application/gpx+xml", "text/xml", "*/*"))
                        },
                        canSendPlan = canSendPlan,
                        isSendingPlan = isSendingPlan,
                        onSendPlan = {
                            validationMessage = validatePlanSettings(group, participantPassword)
                            if (validationMessage != null) return@MainTrackingContent
                            isSendingPlan = true
                            sendStatusMessage = "Checking group"
                            coroutineScope.launch {
                                runCatching {
                                    val client = ArkluzLiveTrackingClient(context)
                                    client.registerOrJoinGroup(settings)
                                    sendStatusMessage = "Sending planned route"
                                    client.uploadPlannedRoute(settings)
                                }.onSuccess { result ->
                                    planSent = true
                                    sendStatusMessage = result.message.ifBlank { "Sent" }
                                }.onFailure { error ->
                                    sendStatusMessage = "Send failed: ${error.message ?: "unknown error"}"
                                }
                                isSendingPlan = false
                            }
                        },
                        sessionState = sessionState,
                        validationMessage = validationMessage,
                        sendStatusMessage = sendStatusMessage,
                        onStart = {
                            validationMessage = validateStartSettings(group, participantPassword, userName)
                            if (!canStart) return@MainTrackingContent
                            if (!hasLocationPermission) {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                                return@MainTrackingContent
                            }
                            validationMessage = null
                            LiveTrackingService.start(context = context, settings = settings)
                        },
                        onStop = { LiveTrackingService.stop(context) },
                        groupTrackUrl = groupTrackUrl,
                        userTrackUrl = userTrackUrl,
                        scrollState = scrollState,
                        contentSpacing = contentSpacing,
                    )
                }

                LiveTrackingPage.SETTINGS -> {
                    SettingsContent(
                        onBack = { page = LiveTrackingPage.MAIN },
                        group = group,
                        onGroupChange = { group = it },
                        participantPassword = participantPassword,
                        onParticipantPasswordChange = { participantPassword = it },
                        followerPassword = followerPassword,
                        onFollowerPasswordChange = { followerPassword = it },
                        userName = userName,
                        onUserNameChange = { userName = it },
                        notificationEmailInput = notificationEmailInput,
                        onNotificationEmailInputChange = { notificationEmailInput = it },
                        notificationEmailAddresses = notificationEmailAddresses,
                        onNotificationEmailAdd = { email ->
                            notificationEmailAddresses = notificationEmailAddresses + email
                        },
                        onNotificationEmailRemove = { email ->
                            notificationEmailAddresses = notificationEmailAddresses - email
                        },
                        alertEmailInput = alertEmailInput,
                        onAlertEmailInputChange = { alertEmailInput = it },
                        alertEmailAddresses = alertEmailAddresses,
                        onAlertEmailAdd = { email ->
                            alertEmailAddresses = alertEmailAddresses + email
                        },
                        onAlertEmailRemove = { email ->
                            alertEmailAddresses = alertEmailAddresses - email
                        },
                        stuckAlarmMinutes = stuckAlarmMinutes,
                        onStuckAlarmMinutesChange = { value ->
                            stuckAlarmMinutes = value.filter(Char::isDigit)
                        },
                        updateIntervalSeconds = updateIntervalSeconds,
                        onUpdateIntervalSecondsChange = { updateIntervalSeconds = it },
                        customUpdateIntervalSeconds = customUpdateIntervalSeconds,
                        onCustomUpdateIntervalSecondsChange = { customUpdateIntervalSeconds = it },
                        scrollState = scrollState,
                        contentSpacing = contentSpacing,
                    )
                }

                LiveTrackingPage.ABOUT -> {
                    AboutContent(
                        onBack = { page = LiveTrackingPage.MAIN },
                        scrollState = scrollState,
                        contentSpacing = contentSpacing,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.MainTrackingContent(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    selectedGpxName: String,
    comments: String,
    onCommentsChange: (String) -> Unit,
    onPickGpx: () -> Unit,
    canSendPlan: Boolean,
    isSendingPlan: Boolean,
    onSendPlan: () -> Unit,
    sessionState: LiveTrackingUiState,
    validationMessage: String?,
    sendStatusMessage: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    groupTrackUrl: String,
    userTrackUrl: String,
    scrollState: androidx.compose.foundation.ScrollState,
    contentSpacing: androidx.compose.ui.unit.Dp,
) {
    val context = LocalContext.current

    HeaderRow(
        onBack = onBack,
        actions = {
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Settings")
            }
            OutlinedButton(onClick = onOpenAbout, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpCenter,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("About / FAQ")
            }
        },
    )

    Column(
        modifier =
            Modifier
                .weight(1f)
                .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(contentSpacing),
    ) {
        TrackingPanel(title = "Today's planned route") {
            OutlinedButton(
                onClick = onPickGpx,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Route,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Select GPX")
            }
            Text(
                text = selectedGpxName.ifBlank { "No GPX selected" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        TrackingPanel(title = "Comments to send in today's notification email") {
            OutlinedTextField(
                value = comments,
                onValueChange = onCommentsChange,
                label = { Text("Comments") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onSendPlan,
                enabled = canSendPlan && !isSendingPlan,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isSendingPlan) "Sending" else "Send")
            }
            sendStatusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        TrackingPanel(title = "Session") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onStart,
                    enabled = !sessionState.isTracking,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Start")
                }
                OutlinedButton(
                    onClick = onStop,
                    enabled = sessionState.isTracking,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Stop")
                }
            }
            Text(
                text = sessionStatusText(sessionState),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            validationMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            sessionState.lastError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        TrackingPanel(title = "Track links") {
            TrackLinkRow(
                label = "Group",
                url = groupTrackUrl,
                onView = { openUrl(context, groupTrackUrl) },
                onShare = { shareUrl(context, groupTrackUrl) },
            )
            TrackLinkRow(
                label = "User",
                url = userTrackUrl,
                onView = { openUrl(context, userTrackUrl) },
                onShare = { shareUrl(context, userTrackUrl) },
            )
        }
    }
}

@Composable
private fun ColumnScope.SettingsContent(
    onBack: () -> Unit,
    group: String,
    onGroupChange: (String) -> Unit,
    participantPassword: String,
    onParticipantPasswordChange: (String) -> Unit,
    followerPassword: String,
    onFollowerPasswordChange: (String) -> Unit,
    userName: String,
    onUserNameChange: (String) -> Unit,
    notificationEmailInput: String,
    onNotificationEmailInputChange: (String) -> Unit,
    notificationEmailAddresses: List<String>,
    onNotificationEmailAdd: (String) -> Unit,
    onNotificationEmailRemove: (String) -> Unit,
    alertEmailInput: String,
    onAlertEmailInputChange: (String) -> Unit,
    alertEmailAddresses: List<String>,
    onAlertEmailAdd: (String) -> Unit,
    onAlertEmailRemove: (String) -> Unit,
    stuckAlarmMinutes: String,
    onStuckAlarmMinutesChange: (String) -> Unit,
    updateIntervalSeconds: Int,
    onUpdateIntervalSecondsChange: (Int) -> Unit,
    customUpdateIntervalSeconds: String,
    onCustomUpdateIntervalSecondsChange: (String) -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
    contentSpacing: androidx.compose.ui.unit.Dp,
) {
    HeaderRow(onBack = onBack) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
    }

    Column(
        modifier =
            Modifier
                .weight(1f)
                .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(contentSpacing),
    ) {
        TrackingPanel(title = "Tracking account") {
            OutlinedTextField(
                value = group,
                onValueChange = onGroupChange,
                label = { Text("Private group") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = participantPassword,
                onValueChange = onParticipantPasswordChange,
                label = { Text("Participant password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = followerPassword,
                onValueChange = onFollowerPasswordChange,
                label = { Text("Follower password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
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
                onSelected = { seconds ->
                    onUpdateIntervalSecondsChange(seconds)
                    onCustomUpdateIntervalSecondsChange("")
                },
            )
            OutlinedTextField(
                value = customUpdateIntervalSeconds,
                onValueChange = { rawValue ->
                    val sanitized = rawValue.filter(Char::isDigit).take(3)
                    onCustomUpdateIntervalSecondsChange(sanitized)
                    sanitized
                        .toIntOrNull()
                        ?.coerceIn(15, 900)
                        ?.let(onUpdateIntervalSecondsChange)
                },
                label = { Text("Custom seconds") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            EmailAddressInput(
                label = "Start notification email",
                input = notificationEmailInput,
                onInputChange = onNotificationEmailInputChange,
                addresses = notificationEmailAddresses,
                onAdd = onNotificationEmailAdd,
                onRemove = onNotificationEmailRemove,
            )
            EmailAddressInput(
                label = "Alert email",
                input = alertEmailInput,
                onInputChange = onAlertEmailInputChange,
                addresses = alertEmailAddresses,
                onAdd = onAlertEmailAdd,
                onRemove = onAlertEmailRemove,
            )
            OutlinedTextField(
                value = stuckAlarmMinutes,
                onValueChange = onStuckAlarmMinutesChange,
                label = { Text("Stuck alarm minutes") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
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
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submitEmail(): Boolean {
        val email = input.trim().trimEnd(',', ';').lowercase()
        if (email.isBlank()) return false
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errorMessage = "Enter a valid email address"
            return true
        }
        if (addresses.any { it.equals(email, ignoreCase = true) }) {
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
        OutlinedTextField(
            value = input,
            onValueChange = {
                onInputChange(it)
                errorMessage = null
            },
            label = { Text(label) },
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
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        when {
                            event.key != Key.Enter -> false
                            event.type == KeyEventType.KeyDown -> submitEmail()
                            else -> true
                        }
                    },
        )
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

private fun formatUpdateInterval(seconds: Int): String {
    if (seconds < 60) return "$seconds seconds"
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (remainingSeconds == 0) {
        "$minutes min"
    } else {
        "$minutes min $remainingSeconds sec"
    }
}

@Composable
private fun ColumnScope.AboutContent(
    onBack: () -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
    contentSpacing: androidx.compose.ui.unit.Dp,
) {
    val context = LocalContext.current

    HeaderRow(onBack = onBack) {
        Text(
            text = "About / FAQ",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
    }

    Column(
        modifier =
            Modifier
                .weight(1f)
                .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(contentSpacing),
    ) {
        TrackingPanel(title = "Privacy policy") {
            Text(
                text =
                    "Tracks and positions are automatically deleted after 7 days. Email addresses are only used " +
                        "to send a notification email when tracking starts or unexpectedly stops, and they are " +
                        "not stored. No data is processed, stored or shared, for any other purpose.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        TrackingPanel(title = "Disclaimer") {
            Text(
                text =
                    "This service is provided as a non-commercial, home-grown tool to assist in locating " +
                        "participants during sporting activities. It is offered \"as is\" without any guarantees " +
                        "or warranties. Use of this service is at your own risk; neither the developer nor the " +
                        "hosting provider (OVH) assumes liability for any direct, indirect, incidental, or " +
                        "consequential damages arising out of its use. This service does not replace emergency " +
                        "or professional safety measures. If you need urgent assistance, please contact " +
                        "appropriate emergency services. By using this service, you acknowledge and accept " +
                        "these terms.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = { openUrl(context = context, url = ArkluzTrackingEndpoint.PRODUCTION.url) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open arkluz.com/trk")
            }
            OutlinedButton(
                onClick = { openUrl(context = context, url = "https://arkluz.com/trk?contact") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Contact")
            }
        }

        TrackingPanel(title = "Contributions") {
            Text(
                text = "Jérôme Seydoux",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun HeaderRow(
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(20.dp),
            )
        }
        actions()
    }
}

private fun validatePlanSettings(
    group: String,
    participantPassword: String,
): String? =
    when {
        group.isBlank() -> "Private group is required."
        participantPassword.isBlank() -> "Participant password is required."
        else -> null
    }

private fun validateStartSettings(
    group: String,
    participantPassword: String,
    userName: String,
): String? =
    when {
        group.isBlank() -> "Private group is required."
        participantPassword.isBlank() -> "Participant password is required."
        userName.isBlank() -> "Participant name is required."
        else -> null
    }

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun sessionStatusText(state: LiveTrackingUiState): String {
    val lastUpdate = state.lastSuccessfulUpdateEpochMs
    val lastUpdateText =
        if (lastUpdate == null) {
            "none"
        } else {
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastUpdate))
        }
    return "${state.status}. Last successful update: $lastUpdateText"
}

@Composable
private fun TrackingPanel(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun TrackLinkRow(
    label: String,
    url: String,
    onView: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalIconButton(
            onClick = onView,
            enabled = url.isNotBlank(),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Visibility,
                contentDescription = "View $label track",
                modifier = Modifier.size(18.dp),
            )
        }
        FilledTonalIconButton(
            onClick = onShare,
            enabled = url.isNotBlank(),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = "Share $label track",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun arkluzTrackUrl(
    baseUrl: String,
    group: String,
    followerPassword: String,
    user: String?,
    selectedUser: String?,
): String {
    val cleanGroup = group.trim()
    val cleanPassword = followerPassword.trim()
    if (cleanGroup.isBlank() || cleanPassword.isBlank()) return ""

    val builder =
        Uri
            .parse(baseUrl)
            .buildUpon()
            .appendQueryParameter("q", "track")
            .appendQueryParameter("group", cleanGroup)
            .appendQueryParameter("p", cleanPassword)
    user
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { builder.appendQueryParameter("user", it) }
    selectedUser
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { builder.appendQueryParameter("select", it) }
    return builder.build().toString()
}

private fun openUrl(
    context: Context,
    url: String,
) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun shareUrl(
    context: Context,
    url: String,
) {
    if (url.isBlank()) return
    val intent =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(intent, "Share track link"))
}
