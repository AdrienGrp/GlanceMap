package com.glancemap.glancemapwearos.presentation.features.routetools

import android.util.Xml
import com.glancemap.glancemapwearos.presentation.features.gpx.TrackPoint
import org.xmlpull.v1.XmlSerializer
import java.io.StringWriter
import java.util.Locale

internal fun encodeTrackAsGpx(
    title: String,
    points: List<TrackPoint>,
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
                point.elevation?.let { elevation ->
                    startTag(null, "ele")
                    text(formatElevation(elevation))
                    endTag(null, "ele")
                }
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

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.8f", value)

private fun formatElevation(value: Double): String = String.format(Locale.US, "%.1f", value)
