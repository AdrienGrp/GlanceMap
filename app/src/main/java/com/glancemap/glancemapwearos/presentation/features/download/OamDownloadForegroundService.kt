@file:Suppress("TooManyFunctions")

package com.glancemap.glancemapwearos.presentation.features.download

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.glancemap.glancemapwearos.R
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.presentation.MainActivity

class OamDownloadForegroundService : Service() {
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_PROGRESS -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Downloading offline bundle"
                val detail = intent.getStringExtra(EXTRA_DETAIL) ?: "Preparing"
                val bytesDone = intent.getLongExtra(EXTRA_BYTES_DONE, 0L)
                val totalBytes = intent.getLongExtra(EXTRA_TOTAL_BYTES, -1L).takeIf { it > 0L }
                startOrUpdateForeground(title, detail, bytesDone, totalBytes)
            }
            ACTION_STOP -> stopSelf()
            else -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("WakelockTimeout")
    private fun startOrUpdateForeground(
        title: String,
        detail: String,
        bytesDone: Long,
        totalBytes: Long?,
    ) {
        acquireLocks()
        val notification = buildProgressNotification(title, detail, bytesDone, totalBytes)
        val foregroundServiceType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundServiceType)
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        if (wakeLock?.isHeld != true) {
            val powerManager = getSystemService(PowerManager::class.java)
            wakeLock =
                powerManager
                    ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                    ?.apply {
                        setReferenceCounted(false)
                        acquire(WAKE_LOCK_TIMEOUT_MS)
                    }
        }
        if (wifiLock?.isHeld != true) {
            val wifiManager = applicationContext.getSystemService(WifiManager::class.java)
            wifiLock =
                wifiManager
                    ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WIFI_LOCK_TAG)
                    ?.apply {
                        setReferenceCounted(false)
                        runCatching { acquire() }
                    }
        }
        DebugTelemetry.log("OamDownload", "event=foreground_keepalive_active")
    }

    private fun releaseLocks() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                runCatching { lock.release() }
            }
        }
        wifiLock?.let { lock ->
            if (lock.isHeld) {
                runCatching { lock.release() }
            }
        }
        wakeLock = null
        wifiLock = null
        DebugTelemetry.log("OamDownload", "event=foreground_keepalive_stopped")
    }

    private fun buildProgressNotification(
        title: String,
        detail: String,
        bytesDone: Long,
        totalBytes: Long?,
    ): android.app.Notification {
        val builder =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(detail)
                .setContentIntent(contentIntent())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        val total = totalBytes?.takeIf { it > 0L }
        if (total != null) {
            val progress =
                ((bytesDone.coerceAtLeast(0L) * 100L) / total)
                    .toInt()
                    .coerceIn(0, 100)
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Map downloads",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun contentIntent(): PendingIntent {
        val intent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val ACTION_PROGRESS = "com.glancemap.glancemapwearos.action.OAM_DOWNLOAD_PROGRESS"
        private const val ACTION_STOP = "com.glancemap.glancemapwearos.action.OAM_DOWNLOAD_STOP"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_DETAIL = "detail"
        private const val EXTRA_BYTES_DONE = "bytes_done"
        private const val EXTRA_TOTAL_BYTES = "total_bytes"
        private const val CHANNEL_ID = "OamDownloadChannel"
        private const val NOTIFICATION_ID = 42_210
        private const val WAKE_LOCK_TAG = "GlanceMap:OamDownload"
        private const val WIFI_LOCK_TAG = "GlanceMap:OamDownloadWifi"
        private const val WAKE_LOCK_TIMEOUT_MS = 6L * 60L * 60L * 1000L

        fun showProgress(
            context: Context,
            title: String,
            detail: String,
            bytesDone: Long,
            totalBytes: Long?,
        ) {
            val intent =
                Intent(context, OamDownloadForegroundService::class.java).apply {
                    action = ACTION_PROGRESS
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_DETAIL, detail)
                    putExtra(EXTRA_BYTES_DONE, bytesDone)
                    putExtra(EXTRA_TOTAL_BYTES, totalBytes ?: -1L)
                }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent =
                Intent(context, OamDownloadForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
            runCatching { context.startService(intent) }
            context.stopService(Intent(context, OamDownloadForegroundService::class.java))
        }
    }
}
