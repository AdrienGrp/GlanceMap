package com.glancemap.glancemapcompanionapp.livetracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Patterns
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun validatePlanSettings(
    group: String,
    participantPassword: String,
): String? =
    when {
        group.isBlank() -> "Private group is required."
        participantPassword.isBlank() -> "Participant password is required."
        else -> null
    }

internal fun validateAccountSettings(
    group: String,
    participantPassword: String,
    followerPassword: String,
): String? =
    when {
        group.isBlank() -> "Private group is required."
        participantPassword.isBlank() -> "Participant password is required."
        followerPassword.isBlank() -> "Login / Join first."
        else -> null
    }

internal fun validateStartSettings(
    group: String,
    participantPassword: String,
    followerPassword: String,
    userName: String,
): String? =
    when {
        group.isBlank() -> "Private group is required."
        participantPassword.isBlank() -> "Participant password is required."
        followerPassword.isBlank() -> "Login / Join in settings first."
        userName.isBlank() -> "Participant name is required."
        else -> null
    }

internal fun validateRecordedTrackDownloadSettings(
    group: String,
    followerPassword: String,
    userName: String,
    userOnly: Boolean,
): String? =
    when {
        group.isBlank() -> "Private group is required."
        followerPassword.isBlank() -> "Login / Join first."
        userOnly && userName.isBlank() -> "Participant name is required."
        else -> null
    }

internal fun validatePendingEmailInputs(
    notificationEmailInput: String,
    alertEmailInput: String,
): String? =
    validatePendingEmailInput(
        input = notificationEmailInput,
        label = "tracking notification email",
    )
        ?: validatePendingEmailInput(
            input = alertEmailInput,
            label = "alert email",
        )

internal fun validatePendingEmailInput(
    input: String,
    label: String,
): String? {
    val email = input.normalizedEmailInput()
    return when {
        email.isBlank() -> null
        Patterns.EMAIL_ADDRESS.matcher(email).matches() -> null
        else -> "Enter a valid $label address."
    }
}

internal fun emailAddressesForRequest(
    addresses: List<String>,
    pendingInput: String,
): String {
    val pendingEmail = pendingInput.normalizedEmailInput()
    val allAddresses =
        if (
            pendingEmail.isNotBlank() &&
            Patterns.EMAIL_ADDRESS.matcher(pendingEmail).matches() &&
            addresses.none { it.equals(pendingEmail, ignoreCase = true) }
        ) {
            addresses + pendingEmail
        } else {
            addresses
        }
    return allAddresses.joinToString(",")
}

private fun String.normalizedEmailInput(): String = trim().trimEnd(',', ';').lowercase()

internal fun recordedTrackDownloadFilename(
    group: String,
    userName: String,
    target: RecordedTrackDownloadTarget,
): String {
    val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val rawName =
        when (target) {
            RecordedTrackDownloadTarget.USER -> userName.ifBlank { "user" }
            RecordedTrackDownloadTarget.GROUP -> group.ifBlank { "group" }
        }
    val safeName =
        rawName
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .trim('-')
            .ifBlank { target.name.lowercase() }
    return "$day-$safeName.gpx"
}

internal fun editableSettingsSnapshot(
    group: String,
    userName: String,
    notificationEmailAddresses: List<String>,
    alertEmailAddresses: List<String>,
    stuckAlarmMinutes: String,
    updateIntervalSeconds: Int,
): SavedLiveTrackingSettings =
    SavedLiveTrackingSettings(
        group = group,
        userName = userName,
        notificationEmailAddresses = notificationEmailAddresses,
        alertEmailAddresses = alertEmailAddresses,
        stuckAlarmMinutes = stuckAlarmMinutes,
        updateIntervalSeconds = updateIntervalSeconds,
    )

internal fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

internal fun sessionStatusText(state: LiveTrackingUiState): String {
    val lastUpdate = state.lastSuccessfulUpdateEpochMs
    val lastUpdateText =
        if (lastUpdate == null) {
            "none"
        } else {
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastUpdate))
        }
    return "${state.status}. Last successful update: $lastUpdateText"
}

internal fun arkluzTrackUrl(
    baseUrl: String,
    group: String,
    followerPassword: String,
    user: String?,
    selectedUser: String?,
): String {
    val cleanGroup = group.trim()
    val cleanPassword = followerPassword.trim()
    if (cleanGroup.isBlank() || cleanPassword.isBlank()) return ""

    val builder =
        Uri
            .parse(baseUrl)
            .buildUpon()
            .appendQueryParameter("q", "track")
            .appendQueryParameter("group", cleanGroup)
            .appendQueryParameter("p", cleanPassword)
    user
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { builder.appendQueryParameter("user", it) }
    selectedUser
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { builder.appendQueryParameter("select", it) }
    return builder.build().toString()
}

internal fun openUrl(
    context: Context,
    url: String,
) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

internal fun contactEmailPickerIntent(): Intent =
    Intent(
        Intent.ACTION_PICK,
        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
    )

internal fun resolveSelectedContactEmail(
    context: Context,
    uri: Uri,
): String? =
    context.contentResolver
        .query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor
                .getString(0)
                ?.trim()
                ?.lowercase()
                ?.takeIf { Patterns.EMAIL_ADDRESS.matcher(it).matches() }
        }

internal fun shareUrl(
    context: Context,
    url: String,
) {
    if (url.isBlank()) return
    val intent =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(intent, "Share track link"))
}

internal fun emailArkluzSupport(
    context: Context,
    errorMessage: String,
) {
    val body =
        buildString {
            appendLine("Hello,")
            appendLine()
            appendLine("I got this Live Tracking error from GlanceMap Companion:")
            appendLine(errorMessage.ifBlank { "Unknown error" })
            appendLine()
            appendLine("Thanks")
        }
    val intent =
        Intent(
            Intent.ACTION_SENDTO,
            Uri.parse("mailto:arkluz@arkluz.com"),
        ).apply {
            putExtra(Intent.EXTRA_SUBJECT, "GlanceMap Live Tracking error")
            putExtra(Intent.EXTRA_TEXT, body)
        }
    runCatching {
        context.startActivity(intent)
    }
}
