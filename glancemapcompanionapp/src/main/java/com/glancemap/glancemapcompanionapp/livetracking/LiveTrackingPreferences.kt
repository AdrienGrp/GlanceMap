package com.glancemap.glancemapcompanionapp.livetracking

import android.content.Context

internal data class SavedLiveTrackingSettings(
    val group: String = "",
    val participantPassword: String = "",
    val followerPassword: String = "",
    val userName: String = "",
    val notificationEmailAddresses: List<String> = emptyList(),
    val alertEmailAddresses: List<String> = emptyList(),
    val stuckAlarmMinutes: String = "15",
    val updateIntervalSeconds: Int = 60,
)

internal object LiveTrackingPreferences {
    private const val PREFS_NAME = "arkluz_live_tracking"
    private const val KEY_GROUP = "group"
    private const val KEY_PARTICIPANT_PASSWORD = "participant_password"
    private const val KEY_FOLLOWER_PASSWORD = "follower_password"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_NOTIFICATION_EMAILS = "notification_emails"
    private const val KEY_ALERT_EMAILS = "alert_emails"
    private const val KEY_STUCK_ALARM_MINUTES = "stuck_alarm_minutes"
    private const val KEY_UPDATE_INTERVAL_SECONDS = "update_interval_seconds"

    fun load(context: Context): SavedLiveTrackingSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return SavedLiveTrackingSettings(
            group = prefs.getString(KEY_GROUP, "").orEmpty(),
            participantPassword = prefs.getString(KEY_PARTICIPANT_PASSWORD, "").orEmpty(),
            followerPassword = prefs.getString(KEY_FOLLOWER_PASSWORD, "").orEmpty(),
            userName = prefs.getString(KEY_USER_NAME, "").orEmpty(),
            notificationEmailAddresses = prefs.getString(KEY_NOTIFICATION_EMAILS, "").orEmpty().toEmailList(),
            alertEmailAddresses = prefs.getString(KEY_ALERT_EMAILS, "").orEmpty().toEmailList(),
            stuckAlarmMinutes = prefs.getString(KEY_STUCK_ALARM_MINUTES, "15").orEmpty().ifBlank { "15" },
            updateIntervalSeconds = prefs.getInt(KEY_UPDATE_INTERVAL_SECONDS, 60),
        )
    }

    fun save(
        context: Context,
        settings: SavedLiveTrackingSettings,
    ) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GROUP, settings.group)
            .putString(KEY_PARTICIPANT_PASSWORD, settings.participantPassword)
            .putString(KEY_FOLLOWER_PASSWORD, settings.followerPassword)
            .putString(KEY_USER_NAME, settings.userName)
            .putString(KEY_NOTIFICATION_EMAILS, settings.notificationEmailAddresses.joinToString(","))
            .putString(KEY_ALERT_EMAILS, settings.alertEmailAddresses.joinToString(","))
            .putString(KEY_STUCK_ALARM_MINUTES, settings.stuckAlarmMinutes)
            .putInt(KEY_UPDATE_INTERVAL_SECONDS, settings.updateIntervalSeconds)
            .apply()
    }

    fun clear(context: Context) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun String.toEmailList(): List<String> =
        split(",")
            .map(String::trim)
            .filter(String::isNotBlank)
}
