package com.glancemap.glancemapwearos.presentation.features.recording

import android.util.Xml
import org.xmlpull.v1.XmlSerializer
import java.io.StringWriter
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun encodeRecordedTraceAsGpx(
    title: String,
    points: List<RecordedTracePoint>,
): ByteArray {
    val writer = StringWriter()
    val serializer: XmlSerializer =
        Xml.newSerializer().apply {
            setOutput(writer)
            startDocument("UTF-8", true)
            startTag(null, "gpx")
            attribute(null, "version", "1.1")
            attribute(null, "creator", "GlanceMap")
            attribute(null, "xmlns", "http://www.topografix.com/GPX/1/1")

            startTag(null, "metadata")
            startTag(null, "name")
            text(title)
            endTag(null, "name")
            endTag(null, "metadata")

            startTag(null, "trk")
            startTag(null, "name")
            text(title)
            endTag(null, "name")
            startTag(null, "trkseg")
            points.forEach { point ->
                startTag(null, "trkpt")
                attribute(null, "lat", formatCoordinate(point.latLong.latitude))
                attribute(null, "lon", formatCoordinate(point.latLong.longitude))
                point.elevationMeters?.let { elevation ->
                    startTag(null, "ele")
                    text(formatElevation(elevation))
                    endTag(null, "ele")
                }
                startTag(null, "time")
                text(DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(point.timeMillis)))
                endTag(null, "time")
                endTag(null, "trkpt")
            }
            endTag(null, "trkseg")
            endTag(null, "trk")
            endTag(null, "gpx")
            endDocument()
        }
    serializer.flush()
    return writer.toString().toByteArray(Charsets.UTF_8)
}

internal fun buildRecordingFileName(nowMillis: Long): String =
    "Recording-${RECORDING_FILE_TIME_FORMAT.format(Instant.ofEpochMilli(nowMillis))}.gpx"

internal fun buildRecordingFileNameFromTitle(title: String): String =
    "${sanitizeRecordingFileStem(title)}.gpx"

internal fun buildRecordingTitle(nowMillis: Long): String =
    "Recording ${RECORDING_TITLE_TIME_FORMAT.format(Instant.ofEpochMilli(nowMillis))}"

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.8f", value)

private fun formatElevation(value: Double): String = String.format(Locale.US, "%.1f", value)

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
