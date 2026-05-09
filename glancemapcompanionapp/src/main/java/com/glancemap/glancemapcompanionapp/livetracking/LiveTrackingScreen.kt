package com.glancemap.glancemapcompanionapp.livetracking

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpCenter
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactMail
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.glancemap.glancemapcompanionapp.resolveUriDisplayName
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private enum class LiveTrackingPage {
    MAIN,
    LOGIN,
    SETTINGS,
    ABOUT,
}

private enum class EmailPickerTarget {
    NOTIFICATION,
    ALERT,
}

@Composable
fun LiveTrackingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionState by LiveTrackingSessionStore.state.collectAsState()
    val savedSettings = remember(context) { LiveTrackingPreferences.load(context) }
    var page by remember { mutableStateOf(LiveTrackingPage.MAIN) }
    var group by remember { mutableStateOf(savedSettings.group) }
    var participantPassword by remember { mutableStateOf(savedSettings.participantPassword) }
    var followerPassword by remember { mutableStateOf(savedSettings.followerPassword) }
    var userName by remember { mutableStateOf(savedSettings.userName) }
    var notificationEmailInput by remember { mutableStateOf("") }
    var notificationEmailAddresses by remember {
        mutableStateOf(savedSettings.notificationEmailAddresses)
    }
    var alertEmailInput by remember { mutableStateOf("") }
    var alertEmailAddresses by remember { mutableStateOf(savedSettings.alertEmailAddresses) }
    var stuckAlarmMinutes by remember { mutableStateOf(savedSettings.stuckAlarmMinutes) }
    var comments by remember { mutableStateOf("") }
    var trackingEndpoint by remember { mutableStateOf(ArkluzTrackingEndpoint.DEVELOPMENT) }
    var updateIntervalSeconds by remember { mutableStateOf(savedSettings.updateIntervalSeconds) }
    var selectedGpxUri by remember { mutableStateOf<Uri?>(null) }
    var selectedGpxName by remember { mutableStateOf("") }
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var headerMessage by remember { mutableStateOf<String?>(null) }
    var sendStatusMessage by remember { mutableStateOf<String?>(null) }
    var loginJoinStatusMessage by remember { mutableStateOf<String?>(null) }
    var isLoginJoinLoading by remember { mutableStateOf(false) }
    var saveSettingsStatusMessage by remember { mutableStateOf<String?>(null) }
    var isSavingSettings by remember { mutableStateOf(false) }
    var pendingRegistrationGroup by remember { mutableStateOf<String?>(null) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var createGroupPasswordConfirmation by remember { mutableStateOf("") }
    var isSendingPlan by remember { mutableStateOf(false) }
    var deleteTracksStatusMessage by remember { mutableStateOf<String?>(null) }
    var isDeletingTracks by remember { mutableStateOf(false) }
    var planSent by remember { mutableStateOf(false) }
    var emailPickerTarget by remember { mutableStateOf<EmailPickerTarget?>(null) }

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
    val contactEmailPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val target = emailPickerTarget
            emailPickerTarget = null
            if (result.resultCode != Activity.RESULT_OK || target == null) return@rememberLauncherForActivityResult
            val email =
                result.data
                    ?.data
                    ?.let { uri -> resolveSelectedContactEmail(context, uri) }
            if (email.isNullOrBlank()) {
                saveSettingsStatusMessage = "No email address selected"
                return@rememberLauncherForActivityResult
            }
            when (target) {
                EmailPickerTarget.NOTIFICATION -> {
                    if (notificationEmailAddresses.any { it.equals(email, ignoreCase = true) }) {
                        saveSettingsStatusMessage = "Email already added"
                    } else {
                        notificationEmailAddresses = notificationEmailAddresses + email
                        notificationEmailInput = ""
                        saveSettingsStatusMessage = null
                    }
                }

                EmailPickerTarget.ALERT -> {
                    if (alertEmailAddresses.any { it.equals(email, ignoreCase = true) }) {
                        saveSettingsStatusMessage = "Email already added"
                    } else {
                        alertEmailAddresses = alertEmailAddresses + email
                        alertEmailInput = ""
                        saveSettingsStatusMessage = null
                    }
                }
            }
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

    LaunchedEffect(
        group,
        participantPassword,
        followerPassword,
        userName,
        notificationEmailAddresses,
        alertEmailAddresses,
        stuckAlarmMinutes,
        updateIntervalSeconds,
    ) {
        val currentSettings =
            SavedLiveTrackingSettings(
                group = group,
                participantPassword = participantPassword,
                followerPassword = followerPassword,
                userName = userName,
                notificationEmailAddresses = notificationEmailAddresses,
                alertEmailAddresses = alertEmailAddresses,
                stuckAlarmMinutes = stuckAlarmMinutes,
                updateIntervalSeconds = updateIntervalSeconds,
            )
        LiveTrackingPreferences.save(
            context = context,
            settings = currentSettings,
        )
        if (followerPassword.isNotBlank()) {
            LiveTrackingPreferences.saveGroupSettings(
                context = context,
                settings = currentSettings,
            )
        }
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
                notificationEmailInput,
                alertEmailAddresses,
                alertEmailInput,
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
                    notificationEmails =
                        emailAddressesForRequest(
                            addresses = notificationEmailAddresses,
                            pendingInput = notificationEmailInput,
                        ),
                    alertEmails =
                        emailAddressesForRequest(
                            addresses = alertEmailAddresses,
                            pendingInput = alertEmailInput,
                        ),
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
        val isConnected = followerPassword.isNotBlank()
        val hasPlanContent = selectedGpxUri != null || comments.isNotBlank()
        val showSendPlan = sessionState.isTracking && hasPlanContent
        val canSendPlan = showSendPlan && isConnected
        val canStart =
            group.isNotBlank() &&
                participantPassword.isNotBlank() &&
                followerPassword.isNotBlank() &&
                userName.isNotBlank()

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
        ) {
            when (page) {
                LiveTrackingPage.MAIN -> {
                    MainTrackingContent(
                        onBack = onBack,
                        onOpenLogin = { page = LiveTrackingPage.LOGIN },
                        onOpenSettings = {
                            page = LiveTrackingPage.SETTINGS
                            headerMessage = null
                        },
                        onOpenAbout = { page = LiveTrackingPage.ABOUT },
                        isConnected = isConnected,
                        group = group,
                        headerMessage = headerMessage,
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
                        showSendPlan = showSendPlan,
                        canSendPlan = canSendPlan,
                        isSendingPlan = isSendingPlan,
                        onSendPlan = {
                            if (!sessionState.isTracking) {
                                sendStatusMessage = "Start live tracking before sending a route or comment."
                                return@MainTrackingContent
                            }
                            validationMessage =
                                validateAccountSettings(
                                    group = group,
                                    participantPassword = participantPassword,
                                    followerPassword = followerPassword,
                                )
                                    ?: validatePendingEmailInputs(
                                        notificationEmailInput = notificationEmailInput,
                                        alertEmailInput = alertEmailInput,
                                    )
                            if (validationMessage != null) return@MainTrackingContent
                            isSendingPlan = true
                            sendStatusMessage = "Checking group"
                            coroutineScope.launch {
                                runCatching {
                                    val client = ArkluzLiveTrackingClient(context)
                                    client
                                        .registerOrJoinGroup(settings)
                                        .viewerPassword
                                        ?.let { followerPassword = it }
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
                        updateIntervalSeconds = updateIntervalSeconds,
                        validationMessage = validationMessage,
                        sendStatusMessage = sendStatusMessage,
                        onStart = {
                            validationMessage =
                                validateStartSettings(
                                    group = group,
                                    participantPassword = participantPassword,
                                    followerPassword = followerPassword,
                                    userName = userName,
                                )
                                    ?: validatePendingEmailInputs(
                                        notificationEmailInput = notificationEmailInput,
                                        alertEmailInput = alertEmailInput,
                                    )
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
                        userName = userName,
                        groupTrackUrl = groupTrackUrl,
                        userTrackUrl = userTrackUrl,
                        scrollState = scrollState,
                        contentSpacing = contentSpacing,
                    )
                }

                LiveTrackingPage.LOGIN -> {
                    LoginJoinContent(
                        onBack = { page = LiveTrackingPage.MAIN },
                        group = group,
                        onGroupChange = {
                            group = it
                            followerPassword = ""
                            loginJoinStatusMessage = null
                            pendingRegistrationGroup = null
                            showCreateGroupDialog = false
                            createGroupPasswordConfirmation = ""
                        },
                        participantPassword = participantPassword,
                        onParticipantPasswordChange = {
                            participantPassword = it
                            followerPassword = ""
                            if (pendingRegistrationGroup != group.trim()) {
                                loginJoinStatusMessage = null
                                pendingRegistrationGroup = null
                                showCreateGroupDialog = false
                                createGroupPasswordConfirmation = ""
                            }
                        },
                        isLoginJoinLoading = isLoginJoinLoading,
                        isConnected = isConnected,
                        loginJoinStatusMessage = loginJoinStatusMessage,
                        onLoginJoin = {
                            loginJoinStatusMessage = validatePlanSettings(group, participantPassword)
                            if (loginJoinStatusMessage == null) {
                                val cleanGroup = group.trim()
                                isLoginJoinLoading = true
                                loginJoinStatusMessage = "Checking group"
                                coroutineScope.launch {
                                    runCatching {
                                        val client = ArkluzLiveTrackingClient(context)
                                        val checkResult = client.checkGroup(settings)
                                        if (checkResult.groupAvailable) {
                                            pendingRegistrationGroup = cleanGroup
                                            "Group available"
                                        } else {
                                            checkNotNull(checkResult.viewerPassword) {
                                                "Connected, but viewer password was not returned"
                                            }.let { followerPassword = it }
                                            "Connected"
                                        }
                                    }.onSuccess { status ->
                                        if (status == "Group available") {
                                            loginJoinStatusMessage = "Group does not exist."
                                            createGroupPasswordConfirmation = ""
                                            showCreateGroupDialog = true
                                        } else {
                                            LiveTrackingPreferences.loadGroupSettings(context, cleanGroup)?.let { profile ->
                                                userName = profile.userName
                                                notificationEmailAddresses = profile.notificationEmailAddresses
                                                alertEmailAddresses = profile.alertEmailAddresses
                                                stuckAlarmMinutes = profile.stuckAlarmMinutes
                                                updateIntervalSeconds = profile.updateIntervalSeconds
                                            }
                                            loginJoinStatusMessage = "Connected to $cleanGroup"
                                        }
                                    }.onFailure { error ->
                                        loginJoinStatusMessage = error.message ?: "Unable to connect"
                                    }
                                    isLoginJoinLoading = false
                                }
                            }
                        },
                        showCreateGroupDialog = showCreateGroupDialog,
                        createGroupPasswordConfirmation = createGroupPasswordConfirmation,
                        onCreateGroupPasswordConfirmationChange = { createGroupPasswordConfirmation = it },
                        onDismissCreateGroupDialog = {
                            showCreateGroupDialog = false
                            createGroupPasswordConfirmation = ""
                        },
                        onConfirmCreateGroup = {
                            loginJoinStatusMessage = validatePlanSettings(group, participantPassword)
                            if (loginJoinStatusMessage == null) {
                                if (createGroupPasswordConfirmation.trim() != participantPassword.trim()) {
                                    loginJoinStatusMessage = "Password confirmation does not match."
                                    return@LoginJoinContent
                                }
                                showCreateGroupDialog = false
                                isLoginJoinLoading = true
                                loginJoinStatusMessage = "Creating group"
                                coroutineScope.launch {
                                    runCatching {
                                        val client = ArkluzLiveTrackingClient(context)
                                        val registerResult = client.registerOrJoinGroup(settings)
                                        val viewerPassword =
                                            registerResult.viewerPassword
                                                ?: client.checkGroup(settings).viewerPassword
                                        checkNotNull(viewerPassword) {
                                            "Group created, but viewer password was not returned"
                                        }.let { followerPassword = it }
                                        pendingRegistrationGroup = null
                                        createGroupPasswordConfirmation = ""
                                        "Created + connected"
                                    }.onSuccess { status ->
                                        LiveTrackingPreferences.loadGroupSettings(context, group.trim())?.let { profile ->
                                            userName = profile.userName
                                            notificationEmailAddresses = profile.notificationEmailAddresses
                                            alertEmailAddresses = profile.alertEmailAddresses
                                            stuckAlarmMinutes = profile.stuckAlarmMinutes
                                            updateIntervalSeconds = profile.updateIntervalSeconds
                                        }
                                        loginJoinStatusMessage = "$status to ${group.trim()}"
                                    }.onFailure { error ->
                                        loginJoinStatusMessage = error.message ?: "Unable to create group"
                                    }
                                    isLoginJoinLoading = false
                                }
                            }
                        },
                        scrollState = scrollState,
                        contentSpacing = contentSpacing,
                    )
                }

                LiveTrackingPage.SETTINGS -> {
                    SettingsContent(
                        onBack = { page = LiveTrackingPage.MAIN },
                        userName = userName,
                        onUserNameChange = {
                            userName = it
                            saveSettingsStatusMessage = null
                        },
                        notificationEmailInput = notificationEmailInput,
                        onNotificationEmailInputChange = {
                            notificationEmailInput = it
                            saveSettingsStatusMessage = null
                        },
                        notificationEmailAddresses = notificationEmailAddresses,
                        onNotificationEmailAdd = { email ->
                            notificationEmailAddresses = notificationEmailAddresses + email
                            saveSettingsStatusMessage = null
                        },
                        onNotificationEmailRemove = { email ->
                            notificationEmailAddresses = notificationEmailAddresses - email
                            saveSettingsStatusMessage = null
                        },
                        onPickNotificationEmailFromContacts = {
                            emailPickerTarget = EmailPickerTarget.NOTIFICATION
                            runCatching {
                                contactEmailPicker.launch(contactEmailPickerIntent())
                            }.onFailure {
                                emailPickerTarget = null
                                saveSettingsStatusMessage = "No contacts app found"
                            }
                        },
                        alertEmailInput = alertEmailInput,
                        onAlertEmailInputChange = {
                            alertEmailInput = it
                            saveSettingsStatusMessage = null
                        },
                        alertEmailAddresses = alertEmailAddresses,
                        onAlertEmailAdd = { email ->
                            alertEmailAddresses = alertEmailAddresses + email
                            saveSettingsStatusMessage = null
                        },
                        onAlertEmailRemove = { email ->
                            alertEmailAddresses = alertEmailAddresses - email
                            saveSettingsStatusMessage = null
                        },
                        onPickAlertEmailFromContacts = {
                            emailPickerTarget = EmailPickerTarget.ALERT
                            runCatching {
                                contactEmailPicker.launch(contactEmailPickerIntent())
                            }.onFailure {
                                emailPickerTarget = null
                                saveSettingsStatusMessage = "No contacts app found"
                            }
                        },
                        stuckAlarmMinutes = stuckAlarmMinutes,
                        onStuckAlarmMinutesChange = { value ->
                            stuckAlarmMinutes =
                                if (value == "-1") {
                                    value
                                } else {
                                    value.filter(Char::isDigit)
                                }
                            saveSettingsStatusMessage = null
                        },
                        updateIntervalSeconds = updateIntervalSeconds,
                        onUpdateIntervalSecondsChange = {
                            updateIntervalSeconds = it
                            saveSettingsStatusMessage = null
                        },
                        isSavingSettings = isSavingSettings,
                        saveSettingsStatusMessage = saveSettingsStatusMessage,
                        onSaveSettings = {
                            saveSettingsStatusMessage =
                                validateStartSettings(
                                    group = group,
                                    participantPassword = participantPassword,
                                    followerPassword = followerPassword,
                                    userName = userName,
                                )
                                    ?: validatePendingEmailInputs(
                                        notificationEmailInput = notificationEmailInput,
                                        alertEmailInput = alertEmailInput,
                                    )
                            if (saveSettingsStatusMessage == null) {
                                isSavingSettings = true
                                saveSettingsStatusMessage = "Saving settings"
                                coroutineScope.launch {
                                    runCatching {
                                        val result = ArkluzLiveTrackingClient(context).saveSettings(settings)
                                        result.viewerPassword?.let { followerPassword = it }
                                        result
                                    }.onSuccess {
                                        saveSettingsStatusMessage = "Settings saved"
                                    }.onFailure { error ->
                                        saveSettingsStatusMessage =
                                            "Save failed: ${error.message ?: "unknown error"}"
                                    }
                                    isSavingSettings = false
                                }
                            }
                        },
                        onLogout = {
                            LiveTrackingService.stop(context)
                            LiveTrackingPreferences.saveGroupSettings(
                                context = context,
                                settings =
                                    SavedLiveTrackingSettings(
                                        group = group,
                                        userName = userName,
                                        notificationEmailAddresses = notificationEmailAddresses,
                                        alertEmailAddresses = alertEmailAddresses,
                                        stuckAlarmMinutes = stuckAlarmMinutes,
                                        updateIntervalSeconds = updateIntervalSeconds,
                                    ),
                            )
                            LiveTrackingPreferences.clear(context)
                            group = ""
                            participantPassword = ""
                            followerPassword = ""
                            loginJoinStatusMessage = null
                            headerMessage = null
                            validationMessage = null
                            sendStatusMessage = null
                            saveSettingsStatusMessage = null
                            deleteTracksStatusMessage = null
                            pendingRegistrationGroup = null
                            showCreateGroupDialog = false
                            createGroupPasswordConfirmation = ""
                            userName = ""
                            notificationEmailInput = ""
                            notificationEmailAddresses = emptyList()
                            alertEmailInput = ""
                            alertEmailAddresses = emptyList()
                            stuckAlarmMinutes = "15"
                            updateIntervalSeconds = 60
                            page = LiveTrackingPage.MAIN
                        },
                        isDeletingTracks = isDeletingTracks,
                        deleteTracksStatusMessage = deleteTracksStatusMessage,
                        onDeleteRecordedTracks = {
                            deleteTracksStatusMessage = validatePlanSettings(group, participantPassword)
                            if (deleteTracksStatusMessage == null) {
                                isDeletingTracks = true
                                deleteTracksStatusMessage = "Deleting recorded tracks"
                                coroutineScope.launch {
                                    runCatching {
                                        ArkluzLiveTrackingClient(context).deleteRecordedTracks(settings)
                                    }.onSuccess { result ->
                                        deleteTracksStatusMessage =
                                            result.message.takeUnless { it == "Server accepted request" }
                                                ?: "Recorded tracks deleted"
                                    }.onFailure { error ->
                                        deleteTracksStatusMessage =
                                            "Delete failed: ${error.message ?: "unknown error"}"
                                    }
                                    isDeletingTracks = false
                                }
                            }
                        },
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
    onOpenLogin: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    isConnected: Boolean,
    group: String,
    headerMessage: String?,
    selectedGpxName: String,
    comments: String,
    onCommentsChange: (String) -> Unit,
    onPickGpx: () -> Unit,
    showSendPlan: Boolean,
    canSendPlan: Boolean,
    isSendingPlan: Boolean,
    onSendPlan: () -> Unit,
    sessionState: LiveTrackingUiState,
    updateIntervalSeconds: Int,
    validationMessage: String?,
    sendStatusMessage: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    userName: String,
    groupTrackUrl: String,
    userTrackUrl: String,
    scrollState: androidx.compose.foundation.ScrollState,
    contentSpacing: androidx.compose.ui.unit.Dp,
) {
    val context = LocalContext.current

    HeaderRow(
        onBack = onBack,
        actions = {
            Button(onClick = onOpenLogin, modifier = Modifier.weight(1f)) {
                Text(if (isConnected) group.ifBlank { "Connected" } else "Login / Join")
            }
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint =
                        if (isConnected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "Settings",
                    color =
                        if (isConnected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            FilledTonalIconButton(
                onClick = onOpenAbout,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpCenter,
                    contentDescription = "About / FAQ",
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
    headerMessage?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 44.dp),
        )
    }

    ScrollableScreenContent(
        scrollState = scrollState,
        contentSpacing = contentSpacing,
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
            if (showSendPlan) {
                Button(
                    onClick = onSendPlan,
                    enabled = canSendPlan && !isSendingPlan,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isSendingPlan) "Sending" else "Send")
                }
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
            Text(
                text = "GPS update frequency: every ${formatUpdateInterval(updateIntervalSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

        TrackingPanel(title = "Today's tracks") {
            TrackLinkRow(
                label = "Group",
                url = groupTrackUrl,
                onView = { openUrl(context, groupTrackUrl) },
                onShare = { shareUrl(context, groupTrackUrl) },
            )
            TrackLinkRow(
                label = userName.trim().ifBlank { "Participant" },
                url = userTrackUrl,
                onView = { openUrl(context, userTrackUrl) },
                onShare = { shareUrl(context, userTrackUrl) },
            )
        }
    }
}

@Composable
private fun ColumnScope.LoginJoinContent(
    onBack: () -> Unit,
    group: String,
    onGroupChange: (String) -> Unit,
    participantPassword: String,
    onParticipantPasswordChange: (String) -> Unit,
    isLoginJoinLoading: Boolean,
    isConnected: Boolean,
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
    val visibleStatusMessage =
        loginJoinStatusMessage
            ?: if (isConnected) {
                "Connected to ${group.trim().ifBlank { "group" }}"
            } else {
                null
            }

    HeaderRow(onBack = onBack) {
        Text(
            text = "Login / Join",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
    }

    ScrollableScreenContent(
        scrollState = scrollState,
        contentSpacing = contentSpacing,
    ) {
        TrackingPanel(title = "Login / Join") {
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
                enabled = !isLoginJoinLoading && !isConnected,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        isConnected -> "Connected"
                        isLoginJoinLoading -> "Checking"
                        else -> "Connect"
                    },
                )
            }
            visibleStatusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (message.startsWith("Error", ignoreCase = true)) {
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

@Composable
private fun ColumnScope.SettingsContent(
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
    onLogout: () -> Unit,
    isDeletingTracks: Boolean,
    deleteTracksStatusMessage: String?,
    onDeleteRecordedTracks: () -> Unit,
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
                label = "Also send alerts to",
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
        }

        TrackingPanel(title = "Recorded tracks") {
            OutlinedButton(
                onClick = onDeleteRecordedTracks,
                enabled = !isDeletingTracks,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(if (isDeletingTracks) "Deleting" else "Delete recorded tracks")
            }
            deleteTracksStatusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (message.startsWith("Delete failed", ignoreCase = true)) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Logout")
        }
    }
}

@Composable
private fun PasswordField(
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

    ScrollableScreenContent(
        scrollState = scrollState,
        contentSpacing = contentSpacing,
    ) {
        TrackingPanel(title = "Track links") {
            Text(
                text =
                    "Group view opens the shared group map. It is useful when several participants use the " +
                        "same group, for example during an orienteering event where everyone follows their " +
                        "own route. The selected participant is highlighted with a position popup.\n\n" +
                        "User view opens only your participant track.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

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

private fun validateAccountSettings(
    group: String,
    participantPassword: String,
    followerPassword: String,
): String? =
    when {
        group.isBlank() -> "Private group is required."
        participantPassword.isBlank() -> "Participant password is required."
        followerPassword.isBlank() -> "Login / Join first."
        else -> null
    }

private fun validateStartSettings(
    group: String,
    participantPassword: String,
    followerPassword: String,
    userName: String,
): String? =
    when {
        group.isBlank() -> "Private group is required."
        participantPassword.isBlank() -> "Participant password is required."
        followerPassword.isBlank() -> "Login / Join in settings first."
        userName.isBlank() -> "Participant name is required."
        else -> null
    }

private fun validatePendingEmailInputs(
    notificationEmailInput: String,
    alertEmailInput: String,
): String? =
    validatePendingEmailInput(
        input = notificationEmailInput,
        label = "tracking notification email",
    )
        ?: validatePendingEmailInput(
            input = alertEmailInput,
            label = "alert email",
        )

private fun validatePendingEmailInput(
    input: String,
    label: String,
): String? {
    val email = input.normalizedEmailInput()
    return when {
        email.isBlank() -> null
        Patterns.EMAIL_ADDRESS.matcher(email).matches() -> null
        else -> "Enter a valid $label address."
    }
}

private fun emailAddressesForRequest(
    addresses: List<String>,
    pendingInput: String,
): String {
    val pendingEmail = pendingInput.normalizedEmailInput()
    val allAddresses =
        if (
            pendingEmail.isNotBlank() &&
            Patterns.EMAIL_ADDRESS.matcher(pendingEmail).matches() &&
            addresses.none { it.equals(pendingEmail, ignoreCase = true) }
        ) {
            addresses + pendingEmail
        } else {
            addresses
        }
    return allAddresses.joinToString(",")
}

private fun String.normalizedEmailInput(): String = trim().trimEnd(',', ';').lowercase()

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
private fun ColumnScope.ScrollableScreenContent(
    scrollState: androidx.compose.foundation.ScrollState,
    contentSpacing: androidx.compose.ui.unit.Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(end = 8.dp)
                    .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
            content = content,
        )
        ScrollbarIndicator(
            scrollState = scrollState,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
        )
    }
}

@Composable
private fun ScrollbarIndicator(
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
) {
    if (scrollState.maxValue <= 0) return

    val density = LocalDensity.current
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    BoxWithConstraints(
        modifier =
            modifier
                .width(4.dp)
                .padding(vertical = 2.dp),
    ) {
        val maxScroll = scrollState.maxValue
        if (maxScroll <= 0) return@BoxWithConstraints

        val viewportHeightPx = constraints.maxHeight.toFloat()
        if (viewportHeightPx <= 0f) return@BoxWithConstraints

        val contentHeightPx = viewportHeightPx + maxScroll
        val minThumbHeightPx = with(density) { 28.dp.toPx() }
        val thumbHeightPx =
            (viewportHeightPx * viewportHeightPx / contentHeightPx)
                .coerceAtLeast(minThumbHeightPx)
                .coerceAtMost(viewportHeightPx)
        val scrollFraction = scrollState.value.coerceIn(0, maxScroll).toFloat() / maxScroll.toFloat()
        val thumbOffsetPx = ((viewportHeightPx - thumbHeightPx) * scrollFraction).takeIf { it.isFinite() } ?: 0f

        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(trackColor, RoundedCornerShape(999.dp)),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(x = 0, y = thumbOffsetPx.roundToInt()) }
                    .width(3.dp)
                    .height(with(density) { thumbHeightPx.toDp() })
                    .background(thumbColor, RoundedCornerShape(999.dp)),
        )
    }
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

private fun contactEmailPickerIntent(): Intent =
    Intent(
        Intent.ACTION_PICK,
        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
    )

private fun resolveSelectedContactEmail(
    context: Context,
    uri: Uri,
): String? =
    context.contentResolver
        .query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor
                .getString(0)
                ?.trim()
                ?.lowercase()
                ?.takeIf { Patterns.EMAIL_ADDRESS.matcher(it).matches() }
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
