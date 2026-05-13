package com.glancemap.glancemapcompanionapp.livetracking

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.glancemap.glancemapcompanionapp.companionAdaptiveSpec
import com.glancemap.glancemapcompanionapp.resolveUriDisplayName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val START_AFTER_UPLOAD_DELAY_MS = 1500L

private enum class LiveTrackingPage {
    MAIN,
    LOGIN,
    SETTINGS,
}

private enum class EmailPickerTarget {
    NOTIFICATION,
    ALERT,
}

internal enum class RecordedTrackDownloadTarget {
    USER,
    GROUP,
}

@Composable
fun LiveTrackingScreen(
    onBack: () -> Unit,
    onOpenQuickGuide: () -> Unit,
    lastTransferGpxUri: Uri? = null,
    lastTransferGpxName: String = "",
) {
    val context = LocalContext.current
    val fontScale = LocalDensity.current.fontScale
    val coroutineScope = rememberCoroutineScope()
    val sessionState by LiveTrackingSessionStore.state.collectAsState()
    val savedSettings = remember(context) { LiveTrackingPreferences.load(context) }
    val savedDraft = remember(context) { LiveTrackingPreferences.loadDraft(context) }
    var page by remember { mutableStateOf(LiveTrackingPage.MAIN) }
    var loginReturnPage by remember { mutableStateOf(LiveTrackingPage.MAIN) }
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
    var comments by remember { mutableStateOf(savedDraft.comments) }
    var trackingEndpoint by remember { mutableStateOf(ArkluzTrackingEndpoint.DEVELOPMENT) }
    var updateIntervalSeconds by remember { mutableStateOf(savedSettings.updateIntervalSeconds) }
    var selectedGpxUri by remember {
        mutableStateOf(savedDraft.gpxUri.takeIf(String::isNotBlank)?.let(Uri::parse))
    }
    var selectedGpxName by remember { mutableStateOf(savedDraft.gpxName) }
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var headerMessage by remember { mutableStateOf<String?>(null) }
    var sendStatusMessage by remember { mutableStateOf<String?>(null) }
    var loginJoinStatusMessage by remember { mutableStateOf<String?>(null) }
    var isLoginJoinLoading by remember { mutableStateOf(false) }
    var saveSettingsStatusMessage by remember { mutableStateOf<String?>(null) }
    var isSavingSettings by remember { mutableStateOf(false) }
    var settingsSnapshot by remember { mutableStateOf<SavedLiveTrackingSettings?>(null) }
    var showUnsavedSettingsDialog by remember { mutableStateOf(false) }
    var pendingRegistrationGroup by remember { mutableStateOf<String?>(null) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var createGroupPasswordConfirmation by remember { mutableStateOf("") }
    var isSendingPlan by remember { mutableStateOf(false) }
    var isStartingSession by remember { mutableStateOf(false) }
    var deleteTracksStatusMessage by remember { mutableStateOf<String?>(null) }
    var isDeletingTracks by remember { mutableStateOf(false) }
    var recordedTrackDownloadStatusMessage by remember { mutableStateOf<String?>(null) }
    var isDownloadingRecordedTrack by remember { mutableStateOf(false) }
    var pendingRecordedTrackDownloadTarget by remember { mutableStateOf<RecordedTrackDownloadTarget?>(null) }
    var planSent by remember { mutableStateOf(false) }
    var emailPickerTarget by remember { mutableStateOf<EmailPickerTarget?>(null) }
    var showUseLastTransferGpxDialog by remember { mutableStateOf(false) }

    fun savePlannedDraft(
        draftComments: String = comments,
        draftGpxUri: Uri? = selectedGpxUri,
        draftGpxName: String = selectedGpxName,
    ) {
        LiveTrackingPreferences.saveDraft(
            context = context,
            draft =
                SavedLiveTrackingDraft(
                    comments = draftComments,
                    gpxUri = draftGpxUri?.toString().orEmpty(),
                    gpxName = draftGpxName,
                ),
        )
    }

    fun selectPlannedGpx(
        uri: Uri,
        name: String,
    ) {
        val cleanName = name.ifBlank { resolveUriDisplayName(context, uri).ifBlank { "Selected GPX" } }
        selectedGpxUri = uri
        selectedGpxName = cleanName
        planSent = false
        sendStatusMessage = null
        savePlannedDraft(
            draftGpxUri = uri,
            draftGpxName = cleanName,
        )
    }

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
            selectPlannedGpx(
                uri = uri,
                name = resolveUriDisplayName(context, uri),
            )
        }
    val recordedTrackSavePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/gpx+xml"),
        ) { uri ->
            val target = pendingRecordedTrackDownloadTarget
            pendingRecordedTrackDownloadTarget = null
            if (uri == null || target == null) return@rememberLauncherForActivityResult

            isDownloadingRecordedTrack = true
            recordedTrackDownloadStatusMessage = "Downloading recorded GPX"
            val downloadSettings =
                LiveTrackingSettings(
                    trackingUrl = trackingEndpoint.url,
                    updateIntervalSeconds = updateIntervalSeconds,
                    group = group,
                    participantPassword = participantPassword,
                    followerPassword = followerPassword,
                    userName = userName,
                    notificationEmails = "",
                    alertEmails = "",
                    stuckAlarmMinutes = stuckAlarmMinutes,
                    comments = "",
                    gpxUri = null,
                    gpxName = "",
                )
            coroutineScope.launch {
                runCatching {
                    ArkluzLiveTrackingClient(context).downloadRecordedGpx(
                        settings = downloadSettings,
                        userOnly = target == RecordedTrackDownloadTarget.USER,
                        outputUri = uri,
                    )
                }.onSuccess { result ->
                    recordedTrackDownloadStatusMessage = result.message
                }.onFailure { error ->
                    recordedTrackDownloadStatusMessage =
                        "Download failed: ${error.message ?: "unknown error"}"
                }
                isDownloadingRecordedTrack = false
            }
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
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val adaptive =
            remember(maxWidth, maxHeight, fontScale) {
                companionAdaptiveSpec(
                    windowWidth = maxWidth,
                    windowHeight = maxHeight,
                    fontScale = fontScale,
                )
            }
        val compactLayout = adaptive.useCompactPageLayout
        val contentPadding = if (compactLayout) 6.dp else 12.dp
        val contentSpacing = if (compactLayout) 6.dp else 10.dp
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
        val showSendPlan = sessionState.isTracking
        val canSendPlan = showSendPlan && hasPlanContent && isConnected
        val canStart =
            group.isNotBlank() &&
                participantPassword.isNotBlank() &&
                followerPassword.isNotBlank() &&
                userName.isNotBlank()

        fun currentSettingsSnapshot(): SavedLiveTrackingSettings =
            editableSettingsSnapshot(
                group = group,
                userName = userName,
                notificationEmailAddresses = notificationEmailAddresses,
                alertEmailAddresses = alertEmailAddresses,
                stuckAlarmMinutes = stuckAlarmMinutes,
                updateIntervalSeconds = updateIntervalSeconds,
            )

        fun applySettingsSnapshot(snapshot: SavedLiveTrackingSettings) {
            userName = snapshot.userName
            notificationEmailInput = ""
            notificationEmailAddresses = snapshot.notificationEmailAddresses
            alertEmailInput = ""
            alertEmailAddresses = snapshot.alertEmailAddresses
            stuckAlarmMinutes = snapshot.stuckAlarmMinutes
            updateIntervalSeconds = snapshot.updateIntervalSeconds
            saveSettingsStatusMessage = null
        }

        fun saveSettings(exitAfterSave: Boolean = false) {
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
                        settingsSnapshot = currentSettingsSnapshot()
                        saveSettingsStatusMessage = "Settings saved"
                        if (exitAfterSave) {
                            page = LiveTrackingPage.MAIN
                        }
                    }.onFailure { error ->
                        saveSettingsStatusMessage =
                            "Save failed: ${error.message ?: "unknown error"}"
                    }
                    isSavingSettings = false
                }
            }
        }

        fun requestLeaveSettings() {
            if (settingsSnapshot?.let { it != currentSettingsSnapshot() } == true) {
                showUnsavedSettingsDialog = true
            } else {
                page = LiveTrackingPage.MAIN
            }
        }

        BackHandler(enabled = page == LiveTrackingPage.SETTINGS) {
            requestLeaveSettings()
        }

        BackHandler(enabled = page == LiveTrackingPage.LOGIN) {
            page = loginReturnPage
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
        ) {
            when (page) {
                LiveTrackingPage.MAIN -> {
                    MainTrackingContent(
                        onBack = onBack,
                        onOpenLogin = {
                            loginReturnPage = page
                            page = LiveTrackingPage.LOGIN
                        },
                        onOpenSettings = {
                            if (isConnected) {
                                settingsSnapshot = currentSettingsSnapshot()
                                page = LiveTrackingPage.SETTINGS
                                headerMessage = null
                            } else {
                                headerMessage = "Login / Join first to open settings."
                            }
                        },
                        onOpenGuide = onOpenQuickGuide,
                        isConnected = isConnected,
                        group = group,
                        headerMessage = headerMessage,
                        selectedGpxName = selectedGpxName,
                        comments = comments,
                        onCommentsChange = {
                            comments = it
                            planSent = false
                            sendStatusMessage = null
                            savePlannedDraft(draftComments = it)
                        },
                        onPickGpx = {
                            if (
                                lastTransferGpxUri != null &&
                                selectedGpxUri?.toString() != lastTransferGpxUri.toString()
                            ) {
                                showUseLastTransferGpxDialog = true
                            } else {
                                gpxPicker.launch(arrayOf("application/gpx+xml", "text/xml", "*/*"))
                            }
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
                        isStartingSession = isStartingSession,
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
                            if (validationMessage != null) return@MainTrackingContent
                            if (!canStart) return@MainTrackingContent
                            if (isSendingPlan) {
                                sendStatusMessage = "Please wait for the current send to finish before starting."
                                return@MainTrackingContent
                            }
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
                            sendStatusMessage = null
                            if (hasPlanContent && !planSent) {
                                isStartingSession = true
                                isSendingPlan = true
                                sendStatusMessage = "Sending planned route"
                                coroutineScope.launch {
                                    runCatching {
                                        val client = ArkluzLiveTrackingClient(context)
                                        client
                                            .registerOrJoinGroup(settings)
                                            .viewerPassword
                                            ?.let { followerPassword = it }
                                        client.uploadPlannedRoute(settings)
                                    }.onSuccess { result ->
                                        planSent = true
                                        sendStatusMessage = result.message.ifBlank { "Sent" }
                                        delay(START_AFTER_UPLOAD_DELAY_MS)
                                        sendStatusMessage = "Starting live tracking"
                                        LiveTrackingService.start(
                                            context = context,
                                            settings = settings,
                                        )
                                    }.onFailure { error ->
                                        sendStatusMessage = "Send failed: ${error.message ?: "unknown error"}"
                                    }
                                    isSendingPlan = false
                                    isStartingSession = false
                                }
                            } else {
                                LiveTrackingService.start(context = context, settings = settings)
                            }
                        },
                        onStop = { LiveTrackingService.stop(context) },
                        userName = userName,
                        groupTrackUrl = groupTrackUrl,
                        userTrackUrl = userTrackUrl,
                        recordedTrackDownloadStatusMessage = recordedTrackDownloadStatusMessage,
                        isDownloadingRecordedTrack = isDownloadingRecordedTrack,
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
                        onDownloadUserTrack = {
                            recordedTrackDownloadStatusMessage =
                                validateRecordedTrackDownloadSettings(
                                    group = group,
                                    followerPassword = followerPassword,
                                    userName = userName,
                                    userOnly = true,
                                )
                            if (recordedTrackDownloadStatusMessage == null) {
                                pendingRecordedTrackDownloadTarget = RecordedTrackDownloadTarget.USER
                                recordedTrackSavePicker.launch(
                                    recordedTrackDownloadFilename(
                                        group = group,
                                        userName = userName,
                                        target = RecordedTrackDownloadTarget.USER,
                                    ),
                                )
                            }
                        },
                        scrollState = scrollState,
                        contentSpacing = contentSpacing,
                        isCompactLayout = compactLayout,
                        isCompactScreen = adaptive.isCompactScreen,
                        adaptive = adaptive,
                    )
                }

                LiveTrackingPage.LOGIN -> {
                    LoginJoinContent(
                        onBack = { page = loginReturnPage },
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
                                            headerMessage = null
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
                                        headerMessage = null
                                    }.onFailure { error ->
                                        loginJoinStatusMessage = error.message ?: "Unable to create group"
                                    }
                                    isLoginJoinLoading = false
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
                            recordedTrackDownloadStatusMessage = null
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
                            comments = ""
                            selectedGpxUri = null
                            selectedGpxName = ""
                            planSent = false
                            page = LiveTrackingPage.MAIN
                        },
                        scrollState = scrollState,
                        contentSpacing = contentSpacing,
                    )
                }

                LiveTrackingPage.SETTINGS -> {
                    SettingsContent(
                        onBack = { requestLeaveSettings() },
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
                        onSaveSettings = { saveSettings() },
                        scrollState = scrollState,
                        contentSpacing = contentSpacing,
                    )
                }
            }
        }
        if (showUseLastTransferGpxDialog && lastTransferGpxUri != null) {
            AlertDialog(
                onDismissRequest = { showUseLastTransferGpxDialog = false },
                title = { Text("Use selected GPX?") },
                text = {
                    Text(
                        lastTransferGpxName
                            .ifBlank { "The last GPX selected in Send to Watch" },
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showUseLastTransferGpxDialog = false
                            selectPlannedGpx(
                                uri = lastTransferGpxUri,
                                name = lastTransferGpxName,
                            )
                        },
                    ) {
                        Text("Use this GPX")
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showUseLastTransferGpxDialog = false }) {
                            Text("Cancel")
                        }
                        OutlinedButton(
                            onClick = {
                                showUseLastTransferGpxDialog = false
                                gpxPicker.launch(arrayOf("application/gpx+xml", "text/xml", "*/*"))
                            },
                        ) {
                            Text("Choose another")
                        }
                    }
                },
            )
        }
        if (showUnsavedSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showUnsavedSettingsDialog = false },
                title = { Text("Save settings?") },
                text = { Text("You have unsaved changes. Save them before leaving settings?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showUnsavedSettingsDialog = false
                            saveSettings(exitAfterSave = true)
                        },
                        enabled = !isSavingSettings,
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showUnsavedSettingsDialog = false }) {
                            Text("Cancel")
                        }
                        OutlinedButton(
                            onClick = {
                                settingsSnapshot?.let(::applySettingsSnapshot)
                                showUnsavedSettingsDialog = false
                                page = LiveTrackingPage.MAIN
                            },
                        ) {
                            Text("Discard")
                        }
                    }
                },
            )
        }
    }
}
