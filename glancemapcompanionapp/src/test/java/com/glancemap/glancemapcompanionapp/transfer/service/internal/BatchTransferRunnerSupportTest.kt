package com.glancemap.glancemapcompanionapp.transfer.service.internal

import com.glancemap.glancemapcompanionapp.transfer.TransferStrategyFactory
import com.glancemap.glancemapcompanionapp.transfer.strategy.HttpTransferServer
import com.glancemap.glancemapcompanionapp.transfer.strategy.TransferResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchTransferRunnerSupportTest {
    @Test
    fun `detects different private subnet http failure`() {
        val message =
            "Cannot reach phone HTTP server at http://192.168.43.1:45558/ " +
                "(failed to connect to /192.168.43.1 (port 45558) from /192.168.0.241 " +
                "(port 36600) after 1500ms)"

        assertTrue(isLikelyDifferentSubnetHttpFailure(message))
    }

    @Test
    fun `ignores same subnet http failure`() {
        val message =
            "Cannot reach phone HTTP server at http://192.168.0.189:45055/ " +
                "(failed to connect to /192.168.0.189 (port 45055) from /192.168.0.241 " +
                "(port 36600) after 1500ms)"

        assertFalse(isLikelyDifferentSubnetHttpFailure(message))
    }

    @Test
    fun `detects explicit phone http unreachable failure on same subnet`() {
        val message =
            "Cannot reach phone HTTP server at http://192.168.0.189:45055/ " +
                "(failed to connect to /192.168.0.189 (port 45055) from /192.168.0.241 " +
                "(port 36600) after 1500ms)"

        assertTrue(isExplicitPhoneHttpUnreachableFailure(message))
    }

    @Test
    fun `treats different private subnets as unreachable peers`() {
        assertTrue(areClearlyDifferentPrivateSubnets("192.168.43.1", "192.168.0.241"))
        assertTrue(areClearlyDifferentPrivateSubnets("10.0.0.5", "10.0.1.8"))
        assertFalse(areClearlyDifferentPrivateSubnets("192.168.0.5", "192.168.0.241"))
    }

    @Test
    fun `extracts transferred bytes from retry result message`() {
        val message = "HTTP_STALLED_RETRY: file=Bayern_oam.osm.map idleMs=30574 timeoutMs=75000 sent=985631096"

        assertEquals(985631096L, extractTransferredBytesFromRetryMessage(message))
    }

    @Test
    fun `detects meaningful retry progress for large file`() {
        assertTrue(
            hasMeaningfulFreshHttpRetryProgress(
                previousSentBytes = 766185744L,
                currentSentBytes = 985631096L,
                totalSize = 1_032_008_362L,
            ),
        )
        assertFalse(
            hasMeaningfulFreshHttpRetryProgress(
                previousSentBytes = 766185744L,
                currentSentBytes = 770000000L,
                totalSize = 1_032_008_362L,
            ),
        )
    }

    @Test
    fun `allows large channel rescue fallback when phone http server is unreachable`() {
        val result =
            TransferResult(
                success = false,
                message =
                    "Cannot reach phone HTTP server at http://192.168.0.189:45055/ " +
                        "(failed to connect to /192.168.0.189 (port 45055) from /192.168.0.241 " +
                        "(port 36600) after 1500ms)",
            )

        assertTrue(
            shouldFallbackToChannel(
                strategy = HttpTransferServer(),
                fileSize = TransferStrategyFactory.CHANNEL_FALLBACK_MAX_BYTES + 1L,
                result = result,
            ),
        )
        assertTrue(shouldAllowLargeChannelRescueFallback(result))
        assertTrue(shouldPreferChannelForRemainingBatch(result))
    }

    @Test
    fun `does not poison remaining batch on recoverable http stall`() {
        val result =
            TransferResult(
                success = false,
                message = "HTTP_STALLED_RETRY: file=Bayern_oam.osm.map idleMs=45789 timeoutMs=45000 sent=624951296",
            )

        assertFalse(shouldPreferChannelForRemainingBatch(result))
    }

    @Test
    fun `does not poison remaining batch on generic server error`() {
        val result =
            TransferResult(
                success = false,
                message = "Server Error: CHECKSUM_MISMATCH",
            )

        assertFalse(shouldPreferChannelForRemainingBatch(result))
    }
}
