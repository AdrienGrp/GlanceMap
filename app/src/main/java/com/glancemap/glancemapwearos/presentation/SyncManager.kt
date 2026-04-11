package com.glancemap.glancemapwearos.presentation

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SyncManager(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SyncManager"
        private const val SYNC_SETTLE_DELAY_MS = 1_500L
    }

    private enum class SyncKind {
        GPX,
        MAP,
        POI
    }

    private val _gpxSyncRequest = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val gpxSyncRequest = _gpxSyncRequest.asSharedFlow()

    private val _mapSyncRequest = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val mapSyncRequest = _mapSyncRequest.asSharedFlow()

    private val _poiSyncRequest = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val poiSyncRequest = _poiSyncRequest.asSharedFlow()

    private val lock = Any()
    private val pendingKinds = linkedSetOf<SyncKind>()
    private var activeTransferCount = 0
    private var flushJob: Job? = null

    fun requestGpxSync() {
        requestSync(SyncKind.GPX)
    }

    fun requestMapSync() {
        requestSync(SyncKind.MAP)
    }

    fun requestPoiSync() {
        requestSync(SyncKind.POI)
    }

    fun onTransferStarted() {
        synchronized(lock) {
            activeTransferCount++
            flushJob?.cancel()
            flushJob = null
            Log.d(TAG, "Transfer started. activeTransfers=$activeTransferCount")
        }
    }

    fun onTransferFinished() {
        val shouldSchedule = synchronized(lock) {
            if (activeTransferCount > 0) {
                activeTransferCount--
            }
            Log.d(
                TAG,
                "Transfer finished. activeTransfers=$activeTransferCount pending=$pendingKinds"
            )
            activeTransferCount == 0 && pendingKinds.isNotEmpty()
        }
        if (shouldSchedule) {
            scheduleFlush()
        }
    }

    private fun requestSync(kind: SyncKind) {
        val shouldSchedule = synchronized(lock) {
            pendingKinds += kind
            Log.d(TAG, "Queued sync kind=$kind activeTransfers=$activeTransferCount pending=$pendingKinds")
            activeTransferCount == 0
        }
        if (shouldSchedule) {
            scheduleFlush()
        }
    }

    private fun scheduleFlush() {
        synchronized(lock) {
            flushJob?.cancel()
            flushJob = scope.launch {
                delay(SYNC_SETTLE_DELAY_MS)
                flushPending()
            }
        }
    }

    private fun flushPending() {
        val kindsToFlush = synchronized(lock) {
            if (activeTransferCount > 0 || pendingKinds.isEmpty()) {
                flushJob = null
                return
            }
            val snapshot = pendingKinds.toSet()
            pendingKinds.clear()
            flushJob = null
            snapshot
        }

        Log.d(TAG, "Flushing sync requests kinds=$kindsToFlush")
        if (SyncKind.GPX in kindsToFlush) {
            _gpxSyncRequest.tryEmit(Unit)
        }
        if (SyncKind.MAP in kindsToFlush) {
            _mapSyncRequest.tryEmit(Unit)
        }
        if (SyncKind.POI in kindsToFlush) {
            _poiSyncRequest.tryEmit(Unit)
        }
    }
}
