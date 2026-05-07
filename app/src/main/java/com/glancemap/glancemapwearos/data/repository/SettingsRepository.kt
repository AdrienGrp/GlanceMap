package com.glancemap.glancemapwearos.data.repository

import com.glancemap.glancemapwearos.core.gpx.GpxElevationFilterDefaults
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    companion object {
        const val TIME_FORMAT_24_HOUR = "24_HOUR"
        const val TIME_FORMAT_12_HOUR = "12_HOUR"

        const val DEFAULT_GPS_INTERVAL_MS = 3000L
        const val DEFAULT_AMBIENT_GPS_INTERVAL_MS = 60_000L
        const val MIN_AMBIENT_GPS_INTERVAL_MS = 1_000L
        const val MAX_AMBIENT_GPS_INTERVAL_MS = 120_000L

        const val ZOOM_BUTTONS_BOTH = "BOTH"
        const val ZOOM_BUTTONS_HIDE_BOTH = "HIDE_BOTH"
        const val ZOOM_BUTTONS_HIDE_PLUS = "HIDE_PLUS"

        const val NORTH_REFERENCE_TRUE = "TRUE"
        const val NORTH_REFERENCE_MAGNETIC = "MAGNETIC"
        const val COMPASS_SETTINGS_MODE_AUTOMATIC = "AUTOMATIC"
        const val COMPASS_SETTINGS_MODE_ADVANCED = "ADVANCED"
        const val COMPASS_PROVIDER_GOOGLE_FUSED = "GOOGLE_FUSED"
        const val COMPASS_PROVIDER_SENSOR_MANAGER = "SENSOR_MANAGER"
        const val COMPASS_HEADING_SOURCE_AUTO = "AUTO"
        const val COMPASS_HEADING_SOURCE_TYPE_HEADING = "TYPE_HEADING"
        const val COMPASS_HEADING_SOURCE_ROTATION_VECTOR = "ROTATION_VECTOR"
        const val COMPASS_HEADING_SOURCE_MAGNETOMETER = "MAGNETOMETER"

        const val MARKER_STYLE_DOT = "DOT"
        const val MARKER_STYLE_TRIANGLE = "TRIANGLE"

        const val DEFAULT_GPX_FLAT_SPEED_MPS = 3.5f / 3.6f
        const val MAX_GPX_FLAT_SPEED_MPS = 20f / 3.6f
        const val DEFAULT_GPX_ADVANCED_ETA_ENABLED = false
        const val DEFAULT_GPX_UPHILL_VERTICAL_METERS_PER_HOUR = 600f
        const val DEFAULT_GPX_DOWNHILL_VERTICAL_METERS_PER_HOUR = 900f
        const val MIN_GPX_VERTICAL_METERS_PER_HOUR = 100f
        const val MAX_GPX_UPHILL_VERTICAL_METERS_PER_HOUR = 2_000f
        const val MAX_GPX_DOWNHILL_VERTICAL_METERS_PER_HOUR = 3_000f
        const val DEFAULT_GPX_ELEVATION_SMOOTHING_DISTANCE_METERS =
            GpxElevationFilterDefaults.DEFAULT_SMOOTHING_DISTANCE_METERS
        const val DEFAULT_GPX_ELEVATION_NEUTRAL_DIFF_THRESHOLD_METERS =
            GpxElevationFilterDefaults.DEFAULT_NEUTRAL_DIFF_THRESHOLD_METERS
        const val DEFAULT_GPX_ELEVATION_TREND_ACTIVATION_THRESHOLD_METERS =
            GpxElevationFilterDefaults.DEFAULT_TREND_ACTIVATION_THRESHOLD_METERS
        const val DEFAULT_GPX_ELEVATION_AUTO_ADJUST_PER_GPX = true
        const val DEFAULT_GPX_TRACK_OPACITY_PERCENT = 70
        const val MIN_GPX_TRACK_OPACITY_PERCENT = 10
        const val MAX_GPX_TRACK_OPACITY_PERCENT = 100

        const val POI_ICON_SIZE_SMALL_PX = 18
        const val POI_ICON_SIZE_DEFAULT_PX = 22
        const val POI_ICON_SIZE_MEDIUM_PX = 24
        const val POI_ICON_SIZE_LARGE_PX = 26

        const val POI_POPUP_TIMEOUT_DEFAULT_SECONDS = 5
        const val POI_POPUP_TIMEOUT_MIN_SECONDS = 1
        const val POI_POPUP_TIMEOUT_MAX_SECONDS = 20

        const val DEFAULT_MAP_ZOOM_DEFAULT_SCALE_METERS = 200
        const val DEFAULT_MAP_ZOOM_MIN_SCALE_METERS = 50_000
        const val DEFAULT_MAP_ZOOM_MAX_SCALE_METERS = 20
    }

    val gpsInterval: Flow<Long>

    suspend fun setGpsInterval(interval: Long)

    val ambientGpsInterval: Flow<Long>

    suspend fun setAmbientGpsInterval(interval: Long)

    val watchGpsOnly: Flow<Boolean>

    suspend fun setWatchGpsOnly(isOnly: Boolean)

    val gpsInAmbientMode: Flow<Boolean>

    suspend fun setGpsInAmbientMode(enabled: Boolean)

    val gpsDebugTelemetry: Flow<Boolean>

    suspend fun setGpsDebugTelemetry(enabled: Boolean)

    val gpsPassiveLocationExperiment: Flow<Boolean>

    suspend fun setGpsPassiveLocationExperiment(enabled: Boolean)

    val gpsDebugTelemetryPopupEnabled: Flow<Boolean>

    suspend fun setGpsDebugTelemetryPopupEnabled(enabled: Boolean)

    val promptForCalibration: Flow<Boolean>

    suspend fun setPromptForCalibration(enabled: Boolean)

    val showTimeInNavigate: Flow<Boolean>

    suspend fun setShowTimeInNavigate(enabled: Boolean)

    val navigateTimeFormat: Flow<String>

    suspend fun setNavigateTimeFormat(format: String)

    val mapZoomButtonsMode: Flow<String>

    suspend fun setMapZoomButtonsMode(mode: String)

    val gpsAccuracyCircleEnabled: Flow<Boolean>

    suspend fun setGpsAccuracyCircleEnabled(enabled: Boolean)

    val mapZoomDefault: Flow<Int>

    suspend fun setMapZoomDefault(zoom: Int)

    val mapZoomMin: Flow<Int>

    suspend fun setMapZoomMin(zoom: Int)

    val mapZoomMax: Flow<Int>

    suspend fun setMapZoomMax(zoom: Int)

    val mapZoomDefaultScaleMeters: Flow<Int>

    suspend fun setMapZoomDefaultScaleMeters(scaleMeters: Int)

    val mapZoomMinScaleMeters: Flow<Int>

    suspend fun setMapZoomMinScaleMeters(scaleMeters: Int)

    val mapZoomMaxScaleMeters: Flow<Int>

    suspend fun setMapZoomMaxScaleMeters(scaleMeters: Int)

    val northIndicatorMode: Flow<String>

    suspend fun setNorthIndicatorMode(mode: String)

    val northReferenceMode: Flow<String>

    suspend fun setNorthReferenceMode(mode: String)

    val compassSettingsMode: Flow<String>

    suspend fun setCompassSettingsMode(mode: String)

    val compassProviderMode: Flow<String>

    suspend fun setCompassProviderMode(mode: String)

    val compassHeadingSourceMode: Flow<String>

    suspend fun setCompassHeadingSourceMode(mode: String)

    val compassConeAccuracyColorsEnabled: Flow<Boolean>

    suspend fun setCompassConeAccuracyColorsEnabled(enabled: Boolean)

    val navigationMarkerStyleInitial: String
    val navigationMarkerStyle: Flow<String>

    suspend fun setNavigationMarkerStyle(style: String)

    val mapDoubleTapAction: Flow<String>

    suspend fun setMapDoubleTapAction(action: String)

    val liveElevation: Flow<Boolean>

    suspend fun setLiveElevation(enabled: Boolean)

    val liveDistance: Flow<Boolean>

    suspend fun setLiveDistance(enabled: Boolean)

    val offlineMode: Flow<Boolean>

    suspend fun setOfflineMode(enabled: Boolean)

    val crownZoomEnabled: Flow<Boolean>

    suspend fun setCrownZoomEnabled(enabled: Boolean)

    val crownZoomInverted: Flow<Boolean>

    suspend fun setCrownZoomInverted(inverted: Boolean)

    val gpxTrackColor: Flow<Int>

    suspend fun setGpxTrackColor(color: Int)

    val gpxTrackWidth: Flow<Float>

    suspend fun setGpxTrackWidth(width: Float)

    val gpxTrackOpacityPercent: Flow<Int>

    suspend fun setGpxTrackOpacityPercent(opacityPercent: Int)

    // Auto-recenter settings
    val autoRecenterEnabled: Flow<Boolean>

    suspend fun setAutoRecenterEnabled(enabled: Boolean)

    val autoRecenterDelay: Flow<Int>

    suspend fun setAutoRecenterDelay(delay: Int)

    val selectedMapPath: Flow<String?>

    suspend fun setSelectedMapPath(path: String?)

    // Navigation Session Settings
    val keepAppOpen: Flow<Boolean>

    suspend fun setKeepAppOpen(enabled: Boolean)

    val keepAppOpenTipShown: Flow<Boolean>

    suspend fun setKeepAppOpenTipShown(shown: Boolean)

    val compassMode: Flow<Boolean>

    suspend fun setCompassMode(isCompassMode: Boolean)

    val isGpxInspectionEnabled: Flow<Boolean>

    suspend fun setGpxInspectionEnabled(enabled: Boolean)

    val gpxFlatSpeedMps: Flow<Float>

    suspend fun setGpxFlatSpeedMps(speedMps: Float)

    val gpxAdvancedEtaEnabled: Flow<Boolean>

    suspend fun setGpxAdvancedEtaEnabled(enabled: Boolean)

    val gpxUphillVerticalMetersPerHour: Flow<Float>

    suspend fun setGpxUphillVerticalMetersPerHour(metersPerHour: Float)

    val gpxDownhillVerticalMetersPerHour: Flow<Float>

    suspend fun setGpxDownhillVerticalMetersPerHour(metersPerHour: Float)

    val gpxElevationSmoothingDistanceMeters: Flow<Float>

    suspend fun setGpxElevationSmoothingDistanceMeters(distanceMeters: Float)

    val gpxElevationNeutralDiffThresholdMeters: Flow<Float>

    suspend fun setGpxElevationNeutralDiffThresholdMeters(thresholdMeters: Float)

    val gpxElevationTrendActivationThresholdMeters: Flow<Float>

    suspend fun setGpxElevationTrendActivationThresholdMeters(thresholdMeters: Float)

    val gpxElevationAutoAdjustPerGpx: Flow<Boolean>

    suspend fun setGpxElevationAutoAdjustPerGpx(enabled: Boolean)

    val gpxLongPressTipShown: Flow<Boolean>

    suspend fun setGpxLongPressTipShown(shown: Boolean)

    val isMetric: Flow<Boolean>

    suspend fun setMetric(isMetric: Boolean)

    val poiIconSizePx: Flow<Int>

    suspend fun setPoiIconSizePx(sizePx: Int)

    val poiTapToCenterEnabled: Flow<Boolean>

    suspend fun setPoiTapToCenterEnabled(enabled: Boolean)

    val poiPopupTimeoutSeconds: Flow<Int>

    suspend fun setPoiPopupTimeoutSeconds(seconds: Int)

    val poiPopupManualCloseOnly: Flow<Boolean>

    suspend fun setPoiPopupManualCloseOnly(enabled: Boolean)

    suspend fun resetToDefaults()
}
