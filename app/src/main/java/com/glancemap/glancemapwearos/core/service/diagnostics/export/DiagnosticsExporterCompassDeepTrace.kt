package com.glancemap.glancemapwearos.core.service.diagnostics.export

import com.glancemap.glancemapwearos.core.service.diagnostics.COMPASS_DEEP_TRACE_SCHEMA_VERSION
import com.glancemap.glancemapwearos.core.service.diagnostics.CompassDeepTraceSnapshot

internal fun Appendable.writeCompassDeepTraceSection(snapshot: CompassDeepTraceSnapshot) {
    appendLine()
    appendLine("Compass Deep Trace")
    appendLine("schemaVersion=$COMPASS_DEEP_TRACE_SCHEMA_VERSION")
    appendLine("activeAtExport=${snapshot.active}")
    appendLine("sessionCount=${snapshot.sessionCount}")
    appendLine("aggregateWindowCount=${snapshot.windowCount}")
    appendLine("droppedAggregateLines=${snapshot.droppedLines}")
    appendLine("lastStopReason=${snapshot.lastStopReason ?: "na"}")
    if (snapshot.lines.isEmpty()) {
        appendLine("No compass deep trace captured.")
    } else {
        snapshot.lines.forEach(::appendLine)
    }
}
