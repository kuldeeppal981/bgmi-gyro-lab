package com.bgmigyrolab.app.gyro

import com.bgmigyrolab.app.data.Calibration
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Integrates every gyro sample (fed from the sensor thread) into yaw/pitch angles.
 * The UI reads [angles] once per frame, so no sample is skipped between frames.
 */
class AimIntegrator {

    private val lock = Any()
    private var yawRad = 0f
    private var pitchRad = 0f
    private var lastTs = 0L

    @Volatile
    var calibration: Calibration = Calibration()

    fun reset() {
        synchronized(lock) {
            yawRad = 0f
            pitchRad = 0f
            lastTs = 0L
        }
    }

    /** Current (yaw, pitch) in radians. */
    fun angles(): Pair<Float, Float> = synchronized(lock) { yawRad to pitchRad }

    fun onSample(sample: GyroSample) {
        val cal = calibration
        synchronized(lock) {
            val prev = lastTs
            lastTs = sample.timestampNanos
            if (prev <= 0L) return
            val dt = (sample.timestampNanos - prev) / 1_000_000_000f
            if (dt <= 0f || dt > 0.08f) return

            val wx = sample.x - cal.biasX
            val wy = sample.y - cal.biasY
            var yawRate = if (cal.landscape) -wx else -wy
            var pitchRate = if (cal.landscape) -wy else -wx

            // Deadzone on the aim axes only (roll ignored). Between 1x and 2x the deadzone the
            // speed ramps up; above 2x it's untouched, so normal turns are never slowed down.
            val dz = cal.deadzoneRadPerSec
            val mag = hypot(yawRate, pitchRate)
            if (mag <= dz) return
            val scale = if (mag >= 2f * dz) 1f else (mag - dz) / dz
            yawRate *= scale
            pitchRate *= scale

            if (cal.invertYaw) yawRate = -yawRate
            if (cal.invertPitch) pitchRate = -pitchRate
            yawRad += yawRate * dt
            pitchRad += pitchRate * dt
        }
    }
}

fun hypot2(a: Float, b: Float): Float = hypot(a, b)

fun hypot3(a: Float, b: Float, c: Float): Float = sqrt(a * a + b * b + c * c)
