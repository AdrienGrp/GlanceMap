package com.glancemap.glancemapwearos.presentation.features.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalDistanceSessionTest {
    @Test
    fun firstSensorSampleStartsSessionAtZero() {
        val state = advanceExternalDistanceSession(250.0, baseMeters = null, previousSessionMeters = null)

        assertEquals(250.0, state?.baseMeters ?: Double.NaN, 0.001)
        assertEquals(0.0, state?.sessionMeters ?: Double.NaN, 0.001)
    }

    @Test
    fun reconnectCounterResetPreservesCompletedSessionDistance() {
        val state = advanceExternalDistanceSession(0.0, baseMeters = 0.0, previousSessionMeters = 74.0)

        assertEquals(-74.0, state?.baseMeters ?: Double.NaN, 0.001)
        assertEquals(74.0, state?.sessionMeters ?: Double.NaN, 0.001)

        val next = advanceExternalDistanceSession(33.0, state?.baseMeters, state?.sessionMeters)
        assertEquals(107.0, next?.sessionMeters ?: Double.NaN, 0.001)
    }

    @Test
    fun restoredSessionContinuesFromAnyNewDeviceCounter() {
        val state = advanceExternalDistanceSession(600.0, baseMeters = null, previousSessionMeters = 107.0)
        val next = advanceExternalDistanceSession(610.0, state?.baseMeters, state?.sessionMeters)

        assertEquals(117.0, next?.sessionMeters ?: Double.NaN, 0.001)
    }

    @Test
    fun tinyCounterRegressionDoesNotReduceDistanceOrRebase() {
        val state = advanceExternalDistanceSession(173.7, baseMeters = 100.0, previousSessionMeters = 74.0)

        assertEquals(100.0, state?.baseMeters ?: Double.NaN, 0.001)
        assertEquals(74.0, state?.sessionMeters ?: Double.NaN, 0.001)
    }

    @Test
    fun invalidSensorDistanceIsIgnored() {
        assertNull(advanceExternalDistanceSession(Double.NaN, baseMeters = 0.0, previousSessionMeters = 20.0))
        assertNull(advanceExternalDistanceSession(-1.0, baseMeters = 0.0, previousSessionMeters = 20.0))
    }
}
