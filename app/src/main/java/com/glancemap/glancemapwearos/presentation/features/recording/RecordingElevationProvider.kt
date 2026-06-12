package com.glancemap.glancemapwearos.presentation.features.recording

import android.content.Context
import com.glancemap.glancemapwearos.core.maps.Dem3CoverageUtils
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.features.maps.ReliefDemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecordingElevationProvider(context: Context) {
    private val demRepository =
        ReliefDemRepository(
            demRootDirs = Dem3CoverageUtils.demRootDirs(context.applicationContext),
            tag = "TraceRecordingDem",
        )

    suspend fun resolveElevation(
        latitude: Double,
        longitude: Double,
        gpsAltitudeMeters: Double?,
        source: String,
    ): RecordingElevationResult =
        withContext(Dispatchers.IO) {
            val sanitizedSource =
                when (source) {
                    SettingsRepository.RECORDING_ELEVATION_SOURCE_DEM,
                    SettingsRepository.RECORDING_ELEVATION_SOURCE_AUTO,
                    SettingsRepository.RECORDING_ELEVATION_SOURCE_GPS,
                    -> source
                    else -> SettingsRepository.DEFAULT_RECORDING_ELEVATION_SOURCE
                }
            val demElevation =
                if (sanitizedSource != SettingsRepository.RECORDING_ELEVATION_SOURCE_GPS) {
                    demRepository
                        .elevationAt(latitude, longitude)
                        ?.takeIf { it.isFinite() && it > DEM_VOID_ELEVATION_METERS }
                } else {
                    null
                }
            val elevation =
                when (sanitizedSource) {
                    SettingsRepository.RECORDING_ELEVATION_SOURCE_DEM -> demElevation
                    SettingsRepository.RECORDING_ELEVATION_SOURCE_AUTO -> demElevation ?: gpsAltitudeMeters
                    else -> gpsAltitudeMeters
                }
            val resolvedSource =
                when {
                    sanitizedSource == SettingsRepository.RECORDING_ELEVATION_SOURCE_DEM && demElevation != null ->
                        SettingsRepository.RECORDING_ELEVATION_SOURCE_DEM
                    sanitizedSource == SettingsRepository.RECORDING_ELEVATION_SOURCE_AUTO && demElevation != null ->
                        SettingsRepository.RECORDING_ELEVATION_SOURCE_DEM
                    sanitizedSource == SettingsRepository.RECORDING_ELEVATION_SOURCE_AUTO && gpsAltitudeMeters != null ->
                        SettingsRepository.RECORDING_ELEVATION_SOURCE_GPS
                    sanitizedSource == SettingsRepository.RECORDING_ELEVATION_SOURCE_GPS && gpsAltitudeMeters != null ->
                        SettingsRepository.RECORDING_ELEVATION_SOURCE_GPS
                    else -> sanitizedSource
                }

            RecordingElevationResult(
                elevationMeters = elevation,
                resolvedSource = resolvedSource,
                demAttempted = sanitizedSource != SettingsRepository.RECORDING_ELEVATION_SOURCE_GPS,
                demHit = demElevation != null,
                gpsUsed = elevation != null && resolvedSource == SettingsRepository.RECORDING_ELEVATION_SOURCE_GPS,
            )
        }
}

data class RecordingElevationResult(
    val elevationMeters: Double?,
    val resolvedSource: String,
    val demAttempted: Boolean,
    val demHit: Boolean,
    val gpsUsed: Boolean,
)

private const val DEM_VOID_ELEVATION_METERS = -10_000.0
