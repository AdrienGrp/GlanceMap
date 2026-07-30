package com.glancemap.glancemapcompanionapp.livetracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArkluzUploadResponseTest {
    @Test
    fun acceptsPlainOkResponse() {
        val result = "OK\n".toArkluzUploadResult()

        assertEquals("Comment sent", result.message)
    }

    @Test
    fun rejectsArkluzErrorResponse() {
        val error = runCatching { "Error: invalid GPX\n".toArkluzUploadResult() }.exceptionOrNull()

        assertNotNull(error)
        assertEquals("Error: invalid GPX", error?.message)
    }

    @Test
    fun rejectsUnexpectedResponse() {
        val error = runCatching { "<html>Upload complete</html>".toArkluzUploadResult() }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error?.message.orEmpty().contains("Upload complete"))
    }
}
