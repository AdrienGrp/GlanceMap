package com.glancemap.glancemapcompanionapp.transfer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferUtilsLocalIpCandidateTest {

    @Test
    fun `prefers wlan candidate over hotspot candidate`() {
        val hotspot = TransferUtils.buildLocalIpCandidate(
            ifName = "ap0",
            ip = "192.168.43.1",
            source = "interfaces"
        )
        val wifi = TransferUtils.buildLocalIpCandidate(
            ifName = "wlan0",
            ip = "192.168.0.189",
            source = "interfaces"
        )

        val best = TransferUtils.selectBestLocalIpCandidate(listOfNotNull(hotspot, wifi))

        assertEquals("192.168.0.189", best?.ip)
    }

    @Test
    fun `keeps hotspot candidate when it is the only option`() {
        val hotspot = TransferUtils.buildLocalIpCandidate(
            ifName = "softap0",
            ip = "192.168.43.1",
            source = "interfaces"
        )

        val best = TransferUtils.selectBestLocalIpCandidate(listOfNotNull(hotspot))

        assertEquals("192.168.43.1", best?.ip)
    }

    @Test
    fun `wifi interface scores above hotspot interface`() {
        val wifiScore = TransferUtils.scoreLocalIpCandidate("wlan0", "192.168.0.189")
        val hotspotScore = TransferUtils.scoreLocalIpCandidate("ap0", "192.168.43.1")

        assertTrue(wifiScore > hotspotScore)
    }
}
