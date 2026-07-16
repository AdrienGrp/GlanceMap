package com.glancemap.glancemapcompanionapp.livetracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArkluzSessionControlUrlTest {
    @Test
    fun locationTimeUsesEpochMilliseconds() {
        val url = buildArkluzLocationUrl(update())

        assertEquals("1750000000000", url.queryParameter("time"))
    }

    @Test
    fun pauseIncludesPauseAndDateIdWithoutStartingNewActivity() {
        val url = buildArkluzLocationUrl(update(pause = true, dateId = "20260622-42"))

        assertTrue("pause" in url.queryParameterNames)
        assertFalse("start" in url.queryParameterNames)
        assertFalse("resume" in url.queryParameterNames)
        assertEquals("20260622-42", url.queryParameter("date_id"))
    }

    @Test
    fun resumeIncludesResumeAndDateIdWithoutStartingNewActivity() {
        val url = buildArkluzLocationUrl(update(resume = true, dateId = "20260622-42"))

        assertTrue("resume" in url.queryParameterNames)
        assertFalse("start" in url.queryParameterNames)
        assertFalse("pause" in url.queryParameterNames)
        assertEquals("20260622-42", url.queryParameter("date_id"))
    }

    private fun update(
        pause: Boolean = false,
        resume: Boolean = false,
        dateId: String? = null,
    ) = ArkluzLocationUpdate(
        trackingUrl = "https://arkluz.com/trk",
        latitude = 45.0,
        longitude = 6.0,
        altitudeMeters = null,
        speedMetersPerSecond = null,
        accuracyMeters = 5f,
        epochMilliseconds = 1_750_000_000_000,
        batteryPercent = 80,
        gsmSignalPercent = -1,
        group = "Alpes",
        participantPassword = "secret",
        userName = "André",
        notificationEmails = "",
        alertEmails = "",
        stuckAlarmMinutes = "15",
        start = false,
        stop = false,
        pause = pause,
        resume = resume,
        dateId = dateId,
    )
}
