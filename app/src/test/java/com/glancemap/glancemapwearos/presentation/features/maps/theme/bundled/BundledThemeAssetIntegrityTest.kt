package com.glancemap.glancemapwearos.presentation.features.maps.theme.bundled

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BundledThemeAssetIntegrityTest {

    @Test
    fun frenchKissSymbolReferencesResolveToDefinedSymbols() {
        val xml = File("src/main/assets/theme/frenchkiss/frenchkiss.xml").readText()
        val definedSymbols = xml
            .let(Regex("""<symbol\b[^>]*id="([^"]+)"""")::findAll)
            .map { it.groupValues[1] }
            .toSet()
        val referencedSymbols = xml
            .let(Regex("""symbol-id="([^"]+)"""")::findAll)
            .map { it.groupValues[1] }
            .toSet()

        val undefinedSymbols = referencedSymbols - definedSymbols
        assertTrue(
            "French Kiss has undefined symbol references: $undefinedSymbols",
            undefinedSymbols.isEmpty()
        )
    }

    @Test
    fun frenchKissContainsContourAndMountainEnhancements() {
        val xml = File("src/main/assets/theme/frenchkiss/frenchkiss.xml").readText()

        assertTrue(
            "French Kiss should render contour lines for contour_ext data",
            xml.contains("""k="contour_ext" v="elevation_minor"""") &&
                xml.contains("""k="contour_ext" v="elevation_medium|elevation_major"""")
        )
        assertTrue(
            "French Kiss should label major contour lines",
            xml.contains("""<pathText priority="-50" k="ele"""")
        )
        assertTrue(
            "French Kiss should include hill labels in addition to peaks",
            xml.contains("""<rule e="node" k="natural" v="hill">""")
        )
    }
}
