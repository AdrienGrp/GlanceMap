package com.glancemap.glancemapwearos.core.service.transfer.runtime
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager

internal class TransferLockManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val powerManager by lazy { appContext.getSystemService(Context.POWER_SERVICE) as PowerManager }
    private val wifiManager by lazy { appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }

    fun acquireWakeLock(
        tag: String,
        timeoutMs: Long,
    ): PowerManager.WakeLock =
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).apply {
            setReferenceCounted(false)
            acquire(timeoutMs)
        }

    fun releaseWakeLock(wakeLock: PowerManager.WakeLock) {
        if (wakeLock.isHeld) wakeLock.release()
    }

    fun acquireWifiLock(tag: String): WifiManager.WifiLock =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, tag)
        } else {
            @Suppress("DEPRECATION")
            wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, tag)
        }.apply {
            setReferenceCounted(false)
            acquire()
        }

    fun releaseWifiLock(wifiLock: WifiManager.WifiLock) {
        if (wifiLock.isHeld) wifiLock.release()
    }
}
