package com.glancemap.glancemapwearos.presentation.features.routetools

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal fun buildEditedFileName(
    sourceFileName: String,
    mode: RouteModifyMode,
): String =
    buildEditedFileName(
        sourceFileName = sourceFileName,
        modeSlug =
            when (mode) {
                RouteModifyMode.RESHAPE_ROUTE -> "reshape"
                RouteModifyMode.REPLACE_SECTION_A_TO_B -> "replace-ab"
                RouteModifyMode.KEEP_ONLY_A_TO_B -> "keep-ab"
                RouteModifyMode.TRIM_START_TO_HERE -> "trim-start"
                RouteModifyMode.TRIM_END_FROM_HERE -> "trim-end"
                RouteModifyMode.REVERSE_GPX -> "reverse"
            },
    )

internal fun buildEditedFileName(
    sourceFileName: String,
    modeSlug: String,
): String {
    val base = sourceFileName.removeSuffix(".gpx")
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    return "${sanitizeFileStem(base)}-$modeSlug-$stamp.gpx"
}

internal fun buildRenamedGpxFileName(title: String): String = "${sanitizeFileStem(title)}.gpx"

internal fun sanitizeFileStem(input: String): String =
    input
        .replace(Regex("\\.gpx$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-')
        .ifBlank { "gpx-edit" }
