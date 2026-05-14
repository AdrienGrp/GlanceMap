package com.glancemap.glancemapwearos.core.routing

import android.content.Context
import com.glancemap.glancemapwearos.core.maps.GeoBounds
import com.glancemap.glancemapwearos.core.maps.geoBoundsOrNull
import java.io.File

internal const val ROUTING_ROOT_DIR_NAME = "brouter"
internal const val ROUTING_SEGMENTS_DIR_NAME = "segments4"
internal const val ROUTING_PROFILES_DIR_NAME = "profiles2"
internal const val ROUTING_DEFAULT_PROFILE_FILE_NAME = "hiking-mountain.brf"
internal const val ROUTING_DUMMY_PROFILE_FILE_NAME = "dummy.brf"

private const val ROUTING_SEGMENT_DEGREES = 5.0
private val ROUTING_SEGMENT_FILE_NAME_REGEX =
    Regex("""^([EW])(\d{1,3})_([NS])(\d{1,2})\.rd5$""", RegexOption.IGNORE_CASE)

internal fun isRoutingSegmentFileName(fileName: String): Boolean = fileName.endsWith(".rd5", ignoreCase = true)

internal fun routingSegmentBounds(fileName: String): GeoBounds? =
    ROUTING_SEGMENT_FILE_NAME_REGEX.matchEntire(File(fileName).name)?.let { match ->
        val minLon =
            signedRoutingDegrees(
                hemisphere = match.groupValues[1].uppercase(),
                degrees = match.groupValues[2].toDoubleOrNull(),
            )
        val minLat =
            signedRoutingDegrees(
                hemisphere = match.groupValues[3].uppercase(),
                degrees = match.groupValues[4].toDoubleOrNull(),
            )
        minLon?.let { lon ->
            minLat?.let { lat ->
                geoBoundsOrNull(
                    minLat = lat,
                    maxLat = lat + ROUTING_SEGMENT_DEGREES,
                    minLon = lon,
                    maxLon = lon + ROUTING_SEGMENT_DEGREES,
                )
            }
        }
    }

private fun signedRoutingDegrees(
    hemisphere: String,
    degrees: Double?,
): Double? =
    when (hemisphere) {
        "E", "N" -> degrees
        "W", "S" -> degrees?.unaryMinus()
        else -> null
    }

internal fun routingRootDir(context: Context): File = File(context.filesDir, ROUTING_ROOT_DIR_NAME).apply { mkdirs() }

internal fun routingSegmentsDir(context: Context): File = File(routingRootDir(context), ROUTING_SEGMENTS_DIR_NAME).apply { mkdirs() }

internal fun routingProfilesDir(context: Context): File = File(routingRootDir(context), ROUTING_PROFILES_DIR_NAME).apply { mkdirs() }

internal fun defaultRoutingProfileFile(context: Context): File = File(routingProfilesDir(context), ROUTING_DEFAULT_PROFILE_FILE_NAME)

internal fun dummyRoutingProfileFile(context: Context): File = File(routingProfilesDir(context), ROUTING_DUMMY_PROFILE_FILE_NAME)

internal fun routingSegmentTargetFile(
    context: Context,
    fileName: String,
): File = File(routingSegmentsDir(context), File(fileName).name)

internal fun routingSegmentPartFile(
    context: Context,
    fileName: String,
): File {
    val target = routingSegmentTargetFile(context, fileName)
    return File(target.parentFile ?: routingSegmentsDir(context), ".${target.name}.part")
}
