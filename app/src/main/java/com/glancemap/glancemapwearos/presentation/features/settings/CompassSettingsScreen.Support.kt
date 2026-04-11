package com.glancemap.glancemapwearos.presentation.features.settings

import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.domain.sensors.CompassHeadingSourceMode
import com.glancemap.glancemapwearos.domain.sensors.CompassProviderType
import com.glancemap.glancemapwearos.domain.sensors.CompassViewModel
import com.glancemap.glancemapwearos.domain.sensors.HeadingSource
import com.glancemap.glancemapwearos.domain.sensors.HeadingSourceStatus
import com.glancemap.glancemapwearos.domain.sensors.NorthReferenceMode
import com.glancemap.glancemapwearos.domain.sensors.NorthReferenceStatus
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import kotlin.math.abs

internal data class CompatibilityTestUiState(
    val running: Boolean = false,
    val progressLabel: String = "",
    val stepIndex: Int = 0,
    val totalSteps: Int = 0,
    val result: CompatibilityTestResult? = null,
    val errorMessage: String? = null
)

internal data class CompatibilityTestResult(
    val recommendedMode: CompassHeadingSourceMode,
    val bestCandidate: CompatibilityCandidateScore,
    val candidates: List<CompatibilityCandidateScore>,
    val availability: HeadingSourceStatus
)

internal data class CompatibilityCandidateScore(
    val mode: CompassHeadingSourceMode,
    val score: Float,
    val averageAccuracyRank: Float,
    val averageNoiseDeg: Float,
    val turnSpanDeg: Float,
    val sourceMatchRatio: Float,
    val magneticInterferenceRatio: Float,
    val available: Boolean,
    val unavailableReason: String? = null
) {
    fun signalSummary(): String {
        if (!available) {
            return unavailableReason ?: "Unavailable"
        }
        val accuracyLabel = accuracyLabelFromAverageRank(averageAccuracyRank)
        val accuracy = "%.1f".format(averageAccuracyRank)
        val noise = "%.1f".format(averageNoiseDeg)
        val match = "%.0f".format(sourceMatchRatio * 100f)
        val response = turnResponseLabel(turnSpanDeg)
        return "accuracy=$accuracyLabel ($accuracy/3), noise=$noise deg, response=$response, source match=$match%"
    }

    fun userSummary(isBest: Boolean): String {
        val prefix = if (isBest) "* " else "- "
        if (!available) {
            return prefix + headingSourceModeLabel(mode) + ": " + (unavailableReason ?: "Unavailable")
        }
        val accuracyLabel = accuracyLabelFromAverageRank(averageAccuracyRank)
        val noise = "%.1f".format(averageNoiseDeg)
        val response = turnResponseLabel(turnSpanDeg)
        return prefix + headingSourceModeLabel(mode) + ": " + accuracyLabel + ", noise " + noise + " deg, response " + response
    }
}

internal data class CompatibilitySampleSummary(
    val averageAccuracyRank: Float,
    val averageNoiseDeg: Float,
    val headingSpanDeg: Float,
    val sourceMatchRatio: Float,
    val magneticInterferenceRatio: Float
)

internal suspend fun evaluateCompatibilityCandidate(
    mode: CompassHeadingSourceMode,
    availability: HeadingSourceStatus,
    compassViewModel: CompassViewModel,
    readHeading: () -> Float,
    readAccuracy: () -> Int,
    readSource: () -> HeadingSource,
    readMagneticInterference: () -> Boolean,
    onPhaseStart: (Int, String) -> Unit
): CompatibilityCandidateScore {
    compassViewModel.setHeadingSourceMode(mode, forceRefresh = true)
    delay(COMPATIBILITY_SWITCH_SETTLE_MS)

    if (!sourceModeAvailable(mode, availability)) {
        return CompatibilityCandidateScore(
            mode = mode,
            score = Float.NEGATIVE_INFINITY,
            averageAccuracyRank = 0f,
            averageNoiseDeg = 180f,
            turnSpanDeg = 0f,
            sourceMatchRatio = 0f,
            magneticInterferenceRatio = 0f,
            available = false,
            unavailableReason = sourceModeUnavailableReason(mode, availability)
        )
    }

    onPhaseStart(1, "Keep watch still: ${headingSourceModeLabel(mode)}")
    val stillSummary = collectCompatibilitySampleSummary(
        sampleCount = COMPATIBILITY_STILL_SAMPLE_COUNT,
        readHeading = readHeading,
        readAccuracy = readAccuracy,
        readSource = readSource,
        readMagneticInterference = readMagneticInterference,
        expectedMode = mode
    )

    onPhaseStart(2, "Turn slowly left/right: ${headingSourceModeLabel(mode)}")
    delay(COMPATIBILITY_TURN_PREPARE_MS)
    val turnSummary = collectCompatibilitySampleSummary(
        sampleCount = COMPATIBILITY_TURN_SAMPLE_COUNT,
        readHeading = readHeading,
        readAccuracy = readAccuracy,
        readSource = readSource,
        readMagneticInterference = readMagneticInterference,
        expectedMode = mode
    )

    val score = compatibilityScore(
        averageAccuracyRank = stillSummary.averageAccuracyRank,
        sourceMatchRatio = stillSummary.sourceMatchRatio,
        averageNoiseDeg = stillSummary.averageNoiseDeg,
        magneticInterferenceRatio = stillSummary.magneticInterferenceRatio,
        turnSpanDeg = turnSummary.headingSpanDeg
    )
    return CompatibilityCandidateScore(
        mode = mode,
        score = score,
        averageAccuracyRank = stillSummary.averageAccuracyRank,
        averageNoiseDeg = stillSummary.averageNoiseDeg,
        turnSpanDeg = turnSummary.headingSpanDeg,
        sourceMatchRatio = stillSummary.sourceMatchRatio,
        magneticInterferenceRatio = stillSummary.magneticInterferenceRatio,
        available = true
    )
}

internal fun compatibilityScore(
    averageAccuracyRank: Float,
    sourceMatchRatio: Float,
    averageNoiseDeg: Float,
    magneticInterferenceRatio: Float,
    turnSpanDeg: Float
): Float {
    val noisePenalty = when {
        averageNoiseDeg >= 20f -> 3.5f
        averageNoiseDeg >= 12f -> 2.5f
        averageNoiseDeg >= 7f -> 1.5f
        averageNoiseDeg >= 4f -> 0.6f
        else -> 0f
    }
    return (averageAccuracyRank * 2.0f) +
        (sourceMatchRatio * 4.0f) -
        noisePenalty -
        (magneticInterferenceRatio * 2.0f) +
        turnResponseBonus(turnSpanDeg)
}

internal suspend fun collectCompatibilitySampleSummary(
    sampleCount: Int,
    readHeading: () -> Float,
    readAccuracy: () -> Int,
    readSource: () -> HeadingSource,
    readMagneticInterference: () -> Boolean,
    expectedMode: CompassHeadingSourceMode
): CompatibilitySampleSummary {
    var measuredSamples = 0
    var accuracyRankTotal = 0f
    var sourceMatchCount = 0
    var interferenceCount = 0
    val headingSamples = mutableListOf<Float>()

    repeat(sampleCount) {
        val heading = readHeading()
        if (heading.isFinite()) {
            headingSamples += heading
        }
        accuracyRankTotal += accuracyRank(readAccuracy())
        if (sourceMatchesMode(readSource(), expectedMode)) {
            sourceMatchCount += 1
        }
        if (readMagneticInterference()) {
            interferenceCount += 1
        }
        measuredSamples += 1
        delay(COMPATIBILITY_SAMPLE_INTERVAL_MS)
    }

    return CompatibilitySampleSummary(
        averageAccuracyRank = if (measuredSamples > 0) accuracyRankTotal / measuredSamples else 0f,
        averageNoiseDeg = averageAngularNoiseDeg(headingSamples),
        headingSpanDeg = headingSpanDeg(headingSamples),
        sourceMatchRatio = if (measuredSamples > 0) sourceMatchCount.toFloat() / measuredSamples else 0f,
        magneticInterferenceRatio = if (measuredSamples > 0) interferenceCount.toFloat() / measuredSamples else 0f
    )
}

internal fun averageAngularNoiseDeg(samples: List<Float>): Float {
    if (samples.size < 2) return 180f
    var sum = 0f
    var count = 0
    samples.zipWithNext().forEach { (a, b) ->
        sum += abs(shortestAngularDiffDeg(a, b))
        count += 1
    }
    return if (count == 0) 180f else (sum / count)
}

internal fun headingSpanDeg(samples: List<Float>): Float {
    if (samples.size < 2) return 0f
    var minOffset = 0f
    var maxOffset = 0f
    val anchor = samples.first()
    samples.forEach { sample ->
        val offset = shortestAngularDiffDeg(anchor, sample)
        minOffset = minOf(minOffset, offset)
        maxOffset = maxOf(maxOffset, offset)
    }
    return maxOffset - minOffset
}

internal fun turnResponseBonus(turnSpanDeg: Float): Float {
    return when {
        turnSpanDeg >= 45f -> 1.2f
        turnSpanDeg >= 28f -> 0.7f
        turnSpanDeg >= 14f -> 0.2f
        else -> 0f
    }
}

internal fun turnResponseLabel(turnSpanDeg: Float): String {
    return when {
        turnSpanDeg >= 45f -> "good"
        turnSpanDeg >= 28f -> "ok"
        turnSpanDeg >= 14f -> "limited"
        else -> "not enough movement"
    }
}

internal fun shortestAngularDiffDeg(from: Float, to: Float): Float {
    var diff = (to - from + 540f) % 360f - 180f
    if (abs(diff + 180f) < 1e-3f) {
        diff = if ((to - from) >= 0f) 180f else -180f
    }
    return diff
}

internal fun sourceMatchesMode(
    source: HeadingSource,
    mode: CompassHeadingSourceMode
): Boolean {
    return when (mode) {
        CompassHeadingSourceMode.AUTO -> source != HeadingSource.NONE
        CompassHeadingSourceMode.TYPE_HEADING -> source == HeadingSource.HEADING_SENSOR
        CompassHeadingSourceMode.ROTATION_VECTOR -> source == HeadingSource.ROTATION_VECTOR
        CompassHeadingSourceMode.MAGNETOMETER -> source == HeadingSource.MAG_ACCEL_FALLBACK
    }
}

internal fun accuracyRank(accuracy: Int): Float {
    return when (accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> 3f
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> 2f
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> 1f
        else -> 0f
    }
}

internal fun accuracyLabelFromAverageRank(rank: Float): String {
    return when {
        rank >= 2.7f -> "High"
        rank >= 2.2f -> "Medium-high"
        rank >= 1.7f -> "Medium"
        rank >= 1.2f -> "Low-medium"
        rank > 0f -> "Low"
        else -> "Unreliable"
    }
}

internal fun recommendedModeFromCandidate(
    best: CompatibilityCandidateScore,
    availability: HeadingSourceStatus
): CompassHeadingSourceMode {
    if (autoResolvesToMode(best.mode, availability)) {
        return CompassHeadingSourceMode.AUTO
    }
    return best.mode
}

internal fun CompatibilityTestResult.recommendedCompassModeLabel(): String {
    return when (recommendedMode) {
        CompassHeadingSourceMode.AUTO -> "Automatic (recommended)"
        else -> "Advanced..."
    }
}

internal fun CompatibilityTestResult.recommendedHeadingSourceLine(): String {
    if (!bestCandidate.available) return ""
    return if (recommendedMode == CompassHeadingSourceMode.AUTO) {
        "Automatic will use: ${headingSourceModeLabel(bestCandidate.mode)}\n"
    } else {
        "Heading source: ${headingSourceModeLabel(recommendedMode)}\n"
    }
}

internal fun CompatibilityTestResult.candidateSummaryLines(): String {
    return candidates.joinToString("\n") { candidate ->
        candidate.userSummary(isBest = candidate.mode == bestCandidate.mode && candidate.available)
    }
}

internal fun autoResolvesToMode(
    mode: CompassHeadingSourceMode,
    availability: HeadingSourceStatus
): Boolean {
    return when (mode) {
        CompassHeadingSourceMode.TYPE_HEADING -> availability.headingSensorAvailable
        CompassHeadingSourceMode.ROTATION_VECTOR ->
            !availability.headingSensorAvailable && availability.rotationVectorAvailable
        CompassHeadingSourceMode.MAGNETOMETER ->
            !availability.headingSensorAvailable &&
                !availability.rotationVectorAvailable &&
                availability.magAccelFallbackAvailable
        CompassHeadingSourceMode.AUTO -> true
    }
}

internal fun sourceModeAvailable(
    mode: CompassHeadingSourceMode,
    availability: HeadingSourceStatus
): Boolean {
    return when (mode) {
        CompassHeadingSourceMode.AUTO -> true
        CompassHeadingSourceMode.TYPE_HEADING -> availability.headingSensorAvailable
        CompassHeadingSourceMode.ROTATION_VECTOR -> availability.rotationVectorAvailable
        CompassHeadingSourceMode.MAGNETOMETER -> availability.magAccelFallbackAvailable
    }
}

internal fun sourceModeUnavailableReason(
    mode: CompassHeadingSourceMode,
    availability: HeadingSourceStatus
): String {
    return when (mode) {
        CompassHeadingSourceMode.TYPE_HEADING -> {
            if (availability.headingSensorAvailable) "Unavailable"
            else "Not exposed by this watch"
        }
        CompassHeadingSourceMode.ROTATION_VECTOR -> {
            if (availability.rotationVectorAvailable) "Unavailable"
            else "Not exposed by this watch"
        }
        CompassHeadingSourceMode.MAGNETOMETER -> {
            if (availability.magAccelFallbackAvailable) "Unavailable"
            else "Magnetometer fallback unavailable"
        }
        CompassHeadingSourceMode.AUTO -> "Unavailable"
    }
}

internal fun northReferenceModeFromSetting(mode: String): NorthReferenceMode {
    return when (mode) {
        SettingsRepository.NORTH_REFERENCE_MAGNETIC -> NorthReferenceMode.MAGNETIC
        else -> NorthReferenceMode.TRUE
    }
}

internal fun headingSourceModeFromSetting(mode: String): CompassHeadingSourceMode {
    return when (mode) {
        SettingsRepository.COMPASS_HEADING_SOURCE_TYPE_HEADING -> CompassHeadingSourceMode.TYPE_HEADING
        SettingsRepository.COMPASS_HEADING_SOURCE_ROTATION_VECTOR -> CompassHeadingSourceMode.ROTATION_VECTOR
        SettingsRepository.COMPASS_HEADING_SOURCE_MAGNETOMETER -> CompassHeadingSourceMode.MAGNETOMETER
        else -> CompassHeadingSourceMode.AUTO
    }
}

internal fun headingSourceSettingFromMode(mode: CompassHeadingSourceMode): String {
    return when (mode) {
        CompassHeadingSourceMode.TYPE_HEADING ->
            SettingsRepository.COMPASS_HEADING_SOURCE_TYPE_HEADING
        CompassHeadingSourceMode.ROTATION_VECTOR ->
            SettingsRepository.COMPASS_HEADING_SOURCE_ROTATION_VECTOR
        CompassHeadingSourceMode.MAGNETOMETER ->
            SettingsRepository.COMPASS_HEADING_SOURCE_MAGNETOMETER
        CompassHeadingSourceMode.AUTO ->
            SettingsRepository.COMPASS_HEADING_SOURCE_AUTO
    }
}

internal fun compassSettingsModeLabel(mode: String): String {
    return when (mode) {
        SettingsRepository.COMPASS_SETTINGS_MODE_ADVANCED -> "Advanced..."
        else -> "Automatic (recommended)"
    }
}

internal fun compassProviderTypeFromSetting(mode: String): CompassProviderType {
    return when (mode) {
        SettingsRepository.COMPASS_PROVIDER_SENSOR_MANAGER -> CompassProviderType.SENSOR_MANAGER
        else -> CompassProviderType.GOOGLE_FUSED
    }
}

internal fun compassProviderModeLabel(mode: String): String {
    return when (compassProviderTypeFromSetting(mode)) {
        CompassProviderType.SENSOR_MANAGER -> "Custom sensors"
        CompassProviderType.GOOGLE_FUSED -> "Google Fused (default)"
    }
}

internal fun compassProviderStatusLabel(
    requestedMode: String,
    activeProviderType: CompassProviderType
): String {
    val requestedType = compassProviderTypeFromSetting(requestedMode)
    if (requestedType == activeProviderType) {
        return compassProviderModeLabel(requestedMode)
    }
    return when {
        requestedType == CompassProviderType.GOOGLE_FUSED &&
            activeProviderType == CompassProviderType.SENSOR_MANAGER ->
            "Google Fused -> Custom backup"
        requestedType == CompassProviderType.SENSOR_MANAGER &&
            activeProviderType == CompassProviderType.GOOGLE_FUSED ->
            "Custom sensors -> Google Fused"
        else -> compassProviderModeLabel(requestedMode)
    }
}

internal fun northReferenceModeLabel(mode: String): String {
    return when (mode) {
        SettingsRepository.NORTH_REFERENCE_MAGNETIC -> "Magnetic north"
        else -> "True north"
    }
}

internal fun northReferenceStatusSecondaryLabel(
    requestedMode: String,
    status: NorthReferenceStatus
): String {
    val requestedLabel = northReferenceModeLabel(requestedMode)
    return when {
        status.waitingForDeclination && status.requestedMode == NorthReferenceMode.TRUE ->
            "$requestedLabel (waiting for location)"
        status.waitingForDeclination && status.requestedMode == NorthReferenceMode.MAGNETIC ->
            "$requestedLabel (needs declination)"
        else -> "$requestedLabel ready"
    }
}

internal fun headingSourceModeLabel(mode: String): String {
    return headingSourceModeLabel(headingSourceModeFromSetting(mode))
}

internal fun headingSourceModeLabel(mode: CompassHeadingSourceMode): String {
    return when (mode) {
        CompassHeadingSourceMode.TYPE_HEADING -> "TYPE_HEADING"
        CompassHeadingSourceMode.ROTATION_VECTOR -> "Rotation vector"
        CompassHeadingSourceMode.MAGNETOMETER -> "Magnetometer"
        CompassHeadingSourceMode.AUTO -> "Auto (recommended)"
    }
}

internal val COMPATIBILITY_CANDIDATES = listOf(
    CompassHeadingSourceMode.TYPE_HEADING,
    CompassHeadingSourceMode.ROTATION_VECTOR,
    CompassHeadingSourceMode.MAGNETOMETER
)

private val SensorStatusChipBackground = Color(0xFF33465F)
private val SensorStatusChipContent = Color(0xFFF4F7FB)
private val SensorStatusChipSecondary = Color(0xFFD0DEEE)

@Composable
internal fun SensorStatusPanel(
    status: HeadingSourceStatus,
    northReferenceStatus: NorthReferenceStatus
) {
    val requested = sensorModeLabel(status.requestedMode)
    val active = sensorActiveLabel(status.activeSource)
    val headingAvailability = "Heading sensor: ${status.headingSensorAvailable.yesNoLabel()}"
    val rotationVectorAvailability =
        "Rotation vector: ${status.rotationVectorAvailable.yesNoLabel()}"
    val magnetometerAvailability =
        "Magnetometer: ${status.magAccelFallbackAvailable.yesNoLabel()}"
    val northStatus =
        "North: ${northReferenceStatus.requestedMode.userLabel()} -> ${northReferenceStatus.effectiveMode.userLabel()}"
    val declinationStatus = when {
        northReferenceStatus.declinationAvailable -> "Declination: ready"
        northReferenceStatus.waitingForDeclination -> "Declination: waiting"
        else -> "Declination: not needed"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 98.dp)
            .background(SensorStatusChipBackground, RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Sensor status",
                style = MaterialTheme.typography.titleMedium,
                color = SensorStatusChipContent
            )
            Text(
                text = "Requested: $requested",
                style = MaterialTheme.typography.bodySmall,
                color = SensorStatusChipSecondary
            )
            Text(
                text = "Active: $active",
                style = MaterialTheme.typography.bodySmall,
                color = SensorStatusChipSecondary
            )
            Text(
                text = headingAvailability,
                style = MaterialTheme.typography.bodySmall,
                color = SensorStatusChipSecondary
            )
            Text(
                text = rotationVectorAvailability,
                style = MaterialTheme.typography.bodySmall,
                color = SensorStatusChipSecondary
            )
            Text(
                text = magnetometerAvailability,
                style = MaterialTheme.typography.bodySmall,
                color = SensorStatusChipSecondary
            )
            Text(
                text = northStatus,
                style = MaterialTheme.typography.bodySmall,
                color = SensorStatusChipSecondary
            )
            Text(
                text = declinationStatus,
                style = MaterialTheme.typography.bodySmall,
                color = SensorStatusChipSecondary
            )
        }
    }
}

private fun sensorModeLabel(mode: CompassHeadingSourceMode): String {
    return when (mode) {
        CompassHeadingSourceMode.AUTO -> "Auto"
        CompassHeadingSourceMode.TYPE_HEADING -> "Type heading"
        CompassHeadingSourceMode.ROTATION_VECTOR -> "Rotation vector"
        CompassHeadingSourceMode.MAGNETOMETER -> "Magnetometer"
    }
}

private fun sensorActiveLabel(source: HeadingSource): String {
    return when (source) {
        HeadingSource.NONE -> "Unavailable"
        HeadingSource.FUSED_ORIENTATION -> "Google fused"
        HeadingSource.HEADING_SENSOR -> "Type heading"
        HeadingSource.ROTATION_VECTOR -> "Rotation vector"
        HeadingSource.MAG_ACCEL_FALLBACK -> "Mag fallback"
    }
}

private fun NorthReferenceMode.userLabel(): String {
    return when (this) {
        NorthReferenceMode.TRUE -> "True"
        NorthReferenceMode.MAGNETIC -> "Magnetic"
    }
}

private fun Boolean.yesNoLabel(): String = if (this) "Yes" else "No"

internal const val COMPATIBILITY_PREPARE_DELAY_MS = 350L
internal const val COMPATIBILITY_SWITCH_SETTLE_MS = 1_700L
internal const val COMPATIBILITY_PHASE_COUNT = 2
internal const val COMPATIBILITY_STILL_SAMPLE_COUNT = 10
internal const val COMPATIBILITY_TURN_SAMPLE_COUNT = 12
internal const val COMPATIBILITY_TURN_PREPARE_MS = 900L
internal const val COMPATIBILITY_SAMPLE_INTERVAL_MS = 100L
