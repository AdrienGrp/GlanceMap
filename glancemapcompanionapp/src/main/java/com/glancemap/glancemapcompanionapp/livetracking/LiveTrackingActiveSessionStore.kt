package com.glancemap.glancemapcompanionapp.livetracking

import android.content.Context
import android.net.Uri

/**
 * Makes an explicitly started live-tracking session recoverable if Android recreates its
 * foreground-service process. The record is removed as soon as the user stops tracking.
 */
internal object LiveTrackingActiveSessionStore {
    private const val PREFS_NAME = "arkluz_live_tracking_active_session"
    private const val KEY_ACTIVE = "active"
    private const val KEY_TRACKING_URL = "tracking_url"
    private const val KEY_UPDATE_INTERVAL_SECONDS = "update_interval_seconds"
    private const val KEY_GROUP = "group"
    private const val KEY_PARTICIPANT_PASSWORD = "participant_password"
    private const val KEY_FOLLOWER_PASSWORD = "follower_password"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_NOTIFICATION_EMAILS = "notification_emails"
    private const val KEY_ALERT_EMAILS = "alert_emails"
    private const val KEY_STUCK_ALARM_MINUTES = "stuck_alarm_minutes"
    private const val KEY_COMMENTS = "comments"
    private const val KEY_GPX_URI = "gpx_uri"
    private const val KEY_GPX_NAME = "gpx_name"
    private const val KEY_PAUSED = "paused"
    private const val KEY_SENT_START = "sent_start"
    private const val KEY_DATE_ID = "date_id"

    internal data class Session(
        val settings: LiveTrackingSettings,
        val isPaused: Boolean,
        val sentStart: Boolean,
        val dateId: String?,
    )

    fun save(
        context: Context,
        settings: LiveTrackingSettings,
        isPaused: Boolean,
        sentStart: Boolean,
        dateId: String?,
    ) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_TRACKING_URL, settings.trackingUrl)
            .putInt(KEY_UPDATE_INTERVAL_SECONDS, settings.updateIntervalSeconds)
            .putString(KEY_GROUP, settings.group)
            .putString(KEY_PARTICIPANT_PASSWORD, settings.participantPassword)
            .putString(KEY_FOLLOWER_PASSWORD, settings.followerPassword)
            .putString(KEY_USER_NAME, settings.userName)
            .putString(KEY_NOTIFICATION_EMAILS, settings.notificationEmails)
            .putString(KEY_ALERT_EMAILS, settings.alertEmails)
            .putString(KEY_STUCK_ALARM_MINUTES, settings.stuckAlarmMinutes)
            .putString(KEY_COMMENTS, settings.comments)
            .putString(KEY_GPX_URI, settings.gpxUri?.toString())
            .putString(KEY_GPX_NAME, settings.gpxName)
            .putBoolean(KEY_PAUSED, isPaused)
            .putBoolean(KEY_SENT_START, sentStart)
            .putString(KEY_DATE_ID, dateId)
            .apply()
    }

    fun load(context: Context): Session? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return null

        val group = prefs.getString(KEY_GROUP, "").orEmpty()
        val participantPassword = prefs.getString(KEY_PARTICIPANT_PASSWORD, "").orEmpty()
        val userName = prefs.getString(KEY_USER_NAME, "").orEmpty()
        if (group.isBlank() || participantPassword.isBlank() || userName.isBlank()) {
            clear(context)
            return null
        }

        return Session(
            settings =
                LiveTrackingSettings(
                    trackingUrl =
                        prefs
                            .getString(KEY_TRACKING_URL, ArkluzTrackingEndpoint.defaultUrl)
                            .orEmpty()
                            .ifBlank { ArkluzTrackingEndpoint.defaultUrl },
                    updateIntervalSeconds = prefs.getInt(KEY_UPDATE_INTERVAL_SECONDS, 60),
                    group = group,
                    participantPassword = participantPassword,
                    followerPassword = prefs.getString(KEY_FOLLOWER_PASSWORD, "").orEmpty(),
                    userName = userName,
                    notificationEmails = prefs.getString(KEY_NOTIFICATION_EMAILS, "").orEmpty(),
                    alertEmails = prefs.getString(KEY_ALERT_EMAILS, "").orEmpty(),
                    stuckAlarmMinutes = prefs.getString(KEY_STUCK_ALARM_MINUTES, "").orEmpty(),
                    comments = prefs.getString(KEY_COMMENTS, "").orEmpty(),
                    gpxUri =
                        prefs
                            .getString(KEY_GPX_URI, "")
                            .orEmpty()
                            .takeIf(String::isNotBlank)
                            ?.let(Uri::parse),
                    gpxName = prefs.getString(KEY_GPX_NAME, "").orEmpty(),
                ),
            isPaused = prefs.getBoolean(KEY_PAUSED, false),
            sentStart = prefs.getBoolean(KEY_SENT_START, false),
            dateId = prefs.getString(KEY_DATE_ID, null),
        )
    }

    fun clear(context: Context) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
