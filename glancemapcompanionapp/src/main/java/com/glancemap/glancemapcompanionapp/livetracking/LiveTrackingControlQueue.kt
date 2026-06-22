package com.glancemap.glancemapcompanionapp.livetracking

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal object LiveTrackingControlQueue {
    private const val PREFS_NAME = "arkluz_live_tracking_control_queue"
    private const val KEY_QUEUE = "queue"
    private val lock = Any()

    fun enqueue(
        context: Context,
        update: ArkluzLocationUpdate,
    ): Int =
        synchronized(lock) {
            val updates = loadLocked(context) + update
            saveLocked(context, updates)
            updates.size
        }

    fun load(context: Context): List<ArkluzLocationUpdate> =
        synchronized(lock) {
            loadLocked(context)
        }

    fun removeFirst(context: Context) {
        synchronized(lock) {
            val remaining = loadLocked(context).drop(1)
            saveLocked(context, remaining)
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            saveLocked(context, emptyList())
        }
    }

    private fun loadLocked(context: Context): List<ArkluzLocationUpdate> {
        val raw =
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_QUEUE, "[]")
                .orEmpty()
        val jsonArray = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until jsonArray.length()) {
                jsonArray.optJSONObject(index)?.toControlUpdateOrNull()?.let(::add)
            }
        }
    }

    private fun saveLocked(
        context: Context,
        updates: List<ArkluzLocationUpdate>,
    ) {
        val jsonArray = JSONArray()
        updates.forEach { jsonArray.put(it.toJson()) }
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUEUE, jsonArray.toString())
            .apply()
    }

    private fun ArkluzLocationUpdate.toJson(): JSONObject =
        JSONObject()
            .put("trackingUrl", trackingUrl)
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("altitudeMeters", altitudeMeters)
            .put("speedMetersPerSecond", speedMetersPerSecond)
            .put("accuracyMeters", accuracyMeters.toDouble())
            .put("epochSeconds", epochSeconds)
            .put("batteryPercent", batteryPercent)
            .put("gsmSignalPercent", gsmSignalPercent)
            .put("group", group)
            .put("participantPassword", participantPassword)
            .put("userName", userName)
            .put("notificationEmails", notificationEmails)
            .put("alertEmails", alertEmails)
            .put("stuckAlarmMinutes", stuckAlarmMinutes)
            .put("pause", pause)
            .put("resume", resume)
            .put("dateId", dateId)

    private fun JSONObject.toControlUpdateOrNull(): ArkluzLocationUpdate? =
        runCatching {
            ArkluzLocationUpdate(
                trackingUrl = getString("trackingUrl"),
                latitude = getDouble("latitude"),
                longitude = getDouble("longitude"),
                altitudeMeters = nullableDouble("altitudeMeters"),
                speedMetersPerSecond = nullableDouble("speedMetersPerSecond")?.toFloat(),
                accuracyMeters = getDouble("accuracyMeters").toFloat(),
                epochSeconds = getLong("epochSeconds"),
                batteryPercent = getInt("batteryPercent"),
                gsmSignalPercent = getInt("gsmSignalPercent"),
                group = getString("group"),
                participantPassword = getString("participantPassword"),
                userName = getString("userName"),
                notificationEmails = optString("notificationEmails"),
                alertEmails = optString("alertEmails"),
                stuckAlarmMinutes = optString("stuckAlarmMinutes"),
                start = false,
                stop = false,
                pause = optBoolean("pause"),
                resume = optBoolean("resume"),
                dateId = optString("dateId").takeIf(String::isNotBlank),
            )
        }.getOrNull()

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (isNull(key)) null else optDouble(key)
}
