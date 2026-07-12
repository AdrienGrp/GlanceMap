package com.glancemap.glancemapwearos.presentation.features.recording.dashboard

internal fun List<RecordingRecapMetric>.inLabelOrder(vararg labels: String): List<RecordingRecapMetric> {
    val byLabel = associateBy { it.label }
    return labels.mapNotNull(byLabel::get)
}

internal fun List<RecordingRecapMetric>.moveLabelAfter(
    label: String,
    afterLabel: String,
): List<RecordingRecapMetric> {
    val moved = firstOrNull { it.label == label }
    val withoutMoved = filterNot { it.label == label }
    val afterIndex = withoutMoved.indexOfFirst { it.label == afterLabel }
    return if (moved == null || afterIndex < 0) {
        this
    } else {
        buildList {
            withoutMoved.forEachIndexed { index, metric ->
                add(metric)
                if (index == afterIndex) add(moved)
            }
        }
    }
}
