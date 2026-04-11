@file:OptIn(
    com.google.android.horologist.annotations.ExperimentalHorologistApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.glancemap.glancemapwearos.presentation.features.poi

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wc
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.glancemap.glancemapwearos.data.repository.PoiType
import com.glancemap.glancemapwearos.data.repository.USER_POI_CATEGORY_ID
import com.glancemap.glancemapwearos.data.repository.USER_POI_SOURCE_NAME
import com.glancemap.glancemapwearos.data.repository.USER_POI_SOURCE_PATH
import com.glancemap.glancemapwearos.presentation.features.maps.MapViewModel
import com.glancemap.glancemapwearos.presentation.navigation.WatchRoutes
import com.glancemap.glancemapwearos.presentation.ui.DeleteConfirmationDialog
import com.glancemap.glancemapwearos.presentation.ui.RenameValueDialog
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec
import com.glancemap.glancemapwearos.presentation.ui.rememberWearScreenSize
import kotlinx.coroutines.launch
import java.util.Locale

private sealed interface PoiListRow {
    data class FileRow(val file: PoiFileUiState) : PoiListRow
    data class CategoryRow(
        val filePath: String,
        val category: PoiCategoryUiState,
        val isExpanded: Boolean
    ) : PoiListRow
    data class CategoryPoiRow(
        val filePath: String,
        val categoryId: Int,
        val depth: Int,
        val point: PoiCategoryPreviewPointUiState
    ) : PoiListRow
    data class CategoryInfoRow(
        val filePath: String,
        val categoryId: Int,
        val depth: Int,
        val text: String,
        val isError: Boolean = false
    ) : PoiListRow
}

private fun MutableList<PoiListRow>.addCategoryPreviewRows(
    filePath: String,
    categoryId: Int,
    depth: Int,
    preview: PoiCategoryPreviewUiState?,
    emptyText: String
) {
    when {
        preview == null || preview.isLoading -> {
            add(
                PoiListRow.CategoryInfoRow(
                    filePath = filePath,
                    categoryId = categoryId,
                    depth = depth,
                    text = "Loading POI..."
                )
            )
        }

        preview.errorMessage != null -> {
            add(
                PoiListRow.CategoryInfoRow(
                    filePath = filePath,
                    categoryId = categoryId,
                    depth = depth,
                    text = preview.errorMessage,
                    isError = true
                )
            )
        }

        preview.totalPoiCount == 0 -> {
            add(
                PoiListRow.CategoryInfoRow(
                    filePath = filePath,
                    categoryId = categoryId,
                    depth = depth,
                    text = emptyText
                )
            )
        }

        else -> {
            preview.points.forEach { point ->
                add(
                    PoiListRow.CategoryPoiRow(
                        filePath = filePath,
                        categoryId = categoryId,
                        depth = depth,
                        point = point
                    )
                )
            }
            if (preview.hasMore) {
                add(
                    PoiListRow.CategoryInfoRow(
                        filePath = filePath,
                        categoryId = categoryId,
                        depth = depth,
                        text = "${preview.points.size}/${preview.totalPoiCount} POI shown"
                    )
                )
            }
        }
    }
}

@Composable
fun PoiScreen(
    navController: NavHostController,
    poiViewModel: PoiViewModel,
    mapViewModel: MapViewModel
) {
    val screenSize = rememberWearScreenSize()
    val adaptive = rememberWearAdaptiveSpec()
    val scope = rememberCoroutineScope()
    val poiFiles by poiViewModel.poiFiles.collectAsState()
    val poiTapToCenterEnabled by poiViewModel.poiTapToCenterEnabled.collectAsState()
    val categoryPreviews by poiViewModel.categoryPreviews.collectAsState()
    val categoryCounts by poiViewModel.categoryCounts.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var isDeleteMode by remember { mutableStateOf(false) }
    var isRenameMode by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<PoiFileUiState?>(null) }
    var poiToRename by remember { mutableStateOf<PoiCategoryPreviewPointUiState?>(null) }
    var poiToDelete by remember { mutableStateOf<PoiCategoryPreviewPointUiState?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInProgress by remember { mutableStateOf(false) }
    var renameError by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val listHorizontalPadding = when (screenSize) {
        WearScreenSize.LARGE -> 16.dp
        WearScreenSize.MEDIUM -> 14.dp
        WearScreenSize.SMALL -> 12.dp
    }
    val listTopPadding = when (screenSize) {
        WearScreenSize.LARGE -> 6.dp
        WearScreenSize.MEDIUM -> 5.dp
        WearScreenSize.SMALL -> 4.dp
    }
    val listBottomPadding = when (screenSize) {
        WearScreenSize.LARGE -> 8.dp
        WearScreenSize.MEDIUM -> 7.dp
        WearScreenSize.SMALL -> 6.dp
    }
    val headerTopPadding = when (screenSize) {
        WearScreenSize.LARGE -> 8.dp
        WearScreenSize.MEDIUM -> 6.dp
        WearScreenSize.SMALL -> 4.dp
    }
    val headerBottomPadding = when (screenSize) {
        WearScreenSize.LARGE -> 2.dp
        WearScreenSize.MEDIUM -> 2.dp
        WearScreenSize.SMALL -> 1.dp
    }
    val headerActionButtonSize = when (screenSize) {
        WearScreenSize.LARGE -> 24.dp
        WearScreenSize.MEDIUM -> 22.dp
        WearScreenSize.SMALL -> 20.dp
    }
    val headerActionIconSize = when (screenSize) {
        WearScreenSize.LARGE -> 14.dp
        WearScreenSize.MEDIUM -> 13.dp
        WearScreenSize.SMALL -> 12.dp
    }
    val headerActionSpacing = when (screenSize) {
        WearScreenSize.LARGE -> 4.dp
        WearScreenSize.MEDIUM -> 3.dp
        WearScreenSize.SMALL -> 2.dp
    }
    val rowSpacing = when (screenSize) {
        WearScreenSize.LARGE -> 8.dp
        WearScreenSize.MEDIUM -> 7.dp
        WearScreenSize.SMALL -> 5.dp
    }
    val emptyStatePadding = when (screenSize) {
        WearScreenSize.LARGE -> 16.dp
        WearScreenSize.MEDIUM -> 14.dp
        WearScreenSize.SMALL -> 12.dp
    }
    val settingsBottomPadding = when (screenSize) {
        WearScreenSize.LARGE -> 5.dp
        WearScreenSize.MEDIUM -> 4.dp
        WearScreenSize.SMALL -> 3.dp
    }
    val settingsButtonSize = when (screenSize) {
        WearScreenSize.LARGE -> 28.dp
        WearScreenSize.MEDIUM -> 26.dp
        WearScreenSize.SMALL -> 24.dp
    }
    val fileActionButtonSize = when (screenSize) {
        WearScreenSize.LARGE -> 32.dp
        WearScreenSize.MEDIUM -> 30.dp
        WearScreenSize.SMALL -> 28.dp
    }
    val fileActionIconSize = when (screenSize) {
        WearScreenSize.LARGE -> 18.dp
        WearScreenSize.MEDIUM -> 16.dp
        WearScreenSize.SMALL -> 14.dp
    }
    val categoryActionButtonSize = when (screenSize) {
        WearScreenSize.LARGE -> 24.dp
        WearScreenSize.MEDIUM -> 22.dp
        WearScreenSize.SMALL -> 20.dp
    }
    val categoryActionIconSize = when (screenSize) {
        WearScreenSize.LARGE -> 14.dp
        WearScreenSize.MEDIUM -> 12.dp
        WearScreenSize.SMALL -> 10.dp
    }
    val categoryIndentStep = when (screenSize) {
        WearScreenSize.LARGE -> 8.dp
        WearScreenSize.MEDIUM -> 7.dp
        WearScreenSize.SMALL -> 5.dp
    }
    val compactMode = screenSize == WearScreenSize.SMALL
    val headerTopSafePadding = headerTopPadding + adaptive.headerTopSafeInset
    val expandedCategoryIdsByFile = remember { mutableStateMapOf<String, Set<Int>>() }

    fun toggleCategoryExpanded(filePath: String, categoryId: Int) {
        val current = expandedCategoryIdsByFile[filePath].orEmpty()
        expandedCategoryIdsByFile[filePath] = if (categoryId in current) {
            current - categoryId
        } else {
            current + categoryId
        }
    }

    val expandedByFileSnapshot = expandedCategoryIdsByFile.toMap()
    val rows = remember(poiFiles, categoryPreviews, isDeleteMode, expandedByFileSnapshot) {
        buildList<PoiListRow> {
            poiFiles.forEach { file ->
                add(PoiListRow.FileRow(file))
                val showExpandedRows = file.isExpanded && (
                    !isDeleteMode || file.path == USER_POI_SOURCE_PATH
                )
                if (showExpandedRows) {
                    if (file.path == USER_POI_SOURCE_PATH) {
                        addCategoryPreviewRows(
                            filePath = file.path,
                            categoryId = USER_POI_CATEGORY_ID,
                            depth = 1,
                            preview = categoryPreviews[
                                PoiCategoryPreviewKey(file.path, USER_POI_CATEGORY_ID)
                            ],
                            emptyText = "No saved places yet."
                        )
                        return@forEach
                    }
                    val expandedIds = expandedByFileSnapshot[file.path].orEmpty()
                    val categoriesById = file.categories.associateBy { it.id }
                    file.categories.forEach { category ->
                        if (isCategoryVisible(category, categoriesById, expandedIds)) {
                            val isCategoryExpanded = category.id in expandedIds
                            add(
                                PoiListRow.CategoryRow(
                                    filePath = file.path,
                                    category = category,
                                    isExpanded = isCategoryExpanded
                                )
                            )
                            if (isCategoryExpanded) {
                                val isSyntheticGroup = category.id < 0 && category.hasChildren
                                if (!isSyntheticGroup) {
                                    val poiDepth = category.depth + 1
                                    addCategoryPreviewRows(
                                        filePath = file.path,
                                        categoryId = category.id,
                                        depth = poiDepth,
                                        preview = categoryPreviews[
                                            PoiCategoryPreviewKey(file.path, category.id)
                                        ],
                                        emptyText = if (category.hasChildren) {
                                            "No direct POI. Expand sub-folders."
                                        } else {
                                            "No POI in this folder."
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val columnState = rememberResponsiveColumnState(
        contentPadding = {
            PaddingValues(
                top = listTopPadding,
                start = listHorizontalPadding,
                end = listHorizontalPadding,
                bottom = listBottomPadding
            )
        }
    )

    LaunchedEffect(Unit) {
        poiViewModel.loadPoiFiles()
    }
    LaunchedEffect(poiFiles.size) {
        if (poiFiles.isEmpty()) {
            isDeleteMode = false
            isRenameMode = false
        }
    }
    LaunchedEffect(poiFiles) {
        val validByFile = poiFiles.associate { file ->
            file.path to file.categories.map { it.id }.toSet()
        }
        expandedCategoryIdsByFile.keys.toList().forEach { filePath ->
            val validIds = validByFile[filePath]
            if (validIds == null) {
                expandedCategoryIdsByFile.remove(filePath)
            } else {
                val filtered = expandedCategoryIdsByFile[filePath].orEmpty().intersect(validIds)
                if (filtered.isEmpty()) {
                    expandedCategoryIdsByFile.remove(filePath)
                } else if (filtered != expandedCategoryIdsByFile[filePath]) {
                    expandedCategoryIdsByFile[filePath] = filtered
                }
            }
        }
    }
    LaunchedEffect(poiFiles, expandedByFileSnapshot, isDeleteMode) {
        if (isDeleteMode) return@LaunchedEffect
        poiFiles.forEach { file ->
            if (!file.isExpanded) return@forEach
            if (file.path == USER_POI_SOURCE_PATH) return@forEach
            val expandedIds = expandedByFileSnapshot[file.path].orEmpty()
            val categoriesById = file.categories.associateBy { it.id }
            file.categories.forEach { category ->
                if (isCategoryVisible(category, categoriesById, expandedIds)) {
                    poiViewModel.loadCategoryCount(file.path, category.id)
                }
            }
        }
    }
    LaunchedEffect(poiFiles, expandedByFileSnapshot) {
        poiFiles.forEach { file ->
            if (!file.isExpanded) return@forEach
            if (file.path == USER_POI_SOURCE_PATH) {
                poiViewModel.loadCategoryPreview(file.path, USER_POI_CATEGORY_ID)
                return@forEach
            }
            val categoriesById = file.categories.associateBy { it.id }
            expandedByFileSnapshot[file.path].orEmpty().forEach { categoryId ->
                val category = categoriesById[categoryId] ?: return@forEach
                val isSyntheticGroup = category.id < 0 && category.hasChildren
                if (!isSyntheticGroup) {
                    poiViewModel.loadCategoryPreview(file.path, categoryId)
                }
            }
        }
    }

    ScreenScaffold(scrollState = columnState) {
        DeleteConfirmationDialog(
            visible = showDeleteDialog,
            title = if (fileToDelete?.path == USER_POI_SOURCE_PATH) {
                "Delete all created places?"
            } else if (poiToDelete != null) {
                "Delete created place?"
            } else {
                "Delete POI file?"
            },
            message = if (fileToDelete?.path == USER_POI_SOURCE_PATH) {
                "Delete every place in mycreation?"
            } else if (poiToDelete != null) {
                "Delete '${poiToDelete?.name}'?"
            } else {
                "Delete '${fileToDelete?.name}'?"
            },
            onConfirm = {
                when {
                    poiToDelete != null -> {
                        val target = poiToDelete
                        scope.launch {
                            target?.let {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                poiViewModel.deleteMyCreationPoi(it.id)
                            }
                        }
                    }
                    fileToDelete != null -> {
                        fileToDelete?.path?.let {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            poiViewModel.deletePoiFile(it)
                        }
                    }
                }
                showDeleteDialog = false
                fileToDelete = null
                poiToDelete = null
            },
            onDismiss = {
                showDeleteDialog = false
                fileToDelete = null
                poiToDelete = null
            }
        )

        RenameValueDialog(
            visible = showRenameDialog && poiToRename != null,
            title = "Rename POI",
            initialValue = poiToRename?.name.orEmpty(),
            isSaving = renameInProgress,
            error = renameError,
            onDismiss = {
                if (!renameInProgress) {
                    showRenameDialog = false
                    poiToRename = null
                    renameError = null
                }
            },
            onConfirm = { newName ->
                val target = poiToRename ?: return@RenameValueDialog
                if (renameInProgress) return@RenameValueDialog
                renameInProgress = true
                renameError = null
                scope.launch {
                    runCatching {
                        poiViewModel.renameMyCreationPoi(target.id, newName)
                    }.onSuccess {
                        renameInProgress = false
                        showRenameDialog = false
                        poiToRename = null
                        renameError = null
                    }.onFailure { error ->
                        renameInProgress = false
                        renameError = error.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: "Failed to rename the POI."
                    }
                }
            }
        )

        AlertDialog(
            visible = showHelpDialog,
            onDismissRequest = { showHelpDialog = false },
            title = { Text("POI Actions") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = adaptive.helpDialogMaxHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                            "Create custom POI from Navigate with the + POI tool.\n" +
                            "Toggle a POI file to show/hide it on Navigate.\n" +
                            "Open a file to enable categories one by one.\n" +
                            "Use arrows to expand files and categories and preview POI.\n" +
                            "Tap a POI row to open Navigate centered on it (if enabled in POI settings).\n" +
                            "Map symbols: star mycreation, W water, H hut, M peak, C camp, F food, T toilets.\n" +
                            "Use delete mode to remove POI files or individual mycreation places.\n" +
                            "Use the gear for POI settings."
                    )
                }
            }
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = headerTopSafePadding, bottom = headerBottomPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(headerActionSpacing)
                ) {
                    Text(
                        text = "POI",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(headerActionSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showHelpDialog = true
                            },
                            modifier = Modifier.size(headerActionButtonSize),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.7f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "POI actions help",
                                modifier = Modifier.size(headerActionIconSize)
                            )
                        }
                        if (poiFiles.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val nextRenameMode = !isRenameMode
                                    isRenameMode = nextRenameMode
                                    if (nextRenameMode) {
                                        isDeleteMode = false
                                    }
                                },
                                modifier = Modifier.size(headerActionButtonSize),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (isRenameMode) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Black.copy(alpha = 0.7f)
                                    },
                                    contentColor = if (isRenameMode) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        Color.White
                                    }
                                )
                            ) {
                                Icon(
                                    imageVector = if (isRenameMode) Icons.Default.Close else Icons.Default.Edit,
                                    contentDescription = if (isRenameMode) {
                                        "Exit rename mode"
                                    } else {
                                        "Enter rename mode"
                                    },
                                    modifier = Modifier.size(headerActionIconSize)
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isDeleteMode = !isDeleteMode
                                    if (isDeleteMode) {
                                        isRenameMode = false
                                    }
                                },
                                modifier = Modifier.size(headerActionButtonSize),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (isDeleteMode) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        Color.Black.copy(alpha = 0.7f)
                                    },
                                    contentColor = if (isDeleteMode) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        Color.White
                                    }
                                )
                            ) {
                                Icon(
                                    imageVector = if (isDeleteMode) Icons.Default.Close else Icons.Default.Delete,
                                    contentDescription = if (isDeleteMode) {
                                        "Exit delete mode"
                                    } else {
                                        "Enter delete mode"
                                    },
                                    modifier = Modifier.size(headerActionIconSize)
                                )
                            }
                        }
                    }
                    if (isDeleteMode) {
                        Text(
                            text = "Delete mode",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (isRenameMode) {
                        Text(
                            text = "Rename mode",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    columnState = columnState
                ) {
                    if (poiFiles.isEmpty()) {
                        item {
                            Text(
                                text = "Use the companion phone app to send .poi files to your watch.",
                                modifier = Modifier.padding(emptyStatePadding),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    items(
                        items = rows,
                        key = { row ->
                            when (row) {
                                is PoiListRow.FileRow -> "f:${row.file.path}"
                                is PoiListRow.CategoryRow -> "c:${row.filePath}:${row.category.id}"
                                is PoiListRow.CategoryPoiRow -> {
                                    "p:${row.filePath}:${row.categoryId}:${row.point.id}"
                                }
                                is PoiListRow.CategoryInfoRow -> {
                                    "i:${row.filePath}:${row.categoryId}:${row.text}"
                                }
                            }
                        }
                    ) { row ->
                        when (row) {
                            is PoiListRow.FileRow -> {
                                PoiFileRow(
                                    file = row.file,
                                    showDelete = isDeleteMode,
                                    rowSpacing = rowSpacing,
                                    actionButtonSize = fileActionButtonSize,
                                    actionIconSize = fileActionIconSize,
                                    compactMode = compactMode,
                                    onToggle = { checked ->
                                        poiViewModel.setFileEnabled(row.file.path, checked)
                                    },
                                    onToggleExpanded = {
                                        poiViewModel.toggleExpanded(row.file.path)
                                    },
                                    onDelete = {
                                        fileToDelete = row.file
                                        showDeleteDialog = true
                                    }
                                )
                            }

                            is PoiListRow.CategoryRow -> {
                                PoiCategoryRow(
                                    category = row.category,
                                    categoryCount = categoryCounts[
                                        PoiCategoryPreviewKey(row.filePath, row.category.id)
                                    ],
                                    isExpanded = row.isExpanded,
                                    categoryIndentStep = categoryIndentStep,
                                    actionButtonSize = categoryActionButtonSize,
                                    actionIconSize = categoryActionIconSize,
                                    compactMode = compactMode,
                                    onToggle = { checked ->
                                        poiViewModel.setCategoryEnabled(
                                            path = row.filePath,
                                            categoryId = row.category.id,
                                            enabled = checked
                                        )
                                    },
                                    onToggleExpanded = {
                                        toggleCategoryExpanded(row.filePath, row.category.id)
                                    }
                                )
                            }

                            is PoiListRow.CategoryPoiRow -> {
                                PoiCategoryPoiRow(
                                    point = row.point,
                                    filePath = row.filePath,
                                    depth = row.depth,
                                    categoryIndentStep = categoryIndentStep,
                                    compactMode = compactMode,
                                    tapToCenterEnabled = poiTapToCenterEnabled,
                                    showDelete = isDeleteMode && isUserPoiFile(row.filePath),
                                    showRename = isRenameMode && isUserPoiFile(row.filePath),
                                    onDelete = {
                                        poiToDelete = row.point
                                        fileToDelete = null
                                        showDeleteDialog = true
                                    },
                                    onRename = {
                                        poiToRename = row.point
                                        showRenameDialog = true
                                        renameError = null
                                    },
                                    onClick = {
                                        if (!poiTapToCenterEnabled) return@PoiCategoryPoiRow
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        poiViewModel.requestNavigateToPoi(row.point)
                                        val popped = navController.popBackStack(
                                            WatchRoutes.NAVIGATE,
                                            inclusive = false
                                        )
                                        if (!popped) {
                                            navController.navigate(WatchRoutes.NAVIGATE) {
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                )
                            }

                            is PoiListRow.CategoryInfoRow -> {
                                PoiCategoryInfoRow(
                                    text = row.text,
                                    depth = row.depth,
                                    categoryIndentStep = categoryIndentStep,
                                    compactMode = compactMode,
                                    isError = row.isError
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = settingsBottomPadding)
            ) {
                IconButton(
                    onClick = { navController.navigate(WatchRoutes.POI_SETTINGS) },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(settingsButtonSize),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.8f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "POI Settings")
                }
            }
        }
    }
}
