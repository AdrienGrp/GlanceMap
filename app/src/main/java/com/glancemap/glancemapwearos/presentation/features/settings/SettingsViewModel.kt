package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val gpsInterval: StateFlow<Long> =
        settingsRepository.gpsInterval
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                SettingsRepository.DEFAULT_GPS_INTERVAL_MS,
            )

    fun setGpsInterval(interval: Long) =
        viewModelScope.launch {
            settingsRepository.setGpsInterval(interval)
        }

    val ambientGpsInterval: StateFlow<Long> =
        settingsRepository.ambientGpsInterval
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                SettingsRepository.DEFAULT_AMBIENT_GPS_INTERVAL_MS,
            )

    fun setAmbientGpsInterval(interval: Long) =
        viewModelScope.launch {
            settingsRepository.setAmbientGpsInterval(interval)
        }

    val watchGpsOnly: StateFlow<Boolean> =
        settingsRepository.watchGpsOnly
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setWatchGpsOnly(isOnly: Boolean) =
        viewModelScope.launch {
            settingsRepository.setWatchGpsOnly(isOnly)
        }

    val gpsInAmbientMode: StateFlow<Boolean> =
        settingsRepository.gpsInAmbientMode
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setGpsInAmbientMode(enabled: Boolean) =
        viewModelScope.launch {
            settingsRepository.setGpsInAmbientMode(enabled)
        }

    val gpsDebugTelemetry: StateFlow<Boolean> =
        settingsRepository.gpsDebugTelemetry
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setGpsDebugTelemetry(enabled: Boolean) =
        viewModelScope.launch {
            settingsRepository.setGpsDebugTelemetry(enabled)
        }

    val gpsDebugTelemetryPopupEnabled: StateFlow<Boolean> =
        settingsRepository.gpsDebugTelemetryPopupEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setGpsDebugTelemetryPopupEnabled(enabled: Boolean) =
        viewModelScope.launch {
            settingsRepository.setGpsDebugTelemetryPopupEnabled(enabled)
        }

    val promptForCalibration: StateFlow<Boolean> =
        settingsRepository.promptForCalibration
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setPromptForCalibration(enabled: Boolean) =
        viewModelScope.launch {
            settingsRepository.setPromptForCalibration(enabled)
        }

    val showTimeInNavigate: StateFlow<Boolean> =
        settingsRepository.showTimeInNavigate
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setShowTimeInNavigate(enabled: Boolean) =
        viewModelScope.launch {
            settingsRepository.setShowTimeInNavigate(enabled)
        }

    val navigateTimeFormat: StateFlow<String> =
        settingsRepository.navigateTimeFormat
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.TIME_FORMAT_24_HOUR)

    fun setNavigateTimeFormat(format: String) =
        viewModelScope.launch {
            settingsRepository.setNavigateTimeFormat(format)
        }

    val mapZoomButtonsMode: StateFlow<String> =
        settingsRepository.mapZoomButtonsMode
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.ZOOM_BUTTONS_BOTH)

    fun setMapZoomButtonsMode(mode: String) =
        viewModelScope.launch {
            settingsRepository.setMapZoomButtonsMode(mode)
        }

    val gpsAccuracyCircleEnabled: StateFlow<Boolean> =
        settingsRepository.gpsAccuracyCircleEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setGpsAccuracyCircleEnabled(enabled: Boolean) =
        viewModelScope.launch {
            settingsRepository.setGpsAccuracyCircleEnabled(enabled)
        }

    val mapZoomDefault: StateFlow<Int> =
        settingsRepository.mapZoomDefault
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 16)

    fun setMapZoomDefault(zoom: Int) =
        viewModelScope.launch {
            settingsRepository.setMapZoomDefault(zoom)
        }

    val mapZoomMin: StateFlow<Int> =
        settingsRepository.mapZoomMin
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8)

    fun setMapZoomMin(zoom: Int) =
        viewModelScope.launch {
            settingsRepository.setMapZoomMin(zoom)
        }

    val mapZoomMax: StateFlow<Int> =
        settingsRepository.mapZoomMax
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 20)

    fun setMapZoomMax(zoom: Int) =
        viewModelScope.launch {
            settingsRepository.setMapZoomMax(zoom)
        }

    val northIndicatorMode: StateFlow<String> =
        settingsRepository.northIndicatorMode
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ALWAYS")

    fun setNorthIndicatorMode(mode: String) =
        viewModelScope.launch {
            settingsRepository.setNorthIndicatorMode(mode)
        }

    val northReferenceMode: StateFlow<String> =
        settingsRepository.northReferenceMode
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                SettingsRepository.NORTH_REFERENCE_TRUE,
            )

    fun setNorthReferenceMode(mode: String) =
        viewModelScope.launch {
            settingsRepository.setNorthReferenceMode(mode)
        }

    val compassSettingsMode: StateFlow<String> =
        settingsRepository.compassSettingsMode
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                SettingsRepository.COMPASS_SETTINGS_MODE_AUTOMATIC,
            )

    fun setCompassSettingsMode(mode: String) =
        viewModelScope.launch {
            settingsRepository.setCompassSettingsMode(mode)
        }

    val compassProviderMode: StateFlow<String> =
        settingsRepository.compassProviderMode
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                SettingsRepository.COMPASS_PROVIDER_GOOGLE_FUSED,
            )

    fun setCompassProviderMode(mode: String) =
        viewModelScope.launch {
            settingsRepository.setCompassProviderMode(mode)
        }

    val compassHeadingSourceMode: StateFlow<String> =
        settingsRepository.compassHeadingSourceMode
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                SettingsRepository.COMPASS_HEADING_SOURCE_AUTO,
            )

    fun setCompassHeadingSourceMode(mode: String) =
        viewModelScope.launch {
            settingsRepository.setCompassHeadingSourceMode(mode)
        }

    val compassConeAccuracyColorsEnabled: StateFlow<Boolean> =
        settingsRepository.compassConeAccuracyColorsEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setCompassConeAccuracyColorsEnabled(enabled: Boolean) =
        viewModelScope.launch {
            settingsRepository.setCompassConeAccuracyColorsEnabled(enabled)
        }

    val navigationMarkerStyle: StateFlow<String> =
        settingsRepository.navigationMarkerStyle
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                settingsRepository.navigationMarkerStyleInitial,
            )

    fun setNavigationMarkerStyle(style: String) =
        viewModelScope.launch {
            settingsRepository.setNavigationMarkerStyle(style)
        }

    val mapDoubleTapAction: StateFlow<String> =
        settingsRepository.mapDoubleTapAction
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "zoom_in")

    fun setMapDoubleTapAction(action: String) =
        viewModelScope.launch {
            settingsRepository.setMapDoubleTapAction(action)
        }

    val liveElevation: StateFlow<Boolean> =
        settingsRepository.liveElevation
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setLiveElevation(enabled: Boolean) =
        viewModelScope.launch {
            settingsRepository.setLiveElevation(enabled)
        }

    val liveDistance: StateFlow<Boolean> =
        settingsRepository.liveDistance
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setLiveDistance(enabled: Boolean) =
        viewModelScope.launch {
            settingsRepository.setLiveDistance(enabled)
        }

    val offlineMode: StateFlow<Boolean> =
        settingsRepository.offlineMode
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setOfflineMode(enabled: Boolean) =
        viewModelScope.launch {
            settingsRepository.setOfflineMode(enabled)
        }

    val crownZoomEnabled: StateFlow<Boolean> =
        settingsRepository.crownZoomEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setCrownZoomEnabled(enabled: Boolean) =
        viewModelScope.launch {
            settingsRepository.setCrownZoomEnabled(enabled)
        }

    val crownZoomInverted: StateFlow<Boolean> =
        settingsRepository.crownZoomInverted
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setCrownZoomInverted(inverted: Boolean) =
        viewModelScope.launch {
            settingsRepository.setCrownZoomInverted(inverted)
        }

    val gpxTrackColor: StateFlow<Int> =
        settingsRepository.gpxTrackColor
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setGpxTrackColor(color: Int) =
        viewModelScope.launch {
            settingsRepository.setGpxTrackColor(color)
        }

    val gpxTrackWidth: StateFlow<Float> =
        settingsRepository.gpxTrackWidth
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8f)

    fun setGpxTrackWidth(width: Float) =
        viewModelScope.launch {
            settingsRepository.setGpxTrackWidth(width)
        }

    val gpxTrackOpacityPercent: StateFlow<Int> =
        settingsRepository.gpxTrackOpacityPercent
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                SettingsRepository.DEFAULT_GPX_TRACK_OPACITY_PERCENT,
            )

    fun setGpxTrackOpacityPercent(opacityPercent: Int) =
        viewModelScope.launch {
            settingsRepository.setGpxTrackOpacityPercent(opacityPercent)
        }

    val autoRecenterEnabled: StateFlow<Boolean> =
        settingsRepository.autoRecenterEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setAutoRecenterEnabled(enabled: Boolean) =
        viewModelScope.launch {
            settingsRepository.setAutoRecenterEnabled(enabled)
        }

    // ✅ FIX: this is StateFlow, not Flow
    val autoRecenterDelay: StateFlow<Int> =
        settingsRepository.autoRecenterDelay
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    fun setAutoRecenterDelay(delay: Int) =
        viewModelScope.launch {
            settingsRepository.setAutoRecenterDelay(delay)
        }

    // ✅ FIX: this is StateFlow, not Flow
    val selectedMapPath: StateFlow<String?> =
        settingsRepository.selectedMapPath
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setSelectedMapPath(path: String?) =
        viewModelScope.launch {
            settingsRepository.setSelectedMapPath(path)
        }

    val keepAppOpen: StateFlow<Boolean> =
        settingsRepository.keepAppOpen
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setKeepAppOpen(enabled: Boolean) =
        viewModelScope.launch {
            settingsRepository.setKeepAppOpen(enabled)
        }

    val keepAppOpenTipShown: StateFlow<Boolean> =
        settingsRepository.keepAppOpenTipShown
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setKeepAppOpenTipShown(shown: Boolean) =
        viewModelScope.launch {
            settingsRepository.setKeepAppOpenTipShown(shown)
        }

    val compassMode: StateFlow<Boolean> =
        settingsRepository.compassMode
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setCompassMode(isCompassMode: Boolean) =
        viewModelScope.launch {
            settingsRepository.setCompassMode(isCompassMode)
        }

    val isGpxInspectionEnabled: StateFlow<Boolean> =
        settingsRepository.isGpxInspectionEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setGpxInspectionEnabled(enabled: Boolean) =
        viewModelScope.launch {
            settingsRepository.setGpxInspectionEnabled(enabled)
        }

    val gpxFlatSpeedMps: StateFlow<Float> =
        settingsRepository.gpxFlatSpeedMps
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                SettingsRepository.DEFAULT_GPX_FLAT_SPEED_MPS,
            )

    fun setGpxFlatSpeedMps(speedMps: Float) =
        viewModelScope.launch {
            settingsRepository.setGpxFlatSpeedMps(speedMps)
        }

    val gpxAdvancedEtaEnabled: StateFlow<Boolean> =
        settingsRepository.gpxAdvancedEtaEnabled
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                SettingsRepository.DEFAULT_GPX_ADVANCED_ETA_ENABLED,
            )

    fun setGpxAdvancedEtaEnabled(enabled: Boolean) =
        viewModelScope.launch {
            settingsRepository.setGpxAdvancedEtaEnabled(enabled)
        }

    val gpxUphillVerticalMetersPerHour: StateFlow<Float> =
        settingsRepository.gpxUphillVerticalMetersPerHour
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                SettingsRepository.DEFAULT_GPX_UPHILL_VERTICAL_METERS_PER_HOUR,
            )

    fun setGpxUphillVerticalMetersPerHour(metersPerHour: Float) =
        viewModelScope.launch {
            settingsRepository.setGpxUphillVerticalMetersPerHour(metersPerHour)
        }

    val gpxDownhillVerticalMetersPerHour: StateFlow<Float> =
        settingsRepository.gpxDownhillVerticalMetersPerHour
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                SettingsRepository.DEFAULT_GPX_DOWNHILL_VERTICAL_METERS_PER_HOUR,
            )

    fun setGpxDownhillVerticalMetersPerHour(metersPerHour: Float) =
        viewModelScope.launch {
            settingsRepository.setGpxDownhillVerticalMetersPerHour(metersPerHour)
        }

    val gpxElevationSmoothingDistanceMeters: StateFlow<Float> =
        settingsRepository.gpxElevationSmoothingDistanceMeters
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                SettingsRepository.DEFAULT_GPX_ELEVATION_SMOOTHING_DISTANCE_METERS,
            )

    fun setGpxElevationSmoothingDistanceMeters(distanceMeters: Float) =
        viewModelScope.launch {
            settingsRepository.setGpxElevationSmoothingDistanceMeters(distanceMeters)
        }

    val gpxElevationNeutralDiffThresholdMeters: StateFlow<Float> =
        settingsRepository.gpxElevationNeutralDiffThresholdMeters
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                SettingsRepository.DEFAULT_GPX_ELEVATION_NEUTRAL_DIFF_THRESHOLD_METERS,
            )

    fun setGpxElevationNeutralDiffThresholdMeters(thresholdMeters: Float) =
        viewModelScope.launch {
            settingsRepository.setGpxElevationNeutralDiffThresholdMeters(thresholdMeters)
        }

    val gpxElevationTrendActivationThresholdMeters: StateFlow<Float> =
        settingsRepository.gpxElevationTrendActivationThresholdMeters
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                SettingsRepository.DEFAULT_GPX_ELEVATION_TREND_ACTIVATION_THRESHOLD_METERS,
            )

    fun setGpxElevationTrendActivationThresholdMeters(thresholdMeters: Float) =
        viewModelScope.launch {
            settingsRepository.setGpxElevationTrendActivationThresholdMeters(thresholdMeters)
        }

    val gpxElevationAutoAdjustPerGpx: StateFlow<Boolean> =
        settingsRepository.gpxElevationAutoAdjustPerGpx
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                SettingsRepository.DEFAULT_GPX_ELEVATION_AUTO_ADJUST_PER_GPX,
            )

    fun setGpxElevationAutoAdjustPerGpx(enabled: Boolean) =
        viewModelScope.launch {
            settingsRepository.setGpxElevationAutoAdjustPerGpx(enabled)
        }

    val isMetric: StateFlow<Boolean> =
        settingsRepository.isMetric
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setMetric(isMetric: Boolean) =
        viewModelScope.launch {
            settingsRepository.setMetric(isMetric)
        }

    val poiIconSizePx: StateFlow<Int> =
        settingsRepository.poiIconSizePx
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                SettingsRepository.POI_ICON_SIZE_DEFAULT_PX,
            )

    fun setPoiIconSizePx(sizePx: Int) =
        viewModelScope.launch {
            settingsRepository.setPoiIconSizePx(sizePx)
        }

    val poiTapToCenterEnabled: StateFlow<Boolean> =
        settingsRepository.poiTapToCenterEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setPoiTapToCenterEnabled(enabled: Boolean) =
        viewModelScope.launch {
            settingsRepository.setPoiTapToCenterEnabled(enabled)
        }

    val poiPopupTimeoutSeconds: StateFlow<Int> =
        settingsRepository.poiPopupTimeoutSeconds
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                SettingsRepository.POI_POPUP_TIMEOUT_DEFAULT_SECONDS,
            )

    fun setPoiPopupTimeoutSeconds(seconds: Int) =
        viewModelScope.launch {
            settingsRepository.setPoiPopupTimeoutSeconds(seconds)
        }

    val poiPopupManualCloseOnly: StateFlow<Boolean> =
        settingsRepository.poiPopupManualCloseOnly
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setPoiPopupManualCloseOnly(enabled: Boolean) =
        viewModelScope.launch {
            settingsRepository.setPoiPopupManualCloseOnly(enabled)
        }

    fun resetToDefaults() =
        viewModelScope.launch {
            settingsRepository.resetToDefaults()
        }
}
