package com.bgmigyrolab.app.gyro

data class GyroSample(
    val timestampNanos: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    /** elapsedRealtimeNanos when the app received the event; minus timestampNanos = sensor-to-app delay. */
    val receivedNanos: Long = 0L,
) {
    val magnitude: Float
        get() = kotlin.math.sqrt(x * x + y * y + z * z)

    /** Sensor-to-app delivery delay in ms, or null when the clock bases don't line up on this device. */
    val deliveryMs: Float?
        get() {
            val d = receivedNanos - timestampNanos
            return if (receivedNanos > 0L && d in 0L..1_000_000_000L) d / 1_000_000f else null
        }
}
