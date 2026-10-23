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
        val handler = Handler(t.looper)
        // Gyro is what aim reads; accel + game rotation vector cover engines that fuse them.
        for (sensorType in BOOSTED_SENSORS) {
            val sensor = sensorManager.getDefaultSensor(sensorType) ?: continue
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST, 0, handler)
        }
    }

    override fun onSensorChanged(event: SensorEvent) = Unit

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        thread?.quitSafely()
        thread = null
        _running.value = false
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Gyro boost", NotificationManager.IMPORTANCE_LOW),
            )
        }
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
            .setContentText("Gyro held at max rate. Open BGMI and play.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "gyro_boost"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_STOP = "com.bgmigyrolab.app.STOP_BOOST"
        private val BOOSTED_SENSORS = intArrayOf(
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GAME_ROTATION_VECTOR,
        )

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, GyroBoostService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GyroBoostService::class.java))
        }
    }
}
