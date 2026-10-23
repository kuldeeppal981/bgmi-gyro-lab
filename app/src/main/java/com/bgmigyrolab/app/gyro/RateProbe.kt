package com.bgmigyrolab.app.gyro

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import kotlinx.coroutines.delay

data class RateResult(
    val hz: Float,
    val deliveryMedianMs: Float?,
    val samples: Int,
)

/**
 * Listens to the gyro the way a game would (at a requested rate, e.g. SENSOR_DELAY_GAME)
 * and reports what rate actually arrives. Used to check whether Gyro Boost raises the rate
 * other apps receive.
 */
object RateProbe {

    suspend fun measure(context: Context, samplingPeriodUs: Int, durationMs: Long): RateResult? {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: return null
        val thread = HandlerThread("rate-probe").apply { start() }
        val samples = ArrayList<GyroSample>()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val s = GyroSample(
                    timestampNanos = event.timestamp,
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2],
                    receivedNanos = SystemClock.elapsedRealtimeNanos(),
                )
                synchronized(samples) { samples.add(s) }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, samplingPeriodUs, 0, Handler(thread.looper))
        try {
            delay(durationMs)
        } finally {
            sm.unregisterListener(listener)
            thread.quitSafely()
        }

        val all = synchronized(samples) { samples.toList() }
        val start = all.firstOrNull()?.timestampNanos ?: return RateResult(0f, null, 0)
        // Skip the first 300 ms while the sensor settles on its new rate.
        val kept = all.filter { it.timestampNanos - start > 300_000_000L }
        if (kept.size < 2) return RateResult(0f, null, kept.size)
        val spanSec = (kept.last().timestampNanos - kept.first().timestampNanos) / 1_000_000_000f
        val hz = if (spanSec > 0f) (kept.size - 1) / spanSec else 0f
        val delivery = kept.mapNotNull { it.deliveryMs }.sorted()
        val median = delivery.takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }
        return RateResult(hz, median, kept.size)
    }
}
