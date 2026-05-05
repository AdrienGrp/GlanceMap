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
    val trackingUrl: String = ArkluzTrackingEndpoint.PRODUCTION.url,
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
                        .url(settings.trackingUrl.trim().ifBlank { ArkluzTrackingEndpoint.PRODUCTION.url })
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
                    .ifBlank { ArkluzTrackingEndpoint.PRODUCTION.url }
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("q", "register")
                    .addQueryParameter("group", settings.group.trim())
                    .addQueryParameter("pass", settings.participantPassword.trim())
                    .addEncodedQueryParameter("api!", null)
                    .build()

            execute(
                Request
                    .Builder()
                    .url(url)
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
                    .ifBlank { ArkluzTrackingEndpoint.PRODUCTION.url }
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("lat", location.latitude.toString())
                    .addQueryParameter("lon", location.longitude.toString())
                    .addQueryParameter("acc", location.accuracy.toString())
                    .addQueryParameter("time", location.time.toString())
                    .addQueryParameter("battery", batteryPercent().toString())
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
                urlBuilder.addQueryParameter("start", "1")
            }
            if (stop) {
                urlBuilder.addQueryParameter("stop", "1")
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
            return ArkluzServerResult(serverMessage.toUserFacingServerMessage())
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
}

data class ArkluzServerResult(
    val message: String,
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
        .trim()

private fun String.isArkluzError(): Boolean {
    val normalized = lowercase()
    return normalized.startsWith("error:") ||
        "incorrect password" in normalized ||
        "please specify a group and a password" in normalized
}

private fun String.toUserFacingServerMessage(): String {
    if (isBlank()) return "Server accepted request"
    if (startsWith("OK", ignoreCase = true)) return "Server accepted request"
    return this
}
