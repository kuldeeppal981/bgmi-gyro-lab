package com.bgmigyrolab.app.gyro

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GyroRepository(context: Context) : SensorEventListener, Choreographer.FrameCallback {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val choreographer = Choreographer.getInstance()

    // Sensor events get their own high-priority thread so they never queue behind UI work.
    private val sensorThread = HandlerThread("gyro", Process.THREAD_PRIORITY_URGENT_DISPLAY).apply { start() }
    private val sensorHandler = Handler(sensorThread.looper)

    private val _sample = MutableStateFlow<GyroSample?>(null)
    val sample: StateFlow<GyroSample?> = _sample.asStateFlow()

    private val _vsyncNanos = MutableStateFlow(0L)
    val vsyncNanos: StateFlow<Long> = _vsyncNanos.asStateFlow()

    val hasGyroscope: Boolean get() = gyroscope != null

    /** Hardware max rate from the sensor's min delay, 0 if unknown. */
    val maxRateHz: Float
        get() = gyroscope?.minDelay?.takeIf { it > 0 }?.let { 1_000_000f / it } ?: 0f

    val sensorName: String get() = gyroscope?.let { "${it.vendor} ${it.name}" } ?: "none"

    /** Called on the sensor thread for every sample, so integrators see all of them. */
    @Volatile
    var onSample: ((GyroSample) -> Unit)? = null

    private val lock = Any()
    private val ring = ArrayDeque<GyroSample>(RING_CAPACITY)
    private var listening = false

    fun start() {
        if (listening) return
        val sensor = gyroscope ?: return
        listening = true
        // maxReportLatencyUs = 0 disables FIFO batching, which otherwise delivers samples in late bursts.
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST, 0, sensorHandler)
        choreographer.postFrameCallback(this)
    }

    fun stop() {
        if (!listening) return
        listening = false
        sensorManager.unregisterListener(this)
        choreographer.removeFrameCallback(this)
    }

    fun release() {
        stop()
        sensorThread.quitSafely()
    }

    fun snapshot(): List<GyroSample> {
        synchronized(lock) {
            return ring.toList()
        }
    }

    fun clearBuffer() {
        synchronized(lock) {
            ring.clear()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return
        val sample = GyroSample(
            timestampNanos = event.timestamp,
            x = event.values[0],
            y = event.values[1],
            z = event.values[2],
            receivedNanos = SystemClock.elapsedRealtimeNanos(),
        )
        synchronized(lock) {
            if (ring.size >= RING_CAPACITY) ring.removeFirst()
            ring.addLast(sample)
        }
        onSample?.invoke(sample)
        _sample.value = sample
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun doFrame(frameTimeNanos: Long) {
        if (!listening) return
        val vsync = if (frameTimeNanos > 0) frameTimeNanos else SystemClock.elapsedRealtimeNanos()
        _vsyncNanos.value = vsync
        choreographer.postFrameCallback(this)
    }

    companion object {
        private const val RING_CAPACITY = 4000
    }
}
