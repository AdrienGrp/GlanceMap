package com.glancemap.glancemapwearos.presentation.features.maps

import android.os.Looper
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.Layers
import java.util.WeakHashMap

/**
 * Serializes Mapsforge layer mutations and defers them while the map is processing touch input.
 *
 * Mapsforge can process gesture callbacks while app code is adding/removing layers from Compose
 * effects. Keeping those mutations on the main thread and flushing them after touch idle avoids
 * lock inversion and list-index races inside the map view.
 */
object MapLayerMutationCoordinator {
    private const val GESTURE_IDLE_FLUSH_DELAY_MS = 120L

    private val states = WeakHashMap<MapView, MutationState>()

    fun setGestureActive(
        mapView: MapView,
        active: Boolean,
    ) {
        runOnMapThread(mapView) {
            val state = stateFor(mapView)
            if (active) {
                state.gestureActive = true
                state.flushRunnable?.let(mapView::removeCallbacks)
                state.flushRunnable = null
            } else if (state.gestureActive) {
                state.gestureActive = false
                scheduleFlushAfterIdle(mapView, state)
            }
        }
    }

    fun mutateLayers(
        mapView: MapView,
        mutation: (Layers) -> Unit,
    ) {
        runOnMapThread(mapView) {
            val state = stateFor(mapView)
            if (state.gestureActive) {
                state.pending += { mutation(mapView.layerManager.layers) }
            } else {
                mutation(mapView.layerManager.layers)
            }
        }
    }

    fun flushNow(mapView: MapView) {
        runOnMapThread(mapView) {
            val state = stateFor(mapView)
            state.flushRunnable?.let(mapView::removeCallbacks)
            state.flushRunnable = null
            flushPending(mapView, state)
        }
    }

    private fun runOnMapThread(
        mapView: MapView,
        block: () -> Unit,
    ) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mapView.post(block)
        }
    }

    private fun scheduleFlushAfterIdle(
        mapView: MapView,
        state: MutationState,
    ) {
        state.flushRunnable?.let(mapView::removeCallbacks)
        val flush =
            Runnable {
                val currentState = stateFor(mapView)
                currentState.flushRunnable = null
                if (currentState.gestureActive) return@Runnable
                flushPending(mapView, currentState)
            }
        state.flushRunnable = flush
        mapView.postDelayed(flush, GESTURE_IDLE_FLUSH_DELAY_MS)
    }

    private fun flushPending(
        mapView: MapView,
        state: MutationState,
    ) {
        var executedMutation = false
        while (!state.gestureActive && state.pending.isNotEmpty()) {
            state.pending.removeFirst().invoke()
            executedMutation = true
        }
        if (executedMutation) {
            redrawLayersSafely(mapView)
        }
        if (state.pending.isEmpty()) {
            states.remove(mapView)
        }
    }

    private fun redrawLayersSafely(mapView: MapView) {
        runCatching {
            mapView.layerManager.redrawLayers()
        }.onFailure {
            mapView.postInvalidate()
        }
    }

    private fun stateFor(mapView: MapView): MutationState =
        states.getOrPut(mapView) { MutationState() }

    private class MutationState {
        var gestureActive: Boolean = false
        var flushRunnable: Runnable? = null
        val pending: ArrayDeque<() -> Unit> = ArrayDeque()
    }
}

fun MapView.mutateLayers(mutation: (Layers) -> Unit) {
    MapLayerMutationCoordinator.mutateLayers(this, mutation)
}
