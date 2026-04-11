package com.glancemap.glancemapwearos.core.maps.theme

object RenderThemeXmlCapabilities {
    private val hillShadingTagRegex = Regex("""<\s*hillshading\b[^>]*>""")

    fun supportsNativeHillShading(xml: String): Boolean = hillShadingTagRegex.containsMatchIn(xml)
}
