package com.glancemap.glancemapcompanionapp.livetracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentDispositionFileNameTest {
    @Test
    fun prefersAndDecodesUtf8ExtendedFilename() {
        val header =
            "attachment; filename=\"fallback.gpx\"; " +
                "filename*=UTF-8''2026-06-22-Andr%C3%A9-%C3%89t%C3%A9.gpx"

        assertEquals(
            "2026-06-22-André-Été.gpx",
            contentDispositionFileName(header),
        )
    }

    @Test
    fun preservesPlusCharactersInExtendedFilename() {
        val header = "attachment; filename*=UTF-8''A+B%20C.gpx"

        assertEquals("A+B C.gpx", contentDispositionFileName(header))
    }

    @Test
    fun fallsBackToLegacyFilename() {
        val header = "attachment; filename=\"recorded-track.gpx\""

        assertEquals("recorded-track.gpx", contentDispositionFileName(header))
    }

    @Test
    fun stripsPathComponentsFromServerFilename() {
        val header = "attachment; filename*=UTF-8''..%2Fprivate%2Ftrack.gpx"

        assertEquals("track.gpx", contentDispositionFileName(header))
    }

    @Test
    fun returnsNullWithoutFilename() {
        assertNull(contentDispositionFileName("attachment"))
        assertNull(contentDispositionFileName(null))
    }
}
