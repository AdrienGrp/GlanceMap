package com.glancemap.glancemapwearos.presentation.features.navigate

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry

@Composable
internal fun NavigateBackHandler(
    createdPoiCreateInProgress: Boolean,
    completedRouteToolDraftActive: Boolean,
    routeToolExecutionInProgress: Boolean,
    routeToolSessionActive: Boolean,
    showCreatedPoiRenameDialog: Boolean,
    createdPoiRenameInProgress: Boolean,
    poiCreationSelectionActive: Boolean,
    showRouteToolsPanel: Boolean,
    shortcutTrayExpanded: Boolean,
    backButtonExitsNavigation: Boolean,
    onDismissCompletedRouteToolDraft: () -> Unit,
    onCancelRouteToolSession: () -> Unit,
    onDismissCreatedPoiRename: () -> Unit,
    onCancelPoiCreation: () -> Unit,
    onDismissRouteToolsPanel: () -> Unit,
    onDismissShortcutTray: () -> Unit,
    onMenuClick: () -> Unit,
) {
    BackHandler(enabled = true) {
        when {
            createdPoiCreateInProgress -> Unit
            completedRouteToolDraftActive -> {
                if (!routeToolExecutionInProgress) {
                    onDismissCompletedRouteToolDraft()
                }
            }
            routeToolSessionActive -> onCancelRouteToolSession()
            showCreatedPoiRenameDialog -> {
                if (!createdPoiRenameInProgress) {
                    onDismissCreatedPoiRename()
                }
            }
            poiCreationSelectionActive -> onCancelPoiCreation()
            showRouteToolsPanel -> onDismissRouteToolsPanel()
            shortcutTrayExpanded -> onDismissShortcutTray()
            else -> {
                if (backButtonExitsNavigation) {
                    DebugTelemetry.log(
                        "NavigationTelemetry",
                        "event=navigate_back_to_menu route=navigate_screen reason=no_overlay_open compat=true",
                    )
                    onMenuClick()
                } else {
                    DebugTelemetry.log(
                        "NavigationTelemetry",
                        "event=navigate_back_ignored route=navigate_screen reason=no_overlay_open compat=false",
                    )
                }
            }
        }
    }
}
