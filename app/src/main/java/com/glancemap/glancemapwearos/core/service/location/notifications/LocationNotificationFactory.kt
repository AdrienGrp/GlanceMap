package com.glancemap.glancemapwearos.core.service.location.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import com.glancemap.glancemapwearos.R
import com.glancemap.glancemapwearos.presentation.MainActivity
import com.glancemap.glancemapwearos.presentation.navigation.WatchRoutes

internal class LocationNotificationFactory(
    private val service: Service,
    private val channelId: String,
) {
    fun buildNotification(
        isForegroundPinned: Boolean,
        notificationId: Int,
    ): Notification {
        ensureNotificationChannel()

        val pendingIntent =
            PendingIntent.getActivity(
                service,
                0,
                Intent(service, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("route", WatchRoutes.NAVIGATE)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val builder =
            NotificationCompat
                .Builder(service, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)

        if (isForegroundPinned) {
            builder
                .setContentTitle("App kept open")
                .setContentText("Tap to return to navigation")
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setOngoing(true)

            val ongoingActivity =
                OngoingActivity
                    .Builder(service.applicationContext, notificationId, builder)
                    .setStaticIcon(R.drawable.ic_launcher_foreground)
                    .setTouchIntent(pendingIntent)
                    .build()

            ongoingActivity.apply(service.applicationContext)
        } else {
            builder
                .setContentTitle("Location Service")
                .setContentText("Ready")
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(false)
        }

        return builder.build()
    }

    fun show(
        notificationId: Int,
        notification: Notification,
    ) {
        runCatching {
            val manager = service.getSystemService(NotificationManager::class.java)
            manager.notify(notificationId, notification)
        }
    }

    fun cancel(notificationId: Int) {
        runCatching {
            val manager = service.getSystemService(NotificationManager::class.java)
            manager.cancel(notificationId)
        }
    }

    private fun ensureNotificationChannel() {
        val manager = service.getSystemService(NotificationManager::class.java)
        val channel =
            NotificationChannel(
                channelId,
                "Location Service",
                NotificationManager.IMPORTANCE_LOW,
            )
        manager.createNotificationChannel(channel)
    }
}
