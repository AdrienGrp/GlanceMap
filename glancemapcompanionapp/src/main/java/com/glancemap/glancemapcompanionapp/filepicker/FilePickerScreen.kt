package com.glancemap.glancemapcompanionapp.filepicker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpCenter
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.glancemap.glancemapcompanionapp.FileTransferViewModel
import com.glancemap.glancemapcompanionapp.GeneratedPhoneFile
import com.glancemap.glancemapcompanionapp.PrivacyPolicyActivity
import com.glancemap.glancemapcompanionapp.RefugesImportDialog
import com.glancemap.glancemapcompanionapp.RoutingDownloadDialog
import com.glancemap.glancemapcompanionapp.companionAdaptiveSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FilePickerScreen(viewModel: FileTransferViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsState()
    val isImportingRefuges by viewModel.isImportingRefuges.collectAsState()
    val poiImportProgress by viewModel.poiImportProgress.collectAsState()
    val isDownloadingRouting by viewModel.isDownloadingRouting.collectAsState()
    val routingDownloadProgress by viewModel.routingDownloadProgress.collectAsState()
    val lastRefugesRequest by viewModel.lastRefugesRequest.collectAsState()
    val lastRoutingRequest by viewModel.lastRoutingRequest.collectAsState()
    val refugesRegionPresets by viewModel.refugesRegionPresets.collectAsState()
    val useDetailedRefugesRegionPresets by viewModel.useDetailedRefugesRegionPresets.collectAsState()
    val watchInstalledMaps by viewModel.watchInstalledMaps.collectAsState()
    val isLoadingWatchInstalledMaps by viewModel.isLoadingWatchInstalledMaps.collectAsState()
    val watchInstalledMapsStatusMessage by viewModel.watchInstalledMapsStatusMessage.collectAsState()
    val lastImportedPoiFile by viewModel.lastImportedPoiFile.collectAsState()
    val lastRoutingDownloadedFiles by viewModel.lastRoutingDownloadedFiles.collectAsState()
    val debugCaptureState by viewModel.debugCaptureState.collectAsState()
    val canRefreshLastRefuges = lastRefugesRequest?.bbox?.isNotBlank() == true
    val canRefreshLastRouting = lastRoutingRequest?.bbox?.isNotBlank() == true

    val autoOpenHelpOnFirstLaunch = remember(context) {
        shouldAutoOpenHelpOnFirstLaunch(context).also { shouldShow ->
            if (shouldShow) markHelpShown(context)
        }
    }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showHowToDialog by remember(autoOpenHelpOnFirstLaunch) { mutableStateOf(autoOpenHelpOnFirstLaunch) }
    var showDebugDialog by remember { mutableStateOf(false) }
    var showRefugesDialog by remember { mutableStateOf(false) }
    var showRoutingMenu by remember { mutableStateOf(false) }
    var showRoutingDialog by remember { mutableStateOf(false) }
    var showManagePhoneFilesDialog by remember { mutableStateOf(false) }
    var showMapSourcesMenu by remember { mutableStateOf(false) }
    var showRefugesMenu by remember { mutableStateOf(false) }
    var pendingSinglePhoneSave by remember { mutableStateOf<GeneratedPhoneFile?>(null) }
    var pendingFolderPhoneSave by remember { mutableStateOf<List<GeneratedPhoneFile>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    var phoneStoredFilesSummary by remember { mutableStateOf(emptyPhoneStoredFilesSummary()) }
    var isLoadingPhoneStoredFiles by remember { mutableStateOf(false) }
    var isClearingPhoneStoredFiles by remember { mutableStateOf(false) }
    var phoneStoredFilesRefreshToken by remember { mutableIntStateOf(0) }
    val mapDownloadSources = remember {
        listOf(
            ExternalDownloadSource(
                category = "Topographic maps",
                label = "OpenAndroMaps",
                url = "https://www.openandromaps.org/en/downloads"
            ),
            ExternalDownloadSource(
                category = "Topographic maps",
                label = "OpenHiking",
                url = "https://www.openhiking.eu/en/downloads/mapsforge-maps"
            ),
            ExternalDownloadSource(
                category = "Non-topographic maps",
                label = "BBBike",
                url = "https://extract.bbbike.org/?format=mapsforge-osm.zip"
            ),
            ExternalDownloadSource(
                category = "Non-topographic maps",
                label = "Vector City",
                url = "https://vector.city/"
            ),
            ExternalDownloadSource(
                category = "Non-topographic maps",
                label = "Alternativas Libres",
                url = "https://alternativaslibres.org/en/downloads-mf.php"
            ),
            ExternalDownloadSource(
                category = "Other",
                label = "Freizeitkarte",
                url = "https://www.freizeitkarte-osm.de/android/en/index.html"
            )
        )
    }
    // --- Permission Handling ---
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var hasBluetoothConnectPermission by remember {
        mutableStateOf(hasBluetoothConnectPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasNotificationPermission =
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: hasNotificationPermission
        hasBluetoothConnectPermission =
            permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: hasBluetoothConnectPermission
    }

    val saveSingleFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destinationUri ->
        val pendingFile = pendingSinglePhoneSave
        pendingSinglePhoneSave = null
        if (destinationUri == null || pendingFile == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val message = withContext(Dispatchers.IO) {
                saveGeneratedFileToUri(
                    context = context,
                    source = pendingFile,
                    destinationUri = destinationUri
                )
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    val saveFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        val pendingFiles = pendingFolderPhoneSave
        pendingFolderPhoneSave = emptyList()
        if (treeUri == null || pendingFiles.isEmpty()) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val message = withContext(Dispatchers.IO) {
                saveGeneratedFilesToTree(
                    context = context,
                    files = pendingFiles,
                    treeUri = treeUri
                )
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    val saveGeneratedFilesOnPhone: (List<GeneratedPhoneFile>) -> Unit = remember(
        context,
        saveSingleFileLauncher,
        saveFolderLauncher
    ) {
        { files ->
            when {
                files.isEmpty() -> {
                    Toast.makeText(context, "No file available to save.", Toast.LENGTH_SHORT).show()
                }

                files.size == 1 -> {
                    val file = files.first()
                    pendingFolderPhoneSave = emptyList()
                    pendingSinglePhoneSave = file
                    saveSingleFileLauncher.launch(file.fileName)
                }

                else -> {
                    pendingSinglePhoneSave = null
                    pendingFolderPhoneSave = files
                    saveFolderLauncher.launch(null)
                }
            }
        }
    }

    val requestMissingPermissions = {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= 31 && !hasBluetoothConnectPermission) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadRefugesDefaults(context)
        if (!autoOpenHelpOnFirstLaunch) {
            requestMissingPermissions()
        }
    }

    LaunchedEffect(showRefugesDialog, showRoutingDialog) {
        if (!showRefugesDialog && !showRoutingDialog) return@LaunchedEffect
        if (showRefugesDialog) {
            viewModel.resetPoiImportProgress()
        }
        if (showRoutingDialog) {
            viewModel.resetRoutingDownloadProgress()
        }
        viewModel.findWatchNodes()
    }

    LaunchedEffect(showRefugesDialog, showRoutingDialog, uiState.selectedWatch?.id) {
        if (!showRefugesDialog && !showRoutingDialog) return@LaunchedEffect
        if (uiState.selectedWatch != null) {
            viewModel.refreshWatchInstalledMaps(
                context = context,
                showToastIfUnavailable = false
            )
        }
    }

    LaunchedEffect(showManagePhoneFilesDialog, phoneStoredFilesRefreshToken) {
        if (!showManagePhoneFilesDialog) return@LaunchedEffect
        isLoadingPhoneStoredFiles = true
        phoneStoredFilesSummary = withContext(Dispatchers.IO) {
            loadPhoneStoredFilesSummary(context)
        }
        isLoadingPhoneStoredFiles = false
    }

    // --- Service Binding with Lifecycle ---
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    viewModel.bindService(context)
                    viewModel.findWatchNodes()
                }

                Lifecycle.Event.ON_RESUME -> {
                    hasNotificationPermission = if (Build.VERSION.SDK_INT >= 33) {
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    }
                    hasBluetoothConnectPermission = hasBluetoothConnectPermission(context)
                }

                Lifecycle.Event.ON_STOP -> {
                    viewModel.unbindService(context)
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // --- Multi-file Picker ---
    val multiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult

        // persist best-effort
        uris.forEach {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }

        viewModel.loadFilesFromUris(context, uris)
    }

    // --- File type validation (light UI-side check) ---
    val isAllowedSelection = uiState.selectedFileUris.isNotEmpty()

    val transferSessionActive = uiState.isTransferring || uiState.isPaused
    val uiLocked = transferSessionActive || isImportingRefuges || isDownloadingRouting
    val cancellingTransfer = uiState.statusMessage.contains("Cancelling", ignoreCase = true) ||
        uiState.progressText.contains("Stopping current transfer", ignoreCase = true)
    val waitingForReconnect = !uiState.isPaused &&
        uiState.progressText.contains("Waiting for watch reconnect", ignoreCase = true)
    val fontScale = LocalDensity.current.fontScale

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val windowWidth = maxWidth
        val windowHeight = maxHeight

        val adaptive = remember(windowWidth, windowHeight, fontScale) {
            companionAdaptiveSpec(
                windowWidth = windowWidth,
                windowHeight = windowHeight,
                fontScale = fontScale
            )
        }
        val isCompactScreen = adaptive.isCompactScreen
        val usePageScrollForSmallScreen = adaptive.usePageScrollForSmallScreen

        val pageScrollState = rememberScrollState()
        val historyListState = rememberLazyListState()

        LaunchedEffect(uiState.history.firstOrNull()?.id) {
            if (uiState.history.isNotEmpty()) {
                historyListState.scrollToItem(0)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(adaptive.pagePadding)
        ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            FilledTonalIconButton(
                onClick = { showDebugDialog = true },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(adaptive.helpIconButtonSize),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (debugCaptureState.active) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = if (debugCaptureState.active) {
                        "Stop phone debug capture"
                    } else {
                        "Start phone debug capture"
                    },
                    modifier = Modifier.size(adaptive.helpIconSize)
                )
            }
            Text(
                text = "GlanceMap Companion app",
                style = if (isCompactScreen) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.headlineSmall
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
            FilledTonalIconButton(
                onClick = { showHowToDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(adaptive.helpIconButtonSize)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpCenter,
                    contentDescription = "Help",
                    modifier = Modifier.size(adaptive.helpIconSize)
                )
            }
        }

        if (showDebugDialog) {
            DebugCaptureDialog(
                context = context,
                viewModel = viewModel,
                debugCaptureState = debugCaptureState,
                onDismiss = { showDebugDialog = false }
            )
        }

        if (showManagePhoneFilesDialog) {
            ManagePhoneFilesDialog(
                context = context,
                viewModel = viewModel,
                uiState = uiState,
                uiLocked = uiLocked,
                isLoadingPhoneStoredFiles = isLoadingPhoneStoredFiles,
                isClearingPhoneStoredFiles = isClearingPhoneStoredFiles,
                onIsClearingPhoneStoredFilesChange = { isClearingPhoneStoredFiles = it },
                phoneStoredFilesSummary = phoneStoredFilesSummary,
                onRefreshRequested = { phoneStoredFilesRefreshToken += 1 },
                onDismiss = { showManagePhoneFilesDialog = false },
                coroutineScope = coroutineScope
            )
        }

        Spacer(modifier = Modifier.height(adaptive.titleGap))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = if (usePageScrollForSmallScreen) {
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(pageScrollState)
                        .padding(end = 10.dp)
                } else {
                    Modifier.fillMaxWidth()
                }
            ) {
            FilePickerDownloadSection(
                context = context,
                adaptive = adaptive,
                uiLocked = uiLocked,
                hasNotificationPermission = hasNotificationPermission,
                hasBluetoothConnectPermission = hasBluetoothConnectPermission,
                canRefreshLastRefuges = canRefreshLastRefuges,
                canRefreshLastRouting = canRefreshLastRouting,
                mapDownloadSources = mapDownloadSources,
                showMapSourcesMenu = showMapSourcesMenu,
                onShowMapSourcesMenuChange = { showMapSourcesMenu = it },
                showRefugesMenu = showRefugesMenu,
                onShowRefugesMenuChange = { showRefugesMenu = it },
                showRoutingMenu = showRoutingMenu,
                onShowRoutingMenuChange = { showRoutingMenu = it },
                onRequestMissingPermissions = requestMissingPermissions,
                onShowManagePhoneFiles = { showManagePhoneFilesDialog = true },
                onShowRefugesDialog = { showRefugesDialog = true },
                onShowRoutingDialog = { showRoutingDialog = true },
                onRefreshLastRefuges = {
                    viewModel.refreshLastRefuges(
                        context = context,
                        appendToSelection = true
                    )
                },
                onRefreshLastRouting = {
                    viewModel.refreshLastRouting(
                        context = context,
                        appendToSelection = true
                    )
                }
            )

            if (usePageScrollForSmallScreen) {
                Spacer(modifier = Modifier.height(adaptive.sectionGap))
            } else {
                Spacer(modifier = Modifier.height(adaptive.sectionGap))
            }

            SectionCard(
                title = "2. Select files (.gpx / .map / .poi / .rd5)",
                headerAction = {
                    TextButton(
                        onClick = { viewModel.clearSelectedFiles() },
                        enabled = uiState.selectedFileUris.isNotEmpty() && !uiLocked
                    ) {
                        Text("Clear")
                    }
                },
                modifier = if (usePageScrollForSmallScreen) {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                } else {
                    Modifier.fillMaxWidth()
                }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                ) {
                    Button(
                        onClick = { multiPickerLauncher.launch(arrayOf("*/*")) },
                        enabled = !uiLocked,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Select file(s)")
                    }
                    Text(
                        formatSelectedFilesSummary(uiState.selectedFileDisplayNames),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (usePageScrollForSmallScreen) {
                Spacer(modifier = Modifier.height(adaptive.sectionGap))
            } else {
                Spacer(modifier = Modifier.height(adaptive.sectionGap))
            }

            SectionCard(
                title = "3. Select watch",
                headerAction = {
                    IconButton(
                        onClick = { viewModel.findWatchNodes() },
                        enabled = !uiLocked,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh Watch List",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                modifier = if (usePageScrollForSmallScreen) {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp)
                } else {
                    Modifier.fillMaxWidth()
                }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (uiState.availableWatches.isEmpty()) {
                        Text("No watches found.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            uiState.availableWatches.forEach { watch ->
                                val isSelected = uiState.selectedWatch?.id == watch.id
                                Button(
                                    onClick = { viewModel.onWatchSelected(context, watch) },
                                    enabled = !uiLocked,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) {
                                            Color(0xFF4CAF50)
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    )
                                ) {
                                    Text(
                                        text = watch.displayName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (usePageScrollForSmallScreen) {
                Spacer(modifier = Modifier.height(adaptive.sectionGap))
            } else {
                Spacer(modifier = Modifier.height(adaptive.sectionGap))
            }

            FilePickerTransferSection(
                adaptive = adaptive,
                uiState = uiState,
                uiLocked = uiLocked,
                isAllowedSelection = isAllowedSelection,
                transferSessionActive = transferSessionActive,
                cancellingTransfer = cancellingTransfer,
                waitingForReconnect = waitingForReconnect,
                debugCaptureState = debugCaptureState,
                onSend = { viewModel.sendFiles(context) },
                onResume = { viewModel.resumeTransfer() },
                onPause = { viewModel.pauseTransfer() },
                onCancelRequested = { showCancelDialog = true }
            )

            if (usePageScrollForSmallScreen) {
                Spacer(modifier = Modifier.height(adaptive.sectionGap))
            } else {
                Spacer(modifier = Modifier.height(adaptive.sectionGap))
            }

            FilePickerHistorySection(
                adaptive = adaptive,
                uiState = uiState,
                historyListState = historyListState,
                onClearHistory = { viewModel.clearHistory() }
            )

            Spacer(modifier = Modifier.height(adaptive.sectionGap))

            SectionCard(
                title = "6. Credits & Legal",
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Open the same Credits & Legal documents as the watch app, in the same order.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(PrivacyPolicyActivity.creditsAndLegalIntent(context))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Credits & Legal")
                    }
                }
            }
            }
            if (usePageScrollForSmallScreen) {
                PageScrollbar(
                    scrollState = pageScrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                )
            }
        }

        if (showHowToDialog) {
            FilePickerQuickGuideDialog(
                adaptive = adaptive,
                onDismiss = { showHowToDialog = false }
            )
        }

        if (showCancelDialog) {
            CancelTransferDialog(
                onConfirm = {
                    viewModel.cancelTransfer()
                    showCancelDialog = false
                },
                onDismiss = { showCancelDialog = false }
            )
        }

        if (showRefugesDialog) {
            RefugesImportDialog(
                context = context,
                adaptive = adaptive,
                viewModel = viewModel,
                uiState = uiState,
                isImportingRefuges = isImportingRefuges,
                poiImportProgress = poiImportProgress,
                lastRefugesRequest = lastRefugesRequest,
                refugesRegionPresets = refugesRegionPresets,
                useDetailedRefugesRegionPresets = useDetailedRefugesRegionPresets,
                onUseDetailedRefugesRegionPresetsChange = { enabled ->
                    viewModel.setUseDetailedRefugesRegionPresets(context, enabled)
                },
                watchInstalledMaps = watchInstalledMaps,
                isLoadingWatchInstalledMaps = isLoadingWatchInstalledMaps,
                watchInstalledMapsStatusMessage = watchInstalledMapsStatusMessage,
                lastImportedPoiFile = lastImportedPoiFile,
                saveGeneratedFilesOnPhone = saveGeneratedFilesOnPhone,
                onDismiss = { showRefugesDialog = false }
            )
        }

        if (showRoutingDialog) {
            RoutingDownloadDialog(
                context = context,
                adaptive = adaptive,
                viewModel = viewModel,
                uiState = uiState,
                isDownloadingRouting = isDownloadingRouting,
                routingDownloadProgress = routingDownloadProgress,
                watchInstalledMaps = watchInstalledMaps,
                isLoadingWatchInstalledMaps = isLoadingWatchInstalledMaps,
                watchInstalledMapsStatusMessage = watchInstalledMapsStatusMessage,
                lastRoutingDownloadedFiles = lastRoutingDownloadedFiles,
                saveGeneratedFilesOnPhone = saveGeneratedFilesOnPhone,
                onDismiss = { showRoutingDialog = false }
            )
        }
    }
}
}
