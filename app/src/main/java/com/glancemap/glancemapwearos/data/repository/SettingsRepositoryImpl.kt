package com.glancemap.glancemapwearos.data.repository

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.glancemap.glancemapwearos.R
import com.glancemap.glancemapwearos.core.gpx.GpxElevationFilterDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepositoryImpl private constructor(
    private val context: Context,
) : SettingsRepository {
    private val markerStyleCachePrefs by lazy {
        context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val mapsHelpPrefs by lazy {
        context.getSharedPreferences(MAPS_HELP_PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val debugHelpPrefs by lazy {
        context.getSharedPreferences(DEBUG_HELP_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private object PrefKeys {
        val GPS_INTERVAL = longPreferencesKey("gps_interval")
        val AMBIENT_GPS_INTERVAL = longPreferencesKey("ambient_gps_interval")
        val WATCH_GPS_ONLY = booleanPreferencesKey("watch_gps_only")
        val GPS_IN_AMBIENT_MODE = booleanPreferencesKey("gps_in_ambient_mode")
        val GPS_DEBUG_TELEMETRY = booleanPreferencesKey("gps_debug_telemetry")
        val PROMPT_FOR_CALIBRATION = booleanPreferencesKey("prompt_for_calibration")
        val SHOW_TIME_IN_NAVIGATE = booleanPreferencesKey("show_time_in_navigate")
        val NAVIGATE_TIME_FORMAT = stringPreferencesKey("navigate_time_format")
        val MAP_ZOOM_BUTTONS_MODE = stringPreferencesKey("map_zoom_buttons_mode")
        val GPS_ACCURACY_CIRCLE_ENABLED = booleanPreferencesKey("gps_accuracy_circle_enabled")
        val MAP_ZOOM_DEFAULT = intPreferencesKey("map_zoom_default")
        val MAP_ZOOM_MIN = intPreferencesKey("map_zoom_min")
        val MAP_ZOOM_MAX = intPreferencesKey("map_zoom_max")
        val NORTH_INDICATOR_MODE = stringPreferencesKey("north_indicator_mode")
        val NORTH_REFERENCE_MODE = stringPreferencesKey("north_reference_mode")
        val COMPASS_SETTINGS_MODE = stringPreferencesKey("compass_settings_mode")
        val COMPASS_PROVIDER_MODE = stringPreferencesKey("compass_provider_mode")
        val COMPASS_HEADING_SOURCE_MODE = stringPreferencesKey("compass_heading_source_mode")
        val COMPASS_CONE_ACCURACY_COLORS_ENABLED =
            booleanPreferencesKey("compass_cone_accuracy_colors_enabled")
        val NAVIGATION_MARKER_STYLE = stringPreferencesKey("navigation_marker_style")
        val MAP_DOUBLE_TAP_ACTION = stringPreferencesKey("map_double_tap_action")
        val LIVE_ELEVATION = booleanPreferencesKey("live_elevation")
        val LIVE_DISTANCE = booleanPreferencesKey("live_distance")
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
        val CROWN_ZOOM_ENABLED = booleanPreferencesKey("crown_zoom_enabled")
        val CROWN_ZOOM_INVERTED = booleanPreferencesKey("crown_zoom_inverted")
        val GPX_TRACK_COLOR = intPreferencesKey("gpx_track_color")
        val GPX_TRACK_WIDTH = floatPreferencesKey("gpx_track_width")
        val GPX_TRACK_OPACITY_PERCENT = intPreferencesKey("gpx_track_opacity_percent")
        val AUTO_RECENTER_ENABLED = booleanPreferencesKey("auto_recenter_enabled")
        val AUTO_RECENTER_DELAY = intPreferencesKey("auto_recenter_delay")
        val SELECTED_MAP_PATH = stringPreferencesKey("selected_map_path")
        val KEEP_APP_OPEN = booleanPreferencesKey("keep_app_open")
        val KEEP_APP_OPEN_TIP_SHOWN = booleanPreferencesKey("keep_app_open_tip_shown")
        val COMPASS_MODE = booleanPreferencesKey("compass_mode")
        val GPX_INSPECTION_ENABLED = booleanPreferencesKey("gpx_inspection_enabled")
        val GPX_FLAT_SPEED_MPS = floatPreferencesKey("gpx_flat_speed_mps")
        val GPX_ADVANCED_ETA_ENABLED = booleanPreferencesKey("gpx_advanced_eta_enabled")
        val GPX_UPHILL_VERTICAL_METERS_PER_HOUR =
            floatPreferencesKey("gpx_uphill_vertical_meters_per_hour")
        val GPX_DOWNHILL_VERTICAL_METERS_PER_HOUR =
            floatPreferencesKey("gpx_downhill_vertical_meters_per_hour")
        val GPX_ELEVATION_SMOOTHING_DISTANCE_METERS =
            floatPreferencesKey("gpx_elevation_smoothing_distance_meters")
        val GPX_ELEVATION_NEUTRAL_DIFF_THRESHOLD_METERS =
            floatPreferencesKey("gpx_elevation_neutral_diff_threshold_meters")
        val GPX_ELEVATION_TREND_ACTIVATION_THRESHOLD_METERS =
            floatPreferencesKey("gpx_elevation_trend_activation_threshold_meters")
        val GPX_ELEVATION_AUTO_ADJUST_PER_GPX =
            booleanPreferencesKey("gpx_elevation_auto_adjust_per_gpx")
        val GPX_LONG_PRESS_TIP_SHOWN = booleanPreferencesKey("gpx_long_press_tip_shown")
        val IS_METRIC = booleanPreferencesKey("is_metric")
        val POI_ICON_SIZE_PX = intPreferencesKey("poi_icon_size_px")
        val POI_TAP_TO_CENTER_ENABLED = booleanPreferencesKey("poi_tap_to_center_enabled")
        val POI_POPUP_TIMEOUT_SECONDS = intPreferencesKey("poi_popup_timeout_seconds")
        val POI_POPUP_MANUAL_CLOSE_ONLY = booleanPreferencesKey("poi_popup_manual_close_only")
    }

    override val gpsInterval: Flow<Long> =
        context.dataStore.data.map {
            it[PrefKeys.GPS_INTERVAL] ?: SettingsRepository.DEFAULT_GPS_INTERVAL_MS
        }

    override suspend fun setGpsInterval(interval: Long) {
        context.dataStore.edit { it[PrefKeys.GPS_INTERVAL] = interval }
    }

    override val ambientGpsInterval: Flow<Long> =
        context.dataStore.data.map {
            (it[PrefKeys.AMBIENT_GPS_INTERVAL] ?: SettingsRepository.DEFAULT_AMBIENT_GPS_INTERVAL_MS)
                .coerceIn(
                    SettingsRepository.MIN_AMBIENT_GPS_INTERVAL_MS,
                    SettingsRepository.MAX_AMBIENT_GPS_INTERVAL_MS,
                )
        }

    override suspend fun setAmbientGpsInterval(interval: Long) {
        val safeInterval =
            interval.coerceIn(
                SettingsRepository.MIN_AMBIENT_GPS_INTERVAL_MS,
                SettingsRepository.MAX_AMBIENT_GPS_INTERVAL_MS,
            )
        context.dataStore.edit { it[PrefKeys.AMBIENT_GPS_INTERVAL] = safeInterval }
    }

    override val watchGpsOnly: Flow<Boolean> = context.dataStore.data.map { it[PrefKeys.WATCH_GPS_ONLY] ?: false }

    override suspend fun setWatchGpsOnly(isOnly: Boolean) {
        context.dataStore.edit { it[PrefKeys.WATCH_GPS_ONLY] = isOnly }
    }

    override val gpsInAmbientMode: Flow<Boolean> = context.dataStore.data.map { it[PrefKeys.GPS_IN_AMBIENT_MODE] ?: false }

    override suspend fun setGpsInAmbientMode(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.GPS_IN_AMBIENT_MODE] = enabled }
    }

    override val gpsDebugTelemetry: Flow<Boolean> = context.dataStore.data.map { it[PrefKeys.GPS_DEBUG_TELEMETRY] ?: false }

    override suspend fun setGpsDebugTelemetry(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.GPS_DEBUG_TELEMETRY] = enabled }
    }

    override val promptForCalibration: Flow<Boolean> = context.dataStore.data.map { it[PrefKeys.PROMPT_FOR_CALIBRATION] ?: false }

    override suspend fun setPromptForCalibration(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.PROMPT_FOR_CALIBRATION] = enabled }
    }

    override val showTimeInNavigate: Flow<Boolean> = context.dataStore.data.map { it[PrefKeys.SHOW_TIME_IN_NAVIGATE] ?: true }

    override suspend fun setShowTimeInNavigate(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.SHOW_TIME_IN_NAVIGATE] = enabled }
    }

    override val navigateTimeFormat: Flow<String> =
        context.dataStore.data.map {
            val stored = it[PrefKeys.NAVIGATE_TIME_FORMAT]
            when {
                stored == LEGACY_TIME_FORMAT_SYSTEM -> SettingsRepository.TIME_FORMAT_24_HOUR
                stored != null && stored in allowedTimeFormats -> stored
                else -> SettingsRepository.TIME_FORMAT_24_HOUR
            }
        }

    override suspend fun setNavigateTimeFormat(format: String) {
        context.dataStore.edit {
            it[PrefKeys.NAVIGATE_TIME_FORMAT] =
                if (format in allowedTimeFormats) format else SettingsRepository.TIME_FORMAT_24_HOUR
        }
    }

    override val mapZoomButtonsMode: Flow<String> =
        context.dataStore.data.map {
            val stored = it[PrefKeys.MAP_ZOOM_BUTTONS_MODE]
            when {
                stored == LEGACY_ZOOM_BUTTONS_HIDE_MINUS -> SettingsRepository.ZOOM_BUTTONS_HIDE_PLUS
                stored != null && stored in allowedZoomButtonModes -> stored
                else -> SettingsRepository.ZOOM_BUTTONS_BOTH
            }
        }

    override suspend fun setMapZoomButtonsMode(mode: String) {
        context.dataStore.edit {
            it[PrefKeys.MAP_ZOOM_BUTTONS_MODE] =
                if (mode in allowedZoomButtonModes) mode else SettingsRepository.ZOOM_BUTTONS_BOTH
        }
    }

    override val gpsAccuracyCircleEnabled: Flow<Boolean> =
        context.dataStore.data.map {
            it[PrefKeys.GPS_ACCURACY_CIRCLE_ENABLED] ?: false
        }

    override suspend fun setGpsAccuracyCircleEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.GPS_ACCURACY_CIRCLE_ENABLED] = enabled }
    }

    override val mapZoomDefault: Flow<Int> = context.dataStore.data.map { it[PrefKeys.MAP_ZOOM_DEFAULT] ?: 16 }

    override suspend fun setMapZoomDefault(zoom: Int) {
        context.dataStore.edit { it[PrefKeys.MAP_ZOOM_DEFAULT] = zoom }
    }

    override val mapZoomMin: Flow<Int> = context.dataStore.data.map { it[PrefKeys.MAP_ZOOM_MIN] ?: 8 }

    override suspend fun setMapZoomMin(zoom: Int) {
        context.dataStore.edit { it[PrefKeys.MAP_ZOOM_MIN] = zoom }
    }

    override val mapZoomMax: Flow<Int> = context.dataStore.data.map { it[PrefKeys.MAP_ZOOM_MAX] ?: 20 }

    override suspend fun setMapZoomMax(zoom: Int) {
        context.dataStore.edit { it[PrefKeys.MAP_ZOOM_MAX] = zoom }
    }

    override val northIndicatorMode: Flow<String> = context.dataStore.data.map { it[PrefKeys.NORTH_INDICATOR_MODE] ?: "ALWAYS" }

    override suspend fun setNorthIndicatorMode(mode: String) {
        context.dataStore.edit { it[PrefKeys.NORTH_INDICATOR_MODE] = mode }
    }

    override val northReferenceMode: Flow<String> =
        context.dataStore.data.map {
            val stored = it[PrefKeys.NORTH_REFERENCE_MODE]
            if (stored != null && stored in allowedNorthReferenceModes) {
                stored
            } else {
                SettingsRepository.NORTH_REFERENCE_TRUE
            }
        }

    override suspend fun setNorthReferenceMode(mode: String) {
        context.dataStore.edit {
            it[PrefKeys.NORTH_REFERENCE_MODE] =
                if (mode in allowedNorthReferenceModes) mode else SettingsRepository.NORTH_REFERENCE_TRUE
        }
    }

    override val compassSettingsMode: Flow<String> =
        context.dataStore.data.map {
            val stored = it[PrefKeys.COMPASS_SETTINGS_MODE]
            if (stored != null && stored in allowedCompassSettingsModes) {
                stored
            } else {
                SettingsRepository.COMPASS_SETTINGS_MODE_AUTOMATIC
            }
        }

    override suspend fun setCompassSettingsMode(mode: String) {
        context.dataStore.edit {
            it[PrefKeys.COMPASS_SETTINGS_MODE] =
                if (mode in allowedCompassSettingsModes) {
                    mode
                } else {
                    SettingsRepository.COMPASS_SETTINGS_MODE_AUTOMATIC
                }
        }
    }

    override val compassProviderMode: Flow<String> =
        context.dataStore.data.map {
            val stored = it[PrefKeys.COMPASS_PROVIDER_MODE]
            if (stored != null && stored in allowedCompassProviderModes) {
                stored
            } else {
                SettingsRepository.COMPASS_PROVIDER_GOOGLE_FUSED
            }
        }

    override suspend fun setCompassProviderMode(mode: String) {
        context.dataStore.edit {
            it[PrefKeys.COMPASS_PROVIDER_MODE] =
                if (mode in allowedCompassProviderModes) {
                    mode
                } else {
                    SettingsRepository.COMPASS_PROVIDER_GOOGLE_FUSED
                }
        }
    }

    override val compassHeadingSourceMode: Flow<String> =
        context.dataStore.data.map {
            val stored = it[PrefKeys.COMPASS_HEADING_SOURCE_MODE]
            if (stored != null && stored in allowedCompassHeadingSourceModes) {
                stored
            } else {
                SettingsRepository.COMPASS_HEADING_SOURCE_AUTO
            }
        }

    override suspend fun setCompassHeadingSourceMode(mode: String) {
        context.dataStore.edit {
            it[PrefKeys.COMPASS_HEADING_SOURCE_MODE] =
                if (mode in allowedCompassHeadingSourceModes) {
                    mode
                } else {
                    SettingsRepository.COMPASS_HEADING_SOURCE_AUTO
                }
        }
    }

    override val compassConeAccuracyColorsEnabled: Flow<Boolean> =
        context.dataStore.data.map {
            it[PrefKeys.COMPASS_CONE_ACCURACY_COLORS_ENABLED] ?: true
        }

    override suspend fun setCompassConeAccuracyColorsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.COMPASS_CONE_ACCURACY_COLORS_ENABLED] = enabled }
    }

    override val navigationMarkerStyleInitial: String
        get() = readCachedNavigationMarkerStyle()

    override val navigationMarkerStyle: Flow<String> =
        context.dataStore.data.map {
            val stored = it[PrefKeys.NAVIGATION_MARKER_STYLE]
            val resolved =
                if (stored != null && stored in allowedMarkerStyles) {
                    stored
                } else {
                    readCachedNavigationMarkerStyle()
                }
            writeCachedNavigationMarkerStyle(resolved)
            resolved
        }

    override suspend fun setNavigationMarkerStyle(style: String) {
        val resolved = if (style in allowedMarkerStyles) style else SettingsRepository.MARKER_STYLE_DOT
        context.dataStore.edit {
            it[PrefKeys.NAVIGATION_MARKER_STYLE] = resolved
        }
        writeCachedNavigationMarkerStyle(resolved)
    }

    override val mapDoubleTapAction: Flow<String> = context.dataStore.data.map { it[PrefKeys.MAP_DOUBLE_TAP_ACTION] ?: "zoom_in" }

    override suspend fun setMapDoubleTapAction(action: String) {
        context.dataStore.edit { it[PrefKeys.MAP_DOUBLE_TAP_ACTION] = action }
    }

    override val liveElevation: Flow<Boolean> =
        context.dataStore.data.map {
            it[PrefKeys.LIVE_ELEVATION] ?: false
        }

    override suspend fun setLiveElevation(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.LIVE_ELEVATION] = enabled }
    }

    override val liveDistance: Flow<Boolean> =
        context.dataStore.data.map {
            it[PrefKeys.LIVE_DISTANCE] ?: false
        }

    override suspend fun setLiveDistance(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.LIVE_DISTANCE] = enabled }
    }

    override val offlineMode: Flow<Boolean> =
        context.dataStore.data.map {
            it[PrefKeys.OFFLINE_MODE] ?: false
        }

    override suspend fun setOfflineMode(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.OFFLINE_MODE] = enabled }
    }

    override val crownZoomEnabled: Flow<Boolean> = context.dataStore.data.map { it[PrefKeys.CROWN_ZOOM_ENABLED] ?: true }

    override suspend fun setCrownZoomEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.CROWN_ZOOM_ENABLED] = enabled }
    }

    override val crownZoomInverted: Flow<Boolean> = context.dataStore.data.map { it[PrefKeys.CROWN_ZOOM_INVERTED] ?: true }

    override suspend fun setCrownZoomInverted(inverted: Boolean) {
        context.dataStore.edit { it[PrefKeys.CROWN_ZOOM_INVERTED] = inverted }
    }

    override val gpxTrackColor: Flow<Int> = context.dataStore.data.map { it[PrefKeys.GPX_TRACK_COLOR] ?: ContextCompat.getColor(context, R.color.default_gpx_track) }

    override suspend fun setGpxTrackColor(color: Int) {
        context.dataStore.edit { it[PrefKeys.GPX_TRACK_COLOR] = color }
    }

    override val gpxTrackWidth: Flow<Float> = context.dataStore.data.map { it[PrefKeys.GPX_TRACK_WIDTH] ?: 8f }

    override suspend fun setGpxTrackWidth(width: Float) {
        context.dataStore.edit { it[PrefKeys.GPX_TRACK_WIDTH] = width }
    }

    override val gpxTrackOpacityPercent: Flow<Int> =
        context.dataStore.data.map {
            sanitizeGpxTrackOpacityPercent(
                it[PrefKeys.GPX_TRACK_OPACITY_PERCENT]
                    ?: SettingsRepository.DEFAULT_GPX_TRACK_OPACITY_PERCENT,
            )
        }

    override suspend fun setGpxTrackOpacityPercent(opacityPercent: Int) {
        context.dataStore.edit {
            it[PrefKeys.GPX_TRACK_OPACITY_PERCENT] = sanitizeGpxTrackOpacityPercent(opacityPercent)
        }
    }

    override val autoRecenterEnabled: Flow<Boolean> = context.dataStore.data.map { it[PrefKeys.AUTO_RECENTER_ENABLED] ?: false }

    override suspend fun setAutoRecenterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.AUTO_RECENTER_ENABLED] = enabled }
    }

    override val autoRecenterDelay: Flow<Int> = context.dataStore.data.map { it[PrefKeys.AUTO_RECENTER_DELAY] ?: 5 }

    override suspend fun setAutoRecenterDelay(delay: Int) {
        context.dataStore.edit { it[PrefKeys.AUTO_RECENTER_DELAY] = delay }
    }

    override val selectedMapPath: Flow<String?> = context.dataStore.data.map { it[PrefKeys.SELECTED_MAP_PATH] }

    override suspend fun setSelectedMapPath(path: String?) {
        if (path != null) {
            context.dataStore.edit { it[PrefKeys.SELECTED_MAP_PATH] = path }
        } else {
            context.dataStore.edit { it.remove(PrefKeys.SELECTED_MAP_PATH) }
        }
    }

    override val keepAppOpen: Flow<Boolean> = context.dataStore.data.map { it[PrefKeys.KEEP_APP_OPEN] ?: false }

    override suspend fun setKeepAppOpen(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.KEEP_APP_OPEN] = enabled }
    }

    override val keepAppOpenTipShown: Flow<Boolean> =
        context.dataStore.data.map {
            it[PrefKeys.KEEP_APP_OPEN_TIP_SHOWN] ?: false
        }

    override suspend fun setKeepAppOpenTipShown(shown: Boolean) {
        context.dataStore.edit { it[PrefKeys.KEEP_APP_OPEN_TIP_SHOWN] = shown }
    }

    override val compassMode: Flow<Boolean> = context.dataStore.data.map { it[PrefKeys.COMPASS_MODE] ?: true }

    override suspend fun setCompassMode(isCompassMode: Boolean) {
        context.dataStore.edit { it[PrefKeys.COMPASS_MODE] = isCompassMode }
    }

    override val isGpxInspectionEnabled: Flow<Boolean> = context.dataStore.data.map { it[PrefKeys.GPX_INSPECTION_ENABLED] ?: true }

    override suspend fun setGpxInspectionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.GPX_INSPECTION_ENABLED] = enabled }
    }

    override val gpxFlatSpeedMps: Flow<Float> =
        context.dataStore.data.map {
            (it[PrefKeys.GPX_FLAT_SPEED_MPS] ?: SettingsRepository.DEFAULT_GPX_FLAT_SPEED_MPS)
                .coerceIn(0f, SettingsRepository.MAX_GPX_FLAT_SPEED_MPS)
        }

    override suspend fun setGpxFlatSpeedMps(speedMps: Float) {
        context.dataStore.edit {
            it[PrefKeys.GPX_FLAT_SPEED_MPS] =
                speedMps.coerceIn(0f, SettingsRepository.MAX_GPX_FLAT_SPEED_MPS)
        }
    }

    override val gpxAdvancedEtaEnabled: Flow<Boolean> =
        context.dataStore.data.map {
            it[PrefKeys.GPX_ADVANCED_ETA_ENABLED] ?: SettingsRepository.DEFAULT_GPX_ADVANCED_ETA_ENABLED
        }

    override suspend fun setGpxAdvancedEtaEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.GPX_ADVANCED_ETA_ENABLED] = enabled }
    }

    override val gpxUphillVerticalMetersPerHour: Flow<Float> =
        context.dataStore.data.map {
            sanitizeGpxUphillVerticalMetersPerHour(
                it[PrefKeys.GPX_UPHILL_VERTICAL_METERS_PER_HOUR]
                    ?: SettingsRepository.DEFAULT_GPX_UPHILL_VERTICAL_METERS_PER_HOUR,
            )
        }

    override suspend fun setGpxUphillVerticalMetersPerHour(metersPerHour: Float) {
        context.dataStore.edit {
            it[PrefKeys.GPX_UPHILL_VERTICAL_METERS_PER_HOUR] =
                sanitizeGpxUphillVerticalMetersPerHour(metersPerHour)
        }
    }

    override val gpxDownhillVerticalMetersPerHour: Flow<Float> =
        context.dataStore.data.map {
            sanitizeGpxDownhillVerticalMetersPerHour(
                it[PrefKeys.GPX_DOWNHILL_VERTICAL_METERS_PER_HOUR]
                    ?: SettingsRepository.DEFAULT_GPX_DOWNHILL_VERTICAL_METERS_PER_HOUR,
            )
        }

    override suspend fun setGpxDownhillVerticalMetersPerHour(metersPerHour: Float) {
        context.dataStore.edit {
            it[PrefKeys.GPX_DOWNHILL_VERTICAL_METERS_PER_HOUR] =
                sanitizeGpxDownhillVerticalMetersPerHour(metersPerHour)
        }
    }

    override val gpxElevationSmoothingDistanceMeters: Flow<Float> =
        context.dataStore.data.map {
            GpxElevationFilterDefaults.sanitizeSmoothingDistanceMeters(
                it[PrefKeys.GPX_ELEVATION_SMOOTHING_DISTANCE_METERS]
                    ?: SettingsRepository.DEFAULT_GPX_ELEVATION_SMOOTHING_DISTANCE_METERS,
            )
        }

    override suspend fun setGpxElevationSmoothingDistanceMeters(distanceMeters: Float) {
        context.dataStore.edit {
            it[PrefKeys.GPX_ELEVATION_SMOOTHING_DISTANCE_METERS] =
                GpxElevationFilterDefaults.sanitizeSmoothingDistanceMeters(distanceMeters)
        }
    }

    override val gpxElevationNeutralDiffThresholdMeters: Flow<Float> =
        context.dataStore.data.map {
            GpxElevationFilterDefaults.sanitizeNeutralDiffThresholdMeters(
                it[PrefKeys.GPX_ELEVATION_NEUTRAL_DIFF_THRESHOLD_METERS]
                    ?: SettingsRepository.DEFAULT_GPX_ELEVATION_NEUTRAL_DIFF_THRESHOLD_METERS,
            )
        }

    override suspend fun setGpxElevationNeutralDiffThresholdMeters(thresholdMeters: Float) {
        context.dataStore.edit {
            it[PrefKeys.GPX_ELEVATION_NEUTRAL_DIFF_THRESHOLD_METERS] =
                GpxElevationFilterDefaults.sanitizeNeutralDiffThresholdMeters(thresholdMeters)
        }
    }

    override val gpxElevationTrendActivationThresholdMeters: Flow<Float> =
        context.dataStore.data.map {
            GpxElevationFilterDefaults.sanitizeTrendActivationThresholdMeters(
                it[PrefKeys.GPX_ELEVATION_TREND_ACTIVATION_THRESHOLD_METERS]
                    ?: SettingsRepository.DEFAULT_GPX_ELEVATION_TREND_ACTIVATION_THRESHOLD_METERS,
            )
        }

    override suspend fun setGpxElevationTrendActivationThresholdMeters(thresholdMeters: Float) {
        context.dataStore.edit {
            it[PrefKeys.GPX_ELEVATION_TREND_ACTIVATION_THRESHOLD_METERS] =
                GpxElevationFilterDefaults.sanitizeTrendActivationThresholdMeters(thresholdMeters)
        }
    }

    override val gpxElevationAutoAdjustPerGpx: Flow<Boolean> =
        context.dataStore.data.map {
            it[PrefKeys.GPX_ELEVATION_AUTO_ADJUST_PER_GPX]
                ?: SettingsRepository.DEFAULT_GPX_ELEVATION_AUTO_ADJUST_PER_GPX
        }

    override suspend fun setGpxElevationAutoAdjustPerGpx(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.GPX_ELEVATION_AUTO_ADJUST_PER_GPX] = enabled }
    }

    override val gpxLongPressTipShown: Flow<Boolean> =
        context.dataStore.data.map { it[PrefKeys.GPX_LONG_PRESS_TIP_SHOWN] ?: false }

    override suspend fun setGpxLongPressTipShown(shown: Boolean) {
        context.dataStore.edit { it[PrefKeys.GPX_LONG_PRESS_TIP_SHOWN] = shown }
    }

    override val isMetric: Flow<Boolean> = context.dataStore.data.map { it[PrefKeys.IS_METRIC] ?: true }

    override suspend fun setMetric(isMetric: Boolean) {
        context.dataStore.edit { it[PrefKeys.IS_METRIC] = isMetric }
    }

    override val poiIconSizePx: Flow<Int> =
        context.dataStore.data.map {
            val stored = it[PrefKeys.POI_ICON_SIZE_PX]
            if (stored != null && stored in allowedPoiIconSizesPx) {
                stored
            } else {
                SettingsRepository.POI_ICON_SIZE_DEFAULT_PX
            }
        }

    override suspend fun setPoiIconSizePx(sizePx: Int) {
        context.dataStore.edit {
            it[PrefKeys.POI_ICON_SIZE_PX] =
                if (sizePx in allowedPoiIconSizesPx) sizePx else SettingsRepository.POI_ICON_SIZE_DEFAULT_PX
        }
    }

    override val poiTapToCenterEnabled: Flow<Boolean> =
        context.dataStore.data.map {
            it[PrefKeys.POI_TAP_TO_CENTER_ENABLED] ?: true
        }

    override suspend fun setPoiTapToCenterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.POI_TAP_TO_CENTER_ENABLED] = enabled }
    }

    override val poiPopupTimeoutSeconds: Flow<Int> =
        context.dataStore.data.map {
            val stored =
                it[PrefKeys.POI_POPUP_TIMEOUT_SECONDS]
                    ?: SettingsRepository.POI_POPUP_TIMEOUT_DEFAULT_SECONDS
            sanitizePoiPopupTimeoutSeconds(stored)
        }

    override suspend fun setPoiPopupTimeoutSeconds(seconds: Int) {
        context.dataStore.edit {
            it[PrefKeys.POI_POPUP_TIMEOUT_SECONDS] = sanitizePoiPopupTimeoutSeconds(seconds)
        }
    }

    override val poiPopupManualCloseOnly: Flow<Boolean> =
        context.dataStore.data.map {
            it[PrefKeys.POI_POPUP_MANUAL_CLOSE_ONLY] ?: false
        }

    override suspend fun setPoiPopupManualCloseOnly(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.POI_POPUP_MANUAL_CLOSE_ONLY] = enabled }
    }

    override suspend fun resetToDefaults() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
        markerStyleCachePrefs.edit().remove(CACHE_KEY_NAVIGATION_MARKER_STYLE).apply()
        mapsHelpPrefs.edit().clear().apply()
        debugHelpPrefs.edit().clear().apply()
    }

    companion object {
        private val allowedTimeFormats =
            setOf(
                SettingsRepository.TIME_FORMAT_24_HOUR,
                SettingsRepository.TIME_FORMAT_12_HOUR,
            )
        private const val LEGACY_TIME_FORMAT_SYSTEM = "SYSTEM"
        private val allowedZoomButtonModes =
            setOf(
                SettingsRepository.ZOOM_BUTTONS_BOTH,
                SettingsRepository.ZOOM_BUTTONS_HIDE_BOTH,
                SettingsRepository.ZOOM_BUTTONS_HIDE_PLUS,
            )
        private val allowedMarkerStyles =
            setOf(
                SettingsRepository.MARKER_STYLE_DOT,
                SettingsRepository.MARKER_STYLE_TRIANGLE,
            )
        private val allowedNorthReferenceModes =
            setOf(
                SettingsRepository.NORTH_REFERENCE_TRUE,
                SettingsRepository.NORTH_REFERENCE_MAGNETIC,
            )
        private val allowedCompassSettingsModes =
            setOf(
                SettingsRepository.COMPASS_SETTINGS_MODE_AUTOMATIC,
                SettingsRepository.COMPASS_SETTINGS_MODE_ADVANCED,
            )
        private val allowedCompassProviderModes =
            setOf(
                SettingsRepository.COMPASS_PROVIDER_GOOGLE_FUSED,
                SettingsRepository.COMPASS_PROVIDER_SENSOR_MANAGER,
            )
        private val allowedCompassHeadingSourceModes =
            setOf(
                SettingsRepository.COMPASS_HEADING_SOURCE_AUTO,
                SettingsRepository.COMPASS_HEADING_SOURCE_TYPE_HEADING,
                SettingsRepository.COMPASS_HEADING_SOURCE_ROTATION_VECTOR,
                SettingsRepository.COMPASS_HEADING_SOURCE_MAGNETOMETER,
            )
        private val allowedPoiIconSizesPx =
            setOf(
                SettingsRepository.POI_ICON_SIZE_SMALL_PX,
                SettingsRepository.POI_ICON_SIZE_DEFAULT_PX,
                SettingsRepository.POI_ICON_SIZE_MEDIUM_PX,
                SettingsRepository.POI_ICON_SIZE_LARGE_PX,
            )
        private const val LEGACY_ZOOM_BUTTONS_HIDE_MINUS = "HIDE_MINUS"
        private const val CACHE_PREFS_NAME = "settings_runtime_cache"
        private const val CACHE_KEY_NAVIGATION_MARKER_STYLE = "navigation_marker_style"
        private const val MAPS_HELP_PREFS_NAME = "maps_screen_help_prefs"
        private const val DEBUG_HELP_PREFS_NAME = "debug_settings_help_prefs"

        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepositoryImpl(context.applicationContext).also {
                    INSTANCE = it
                }
            }

        private fun sanitizePoiPopupTimeoutSeconds(seconds: Int): Int =
            seconds.coerceIn(
                SettingsRepository.POI_POPUP_TIMEOUT_MIN_SECONDS,
                SettingsRepository.POI_POPUP_TIMEOUT_MAX_SECONDS,
            )

        private fun sanitizeGpxTrackOpacityPercent(opacityPercent: Int): Int =
            opacityPercent.coerceIn(
                SettingsRepository.MIN_GPX_TRACK_OPACITY_PERCENT,
                SettingsRepository.MAX_GPX_TRACK_OPACITY_PERCENT,
            )

        private fun sanitizeGpxUphillVerticalMetersPerHour(metersPerHour: Float): Float =
            metersPerHour.coerceIn(
                SettingsRepository.MIN_GPX_VERTICAL_METERS_PER_HOUR,
                SettingsRepository.MAX_GPX_UPHILL_VERTICAL_METERS_PER_HOUR,
            )

        private fun sanitizeGpxDownhillVerticalMetersPerHour(metersPerHour: Float): Float =
            metersPerHour.coerceIn(
                SettingsRepository.MIN_GPX_VERTICAL_METERS_PER_HOUR,
                SettingsRepository.MAX_GPX_DOWNHILL_VERTICAL_METERS_PER_HOUR,
            )
    }

    private fun readCachedNavigationMarkerStyle(): String {
        val cached = markerStyleCachePrefs.getString(CACHE_KEY_NAVIGATION_MARKER_STYLE, null)
        return if (cached != null && cached in allowedMarkerStyles) {
            cached
        } else {
            SettingsRepository.MARKER_STYLE_DOT
        }
    }

    private fun writeCachedNavigationMarkerStyle(style: String) {
        if (style !in allowedMarkerStyles) return
        if (markerStyleCachePrefs.getString(CACHE_KEY_NAVIGATION_MARKER_STYLE, null) == style) return
        markerStyleCachePrefs.edit().putString(CACHE_KEY_NAVIGATION_MARKER_STYLE, style).apply()
    }
}
