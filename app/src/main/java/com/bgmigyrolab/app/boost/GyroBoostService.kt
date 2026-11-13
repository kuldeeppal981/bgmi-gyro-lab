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
import android.os.PowerManager
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
 *
 * Boost and recording are independent: boost can run with no recording, and recording
 * is started/stopped on its own (starting it also starts boost if needed).
 */
class GyroBoostService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var gyro: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Null while not recording. Read on the sensor thread, written on the main thread.
    @Volatile
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
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_RECORDING -> {
                if (thread == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                stopRecording()
                refreshNotification()
                return START_STICKY
            }
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        if (thread == null) registerSensors()
        if (intent?.action == ACTION_START_RECORDING) startRecording()
        _running.value = true
        refreshNotification()
        return START_STICKY
    }

    private fun registerSensors() {
        val t = HandlerThread("gyro-boost", Process.THREAD_PRIORITY_URGENT_DISPLAY).apply { start() }
        thread = t
        val h = Handler(t.looper)
        handler = h
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        // Keeps the CPU awake so the sensor thread isn't delayed while the game holds the screen.
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bgmigyrolab:boost")
            ?.apply {
                setReferenceCounted(false)
                acquire(MAX_BOOST_MS)
            }
        // Gyro is what aim reads; accel + game rotation vector cover engines that fuse them.
        for (sensorType in BOOSTED_SENSORS) {
            val sensor = sensorManager.getDefaultSensor(sensorType) ?: continue
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST, 0, h)
        }
        h.postDelayed(tick, 1000L)
    }

    private fun startRecording() {
        if (recorder != null) return
        val g = gyro ?: return
        val h = handler ?: return
        val maxHz = if (g.minDelay > 0) 1_000_000f / g.minDelay else 0f
        recorder = SessionRecorder(applicationContext, "${g.vendor} ${g.name} • max ${maxHz.toInt()} Hz", maxHz)
        // Second gyro listener at normal game rate: shows what rate a game like BGMI actually gets.
        sensorManager.registerListener(gameRateListener, g, SensorManager.SENSOR_DELAY_GAME, 0, h)
        _recording.value = true
    }

    private fun stopRecording() {
        val rec = recorder ?: return
        recorder = null
        sensorManager.unregisterListener(gameRateListener)
        _recording.value = false
        saveReport(rec)
    }

    private fun refreshNotification() {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification()) }
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
        stopRecording()
        handler?.removeCallbacks(tick)
        thread?.quitSafely()
        thread = null
        handler = null
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        _recording.value = false
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
        val isRecording = recorder != null
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
        val recordAction = if (isRecording) {
            Notification.Action.Builder(
                null,
                "Stop recording",
                PendingIntent.getService(
                    this,
                    3,
                    Intent(this, GyroBoostService::class.java).setAction(ACTION_STOP_RECORDING),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            ).build()
        } else {
            Notification.Action.Builder(
                null,
                "Start recording",
                PendingIntent.getForegroundService(
                    this,
                    4,
                    Intent(this, GyroBoostService::class.java).setAction(ACTION_START_RECORDING),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            ).build()
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(if (isRecording) "Gyro boost on • recording" else "Gyro boost on")
            .setContentText(
                if (isRecording) "Recording session. Tap Stop recording for your report."
                else "Not recording. Tap Start recording when you start a match.",
            )
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(recordAction)
            .addAction(Notification.Action.Builder(null, "Stop boost", stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "gyro_boost"
        private const val NOTIFICATION_ID = 7
        private const val REPORT_NOTIFICATION_ID = 8
        private const val MIN_REPORT_SECONDS = 20
        private const val ACTION_STOP = "com.bgmigyrolab.app.STOP_BOOST"
        private const val ACTION_START_RECORDING = "com.bgmigyrolab.app.START_RECORDING"
        private const val ACTION_STOP_RECORDING = "com.bgmigyrolab.app.STOP_RECORDING"
        private const val MAX_BOOST_MS = 4L * 60 * 60 * 1000
        private val BOOSTED_SENSORS = intArrayOf(
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GAME_ROTATION_VECTOR,
        )

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _recording = MutableStateFlow(false)

        /** True while a session is being recorded (boost can run without this). */
        val recording: StateFlow<Boolean> = _recording.asStateFlow()

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

        /** Starts recording; also starts boost if it isn't running. */
        fun startRecording(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, GyroBoostService::class.java).setAction(ACTION_START_RECORDING),
            )
        }

        /** Stops recording and saves the report; boost keeps running. */
        fun stopRecording(context: Context) {
            context.startService(Intent(context, GyroBoostService::class.java).setAction(ACTION_STOP_RECORDING))
        }
    }
}
