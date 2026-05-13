package com.glancemap.glancemapwearos.presentation.features.download

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                        routingFileNames = prefs.getString(key(areaId, "routing"), null).toRoutingFileNames(),
                        downloadedRoutingFileNames = prefs.getString(key(areaId, "routing_downloaded"), null).toRoutingFileNames(),
                        installedAtMillis = prefs.getLong(key(areaId, "installed_at"), 0L),
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
                .putString(key(bundle.areaId, "routing_downloaded"), bundle.downloadedRoutingFileNames.joinToString("\n"))
                .putLong(key(bundle.areaId, "installed_at"), bundle.installedAtMillis)
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
