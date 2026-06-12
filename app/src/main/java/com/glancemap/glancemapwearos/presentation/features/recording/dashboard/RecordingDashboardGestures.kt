package com.glancemap.glancemapwearos.presentation.features.recording.dashboard

import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry

internal fun logRecordingDashboardPageChange(
    pageIndex: Int,
    pageCount: Int,
    source: String,
) {
    DebugTelemetry.log(
        "TraceRecording",
        "event=dashboard_page_change page=${pageIndex + 1} pageCount=$pageCount source=$source",
    )
}

internal fun Int.floorMod(modulus: Int): Int =
    if (modulus <= 0) {
        0
    } else {
        ((this % modulus) + modulus) % modulus
    }

internal const val RECORDING_DASHBOARD_PAGE_SLOT_COUNT = 4
internal const val RECORDING_DASHBOARD_TOTAL_SLOT_COUNT = 8
internal const val POPUP_MINIMIZE_DRAG_THRESHOLD_PX = 24f
internal const val POPUP_EXPAND_DRAG_THRESHOLD_PX = 24f
internal const val POPUP_PAGE_DRAG_THRESHOLD_PX = 24f
