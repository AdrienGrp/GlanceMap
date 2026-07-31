package com.glancemap.glancemapwearos.core.maps.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderThemeXmlCapabilitiesTest {
    @Test
    fun `supports native hill shading when xml contains hillshading tag`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <rendertheme xmlns="http://mapsforge.org/renderTheme" version="5">
                <stylemenu id="sample" defaultvalue="base">
                    <layer id="base" visible="true" />
                </stylemenu>
                <hillshading zoom-min="9" zoom-max="17" />
            </rendertheme>
            """.trimIndent()

        assertTrue(RenderThemeXmlCapabilities.supportsNativeHillShading(xml))
    }

    @Test
    fun `does not support native hill shading when xml omits hillshading tag`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <rendertheme xmlns="http://mapsforge.org/renderTheme" version="5">
                <stylemenu id="sample" defaultvalue="base">
                    <layer id="base" visible="true" />
                </stylemenu>
            </rendertheme>
            """.trimIndent()

        assertFalse(RenderThemeXmlCapabilities.supportsNativeHillShading(xml))
    }

    @Test
    fun `invalid xml falls back to no native hill shading`() {
        val xml = "<rendertheme><hillshading"

        assertFalse(RenderThemeXmlCapabilities.supportsNativeHillShading(xml))
    }
}
