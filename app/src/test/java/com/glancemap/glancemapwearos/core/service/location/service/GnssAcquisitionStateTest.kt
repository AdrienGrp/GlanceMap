package com.glancemap.glancemapwearos.core.service.location.service

import org.junit.Assert.assertEquals
import org.junit.Test

class GnssAcquisitionStateTest {
    @Test
    fun acquisitionStatePrioritizesSatellitesUsedInFix() {
        assertEquals(
            GnssAcquisitionState.SATELLITES_USED,
            resolveGnssAcquisitionState(
                signalSatelliteCount = 0,
                ephemerisSatelliteCount = 0,
                usedInFixCount = 1,
            ),
        )
    }

    @Test
    fun acquisitionStateDistinguishesEachMissingAcquisitionStage() {
        assertEquals(
            GnssAcquisitionState.NO_RADIO_SIGNAL,
            resolveGnssAcquisitionState(0, 0, 0),
        )
        assertEquals(
            GnssAcquisitionState.SIGNALS_NO_EPHEMERIS,
            resolveGnssAcquisitionState(2, 0, 0),
        )
        assertEquals(
            GnssAcquisitionState.EPHEMERIS_NO_FIX,
            resolveGnssAcquisitionState(2, 1, 0),
        )
    }
}
