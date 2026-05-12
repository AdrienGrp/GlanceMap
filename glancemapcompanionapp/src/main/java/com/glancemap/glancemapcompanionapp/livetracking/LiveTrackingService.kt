package com.glancemap.glancemapcompanionapp.livetracking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.glancemap.glancemapcompanionapp.MainActivityMobile
import com.glancemap.glancemapcompanionapp.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LiveTrackingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var arkluzClient: ArkluzLiveTrackingClient
    private var settings: LiveTrackingSettings? = null
    private var lastLocation: Location? = null
    private var sentStart = false
    private var isPaused = false

    private val locationCallback =
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                lastLocation = location
                serviceScope.launch {
                    sendLocation(location = location, start = !sentStart, stop = false)
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        arkluzClient = ArkluzLiveTrackingClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                pauseTracking()
                return START_STICKY
            }

            ACTION_RESUME -> {
                resumeTracking()
                return START_STICKY
            }

            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
        }

        val parsedSettings = intent?.toLiveTrackingSettings()
        if (parsedSettings == null) {
            LiveTrackingSessionStore.setStopped("Missing live tracking settings")
            stopSelf()
            return START_NOT_STICKY
        }

        settings = parsedSettings
        sentStart = false
        isPaused = false
        LiveTrackingSessionStore.setStarting()
        startForegroundNotification("Starting live tracking")
        startTracking(parsedSettings)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { locationClient.removeLocationUpdates(locationCallback) }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startTracking(settings: LiveTrackingSettings) {
        if (!hasLocationPermission()) {
            LiveTrackingSessionStore.setStopped("Location permission is required")
            stopSelf()
            return
        }

        serviceScope.launch {
            LiveTrackingSessionStore.setStatus("Checking group")
            updateNotification("Checking group")
            val groupReady =
                runCatching { arkluzClient.registerOrJoinGroup(settings) }
                    .onFailure { error ->
                        LiveTrackingSessionStore.setStoppedWithError(
                            status = "Start failed",
                            message = error.message ?: "Unable to check group",
                        )
                        updateNotification("Live tracking error")
                        ServiceCompat.stopForeground(
                            this@LiveTrackingService,
                            ServiceCompat.STOP_FOREGROUND_REMOVE,
                        )
                        stopSelf()
                    }.isSuccess
            if (!groupReady) return@launch

            runCatching {
                LiveTrackingSessionStore.setStatus("Waiting for GPS fix")
                updateNotification("Waiting for GPS fix")
                startLocationUpdates()
                sendLastKnownLocationIfAvailable()
            }.onFailure { error ->
                LiveTrackingSessionStore.setError(error.message ?: "Live tracking start failed")
                updateNotification("Live tracking error")
            }
        }
    }

    private suspend fun sendLastKnownLocationIfAvailable() {
        if (!hasLocationPermission()) return
        val location =
            runCatching { locationClient.lastLocation.await() }
                .getOrNull()
                ?: return
        lastLocation = location
        sendLocation(location = location, start = !sentStart, stop = false)
    }

    private suspend fun sendLocation(
        location: Location,
        start: Boolean,
        stop: Boolean,
    ) {
        val activeSettings = settings ?: return
        if (isPaused && !stop) return
        runCatching {
            arkluzClient.sendLocation(
                settings = activeSettings,
                location = location,
                start = start,
                stop = stop,
            )
        }.onSuccess { result ->
            if (start) sentStart = true
            val serverMessage = result.message.takeUnless { it == "Server accepted request" }
            val status =
                serverMessage ?: when {
                    stop -> "Stop sent"
                    start -> "Started and position sent"
                    else -> "Position sent"
                }
            LiveTrackingSessionStore.setSent(status)
            updateNotification(status)
        }.onFailure { error ->
            LiveTrackingSessionStore.setError(error.message ?: "Unable to send position")
            updateNotification("Unable to send position")
        }
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        runCatching { locationClient.removeLocationUpdates(locationCallback) }
        isPaused = false
        val request =
            LocationRequest
                .Builder(Priority.PRIORITY_HIGH_ACCURACY, updateIntervalMs())
                .setMinUpdateIntervalMillis(updateIntervalMs())
                .setMaxUpdateDelayMillis(updateIntervalMs())
                .build()
        locationClient.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    private fun pauseTracking() {
        if (settings == null || isPaused) return
        runCatching { locationClient.removeLocationUpdates(locationCallback) }
        isPaused = true
        LiveTrackingSessionStore.setStatus("Paused")
        updateNotification("Live tracking paused")
    }

    private fun resumeTracking() {
        if (settings == null) return
        if (!isPaused) return
        if (!hasLocationPermission()) {
            LiveTrackingSessionStore.setStopped("Location permission is required")
            updateNotification("Location permission is required")
            stopSelf()
            return
        }
        isPaused = false
        LiveTrackingSessionStore.setStatus("Waiting for GPS fix")
        updateNotification("Waiting for GPS fix")
        startLocationUpdates()
        serviceScope.launch {
            sendLastKnownLocationIfAvailable()
        }
    }

    private fun updateIntervalMs(): Long =
        (settings?.updateIntervalSeconds ?: DEFAULT_UPDATE_INTERVAL_SECONDS)
            .coerceIn(MIN_UPDATE_INTERVAL_SECONDS, MAX_UPDATE_INTERVAL_SECONDS)
            .toLong() * 1000L

    private fun stopTracking() {
        runCatching { locationClient.removeLocationUpdates(locationCallback) }
        isPaused = false
        serviceScope.launch {
            val location = lastLocation
            if (location != null) {
                sendLocation(location = location, start = false, stop = true)
            }
            LiveTrackingSessionStore.setStopped("Stopped")
            ServiceCompat.stopForeground(this@LiveTrackingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startForegroundNotification(text: String) {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(text).build(),
            type,
        )
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text).build())
    }

    private fun buildNotification(text: String): NotificationCompat.Builder {
        val openIntent =
            PendingIntent.getActivity(
                this,
                REQ_OPEN_APP,
                Intent(this, MainActivityMobile::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val stopIntent =
            PendingIntent.getService(
                this,
                REQ_STOP,
                Intent(this, LiveTrackingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val pauseResumeIntent =
            PendingIntent.getService(
                this,
                if (isPaused) REQ_RESUME else REQ_PAUSE,
                Intent(this, LiveTrackingService::class.java)
                    .setAction(if (isPaused) ACTION_RESUME else ACTION_PAUSE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val pauseResumeLabel = if (isPaused) "Start" else "Pause"
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_companionapp_foreground)
            .setContentTitle("Live tracking running")
            .setContentText(text)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, pauseResumeLabel, pauseResumeIntent)
            .addAction(0, "Stop live tracking", stopIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Live Tracking", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val CHANNEL_ID = "live_tracking_channel"
        private const val NOTIFICATION_ID = 42
        private const val REQ_OPEN_APP = 4201
        private const val REQ_STOP = 4202
        private const val REQ_PAUSE = 4203
        private const val REQ_RESUME = 4204
        private const val DEFAULT_UPDATE_INTERVAL_SECONDS = 60
        private const val MIN_UPDATE_INTERVAL_SECONDS = 15
        private const val MAX_UPDATE_INTERVAL_SECONDS = 600
        private const val ACTION_STOP = "com.glancemap.glancemapcompanionapp.livetracking.STOP"
        private const val ACTION_PAUSE = "com.glancemap.glancemapcompanionapp.livetracking.PAUSE"
        private const val ACTION_RESUME = "com.glancemap.glancemapcompanionapp.livetracking.RESUME"

        private const val EXTRA_GROUP = "group"
        private const val EXTRA_TRACKING_URL = "tracking_url"
        private const val EXTRA_UPDATE_INTERVAL_SECONDS = "update_interval_seconds"
        private const val EXTRA_PARTICIPANT_PASSWORD = "participant_password"
        private const val EXTRA_FOLLOWER_PASSWORD = "follower_password"
        private const val EXTRA_USER_NAME = "user_name"
        private const val EXTRA_NOTIFICATION_EMAILS = "notification_emails"
        private const val EXTRA_ALERT_EMAILS = "alert_emails"
        private const val EXTRA_STUCK_ALARM_MINUTES = "stuck_alarm_minutes"
        private const val EXTRA_COMMENTS = "comments"
        private const val EXTRA_GPX_URI = "gpx_uri"
        private const val EXTRA_GPX_NAME = "gpx_name"

        fun start(
            context: Context,
            settings: LiveTrackingSettings,
        ) {
            val intent =
                Intent(context, LiveTrackingService::class.java)
                    .putExtra(EXTRA_TRACKING_URL, settings.trackingUrl)
                    .putExtra(EXTRA_UPDATE_INTERVAL_SECONDS, settings.updateIntervalSeconds)
                    .putExtra(EXTRA_GROUP, settings.group)
                    .putExtra(EXTRA_PARTICIPANT_PASSWORD, settings.participantPassword)
                    .putExtra(EXTRA_FOLLOWER_PASSWORD, settings.followerPassword)
                    .putExtra(EXTRA_USER_NAME, settings.userName)
                    .putExtra(EXTRA_NOTIFICATION_EMAILS, settings.notificationEmails)
                    .putExtra(EXTRA_ALERT_EMAILS, settings.alertEmails)
                    .putExtra(EXTRA_STUCK_ALARM_MINUTES, settings.stuckAlarmMinutes)
                    .putExtra(EXTRA_COMMENTS, settings.comments)
                    .putExtra(EXTRA_GPX_URI, settings.gpxUri?.toString())
                    .putExtra(EXTRA_GPX_NAME, settings.gpxName)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, LiveTrackingService::class.java).setAction(ACTION_STOP),
            )
        }

        private fun Intent.toLiveTrackingSettings(): LiveTrackingSettings? {
            val group = getStringExtra(EXTRA_GROUP).orEmpty()
            val pass = getStringExtra(EXTRA_PARTICIPANT_PASSWORD).orEmpty()
            val user = getStringExtra(EXTRA_USER_NAME).orEmpty()
            if (group.isBlank() || pass.isBlank() || user.isBlank()) return null
            val gpxUri = getStringExtra(EXTRA_GPX_URI)?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            return LiveTrackingSettings(
                trackingUrl =
                    getStringExtra(EXTRA_TRACKING_URL)
                        .orEmpty()
                        .ifBlank { ArkluzTrackingEndpoint.DEVELOPMENT.url },
                updateIntervalSeconds =
                    getIntExtra(
                        EXTRA_UPDATE_INTERVAL_SECONDS,
                        DEFAULT_UPDATE_INTERVAL_SECONDS,
                    ).coerceIn(MIN_UPDATE_INTERVAL_SECONDS, MAX_UPDATE_INTERVAL_SECONDS),
                group = group,
                participantPassword = pass,
                followerPassword = getStringExtra(EXTRA_FOLLOWER_PASSWORD).orEmpty(),
                userName = user,
                notificationEmails = getStringExtra(EXTRA_NOTIFICATION_EMAILS).orEmpty(),
                alertEmails = getStringExtra(EXTRA_ALERT_EMAILS).orEmpty(),
                stuckAlarmMinutes = getStringExtra(EXTRA_STUCK_ALARM_MINUTES).orEmpty(),
                comments = getStringExtra(EXTRA_COMMENTS).orEmpty(),
                gpxUri = gpxUri,
                gpxName = getStringExtra(EXTRA_GPX_NAME).orEmpty(),
            )
        }
    }
}
