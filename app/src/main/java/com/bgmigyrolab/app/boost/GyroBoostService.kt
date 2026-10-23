package com.bgmigyrolab.app.boost

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import java.io.File
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.bgmigyrolab.app.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps motion sensors open at their fastest rate with batching off while you play.
 * On most phones the sensor hub runs at the fastest rate any app requests and every
 * listener gets that stream, so BGMI receives denser, fresher gyro samples.
 * It does not touch BGMI itself.
 */
class GyroBoostService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var recorder: SessionRecorder? = null

    // Second gyro listener at normal game rate: shows what rate a game like BGMI actually gets.
    private val gameRateListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            recorder?.onGameSample()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val tick = object : Runnable {
        override fun run() {
            recorder?.tick()
            handler?.postDelayed(this, 1000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        if (thread == null) registerSensors()
        _running.value = true
        return START_STICKY
    }

    private fun registerSensors() {
        val t = HandlerThread("gyro-boost", Process.THREAD_PRIORITY_URGENT_DISPLAY).apply { start() }
        thread = t
        val h = Handler(t.looper)
        handler = h
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (gyro != null) {
            val maxHz = if (gyro.minDelay > 0) 1_000_000f / gyro.minDelay else 0f
            recorder = SessionRecorder(applicationContext, "${gyro.vendor} ${gyro.name} • max ${maxHz.toInt()} Hz", maxHz)
            sensorManager.registerListener(gameRateListener, gyro, SensorManager.SENSOR_DELAY_GAME, 0, h)
        }
        // Gyro is what aim reads; accel + game rotation vector cover engines that fuse them.
        for (sensorType in BOOSTED_SENSORS) {
            val sensor = sensorManager.getDefaultSensor(sensorType) ?: continue
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST, 0, h)
        }
        h.postDelayed(tick, 1000L)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return
        recorder?.onFastSample(
            event.timestamp,
            event.values[0],
            event.values[1],
            event.values[2],
            SystemClock.elapsedRealtimeNanos(),
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        sensorManager.unregisterListener(gameRateListener)
        handler?.removeCallbacks(tick)
        thread?.quitSafely()
        thread = null
        handler = null
        recorder?.let { saveReport(it) }
        recorder = null
        _running.value = false
        super.onDestroy()
    }

    private fun saveReport(rec: SessionRecorder) {
        // Short runs (e.g. the 8 s Boost check) aren't play sessions.
        if (rec.durationSec < MIN_REPORT_SECONDS) return
        val text = rec.buildReport()
        runCatching { reportFile(this).writeText(text) }
        _lastReport.value = text
        val open = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_REPORT, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, ensureChannel())
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Session report ready")
            .setContentText("Tap to see how the gyro behaved while you played.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(REPORT_NOTIFICATION_ID, n) }
    }

    private fun ensureChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Gyro boost", NotificationManager.IMPORTANCE_LOW),
            )
        }
        return CHANNEL_ID
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, GyroBoostService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Gyro boost on")
            .setContentText("Recording session. Play BGMI, then tap Stop for your report.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "gyro_boost"
        private const val NOTIFICATION_ID = 7
        private const val REPORT_NOTIFICATION_ID = 8
        private const val MIN_REPORT_SECONDS = 20
        private const val ACTION_STOP = "com.bgmigyrolab.app.STOP_BOOST"
        private val BOOSTED_SENSORS = intArrayOf(
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GAME_ROTATION_VECTOR,
        )

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _lastReport = MutableStateFlow<String?>(null)

        /** Most recent session report; falls back to the saved file after an app restart. */
        val lastReport: StateFlow<String?> = _lastReport.asStateFlow()

        private fun reportFile(context: Context) = File(context.filesDir, "last_session_report.txt")

        fun loadSavedReport(context: Context) {
            if (_lastReport.value != null) return
            val f = reportFile(context)
            if (f.exists()) _lastReport.value = runCatching { f.readText() }.getOrNull()
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, GyroBoostService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GyroBoostService::class.java))
        }
    }
}
