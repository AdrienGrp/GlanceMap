package com.glancemap.glancemapwearos.presentation.features.recording

import android.content.Context
import com.glancemap.glancemapwearos.core.maps.Dem3CoverageUtils
import com.glancemap.glancemapwearos.core.maps.DemSource
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.features.maps.ReliefDemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecordingElevationProvider(
    context: Context,
) {
    private val demRepositories =
        DemSource.entries.associateWith { demSource ->
            ReliefDemRepository(
                demRootDir = Dem3CoverageUtils.demRootDir(context.applicationContext, demSource),
                tag = "TraceRecordingDem-${demSource.shortLabel}",
            )
        }

    suspend fun resolveElevation(
        latitude: Double,
        longitude: Double,
        gpsAltitudeMeters: Double?,
        source: String,
        demSource: DemSource = DemSource.DEFAULT,
    ): RecordingElevationResult =
        withContext(Dispatchers.IO) {
            val sanitizedSource =
                when (source) {
                    SettingsRepository.RECORDING_ELEVATION_SOURCE_DEM,
                    SettingsRepository.RECORDING_ELEVATION_SOURCE_AUTO,
                    SettingsRepository.RECORDING_ELEVATION_SOURCE_GPS,
                    SettingsRepository.RECORDING_SOURCE_DISABLED,
                    -> source
                    else -> SettingsRepository.DEFAULT_RECORDING_ELEVATION_SOURCE
                }
            val demSample =
                if (
                    sanitizedSource != SettingsRepository.RECORDING_ELEVATION_SOURCE_GPS &&
                    sanitizedSource != SettingsRepository.RECORDING_SOURCE_DISABLED
                ) {
                    resolveDemSample(latitude, longitude, demSource)
                } else {
                    null
                }
            val demElevation = demSample?.elevationMeters
            val elevation =
                when (sanitizedSource) {
                    SettingsRepository.RECORDING_ELEVATION_SOURCE_DEM -> demElevation
                    SettingsRepository.RECORDING_ELEVATION_SOURCE_AUTO -> demElevation ?: gpsAltitudeMeters
                    SettingsRepository.RECORDING_SOURCE_DISABLED -> null
                    else -> gpsAltitudeMeters
                }
            val resolvedSource =
                when {
                    sanitizedSource == SettingsRepository.RECORDING_ELEVATION_SOURCE_DEM && demElevation != null ->
                        SettingsRepository.RECORDING_ELEVATION_SOURCE_DEM
                    sanitizedSource == SettingsRepository.RECORDING_ELEVATION_SOURCE_DEM && demElevation == null ->
                        RECORDING_ELEVATION_SOURCE_DEM_MISSING
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
                demAttempted =
                    sanitizedSource != SettingsRepository.RECORDING_ELEVATION_SOURCE_GPS &&
                        sanitizedSource != SettingsRepository.RECORDING_SOURCE_DISABLED,
                demHit = demElevation != null,
                gpsUsed = elevation != null && resolvedSource == SettingsRepository.RECORDING_ELEVATION_SOURCE_GPS,
                demTileId = demSample?.tileId,
                demAxisLen = demSample?.axisLen,
                demResolutionLabel = demSample?.resolutionLabel,
            )
        }

    private fun resolveDemSample(
        latitude: Double,
        longitude: Double,
        demSource: DemSource,
    ) = demSource
        .readFallbackOrder()
        .firstNotNullOfOrNull { candidate ->
            demRepositories[candidate]?.elevationSampleAt(latitude, longitude)
        }?.takeIf {
            it.elevationMeters.isFinite() &&
                it.elevationMeters > DEM_VOID_ELEVATION_METERS
        }
}

data class RecordingElevationResult(
    val elevationMeters: Double?,
    val resolvedSource: String,
    val demAttempted: Boolean,
    val demHit: Boolean,
    val gpsUsed: Boolean,
    val demTileId: String?,
    val demAxisLen: Int?,
    val demResolutionLabel: String?,
)

private const val DEM_VOID_ELEVATION_METERS = -10_000.0
internal const val RECORDING_ELEVATION_SOURCE_DEM_MISSING = "DEM_MISSING"
