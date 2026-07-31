package com.glancemap.glancemapwearos.core.service.transfer.http

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpTransferConnectionLoopTest {
    @Test
    fun `fresh http transfer computes checksum inline`() {
        assertTrue(shouldComputeInlineChecksumForHttp(resumeOffset = 0L))
    }

    @Test
    fun `resumed http transfer defers checksum to final verification`() {
        assertFalse(shouldComputeInlineChecksumForHttp(resumeOffset = 1L))
    }
}
