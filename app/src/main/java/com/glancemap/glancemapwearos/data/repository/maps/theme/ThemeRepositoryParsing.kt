package com.glancemap.glancemapwearos.data.repository.maps.theme

import android.content.Context
import android.util.Log
import com.glancemap.glancemapwearos.core.maps.theme.BundledRenderThemeAssetLocator
import com.glancemap.glancemapwearos.core.maps.theme.RenderThemeXmlCapabilities
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.Locale

internal data class ParsedStyleMenu(
    val defaultStyleId: String?,
    val styles: List<StyleDefinition>,
    val overlayDefinitions: Map<String, OverlayDefinition>,
) {
    val stylesById: Map<String, StyleDefinition> = styles.associateBy { it.id }
}

internal data class StyleDefinition(
    val id: String,
    val name: String,
    val overlayLayerIds: List<String>,
)

internal data class OverlayDefinition(
    val layerId: String,
    val name: String,
    val enabledByDefault: Boolean,
)

private data class ParsedMenuLayer(
    val id: String,
    val isStyle: Boolean,
    val name: String,
    val enabledByDefault: Boolean,
    val parentLayerId: String?,
    val directOverlayLayerIds: List<String>,
)

internal fun bundledThemeSupportsNativeHillShading(
    context: Context,
    themeId: String,
    tag: String,
): Boolean {
    val xml =
        readBundledThemeXmlOrNull(
            context = context,
            themeId = themeId,
            tag = tag,
        ) ?: return false
    return RenderThemeXmlCapabilities.supportsNativeHillShading(xml)
}

internal fun parseThemeStyleMenuFromXml(
    context: Context,
    themeId: String,
    tag: String,
): ParsedStyleMenu {
    val parsedLayers = linkedMapOf<String, ParsedMenuLayer>()
    var defaultStyleId: String? = null
    var defaultNameLanguage: String? = null
    val preferredLanguageCandidates = preferredLanguageCandidates()
    val themeXmlPath = BundledRenderThemeAssetLocator.resolveThemeXmlPath(context.assets, themeId)

    try {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        context.assets.open(themeXmlPath).use { input ->
            parser.setInput(input, null)

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "stylemenu") {
                    defaultStyleId = parser.getAttributeValue(null, "defaultvalue")
                    defaultNameLanguage = parser.getAttributeValue(null, "defaultlang")
                    parseStyleMenuContent(
                        parser = parser,
                        parsedLayersOut = parsedLayers,
                        preferredLanguageCandidates = preferredLanguageCandidates,
                        defaultLanguageCandidates = languageCandidates(defaultNameLanguage),
                    )
                    break
                }
                parser.next()
            }
        }
    } catch (e: Exception) {
        Log.e(tag, "Error parsing stylemenu for theme=$themeId", e)
    }

    val overlayDefinitions =
        parsedLayers.values
            .asSequence()
            .filterNot { it.isStyle }
            .associateTo(linkedMapOf()) { layer ->
                layer.id to
                    OverlayDefinition(
                        layerId = layer.id,
                        name = layer.name,
                        enabledByDefault = layer.enabledByDefault,
                    )
            }

    val styles =
        parsedLayers.values
            .asSequence()
            .filter { it.isStyle }
            .map { layer ->
                StyleDefinition(
                    id = layer.id,
                    name = layer.name,
                    overlayLayerIds =
                        resolveOverlayLayerIds(
                            layerId = layer.id,
                            parsedLayers = parsedLayers,
                        ),
                )
            }.distinctBy { it.id }
            .toList()

    return ParsedStyleMenu(
        defaultStyleId = defaultStyleId,
        styles = styles,
        overlayDefinitions = overlayDefinitions,
    )
}

private fun readBundledThemeXmlOrNull(
    context: Context,
    themeId: String,
    tag: String,
): String? {
    val themeXmlPath = BundledRenderThemeAssetLocator.resolveThemeXmlPath(context.assets, themeId)
    return runCatching {
        context.assets
            .open(themeXmlPath)
            .bufferedReader()
            .use { it.readText() }
    }.onFailure { error ->
        Log.w(tag, "Failed reading bundled theme XML for hill shading support. theme=$themeId", error)
    }.getOrNull()
}

private fun parseStyleMenuContent(
    parser: XmlPullParser,
    parsedLayersOut: MutableMap<String, ParsedMenuLayer>,
    preferredLanguageCandidates: Set<String>,
    defaultLanguageCandidates: Set<String>,
) {
    val styleMenuDepth = parser.depth

    while (true) {
        val event = parser.next()
        if (event == XmlPullParser.END_DOCUMENT) return

        if (event == XmlPullParser.END_TAG && parser.depth == styleMenuDepth && parser.name == "stylemenu") {
            return
        }

        if (event != XmlPullParser.START_TAG) continue
        if (parser.name != "layer") continue

        val parsedLayer =
            parseMenuLayerAndConsume(
                parser = parser,
                preferredLanguageCandidates = preferredLanguageCandidates,
                defaultLanguageCandidates = defaultLanguageCandidates,
            )
        parsedLayersOut[parsedLayer.id] = parsedLayer
    }
}

private fun parseMenuLayerAndConsume(
    parser: XmlPullParser,
    preferredLanguageCandidates: Set<String>,
    defaultLanguageCandidates: Set<String>,
): ParsedMenuLayer {
    val layerId = parser.getAttributeValue(null, "id")?.trim().orEmpty()
    val enabledByDefault = parser.getAttributeValue(null, "enabled") == "true"
    val isStyle = parser.getAttributeValue(null, "visible") == "true"
    val parentLayerId = parser.getAttributeValue(null, "parent")?.trim()?.takeIf { it.isNotEmpty() }
    val startDepth = parser.depth
    val namesByLanguage = linkedMapOf<String, String>()
    var fallbackName: String? = null
    val directOverlayIds = mutableListOf<String>()

    while (true) {
        val event = parser.next()
        if (event == XmlPullParser.END_DOCUMENT) break

        if (event == XmlPullParser.END_TAG && parser.depth == startDepth && parser.name == "layer") {
            break
        }

        if (event != XmlPullParser.START_TAG) continue
        when (parser.name) {
            "name" -> {
                val lang = parser.getAttributeValue(null, "lang")
                val value = parser.getAttributeValue(null, "value")?.trim().orEmpty()
                if (value.isEmpty()) continue
                if (fallbackName == null) fallbackName = value
                val normalizedLanguage = normalizeLanguage(lang)
                if (normalizedLanguage != null && normalizedLanguage !in namesByLanguage) {
                    namesByLanguage[normalizedLanguage] = value
                }
            }

            "overlay" -> {
                val id = parser.getAttributeValue(null, "id")?.trim().orEmpty()
                if (id.isNotEmpty()) directOverlayIds += id
            }
        }
    }

    return ParsedMenuLayer(
        id = layerId,
        isStyle = isStyle,
        name =
            selectLocalizedName(
                namesByLanguage = namesByLanguage,
                fallbackName = fallbackName,
                fallbackId = layerId,
                preferredLanguageCandidates = preferredLanguageCandidates,
                defaultLanguageCandidates = defaultLanguageCandidates,
            ),
        enabledByDefault = enabledByDefault,
        parentLayerId = parentLayerId,
        directOverlayLayerIds = directOverlayIds.distinct(),
    )
}

private fun resolveOverlayLayerIds(
    layerId: String,
    parsedLayers: Map<String, ParsedMenuLayer>,
    visiting: MutableSet<String> = linkedSetOf(),
): List<String> {
    if (!visiting.add(layerId)) return emptyList()
    val layer = parsedLayers[layerId] ?: return emptyList()

    val combined = linkedSetOf<String>()
    layer.parentLayerId
        ?.takeIf { it != layerId }
        ?.let { parentId ->
            combined +=
                resolveOverlayLayerIds(
                    layerId = parentId,
                    parsedLayers = parsedLayers,
                    visiting = visiting,
                )
        }
    combined += layer.directOverlayLayerIds
    visiting.remove(layerId)
    return combined.toList()
}

private fun preferredLanguageCandidates(): Set<String> {
    val locale = Locale.getDefault()
    return linkedSetOf<String>().apply {
        addAll(languageCandidates(locale.toLanguageTag()))
        addAll(languageCandidates(locale.language))
    }
}

private fun languageCandidates(language: String?): Set<String> {
    val normalized = normalizeLanguage(language) ?: return emptySet()
    return linkedSetOf<String>().apply {
        add(normalized)
        add(normalized.substringBefore('-'))
    }
}

private fun normalizeLanguage(language: String?): String? {
    val normalized =
        language
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.replace('_', '-')
            .orEmpty()
    return normalized.takeIf { it.isNotEmpty() }
}

private fun selectLocalizedName(
    namesByLanguage: Map<String, String>,
    fallbackName: String?,
    fallbackId: String,
    preferredLanguageCandidates: Set<String>,
    defaultLanguageCandidates: Set<String>,
): String {
    for (candidate in preferredLanguageCandidates) {
        namesByLanguage[candidate]?.let { return extractCleanName(it) }
    }
    for (candidate in defaultLanguageCandidates) {
        namesByLanguage[candidate]?.let { return extractCleanName(it) }
    }
    namesByLanguage["en"]?.let { return extractCleanName(it) }
    namesByLanguage.entries
        .firstOrNull { it.key.startsWith("en-") }
        ?.value
        ?.let { return extractCleanName(it) }

    return extractCleanName(fallbackName ?: fallbackId)
}

private fun extractCleanName(raw: String): String =
    if (raw.length >= 4 && raw[0] == '[' && raw[2] == ']') {
        raw.substring(4).trim()
    } else {
        raw.trim()
    }
