package com.glancemap.glancemapwearos.core.routing

import android.content.Context
import java.io.File

internal const val ROUTING_ROOT_DIR_NAME = "brouter"
internal const val ROUTING_SEGMENTS_DIR_NAME = "segments4"
internal const val ROUTING_PROFILES_DIR_NAME = "profiles2"
internal const val ROUTING_DEFAULT_PROFILE_FILE_NAME = "hiking-mountain.brf"
internal const val ROUTING_DUMMY_PROFILE_FILE_NAME = "dummy.brf"

internal fun isRoutingSegmentFileName(fileName: String): Boolean {
    return fileName.endsWith(".rd5", ignoreCase = true)
}

internal fun routingRootDir(context: Context): File {
    return File(context.filesDir, ROUTING_ROOT_DIR_NAME).apply { mkdirs() }
}

internal fun routingSegmentsDir(context: Context): File {
    return File(routingRootDir(context), ROUTING_SEGMENTS_DIR_NAME).apply { mkdirs() }
}

internal fun routingProfilesDir(context: Context): File {
    return File(routingRootDir(context), ROUTING_PROFILES_DIR_NAME).apply { mkdirs() }
}

internal fun defaultRoutingProfileFile(context: Context): File {
    return File(routingProfilesDir(context), ROUTING_DEFAULT_PROFILE_FILE_NAME)
}

internal fun dummyRoutingProfileFile(context: Context): File {
    return File(routingProfilesDir(context), ROUTING_DUMMY_PROFILE_FILE_NAME)
}

internal fun routingSegmentTargetFile(context: Context, fileName: String): File {
    return File(routingSegmentsDir(context), File(fileName).name)
}

internal fun routingSegmentPartFile(context: Context, fileName: String): File {
    val target = routingSegmentTargetFile(context, fileName)
    return File(target.parentFile ?: routingSegmentsDir(context), ".${target.name}.part")
}
