package com.glancemap.glancemapwearos.presentation.features.navigate

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.glancemap.glancemapwearos.core.routing.LoopRouteSuggestionException
import com.glancemap.glancemapwearos.core.service.location.model.GpsSignalSnapshot
import com.glancemap.glancemapwearos.data.repository.UserPoiRecord
import com.glancemap.glancemapwearos.presentation.features.gpx.GpxViewModel
import com.glancemap.glancemapwearos.presentation.features.poi.PoiOverlayMarker
import com.glancemap.glancemapwearos.presentation.features.poi.PoiViewModel
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteCreateMode
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteModifyMode
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteSaveBehavior
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolCreatePreview
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolKind
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolLoopRetryOption
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolModifyPreview
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolOptions
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolSaveResult
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolSession
import com.glancemap.glancemapwearos.presentation.features.routetools.buildLoopRetryOptions
import com.glancemap.glancemapwearos.presentation.features.routetools.preflightStart
import com.glancemap.glancemapwearos.presentation.features.routetools.withVisibleLoopDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.view.MapView

internal data class NavigateRouteToolActions(
    val openRouteToolsPanel: () -> Unit,
    val startPoiCreationSelection: () -> Unit,
    val savePoiAtCurrentMapCenter: () -> Unit,
    val startRouteToolSelection: (RouteToolSession) -> Unit,
    val undoRouteToolPoint: () -> Unit,
    val createRouteToPoi: (PoiOverlayMarker) -> Unit,
    val executeCreateDraft: (RouteToolSession, Boolean) -> Unit,
    val saveCreatePreview: () -> Unit,
    val refreshLoopPreview: () -> Unit,
    val executeModifyDraft: (RouteToolSession, Boolean) -> Unit,
    val captureRouteToolPoint: (LatLong) -> Unit,
)

@Composable
internal fun rememberNavigateRouteToolActions(
    context: Context,
    scope: CoroutineScope,
    mapView: MapView,
    gpxViewModel: GpxViewModel,
    poiViewModel: PoiViewModel,
    locationViewModel: LocationViewModel,
    recenterTarget: LatLong?,
    gpsSignalSnapshot: GpsSignalSnapshot,
    offlineMode: Boolean,
    activeGpxDetailsCount: Int,
    selectedMapPath: String?,
    triggerHaptic: () -> Unit,
    routeToolOptions: RouteToolOptions,
    setRouteToolOptions: (RouteToolOptions) -> Unit,
    routeToolSession: RouteToolSession?,
    setRouteToolSession: (RouteToolSession?) -> Unit,
    completedRouteToolDraft: RouteToolSession?,
    setCompletedRouteToolDraft: (RouteToolSession?) -> Unit,
    routeToolExecutionInProgress: Boolean,
    setRouteToolExecutionInProgress: (Boolean) -> Unit,
    setRouteToolExecutionStatus: (String?) -> Unit,
    setRouteToolExecutionMessage: (String?) -> Unit,
    setRouteToolLoopRetryOptions: (List<RouteToolLoopRetryOption>) -> Unit,
    setRouteToolResult: (RouteToolSaveResult?) -> Unit,
    setRouteToolRenameInProgress: (Boolean) -> Unit,
    setRouteToolRenameError: (String?) -> Unit,
    routeToolPreview: RouteToolModifyPreview?,
    setRouteToolPreview: (RouteToolModifyPreview?) -> Unit,
    routeToolCreatePreview: RouteToolCreatePreview?,
    setRouteToolCreatePreview: (RouteToolCreatePreview?) -> Unit,
    routeToolCreatePreviewInProgress: Boolean,
    setRouteToolCreatePreviewInProgress: (Boolean) -> Unit,
    routeToolCreatePreviewMessage: String?,
    setRouteToolCreatePreviewMessage: (String?) -> Unit,
    setRouteToolPreflightMessage: (String?) -> Unit,
    setShortcutTrayExpanded: (Boolean) -> Unit,
    setShowRouteToolsPanel: (Boolean) -> Unit,
    setPoiCreationSelectionActive: (Boolean) -> Unit,
    createdPoiCreateInProgress: Boolean,
    setCreatedPoiCreateInProgress: (Boolean) -> Unit,
    setCreatedPoiPendingRename: (UserPoiRecord?) -> Unit,
    setCreatedPoiRenameError: (String?) -> Unit,
    setShowCreatedPoiRenameDialog: (Boolean) -> Unit,
): NavigateRouteToolActions {
    fun clearRouteToolPreviewState() {
        setRouteToolPreview(null)
        setRouteToolCreatePreview(null)
        setRouteToolCreatePreviewMessage(null)
        setRouteToolCreatePreviewInProgress(false)
    }

    fun clearRouteToolExecutionFeedback() {
        setRouteToolExecutionStatus(null)
        setRouteToolExecutionMessage(null)
        setRouteToolPreflightMessage(null)
        setRouteToolLoopRetryOptions(emptyList())
    }

    fun clearRouteToolResultState() {
        setRouteToolRenameInProgress(false)
        setRouteToolRenameError(null)
        setRouteToolResult(null)
    }

    fun beginRouteToolExecution(status: String) {
        setRouteToolExecutionInProgress(true)
        setRouteToolExecutionStatus(status)
        setRouteToolExecutionMessage(null)
        setRouteToolPreflightMessage(null)
        setRouteToolLoopRetryOptions(emptyList())
        setRouteToolCreatePreviewInProgress(false)
    }

    fun finishRouteToolExecution() {
        setRouteToolExecutionInProgress(false)
        setRouteToolExecutionStatus(null)
    }

    fun previewModifyDraft(
        draft: RouteToolSession,
        @Suppress("UNUSED_PARAMETER") showProgressToast: Boolean,
    ) {
        if (routeToolExecutionInProgress) return
        beginRouteToolExecution("Previewing...")
        setRouteToolExecutionMessage(null)
        setRouteToolPreview(null)
        gpxViewModel.previewRouteToolModification(draft) { result ->
            finishRouteToolExecution()
            result
                .onSuccess { preview ->
                    setRouteToolExecutionMessage(null)
                    setRouteToolPreview(preview)
                    setCompletedRouteToolDraft(draft)
                }.onFailure { error ->
                    val message =
                        error.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: "Failed to preview the reshaped GPX."
                    setRouteToolExecutionMessage(message)
                    setRouteToolPreview(null)
                    setCompletedRouteToolDraft(draft)
                }
        }
    }

    fun previewCreateDraft(
        draft: RouteToolSession,
        fallbackSession: RouteToolSession?,
        @Suppress("UNUSED_PARAMETER") showProgressToast: Boolean = false,
    ) {
        val previousPreview = routeToolCreatePreview
        setRouteToolCreatePreviewInProgress(true)
        setRouteToolCreatePreviewMessage(null)
        setRouteToolExecutionMessage(null)
        setRouteToolLoopRetryOptions(emptyList())
        gpxViewModel.previewRouteToolCreation(
            session = draft,
            currentLocation = recenterTarget,
        ) { result ->
            setRouteToolCreatePreviewInProgress(false)
            result
                .onSuccess { preview ->
                    setRouteToolCreatePreview(preview)
                    setRouteToolCreatePreviewMessage(null)
                    setCompletedRouteToolDraft(null)
                    setRouteToolSession(draft)
                }.onFailure { error ->
                    val message =
                        error.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: "Could not update the route."
                    if (draft.options.createMode == RouteCreateMode.LOOP_AROUND_HERE) {
                        if (previousPreview != null) {
                            setRouteToolCreatePreview(previousPreview)
                            setRouteToolCreatePreviewMessage(message)
                            setRouteToolSession(draft)
                        } else {
                            setRouteToolCreatePreview(null)
                            setRouteToolCreatePreviewMessage(null)
                            setRouteToolExecutionMessage(message)
                            setRouteToolLoopRetryOptions(
                                if (error is LoopRouteSuggestionException) {
                                    buildLoopRetryOptions(draft.options, error)
                                } else {
                                    emptyList()
                                },
                            )
                            setCompletedRouteToolDraft(draft)
                            setRouteToolSession(null)
                        }
                    } else {
                        setRouteToolCreatePreviewMessage(message)
                        if (fallbackSession != null) {
                            setRouteToolSession(fallbackSession)
                        }
                    }
                }
        }
    }

    LaunchedEffect(
        routeToolSession,
        recenterTarget,
        routeToolCreatePreview,
        routeToolCreatePreviewMessage,
        routeToolCreatePreviewInProgress,
        routeToolExecutionInProgress,
    ) {
        val current = routeToolSession ?: return@LaunchedEffect
        if (!current.isMultiPointCreate) return@LaunchedEffect
        if (current.chainPoints.size < 2) return@LaunchedEffect
        if (routeToolCreatePreview != null) return@LaunchedEffect
        if (routeToolCreatePreviewMessage != null) return@LaunchedEffect
        if (routeToolCreatePreviewInProgress || routeToolExecutionInProgress) return@LaunchedEffect
        previewCreateDraft(current, current)
    }

    fun openRouteToolsPanel() {
        triggerHaptic()
        setShortcutTrayExpanded(false)
        setPoiCreationSelectionActive(false)
        setRouteToolOptions(routeToolOptions.withVisibleLoopDefaults())
        clearRouteToolExecutionFeedback()
        clearRouteToolResultState()
        clearRouteToolPreviewState()
        poiViewModel.clearOfflinePoiSearch()
        setShowRouteToolsPanel(true)
    }

    fun startPoiCreationSelection() {
        triggerHaptic()
        setShortcutTrayExpanded(false)
        setShowRouteToolsPanel(false)
        setRouteToolSession(null)
        clearRouteToolPreviewState()
        clearRouteToolExecutionFeedback()
        clearRouteToolResultState()
        setCreatedPoiCreateInProgress(false)
        setCreatedPoiPendingRename(null)
        setCreatedPoiRenameError(null)
        setShowCreatedPoiRenameDialog(false)
        poiViewModel.clearOfflinePoiSearch()
        gpxViewModel.dismissInspection()
        setPoiCreationSelectionActive(true)
    }

    fun savePoiAtCurrentMapCenter() {
        if (createdPoiCreateInProgress) return
        val center = mapView.model.mapViewPosition.center
        triggerHaptic()
        setCreatedPoiCreateInProgress(true)
        scope.launch {
            runCatching {
                poiViewModel.createMyCreationPoiAt(
                    lat = center.latitude,
                    lon = center.longitude,
                )
            }.onSuccess { record ->
                setCreatedPoiCreateInProgress(false)
                setCreatedPoiPendingRename(record)
                setCreatedPoiRenameError(null)
                setShowCreatedPoiRenameDialog(true)
            }.onFailure { error ->
                setCreatedPoiCreateInProgress(false)
                val message =
                    error.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "Failed to save the POI."
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startRouteToolSelection(session: RouteToolSession) {
        val preflight =
            session.preflightStart(
                context = context,
                currentLocation = recenterTarget,
                gpsSignalSnapshot = gpsSignalSnapshot,
                isOfflineMode = offlineMode,
                hasSingleActiveGpx = activeGpxDetailsCount == 1,
                selectedMapPath = selectedMapPath,
            )
        if (!preflight.canStart) {
            preflight.message?.let(setRouteToolPreflightMessage)
            if (preflight.shouldRequestFreshLocation) {
                locationViewModel.requestImmediateLocation(source = "ui_route_tool_preflight")
            }
            return
        }

        setRouteToolPreflightMessage(null)
        setShowRouteToolsPanel(false)
        setPoiCreationSelectionActive(false)
        setCompletedRouteToolDraft(null)
        setRouteToolExecutionMessage(null)
        setRouteToolLoopRetryOptions(emptyList())
        clearRouteToolResultState()
        clearRouteToolPreviewState()
        gpxViewModel.dismissInspection()
        if (session.isComplete) {
            when {
                session.options.toolKind == RouteToolKind.CREATE -> {
                    if (session.options.createMode == RouteCreateMode.LOOP_AROUND_HERE) {
                        if (routeToolCreatePreviewInProgress || routeToolExecutionInProgress) return
                        setRouteToolSession(session)
                        previewCreateDraft(
                            draft = session,
                            fallbackSession = null,
                            showProgressToast = true,
                        )
                        return
                    }
                    setRouteToolSession(null)
                    if (routeToolExecutionInProgress) return
                    beginRouteToolExecution("Finding route...")
                    gpxViewModel.applyRouteToolCreation(
                        session = session,
                        currentLocation = recenterTarget,
                        onProgress = setRouteToolExecutionStatus,
                    ) { result ->
                        finishRouteToolExecution()
                        result
                            .onSuccess { saveResult ->
                                setRouteToolExecutionMessage(null)
                                setCompletedRouteToolDraft(null)
                                setShortcutTrayExpanded(false)
                                setRouteToolRenameInProgress(false)
                                setRouteToolRenameError(null)
                                setRouteToolPreview(null)
                                setRouteToolResult(saveResult)
                            }.onFailure { error ->
                                val message =
                                    error.localizedMessage?.takeIf { it.isNotBlank() }
                                        ?: "Failed to create the GPX."
                                setRouteToolExecutionMessage(message)
                                setRouteToolLoopRetryOptions(
                                    if (
                                        session.options.createMode == RouteCreateMode.LOOP_AROUND_HERE &&
                                        error is LoopRouteSuggestionException
                                    ) {
                                        buildLoopRetryOptions(session.options, error)
                                    } else {
                                        emptyList()
                                    },
                                )
                                setRouteToolResult(null)
                                setCompletedRouteToolDraft(session)
                            }
                    }
                }

                session.options.saveBehavior == RouteSaveBehavior.SAVE_AS_NEW -> {
                    setRouteToolSession(null)
                    if (session.options.modifyMode == RouteModifyMode.RESHAPE_ROUTE) {
                        previewModifyDraft(session, true)
                        return
                    }
                    if (routeToolExecutionInProgress) return
                    beginRouteToolExecution("Saving GPX...")
                    gpxViewModel.applyRouteToolModification(
                        session = session,
                        onProgress = setRouteToolExecutionStatus,
                    ) { result ->
                        finishRouteToolExecution()
                        result
                            .onSuccess { saveResult ->
                                setRouteToolExecutionMessage(null)
                                setCompletedRouteToolDraft(null)
                                setShortcutTrayExpanded(false)
                                setRouteToolRenameInProgress(false)
                                setRouteToolRenameError(null)
                                setRouteToolPreview(null)
                                setRouteToolResult(saveResult)
                            }.onFailure { error ->
                                val message =
                                    error.localizedMessage?.takeIf { it.isNotBlank() }
                                        ?: "Failed to save the edited GPX."
                                setRouteToolExecutionMessage(message)
                                setRouteToolResult(null)
                                setCompletedRouteToolDraft(session)
                            }
                    }
                }

                else -> {
                    setRouteToolSession(null)
                    setCompletedRouteToolDraft(session)
                }
            }
            return
        }
        setRouteToolSession(session)
    }

    fun undoRouteToolPoint() {
        val current = routeToolSession ?: return
        if (!current.isMultiPointCreate) return
        if (routeToolCreatePreviewInProgress || routeToolExecutionInProgress) return

        val updated = current.removeLastChainPoint()
        if (updated === current) return

        setRouteToolSession(updated)
        setRouteToolCreatePreviewMessage(null)
        if (updated.chainPoints.size < 2) {
            setRouteToolCreatePreview(null)
            return
        }
        previewCreateDraft(updated, current)
    }

    fun createRouteToPoi(marker: PoiOverlayMarker) {
        triggerHaptic()
        setShortcutTrayExpanded(false)
        setShowRouteToolsPanel(true)
        setRouteToolPreflightMessage(null)
        setPoiCreationSelectionActive(false)
        setRouteToolPreview(null)
        setRouteToolCreatePreview(null)
        setRouteToolCreatePreviewMessage(null)
        setRouteToolCreatePreviewInProgress(false)
        val createOptions =
            routeToolOptions
                .copy(
                    toolKind = RouteToolKind.CREATE,
                    createMode = RouteCreateMode.CURRENT_TO_HERE,
                ).withVisibleLoopDefaults()
        setRouteToolOptions(createOptions)
        startRouteToolSelection(
            RouteToolSession(
                options = createOptions,
                destination = LatLong(marker.lat, marker.lon),
            ),
        )
    }

    fun executeCreateDraft(
        draft: RouteToolSession,
        @Suppress("UNUSED_PARAMETER") showProgressToast: Boolean,
        preview: RouteToolCreatePreview? = null,
    ) {
        if (routeToolExecutionInProgress) return
        beginRouteToolExecution(if (preview != null) "Saving GPX..." else "Finding route...")
        gpxViewModel.applyRouteToolCreation(
            session = draft,
            currentLocation = recenterTarget,
            preview = preview,
            onProgress = setRouteToolExecutionStatus,
        ) { result ->
            finishRouteToolExecution()
            result
                .onSuccess { saveResult ->
                    setRouteToolExecutionMessage(null)
                    setCompletedRouteToolDraft(null)
                    setShortcutTrayExpanded(false)
                    setRouteToolRenameInProgress(false)
                    setRouteToolRenameError(null)
                    setRouteToolPreview(null)
                    setRouteToolCreatePreview(null)
                    setRouteToolCreatePreviewMessage(null)
                    setRouteToolResult(saveResult)
                }.onFailure { error ->
                    val message =
                        error.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: "Failed to create the GPX."
                    setRouteToolExecutionMessage(message)
                    setRouteToolLoopRetryOptions(
                        if (
                            draft.options.createMode == RouteCreateMode.LOOP_AROUND_HERE &&
                            error is LoopRouteSuggestionException
                        ) {
                            buildLoopRetryOptions(draft.options, error)
                        } else {
                            emptyList()
                        },
                    )
                    setRouteToolResult(null)
                    setCompletedRouteToolDraft(draft)
                }
        }
    }

    fun saveCreatePreview() {
        val current = routeToolSession ?: return
        val preview = routeToolCreatePreview ?: return
        if (current.options.toolKind != RouteToolKind.CREATE) return
        if (current.isMultiPointCreate && current.chainPoints.size < 2) return
        if (
            !current.isMultiPointCreate &&
            current.options.createMode != RouteCreateMode.LOOP_AROUND_HERE
        ) {
            return
        }
        if (routeToolCreatePreviewInProgress || routeToolExecutionInProgress) return
        setRouteToolSession(null)
        executeCreateDraft(current, true, preview)
    }

    fun refreshLoopPreview() {
        val current = routeToolSession ?: return
        if (current.options.toolKind != RouteToolKind.CREATE) return
        if (current.options.createMode != RouteCreateMode.LOOP_AROUND_HERE) return
        if (!current.isComplete) return
        if (routeToolCreatePreviewInProgress || routeToolExecutionInProgress) return
        val updated = current.advanceLoopVariation()
        setRouteToolSession(updated)
        setRouteToolExecutionMessage(null)
        setRouteToolLoopRetryOptions(emptyList())
        setCompletedRouteToolDraft(null)
        previewCreateDraft(
            draft = updated,
            fallbackSession = null,
            showProgressToast = true,
        )
    }

    fun executeModifyDraft(
        draft: RouteToolSession,
        @Suppress("UNUSED_PARAMETER") showProgressToast: Boolean,
    ) {
        if (routeToolExecutionInProgress) return
        beginRouteToolExecution("Saving GPX...")
        gpxViewModel.applyRouteToolModification(
            session = draft,
            onProgress = setRouteToolExecutionStatus,
        ) { result ->
            finishRouteToolExecution()
            result
                .onSuccess { saveResult ->
                    setRouteToolExecutionMessage(null)
                    setCompletedRouteToolDraft(null)
                    setShortcutTrayExpanded(false)
                    setRouteToolRenameInProgress(false)
                    setRouteToolRenameError(null)
                    setRouteToolPreview(null)
                    setRouteToolCreatePreview(null)
                    setRouteToolCreatePreviewMessage(null)
                    setRouteToolResult(saveResult)
                }.onFailure { error ->
                    val message =
                        error.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: "Failed to save the edited GPX."
                    setRouteToolExecutionMessage(message)
                    setRouteToolResult(null)
                    setCompletedRouteToolDraft(draft)
                }
        }
    }

    fun captureRouteToolPoint(point: LatLong) {
        val current = routeToolSession ?: return
        if (current.isMultiPointCreate) {
            if (routeToolCreatePreviewInProgress || routeToolExecutionInProgress) return
            val updated = current.captureSelection(point)
            if (updated.chainPoints.size == current.chainPoints.size) return
            setRouteToolSession(updated)
            setRouteToolCreatePreviewMessage(null)
            if (updated.chainPoints.size < 2) {
                setRouteToolCreatePreview(null)
                return
            }
            previewCreateDraft(updated, current)
            return
        }
        val updated = current.captureSelection(point)
        if (updated.isComplete) {
            setRouteToolExecutionStatus(null)
            setRouteToolExecutionMessage(null)
            setRouteToolLoopRetryOptions(emptyList())
            setRouteToolCreatePreview(null)
            setRouteToolCreatePreviewMessage(null)
            setRouteToolCreatePreviewInProgress(false)
            when {
                updated.options.toolKind == RouteToolKind.CREATE -> {
                    if (updated.options.createMode == RouteCreateMode.LOOP_AROUND_HERE) {
                        setRouteToolSession(updated)
                        previewCreateDraft(
                            draft = updated,
                            fallbackSession = null,
                            showProgressToast = true,
                        )
                        return
                    }
                    setRouteToolSession(null)
                    executeCreateDraft(updated, true)
                }

                updated.options.modifyMode == RouteModifyMode.RESHAPE_ROUTE -> {
                    setRouteToolSession(null)
                    previewModifyDraft(updated, true)
                }

                updated.options.saveBehavior == RouteSaveBehavior.SAVE_AS_NEW -> {
                    setRouteToolSession(null)
                    executeModifyDraft(updated, true)
                }

                else -> {
                    setRouteToolSession(null)
                    setCompletedRouteToolDraft(updated)
                }
            }
            return
        }
        setRouteToolSession(updated)
    }

    return NavigateRouteToolActions(
        openRouteToolsPanel = ::openRouteToolsPanel,
        startPoiCreationSelection = ::startPoiCreationSelection,
        savePoiAtCurrentMapCenter = ::savePoiAtCurrentMapCenter,
        startRouteToolSelection = ::startRouteToolSelection,
        undoRouteToolPoint = ::undoRouteToolPoint,
        createRouteToPoi = ::createRouteToPoi,
        executeCreateDraft = ::executeCreateDraft,
        saveCreatePreview = ::saveCreatePreview,
        refreshLoopPreview = ::refreshLoopPreview,
        executeModifyDraft = ::executeModifyDraft,
        captureRouteToolPoint = ::captureRouteToolPoint,
    )
}
