package com.glancemap.glancemapwearos.presentation.features.recording

import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import org.mapsforge.core.model.LatLong

data class RecordedTracePoint(
    val latLong: LatLong,
    val elevationMeters: Double?,
    val timeMillis: Long,
    val accuracyMeters: Float?,
    val speedMps: Float?,
    val elevationSource: String? = null,
    val heartRateBpm: Int? = null,
    val stepCount: Int? = null,
    val cadenceSpm: Int? = null,
    val powerWatts: Int? = null,
    val barometricPressureHpa: Double? = null,
)

data class TraceRecordingUiState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val saving: Boolean = false,
    val points: List<RecordedTracePoint> = emptyList(),
    val latestLivePoint: RecordedTracePoint? = null,
    val distanceMeters: Double = 0.0,
    val startedAtMillis: Long? = null,
    val pausedAtMillis: Long? = null,
    val accumulatedPausedMillis: Long = 0L,
    val gpsActiveDurationMillis: Long = 0L,
    val recordingGapCount: Int = 0,
    val recordingMaxGapMillis: Long = 0L,
    val heartRateBpm: Int? = null,
    val heartRateFromBluetooth: Boolean = false,
    val externalSpeedMps: Float? = null,
    val externalRawDistanceUnits: Long? = null,
    val externalDistanceMeters: Double? = null,
    val externalIntegratedDistanceMeters: Double? = null,
    val externalPowerWatts: Int? = null,
    val externalPowerFromBluetooth: Boolean = false,
    val externalBatteryLevelPercent: Int? = null,
    val cadenceSource: String = SettingsRepository.DEFAULT_RECORDING_CADENCE_SOURCE,
    val speedSource: String = SettingsRepository.DEFAULT_RECORDING_SPEED_SOURCE,
    val distanceSource: String = SettingsRepository.DEFAULT_RECORDING_DISTANCE_SOURCE,
    val stepsSource: String = SettingsRepository.DEFAULT_RECORDING_STEPS_SOURCE,
    val stepCount: Int? = null,
    val stepCountFromBluetooth: Boolean = false,
    val cadenceSpm: Int? = null,
    val cadenceFromBluetooth: Boolean = false,
    val barometricPressureHpa: Double? = null,
    val message: String? = null,
) {
    val pointCount: Int get() = points.size
}
