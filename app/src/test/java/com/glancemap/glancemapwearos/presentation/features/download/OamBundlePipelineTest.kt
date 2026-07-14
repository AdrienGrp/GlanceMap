package com.glancemap.glancemapwearos.presentation.features.download

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OamBundlePipelineTest {
    @Test
    fun `completed archive is reused when size and entry match`() {
        withTempDirectory { directory ->
            val archive = writeZip(directory, "area.map.zip", "Area.map")

            val result =
                reusableBundleArchiveOrNull(
                    directory = directory,
                    fileName = archive.name,
                    entryExtension = ".map",
                    expectedSize = archive.length(),
                )

            assertNotNull(result)
            assertEquals(archive, result)
        }
    }

    @Test
    fun `stale or invalid archive is removed instead of being reused`() {
        withTempDirectory { directory ->
            val staleArchive = writeZip(directory, "stale.map.zip", "Area.map")
            assertNull(
                reusableBundleArchiveOrNull(
                    directory = directory,
                    fileName = staleArchive.name,
                    entryExtension = ".map",
                    expectedSize = staleArchive.length() + 1L,
                ),
            )
            assertFalse(staleArchive.exists())

            val wrongEntryArchive = writeZip(directory, "wrong.map.zip", "Area.txt")
            assertNull(
                reusableBundleArchiveOrNull(
                    directory = directory,
                    fileName = wrongEntryArchive.name,
                    entryExtension = ".map",
                ),
            )
            assertFalse(wrongEntryArchive.exists())
        }
    }

    @Test
    fun `extraction progress stays hidden while download remains active`() =
        runTest {
            val emitted = mutableListOf<OamDownloadProgress>()
            val arbiter = OamBundleProgressArbiter(emitted::add)
            val extractionStarted = CompletableDeferred<Unit>()
            val finishExtraction = CompletableDeferred<Unit>()
            val extraction =
                async {
                    arbiter.runExtraction { progress ->
                        progress(OamDownloadProgress("EXTRACTING", "Area.map", 25L, 100L))
                        extractionStarted.complete(Unit)
                        finishExtraction.await()
                        progress(OamDownloadProgress("EXTRACTING", "Area.map", 100L, 100L))
                    }
                }

            extractionStarted.await()
            arbiter.runNetwork { progress ->
                progress(OamDownloadProgress("DOWNLOADING", "POI zip", 50L, 100L))
                finishExtraction.complete(Unit)
                extraction.await()
            }

            assertEquals(listOf("EXTRACTING", "DOWNLOADING"), emitted.map(OamDownloadProgress::phase))
        }

    @Test
    fun `latest extraction progress appears when download finishes first`() =
        runTest {
            val emitted = mutableListOf<OamDownloadProgress>()
            val arbiter = OamBundleProgressArbiter(emitted::add)
            val extractionStarted = CompletableDeferred<Unit>()
            val finishExtraction = CompletableDeferred<Unit>()
            val extraction =
                async {
                    arbiter.runExtraction { progress ->
                        progress(OamDownloadProgress("EXTRACTING", "Area.map", 25L, 100L))
                        extractionStarted.complete(Unit)
                        finishExtraction.await()
                    }
                }

            extractionStarted.await()
            arbiter.runNetwork { progress ->
                progress(OamDownloadProgress("DOWNLOADING", "POI zip", 100L, 100L))
            }
            finishExtraction.complete(Unit)
            extraction.await()

            assertEquals(
                listOf("EXTRACTING", "DOWNLOADING", "EXTRACTING"),
                emitted.map(OamDownloadProgress::phase),
            )
        }

    private fun writeZip(
        directory: File,
        fileName: String,
        entryName: String,
    ): File =
        File(directory, fileName).also { archive ->
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write("bundle-data".toByteArray())
                zip.closeEntry()
            }
        }

    private fun withTempDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("oam-pipeline-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
