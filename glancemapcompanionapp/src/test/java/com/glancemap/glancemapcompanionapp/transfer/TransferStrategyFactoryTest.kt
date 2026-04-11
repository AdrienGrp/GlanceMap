package com.glancemap.glancemapcompanionapp.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferStrategyFactoryTest {

    @Test
    fun `message sized file stays on message even in shared http batch`() {
        val selectionContext = TransferSelectionContext(
            wifiAvailable = true,
            preferSharedHttpForBatch = true
        )

        assertEquals(
            TransferStrategyKind.MESSAGE,
            TransferStrategyFactory.decide(60L * 1024L, selectionContext)
        )
    }

    @Test
    fun `sub one megabyte file stays off http in shared batch`() {
        val selectionContext = TransferSelectionContext(
            wifiAvailable = true,
            preferSharedHttpForBatch = true
        )

        assertEquals(
            TransferStrategyKind.CHANNEL,
            TransferStrategyFactory.decide(200L * 1024L, selectionContext)
        )
    }

    @Test
    fun `one megabyte file becomes http eligible in shared batch`() {
        val selectionContext = TransferSelectionContext(
            wifiAvailable = true,
            preferSharedHttpForBatch = true
        )

        assertEquals(
            TransferStrategyKind.HTTP,
            TransferStrategyFactory.decide(1L * 1024L * 1024L, selectionContext)
        )
    }

    @Test
    fun `shared http helper uses one megabyte threshold`() {
        assertFalse(TransferStrategyFactory.isSharedHttpBatchEligible((1L * 1024L * 1024L) - 1L))
        assertTrue(TransferStrategyFactory.isSharedHttpBatchEligible(1L * 1024L * 1024L))
    }
}
