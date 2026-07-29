package com.glancemap.glancemapwearos.presentation.features.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MapsforgeHillshadeDemFolderTest {
    @Test
    fun folderExposesOnlyDemTilesRequiredByCurrentMap() {
        val root = Files.createTempDirectory("hillshade-folder").toFile()
        File(root, "N46/N46E006.hgt.zip").apply {
            parentFile?.mkdirs()
            writeText("required")
        }
        File(root, "N46/N46E007.hgt.zip").writeText("unrelated")
        File(root, "N47/N47E006.hgt.gz").apply {
            parentFile?.mkdirs()
            writeText("unrelated")
        }

        val files =
            MapsforgeHillshadeDemFolder(
                demRootDirs = listOf(root),
                requiredTileIds = setOf("n46e006"),
            ).files()

        assertEquals(listOf("N46E006.hgt"), files.map { demFile -> demFile.name })
        root.deleteRecursively()
    }

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

    @Test
    fun unrelatedDetailedTilesDoNotBlockStandardFallbackForCurrentMap() {
        val root = Files.createTempDirectory("hillshade-roots").toFile()
        val detailed = File(root, "dem1").apply { mkdirs() }
        val standard = File(root, "dem3").apply { mkdirs() }
        File(detailed, "N45/N45E005.hgt.gz").apply {
            parentFile?.mkdirs()
            writeText("unrelated-detailed")
        }
        File(standard, "N46/N46E006.hgt.zip").apply {
            parentFile?.mkdirs()
            writeText("standard")
        }

        val resolved =
            resolveHillshadeDemRootDirs(
                demRootDirs = listOf(detailed, standard),
                requiredTileIds = setOf("N46E006"),
            )

        assertEquals(listOf(standard), resolved)
        root.deleteRecursively()
    }

    @Test
    fun partialDetailedCoverageUsesStandardPerTileFallback() {
        val root = Files.createTempDirectory("hillshade-roots").toFile()
        val detailed = File(root, "dem1").apply { mkdirs() }
        val standard = File(root, "dem3").apply { mkdirs() }
        File(detailed, "N46E006.hgt.gz").writeText("detailed")
        File(standard, "N46E006.hgt.zip").writeText("standard-1")
        File(standard, "N46E007.hgt.zip").writeText("standard-2")

        val resolved =
            resolveHillshadeDemRootDirs(
                demRootDirs = listOf(detailed, standard),
                requiredTileIds = setOf("N46E006", "N46E007"),
            )

        assertEquals(listOf(detailed, standard), resolved)
        root.deleteRecursively()
    }

    @Test
    fun completeDetailedCoverageRemainsPreferredForCurrentMap() {
        val root = Files.createTempDirectory("hillshade-roots").toFile()
        val detailed = File(root, "dem1").apply { mkdirs() }
        val standard = File(root, "dem3").apply { mkdirs() }
        File(detailed, "N46E006.hgt.gz").writeText("detailed-1")
        File(detailed, "N46E007.hgt.gz").writeText("detailed-2")
        File(standard, "N46E006.hgt.zip").writeText("standard-1")
        File(standard, "N46E007.hgt.zip").writeText("standard-2")

        val resolved =
            resolveHillshadeDemRootDirs(
                demRootDirs = listOf(detailed, standard),
                requiredTileIds = setOf("N46E006", "N46E007"),
            )

        assertEquals(listOf(detailed), resolved)
        root.deleteRecursively()
    }

    @Test
    fun visibleCoverageCountsDetailedFallbackAndMissingCells() {
        val root = Files.createTempDirectory("hillshade-roots").toFile()
        val detailed = File(root, "dem1").apply { mkdirs() }
        val standard = File(root, "dem3").apply { mkdirs() }
        File(detailed, "N46E006.hgt.gz").writeText("detailed")
        File(standard, "N46E006.hgt.zip").writeText("shadowed-standard")
        File(standard, "N46E007.hgt.zip").writeText("fallback-standard")

        val coverage =
            resolveVisibleHillshadeTerrainCoverage(
                demRootDirs = listOf(detailed, standard),
                requiredTileIds = setOf("N46E006", "N46E007", "N46E008"),
            )

        assertEquals(1, coverage.detailedTileCount)
        assertEquals(1, coverage.standardFallbackTileCount)
        assertEquals(1, coverage.missingTileCount)
        assertEquals(2, coverage.availableTileCount)
        assertTrue(coverage.hasAnyTerrain)
        root.deleteRecursively()
    }

    @Test
    fun emptyDetailedCellDoesNotShadowStandardFallback() {
        val root = Files.createTempDirectory("hillshade-roots").toFile()
        val detailed = File(root, "dem1").apply { mkdirs() }
        val standard = File(root, "dem3").apply { mkdirs() }
        File(detailed, "N46E006.hgt").createNewFile()
        File(standard, "N46E006.hgt").writeText("fallback-standard")

        val coverage =
            resolveVisibleHillshadeTerrainCoverage(
                demRootDirs = listOf(detailed, standard),
                requiredTileIds = setOf("N46E006"),
            )
        val demFiles =
            MapsforgeHillshadeDemFolder(
                demRootDirs = listOf(detailed, standard),
                requiredTileIds = setOf("N46E006"),
            ).files()

        assertEquals(0, coverage.detailedTileCount)
        assertEquals(1, coverage.standardFallbackTileCount)
        assertEquals(1, demFiles.count())
        root.deleteRecursively()
    }
}
