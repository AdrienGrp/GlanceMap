package com.glancemap.glancemapwearos.presentation.features.download

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class OamBundleStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("oam_download_bundles", Context.MODE_PRIVATE)

    suspend fun listInstalledBundles(): List<OamInstalledBundle> =
        withContext(Dispatchers.IO) {
            prefs
                .getStringSet(KEY_AREA_IDS, emptySet())
                .orEmpty()
                .mapNotNull { areaId ->
                    val areaLabel = prefs.getString(key(areaId, "label"), null) ?: return@mapNotNull null
                    val bundleChoice =
                        prefs
                            .getString(key(areaId, "choice"), null)
                            ?.let { runCatching { OamBundleChoice.valueOf(it) }.getOrNull() }
                            ?: OamBundleChoice.MAP_ONLY
                    OamInstalledBundle(
                        areaId = areaId,
                        areaLabel = areaLabel,
                        bundleChoice = bundleChoice,
                        mapFileName = prefs.getString(key(areaId, "map"), null),
                        poiFileName = prefs.getString(key(areaId, "poi"), null),
                        routingFileNames =
                            prefs
                                .getString(key(areaId, "routing"), null)
                                .toRoutingFileNames(),
                        downloadedRoutingFileNames =
                            prefs
                                .getString(key(areaId, "routing_downloaded"), null)
                                .toRoutingFileNames(),
                        installedAtMillis = prefs.getLong(key(areaId, "installed_at"), 0L),
                        remoteFiles = prefs.getString(key(areaId, "remote_files"), null).toRemoteFileMetadata(),
                    )
                }.sortedBy { it.areaLabel.lowercase() }
        }

    suspend fun upsert(bundle: OamInstalledBundle) =
        withContext(Dispatchers.IO) {
            val ids = prefs.getStringSet(KEY_AREA_IDS, emptySet()).orEmpty() + bundle.areaId
            prefs
                .edit()
                .putStringSet(KEY_AREA_IDS, ids)
                .putString(key(bundle.areaId, "label"), bundle.areaLabel)
                .putString(key(bundle.areaId, "choice"), bundle.bundleChoice.name)
                .putString(key(bundle.areaId, "map"), bundle.mapFileName)
                .putString(key(bundle.areaId, "poi"), bundle.poiFileName)
                .putString(key(bundle.areaId, "routing"), bundle.routingFileNames.joinToString("\n"))
                .putString(
                    key(bundle.areaId, "routing_downloaded"),
                    bundle.downloadedRoutingFileNames.joinToString("\n"),
                ).putLong(key(bundle.areaId, "installed_at"), bundle.installedAtMillis)
                .putString(key(bundle.areaId, "remote_files"), bundle.remoteFiles.toJson())
                .apply()
        }

    suspend fun remove(areaId: String) =
        withContext(Dispatchers.IO) {
            val ids = prefs.getStringSet(KEY_AREA_IDS, emptySet()).orEmpty() - areaId
            prefs
                .edit()
                .putStringSet(KEY_AREA_IDS, ids)
                .remove(key(areaId, "label"))
                .remove(key(areaId, "choice"))
                .remove(key(areaId, "map"))
                .remove(key(areaId, "poi"))
                .remove(key(areaId, "routing"))
                .remove(key(areaId, "routing_downloaded"))
                .remove(key(areaId, "installed_at"))
                .remove(key(areaId, "remote_files"))
                .apply()
        }

    private fun key(
        areaId: String,
        suffix: String,
    ): String = "$areaId.$suffix"

    private companion object {
        private const val KEY_AREA_IDS = "area_ids"
    }
}

private fun String?.toRoutingFileNames(): List<String> =
    this
        ?.lineSequence()
        ?.map { it.trim() }
        ?.filter { it.endsWith(".rd5", ignoreCase = true) }
        ?.distinct()
        ?.toList()
        .orEmpty()

private fun String?.toRemoteFileMetadata(): List<OamRemoteFileMetadata> =
    runCatching {
        if (isNullOrBlank()) return@runCatching emptyList()
        val array = JSONArray(this)
        buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.toRemoteFileMetadata()?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

private fun JSONObject.toRemoteFileMetadata(): OamRemoteFileMetadata? {
    val url = optString("url").takeIf { it.isNotBlank() }
    val fileName = optString("fileName").takeIf { it.isNotBlank() }
    return if (url == null || fileName == null) {
        null
    } else {
        OamRemoteFileMetadata(
            url = url,
            fileName = fileName,
            entityTag = optString("entityTag").takeIf { it.isNotBlank() },
            lastModifiedMillis = optLongOrNull("lastModifiedMillis"),
            contentLengthBytes = optLongOrNull("contentLengthBytes"),
        )
    }
}

private fun List<OamRemoteFileMetadata>.toJson(): String {
    val array = JSONArray()
    forEach { file ->
        array.put(
            JSONObject()
                .put("url", file.url)
                .put("fileName", file.fileName)
                .put("entityTag", file.entityTag)
                .put("lastModifiedMillis", file.lastModifiedMillis)
                .put("contentLengthBytes", file.contentLengthBytes),
        )
    }
    return array.toString()
}

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) {
        optLong(name).takeIf { it >= 0L }
    } else {
        null
    }
