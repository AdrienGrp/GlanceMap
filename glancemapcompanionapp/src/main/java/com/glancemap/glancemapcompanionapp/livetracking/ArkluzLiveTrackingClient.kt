package com.glancemap.glancemapcompanionapp.livetracking

import android.content.Context
import android.location.Location
import android.net.Uri
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

data class LiveTrackingSettings(
    val trackingUrl: String = ArkluzTrackingEndpoint.DEVELOPMENT.url,
    val updateIntervalSeconds: Int = 60,
    val group: String,
    val participantPassword: String,
    val followerPassword: String,
    val userName: String,
    val notificationEmails: String,
    val alertEmails: String,
    val stuckAlarmMinutes: String,
    val comments: String,
    val gpxUri: Uri?,
    val gpxName: String,
)

enum class ArkluzTrackingEndpoint(
    val label: String,
    val url: String,
) {
    PRODUCTION("Production", "https://arkluz.com/trk"),
    DEVELOPMENT("Development", "https://arkluz.com/dev/trk"),
}

internal class ArkluzLiveTrackingClient(
    private val context: Context,
) {
    private val appContext = context.applicationContext
    private val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()

    suspend fun uploadPlannedRoute(settings: LiveTrackingSettings): ArkluzServerResult {
        if (settings.gpxUri == null && settings.comments.isBlank()) {
            return ArkluzServerResult("Nothing to send")
        }
        return withContext(Dispatchers.IO) {
            val tempFile =
                settings.gpxUri?.let { gpxUri ->
                    copyToTempFile(gpxUri, settings.gpxName.ifBlank { "planned-route.gpx" })
                }
            try {
                val builder =
                    MultipartBody
                        .Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("q", "upload")
                        .addFormDataPart("group", settings.group.trim())
                        .addFormDataPart("pass", settings.participantPassword.trim())
                if (tempFile != null) {
                    builder.addFormDataPart(
                        "upload",
                        tempFile.name,
                        tempFile.asRequestBody("application/gpx+xml".toMediaType()),
                    )
                }
                settings.comments.trim().takeIf { it.isNotBlank() }?.let { comments ->
                    builder.addFormDataPart("comments", comments)
                }
                val body = builder.build()

                execute(
                    Request
                        .Builder()
                        .url(settings.trackingUrl.trim().ifBlank { ArkluzTrackingEndpoint.DEVELOPMENT.url })
                        .post(body)
                        .build(),
                )
            } finally {
                tempFile?.delete()
            }
        }
    }

    suspend fun registerOrJoinGroup(settings: LiveTrackingSettings): ArkluzServerResult =
        withContext(Dispatchers.IO) {
            val url =
                settings.trackingUrl
                    .trim()
                    .ifBlank { ArkluzTrackingEndpoint.DEVELOPMENT.url }
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("q", "register")
                    .addQueryParameter("group", settings.group.trim())
                    .addQueryParameter("pass", settings.participantPassword.trim())
                    .addEncodedQueryParameter("api", null)
                    .build()

            execute(
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build(),
            )
        }

    suspend fun checkGroup(settings: LiveTrackingSettings): ArkluzServerResult =
        withContext(Dispatchers.IO) {
            val url =
                settings.trackingUrl
                    .trim()
                    .ifBlank { ArkluzTrackingEndpoint.DEVELOPMENT.url }
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("q", "check")
                    .addQueryParameter("group", settings.group.trim())
                    .addQueryParameter("pass", settings.participantPassword.trim())
                    .build()

            execute(
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build(),
            )
        }

    suspend fun deleteRecordedTracks(settings: LiveTrackingSettings): ArkluzServerResult =
        withContext(Dispatchers.IO) {
            val url =
                settings.trackingUrl
                    .trim()
                    .ifBlank { ArkluzTrackingEndpoint.DEVELOPMENT.url }
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("q", "cleanup")
                    .addQueryParameter("group", settings.group.trim())
                    .addQueryParameter("pass", settings.participantPassword.trim())
                    .build()

            execute(
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build(),
            )
        }

    suspend fun downloadRecordedGpx(
        settings: LiveTrackingSettings,
        userOnly: Boolean,
        outputUri: Uri,
    ): ArkluzServerResult =
        withContext(Dispatchers.IO) {
            val urlBuilder =
                settings.trackingUrl
                    .trim()
                    .ifBlank { ArkluzTrackingEndpoint.DEVELOPMENT.url }
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("q", "gpx")
                    .addQueryParameter("day", todayForArkluz())
                    .addQueryParameter("group", settings.group.trim())
                    .addQueryParameter("p", settings.followerPassword.trim())

            if (userOnly) {
                urlBuilder.addQueryParameter("user", settings.userName.trim())
            }

            executeGpxDownload(
                request =
                    Request
                        .Builder()
                        .url(urlBuilder.build())
                        .get()
                        .build(),
                outputUri = outputUri,
            )
        }

    suspend fun saveSettings(settings: LiveTrackingSettings): ArkluzServerResult =
        withContext(Dispatchers.IO) {
            val urlBuilder =
                settings.trackingUrl
                    .trim()
                    .ifBlank { ArkluzTrackingEndpoint.DEVELOPMENT.url }
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("q", "check")
                    .addQueryParameter("group", settings.group.trim())
                    .addQueryParameter("pass", settings.participantPassword.trim())

            settings.userName.trim().takeIf { it.isNotBlank() }?.let { user ->
                urlBuilder.addQueryParameter("user", user)
            }
            settings.notificationEmails.trim().takeIf { it.isNotBlank() }?.let { emails ->
                urlBuilder.addQueryParameter("email", emails)
            }
            settings.alertEmails.trim().takeIf { it.isNotBlank() }?.let { emails ->
                urlBuilder.addQueryParameter("alert", emails)
            }
            settings.stuckAlarmMinutes.trim().takeIf { it.isNotBlank() }?.let { alarm ->
                urlBuilder.addQueryParameter("alarm", alarm)
            }

            execute(
                Request
                    .Builder()
                    .url(urlBuilder.build())
                    .get()
                    .build(),
            )
        }

    suspend fun sendLocation(
        settings: LiveTrackingSettings,
        location: Location,
        start: Boolean,
        stop: Boolean,
    ): ArkluzServerResult =
        withContext(Dispatchers.IO) {
            val urlBuilder =
                settings.trackingUrl
                    .trim()
                    .ifBlank { ArkluzTrackingEndpoint.DEVELOPMENT.url }
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("lat", location.latitude.toString())
                    .addQueryParameter("lon", location.longitude.toString())
                    .addQueryParameter("acc", location.accuracy.toString())
                    .addQueryParameter("time", (location.time / 1000L).toString())
                    .addQueryParameter("battery", batteryPercent().toString())
                    .addQueryParameter("gsm_signal", gsmSignalPercent().toString())
                    .addQueryParameter("group", settings.group.trim())
                    .addQueryParameter("pass", settings.participantPassword.trim())
                    .addQueryParameter("user", settings.userName.trim())

            if (location.hasAltitude()) {
                urlBuilder.addQueryParameter("alt", location.altitude.toString())
            }
            if (location.hasSpeed()) {
                urlBuilder.addQueryParameter("speed", location.speed.toString())
            }
            settings.notificationEmails.trim().takeIf { it.isNotBlank() }?.let { emails ->
                urlBuilder.addQueryParameter("email", emails)
            }
            settings.alertEmails.trim().takeIf { it.isNotBlank() }?.let { emails ->
                urlBuilder.addQueryParameter("alert", emails)
            }
            settings.stuckAlarmMinutes.trim().takeIf { it.isNotBlank() }?.let { alarm ->
                urlBuilder.addQueryParameter("alarm", alarm)
            }
            if (start) {
                urlBuilder.addEncodedQueryParameter("start", null)
            }
            if (stop) {
                urlBuilder.addEncodedQueryParameter("stop", null)
            }

            execute(
                Request
                    .Builder()
                    .url(urlBuilder.build())
                    .get()
                    .build(),
            )
        }

    private fun execute(request: Request): ArkluzServerResult {
        httpClient.newCall(request).execute().use { response ->
            val serverMessage =
                response.body
                    .string()
                    .toReadableServerMessage()
            if (!response.isSuccessful) {
                val detail = serverMessage.ifBlank { "HTTP ${response.code}" }
                throw IllegalStateException("Server returned $detail")
            }
            if (serverMessage.isArkluzError()) {
                throw IllegalStateException(serverMessage)
            }
            return serverMessage.toArkluzServerResult()
        }
    }

    private fun executeGpxDownload(
        request: Request,
        outputUri: Uri,
    ): ArkluzServerResult {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body
            val contentType = body.contentType()?.toString().orEmpty()
            if (!response.isSuccessful) {
                val detail = body.string().toReadableServerMessage().ifBlank { "HTTP ${response.code}" }
                throw IllegalStateException("Server returned $detail")
            }
            if (!contentType.contains("gpx", ignoreCase = true)) {
                val serverMessage = body.string().toReadableServerMessage()
                if (serverMessage.isArkluzError()) {
                    throw IllegalStateException(serverMessage)
                }
                throw IllegalStateException(serverMessage.ifBlank { "Server did not return a GPX file" })
            }

            appContext.contentResolver.openOutputStream(outputUri, "wt").use { output ->
                requireNotNull(output) { "Unable to open destination file" }
                body.byteStream().use { input -> input.copyTo(output) }
            }
            return ArkluzServerResult("Recorded GPX downloaded")
        }
    }

    private fun copyToTempFile(
        uri: Uri,
        displayName: String,
    ): File {
        val safeName =
            displayName
                .replace("\\", "_")
                .replace("/", "_")
                .ifBlank { "planned-route.gpx" }
        val tempFile = File.createTempFile("arkluz-", "-$safeName", appContext.cacheDir)
        appContext.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open GPX file" }
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        return tempFile
    }

    private fun batteryPercent(): Int {
        val batteryManager = appContext.getSystemService(BatteryManager::class.java)
        return batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }

    private fun gsmSignalPercent(): Int = -1
}

private fun todayForArkluz(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

data class ArkluzServerResult(
    val message: String,
    val viewerPassword: String? = null,
    val groupAvailable: Boolean = false,
)

private fun String.toReadableServerMessage(): String =
    replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>|</div>|</li>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n")
        .replace(Regex("\\s*Go back\\.?\\s*$", RegexOption.IGNORE_CASE), "")
        .trim()

private fun String.isArkluzError(): Boolean {
    val normalized = lowercase()
    val singleLine = normalized.replace(Regex("\\s+"), " ")
    return normalized.startsWith("error:") ||
        "incorrect password" in normalized ||
        "please specify a group and a password" in singleLine
}

private fun String.toUserFacingServerMessage(): String {
    if (isBlank()) return "Server accepted request"
    if (startsWith("OK", ignoreCase = true)) return "Server accepted request"
    return this
}

private fun String.toArkluzServerResult(): ArkluzServerResult {
    val lines = lines().map { it.trim() }.filter { it.isNotBlank() }
    val viewerPassword =
        lines
            .drop(1)
            .firstOrNull()
            ?.takeIf { lines.firstOrNull()?.equals("OK", ignoreCase = true) == true }
    return ArkluzServerResult(
        message = toUserFacingServerMessage(),
        viewerPassword = viewerPassword,
        groupAvailable = equals("group available", ignoreCase = true),
    )
}
