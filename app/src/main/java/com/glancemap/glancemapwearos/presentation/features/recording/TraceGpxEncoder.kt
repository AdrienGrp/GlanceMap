package com.glancemap.glancemapwearos.presentation.features.recording

import java.io.StringWriter
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun encodeRecordedTraceAsGpx(
    title: String,
    points: List<RecordedTracePoint>,
): ByteArray {
    val writer = StringWriter()
    writer.append("""<?xml version="1.0" encoding="UTF-8"?>""")
    writer.append(
        """<gpx version="1.1" creator="GlanceMap" xmlns="http://www.topografix.com/GPX/1/1" """ +
            """xmlns:gmap="$GLANCEMAP_GPX_EXTENSION_NAMESPACE">""",
    )
    writer.textTag("metadata") {
        textTag("name", title)
        textTag("extensions") {
            textTag("gmap:activityType", "recording")
        }
    }
    writer.textTag("trk") {
        textTag("name", title)
        textTag("trkseg") {
            points.forEach { point ->
                writer.append("""<trkpt lat="${formatCoordinate(point.latLong.latitude)}" """)
                writer.append("""lon="${formatCoordinate(point.latLong.longitude)}">""")
                point.elevationMeters?.let { elevation ->
                    writer.textTag("ele", formatElevation(elevation))
                }
                writer.textTag("time", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(point.timeMillis)))
                writer.writePointExtensions(point)
                writer.append("</trkpt>")
            }
        }
    }
    writer.append("</gpx>")
    return writer.toString().toByteArray(Charsets.UTF_8)
}

private fun StringWriter.writePointExtensions(point: RecordedTracePoint) {
    val accuracyMeters = point.accuracyMeters?.takeIf { it.isFinite() && it >= 0f }
    val speedMps = point.speedMps?.takeIf { it.isFinite() && it >= 0f }
    val elevationSource = point.elevationSource?.takeIf { it.isNotBlank() }
    val heartRateBpm = point.heartRateBpm?.takeIf { it > 0 }
    val stepCount = point.stepCount?.takeIf { it >= 0 }
    val cadenceSpm = point.cadenceSpm?.takeIf { it > 0 }
    val pressureHpa = point.barometricPressureHpa?.takeIf { it.isFinite() && it > 0.0 }
    if (
        accuracyMeters == null &&
        speedMps == null &&
        elevationSource == null &&
        heartRateBpm == null &&
        stepCount == null &&
        cadenceSpm == null &&
        pressureHpa == null
    ) {
        return
    }

    textTag("extensions") {
        accuracyMeters?.let {
            textTag("gmap:accuracyMeters", formatFloat(it))
        }
        speedMps?.let {
            textTag("gmap:speedMps", formatFloat(it))
        }
        elevationSource?.let {
            textTag("gmap:elevationSource", it)
        }
        heartRateBpm?.let {
            textTag("gmap:heartRateBpm", it.toString())
        }
        stepCount?.let {
            textTag("gmap:stepCount", it.toString())
        }
        cadenceSpm?.let {
            textTag("gmap:cadenceSpm", it.toString())
        }
        pressureHpa?.let {
            textTag("gmap:pressureHpa", formatDouble(it))
        }
    }
}

private fun StringWriter.textTag(
    tagName: String,
    value: String,
) {
    append("<")
    append(tagName)
    append(">")
    append(escapeXmlText(value))
    append("</")
    append(tagName)
    append(">")
}

private fun StringWriter.textTag(
    tagName: String,
    content: StringWriter.() -> Unit,
) {
    append("<")
    append(tagName)
    append(">")
    content()
    append("</")
    append(tagName)
    append(">")
}

internal fun buildRecordingFileName(nowMillis: Long): String =
    "Recording-${RECORDING_FILE_TIME_FORMAT.format(Instant.ofEpochMilli(nowMillis))}.gpx"

internal fun buildRecordingFileNameFromTitle(title: String): String =
    "${sanitizeRecordingFileStem(title)}.gpx"

internal fun buildRecordingTitle(nowMillis: Long): String =
    "Recording ${RECORDING_TITLE_TIME_FORMAT.format(Instant.ofEpochMilli(nowMillis))}"

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.8f", value)

private fun formatElevation(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun formatFloat(value: Float): String = String.format(Locale.US, "%.2f", value)

private fun formatDouble(value: Double): String = String.format(Locale.US, "%.2f", value)

private fun escapeXmlText(value: String): String =
    buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(char)
            }
        }
    }

private fun sanitizeRecordingFileStem(input: String): String =
    input
        .replace(Regex("\\.gpx$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-')
        .ifBlank { "Recording" }

private val RECORDING_FILE_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter
        .ofPattern("yyyyMMdd-HHmmss")
        .withZone(java.time.ZoneId.systemDefault())

private val RECORDING_TITLE_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm")
        .withZone(java.time.ZoneId.systemDefault())

private const val GLANCEMAP_GPX_EXTENSION_NAMESPACE = "https://glancemap.app/gpx/extensions/1"
