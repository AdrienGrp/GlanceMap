package com.glancemap.glancemapwearos.presentation.features.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.glancemap.glancemapwearos.R
import com.glancemap.glancemapwearos.presentation.MainActivity

class OamDownloadNotificationController(
    private val context: Context,
) {
    private val appContext = context.applicationContext

    fun showProgress(
        title: String,
        detail: String,
        bytesDone: Long,
        totalBytes: Long?,
    ) {
        notify(
            buildBase(title, detail)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .apply {
                    val total = totalBytes?.takeIf { it > 0L }
                    if (total != null) {
                        val progress = ((bytesDone.coerceAtLeast(0L) * 100L) / total).toInt().coerceIn(0, 100)
                        setProgress(100, progress, false)
                    } else {
                        setProgress(0, 0, true)
                    }
                },
        )
    }

    fun showPaused(detail: String) {
        notify(
            buildBase("Download paused", detail)
                .setOngoing(false)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS),
        )
    }

    fun showComplete(detail: String) {
        notify(
            buildBase("Download complete", detail)
                .setOngoing(false)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setTimeoutAfter(10_000L),
        )
    }

    fun showError(detail: String) {
        notify(
            buildBase("Download failed", detail)
                .setOngoing(false)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ERROR),
        )
    }

    fun clear() {
        NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
    }

    private fun buildBase(
        title: String,
        detail: String,
    ): NotificationCompat.Builder {
        ensureChannel()
        return NotificationCompat
            .Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(contentIntent())
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    private fun notify(builder: NotificationCompat.Builder) {
        if (!canPostNotifications()) return
        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, builder.build())
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Map downloads",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun contentIntent(): PendingIntent {
        val intent =
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        return PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        private const val CHANNEL_ID = "OamDownloadChannel"
        private const val NOTIFICATION_ID = 42_210
    }
}
