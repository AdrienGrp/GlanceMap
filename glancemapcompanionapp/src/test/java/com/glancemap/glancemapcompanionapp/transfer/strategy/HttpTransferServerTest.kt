package com.glancemap.glancemapcompanionapp.transfer.strategy

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpTransferServerTest {

    private val server = HttpTransferServer()

    @Test
    fun `small fresh transfer keeps default stall timeout`() {
        assertEquals(
            30_000L,
            server.computeHttpStallTimeoutMs(
                totalSize = 32L * 1024L * 1024L,
                resumeOffset = 0L,
                isMapFile = false
            )
        )
    }

    @Test
    fun `large resumed map transfer gets longer stall timeout`() {
        assertEquals(
            90_000L,
            server.computeHttpStallTimeoutMs(
                totalSize = 1_032_008_362L,
                resumeOffset = 631_968_016L,
                isMapFile = true
            )
        )
    }

    @Test
    fun `near-complete resumed transfer gets tail stall timeout`() {
        assertEquals(
            120_000L,
            server.computeHttpStallTimeoutMs(
                totalSize = 1_032_008_362L,
                resumeOffset = 977_750_480L,
                isMapFile = true
            )
        )
    }
}
