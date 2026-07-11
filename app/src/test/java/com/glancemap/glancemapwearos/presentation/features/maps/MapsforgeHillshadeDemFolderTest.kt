package com.glancemap.glancemapwearos.presentation.features.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MapsforgeHillshadeDemFolderTest {
    @Test
    fun detailedWithoutRealTilesFallsBackToStandardRootOnly() {
        val root = Files.createTempDirectory("hillshade-roots").toFile()
        val detailed = File(root, "dem1").apply { mkdirs() }
        val standard = File(root, "dem3").apply { mkdirs() }
        File(detailed, "N46/N46E006.hgt.missing").apply {
            parentFile?.mkdirs()
            writeText("missing_upstream")
        }
        File(detailed, "N46/.N46E006.hgt.gz.part").apply {
            parentFile?.mkdirs()
            writeText("partial")
        }
        File(standard, "N46/N46E006.hgt.zip").apply {
            parentFile?.mkdirs()
            writeText("standard")
        }

        val resolved = resolveHillshadeDemRootDirs(listOf(detailed, standard))

        assertEquals(listOf(standard), resolved)
        root.deleteRecursively()
    }

    @Test
    fun detailedAndStandardRootsRemainInPreferenceOrderWhenBothHaveTiles() {
        val root = Files.createTempDirectory("hillshade-roots").toFile()
        val detailed = File(root, "dem1").apply { mkdirs() }
        val standard = File(root, "dem3").apply { mkdirs() }
        File(detailed, "N46E006.hgt.gz").writeText("detailed")
        File(standard, "N46E006.hgt.zip").writeText("standard")

        val resolved = resolveHillshadeDemRootDirs(listOf(detailed, standard))

        assertEquals(listOf(detailed, standard), resolved)
        root.deleteRecursively()
    }

    @Test
    fun markerOnlyRootsAreNotRenderable() {
        val root = Files.createTempDirectory("hillshade-roots").toFile()
        File(root, "N46E006.hgt.missing").writeText("missing_upstream")

        val resolved = resolveHillshadeDemRootDirs(listOf(root))

        assertTrue(resolved.isEmpty())
        root.deleteRecursively()
    }
}
