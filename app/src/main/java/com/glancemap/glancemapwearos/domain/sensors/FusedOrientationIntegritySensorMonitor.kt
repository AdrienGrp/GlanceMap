package com.glancemap.glancemapwearos.domain.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** Supplies magnetometer-independent relative turns and magnetic integrity to Google Fused. */
internal class FusedOrientationIntegritySensorMonitor(
    context: Context,
) : SensorEventListener {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gameRotationVector =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val gameQuaternion = FloatArray(4)

    private var started = false
    private var previousGameQuaternion: OrientationQuaternion? = null
    private var previousGameSampleAtElapsedMs = 0L
    private var accumulatedRelativeHeadingDeg = 0f
    private var onRelativeHeading: ((Float, Long) -> Unit)? = null
    private var onMagneticField: ((Float, Long) -> Unit)? = null

    val relativeSensorAvailable: Boolean
        get() = gameRotationVector != null

    val magnetometerAvailable: Boolean
        get() = magnetometer != null

    fun start(
        handler: Handler,
        lowPower: Boolean,
        onRelativeHeading: (Float, Long) -> Unit,
        onMagneticField: (Float, Long) -> Unit,
    ) {
        stop()
        this.onRelativeHeading = onRelativeHeading
        this.onMagneticField = onMagneticField
        previousGameQuaternion = null
        previousGameSampleAtElapsedMs = 0L
        accumulatedRelativeHeadingDeg = 0f
        val relativePeriodUs =
            if (lowPower) INTEGRITY_LOW_POWER_PERIOD_US else INTEGRITY_RELATIVE_PERIOD_US
        val magneticPeriodUs =
            if (lowPower) INTEGRITY_LOW_POWER_PERIOD_US else INTEGRITY_MAGNETIC_PERIOD_US
        val relativeRegistered =
            gameRotationVector?.let { sensor ->
                sensorManager.registerListener(this, sensor, relativePeriodUs, handler)
            } == true
        val magneticRegistered =
            magnetometer?.let { sensor ->
                sensorManager.registerListener(this, sensor, magneticPeriodUs, handler)
            } == true
        started = relativeRegistered || magneticRegistered
    }

    fun stop() {
        if (started) sensorManager.unregisterListener(this)
        started = false
        previousGameQuaternion = null
        previousGameSampleAtElapsedMs = 0L
        accumulatedRelativeHeadingDeg = 0f
        onRelativeHeading = null
        onMagneticField = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!started) return
        val atElapsedMs =
            (event.timestamp / NANOS_PER_MILLISECOND).takeIf { it > 0L }
                ?: SystemClock.elapsedRealtime()
        when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR -> publishRelativeHeading(event, atElapsedMs)
            Sensor.TYPE_MAGNETIC_FIELD -> publishMagneticField(event, atElapsedMs)
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor,
        accuracy: Int,
    ) = Unit

    private fun publishRelativeHeading(
        event: SensorEvent,
        atElapsedMs: Long,
    ) {
        if (event.values.size < 3) return
        SensorManager.getQuaternionFromVector(gameQuaternion, event.values)
        val currentQuaternion =
            OrientationQuaternion(
                w = gameQuaternion[0],
                x = gameQuaternion[1],
                y = gameQuaternion[2],
                z = gameQuaternion[3],
            )
        val previousQuaternion = previousGameQuaternion
        val previousAtElapsedMs = previousGameSampleAtElapsedMs
        previousGameQuaternion = currentQuaternion
        previousGameSampleAtElapsedMs = atElapsedMs
        if (previousQuaternion != null && previousAtElapsedMs > 0L) {
            val elapsedMs = (atElapsedMs - previousAtElapsedMs).coerceAtLeast(1L)
            val headingStepDeg =
                gameRotationHeadingDeltaDeg(
                    previous = previousQuaternion,
                    current = currentQuaternion,
                )
            if (
                headingStepDeg != null &&
                isPlausibleRelativeHeadingStep(headingStepDeg, elapsedMs)
            ) {
                accumulatedRelativeHeadingDeg =
                    normalize360Deg(accumulatedRelativeHeadingDeg + headingStepDeg)
            }
        }
        onRelativeHeading?.invoke(accumulatedRelativeHeadingDeg, atElapsedMs)
    }

    private fun publishMagneticField(
        event: SensorEvent,
        atElapsedMs: Long,
    ) {
        if (event.values.size < 3) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return
        onMagneticField?.invoke(sqrt(x * x + y * y + z * z), atElapsedMs)
    }
}

internal data class OrientationQuaternion(
    val w: Float,
    val x: Float,
    val y: Float,
    val z: Float,
)

/**
 * Returns the heading component of the world-frame rotation between two game-RV samples.
 * Swing/tilt is discarded, avoiding Euler azimuth instability when the watch approaches vertical.
 */
internal fun gameRotationHeadingDeltaDeg(
    previous: OrientationQuaternion,
    current: OrientationQuaternion,
): Float? {
    if (!previous.isFinite() || !current.isFinite()) return null
    val deltaW =
        current.w * previous.w +
            current.x * previous.x +
            current.y * previous.y +
            current.z * previous.z
    val deltaZ =
        -current.w * previous.z -
            current.x * previous.y +
            current.y * previous.x +
            current.z * previous.w
    val twistNorm = sqrt(deltaW * deltaW + deltaZ * deltaZ)
    return if (!twistNorm.isFinite() || twistNorm < MIN_TWIST_NORM) {
        null
    } else {
        val quaternionTwistDeg =
            Math.toDegrees(2.0 * atan2(deltaZ.toDouble(), deltaW.toDouble())).toFloat()
        normalizeSignedAngleDeg(-quaternionTwistDeg)
    }
}

internal fun isPlausibleRelativeHeadingStep(
    headingStepDeg: Float,
    elapsedMs: Long,
): Boolean {
    if (!headingStepDeg.isFinite() || elapsedMs <= 0L) return false
    val maximumStepDeg =
        (
            RELATIVE_STEP_BASE_ALLOWANCE_DEG +
                RELATIVE_STEP_MAX_RATE_DEG_PER_SEC * elapsedMs / 1_000f
        ).coerceAtMost(RELATIVE_STEP_ABSOLUTE_MAX_DEG)
    return abs(headingStepDeg) <= maximumStepDeg
}

private fun OrientationQuaternion.isFinite(): Boolean = w.isFinite() && x.isFinite() && y.isFinite() && z.isFinite()

private fun normalizeSignedAngleDeg(angleDeg: Float): Float = ((angleDeg + 540f) % 360f) - 180f

private const val INTEGRITY_RELATIVE_PERIOD_US = 20_000
private const val INTEGRITY_MAGNETIC_PERIOD_US = 100_000
private const val INTEGRITY_LOW_POWER_PERIOD_US = 200_000
private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val MIN_TWIST_NORM = 0.001f
private const val RELATIVE_STEP_BASE_ALLOWANCE_DEG = 5f
private const val RELATIVE_STEP_MAX_RATE_DEG_PER_SEC = 1_080f
private const val RELATIVE_STEP_ABSOLUTE_MAX_DEG = 120f
