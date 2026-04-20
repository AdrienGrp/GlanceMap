package com.glancemap.glancemapwearos.presentation.features.navigate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.data.repository.UserPoiRecord
import com.glancemap.glancemapwearos.presentation.features.poi.PoiSearchUiState
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolDraftSummaryDialog
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolLoopRetryOption
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolOptions
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolProgressDialog
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolResultDialog
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolSaveResult
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolSession
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolsActionPanel
import com.glancemap.glancemapwearos.presentation.ui.RenameValueDialog
import org.mapsforge.core.model.LatLong

@Composable
internal fun NavigateKeepAppOpenDialog(
    visible: Boolean,
    helpDialogMaxHeight: Dp,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = { Text("Stay open") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = helpDialogMaxHeight)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row {
                    Icon(
                        imageVector = Icons.Filled.Visibility,
                        contentDescription = null,
                    )
                    Text(" keeps GlanceMap active.")
                }
                Row {
                    Icon(
                        imageVector = Icons.Filled.VisibilityOff,
                        contentDescription = null,
                    )
                    Text(" lets the watch put the app in the background and remove it from Recent apps.")
                }
            }
        },
        confirmButton = {
            Button(onClick = onContinue) {
                Text("Continue")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Later")
            }
        },
    )
}

@Composable
internal fun NavigateCreatedPoiRenameDialog(
    visible: Boolean,
    pendingRename: UserPoiRecord?,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    RenameValueDialog(
        visible = visible && pendingRename != null,
        title = "Rename POI",
        initialValue = pendingRename?.name.orEmpty(),
        isSaving = isSaving,
        error = error,
        autoFocusInput = false,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
internal fun NavigateRouteToolDialogs(
    showRouteToolsPanel: Boolean,
    canModifyActiveGpx: Boolean,
    coordinateSeed: LatLong?,
    poiSearchState: PoiSearchUiState,
    options: RouteToolOptions,
    preflightMessage: String?,
    onOptionsChange: (RouteToolOptions) -> Unit,
    onSearchPoi: (String) -> Unit,
    onClearPoiSearch: () -> Unit,
    onDismissRouteToolsPanel: () -> Unit,
    onStartRouteToolSelection: (RouteToolSession) -> Unit,
    completedRouteToolDraft: RouteToolSession?,
    routeToolExecutionInProgress: Boolean,
    routeToolExecutionMessage: String?,
    routeToolLoopRetryOptions: List<RouteToolLoopRetryOption>,
    onDismissDraftSummary: () -> Unit,
    onConfirmCreateDraft: (() -> Unit)?,
    onConfirmModifyDraft: (() -> Unit)?,
    onSelectLoopRetryOption: (RouteToolLoopRetryOption) -> Unit,
    routeToolProgressVisible: Boolean,
    routeToolProgressMessage: String,
    routeToolResult: RouteToolSaveResult?,
    isMetric: Boolean,
    routeToolRenameInProgress: Boolean,
    routeToolRenameError: String?,
    onDismissRouteToolResult: () -> Unit,
    onDeleteRouteToolResult: () -> Unit,
    onOpenRouteToolRename: () -> Unit,
    onConfirmRouteToolRename: (String) -> Unit,
) {
    RouteToolsActionPanel(
        visible = showRouteToolsPanel,
        canModifyActiveGpx = canModifyActiveGpx,
        coordinateSeed = coordinateSeed,
        poiSearchState = poiSearchState,
        options = options,
        preflightMessage = preflightMessage,
        onOptionsChange = onOptionsChange,
        onSearchPoi = onSearchPoi,
        onClearPoiSearch = onClearPoiSearch,
        onDismiss = onDismissRouteToolsPanel,
        onStartSelection = onStartRouteToolSelection,
    )

    RouteToolDraftSummaryDialog(
        visible = completedRouteToolDraft != null,
        session = completedRouteToolDraft,
        isExecuting = routeToolExecutionInProgress,
        executionMessage = routeToolExecutionMessage,
        loopRetryOptions = routeToolLoopRetryOptions,
        onDismiss = onDismissDraftSummary,
        onConfirmCreate = onConfirmCreateDraft,
        onConfirmModify = onConfirmModifyDraft,
        onSelectLoopRetryOption = onSelectLoopRetryOption,
    )

    RouteToolProgressDialog(
        visible = routeToolProgressVisible,
        message = routeToolProgressMessage,
    )

    RouteToolResultDialog(
        visible = routeToolResult != null,
        result = routeToolResult,
        isMetric = isMetric,
        renameInProgress = routeToolRenameInProgress,
        renameError = routeToolRenameError,
        onDismiss = onDismissRouteToolResult,
        onDelete = onDeleteRouteToolResult,
        onRenameOpen = onOpenRouteToolRename,
        onRenameConfirm = onConfirmRouteToolRename,
    )
}
