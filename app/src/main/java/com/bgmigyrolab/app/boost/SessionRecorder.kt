package com.bgmigyrolab.app.boost

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.view.Display
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

/**
 * Records gyro health once per second while Gyro Boost runs (i.e. while you play),
 * then turns it into a text report. Sensor callbacks and [tick] run on the boost thread;
 * [buildReport] runs on the main thread, so shared state sits behind [lock].
 */
class SessionRecorder(
    private val context: Context,
    private val sensorInfo: String,
    maxRateHz: Float,
) {

    private class Second(
        val t: Int,
        val hz: Float,
        val gameHz: Float,
        val deliveryMedianMs: Float?,
        val deliveryP95Ms: Float?,
        val deliveryMaxMs: Float?,
        val gaps: Int,
        val maxGapMs: Float,
        val motionDegPerSec: Float,
        val peakMotionDegPerSec: Float,
        val batteryTempC: Float?,
        val thermal: Int,
        val refreshHz: Float,
        val charging: Boolean,
        val headroom: Float?,
    )

    private val lock = Any()
    private val gapThresholdNs = maxOf(8_000_000L, if (maxRateHz > 0f) (3_000_000_000f / maxRateHz).toLong() else 8_000_000L)
    private val startWallMs = System.currentTimeMillis()
    private val startBattery = batteryPercent()
    private val seconds = ArrayList<Second>()

    // Current one-second bucket.
    private var count = 0
    private var gameCount = 0
    private val deliveries = ArrayList<Float>(512)
    private var lastTs = 0L
    private var gaps = 0
    private var maxGapNs = 0L
    private var motionSum = 0f
    private var motionPeak = 0f

    fun onFastSample(timestampNanos: Long, x: Float, y: Float, z: Float, receivedNanos: Long) {
        synchronized(lock) {
            count++
            val d = receivedNanos - timestampNanos
            if (d in 0L..1_000_000_000L) deliveries.add(d / 1_000_000f)
            if (lastTs > 0L) {
                val gap = timestampNanos - lastTs
                if (gap > gapThresholdNs) gaps++
                if (gap > maxGapNs) maxGapNs = gap
            }
            lastTs = timestampNanos
            val deg = Math.toDegrees(sqrt(x * x + y * y + z * z).toDouble()).toFloat()
            motionSum += deg
            if (deg > motionPeak) motionPeak = deg
        }
    }

    fun onGameSample() {
        synchronized(lock) { gameCount++ }
    }

    /** Close the current one-second bucket. Called every second on the boost thread. */
    fun tick() {
        val battery: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        val charging = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val headroom = thermalHeadroom()
        val thermal = thermalStatus()
        val refresh = refreshHz()
        synchronized(lock) {
            val sorted = deliveries.sorted()
            seconds.add(
                Second(
                    t = seconds.size,
                    hz = count.toFloat(),
                    gameHz = gameCount.toFloat(),
                    deliveryMedianMs = sorted.getOrNull(sorted.size / 2),
                    deliveryP95Ms = sorted.getOrNull((sorted.size * 95) / 100),
                    deliveryMaxMs = sorted.lastOrNull(),
                    gaps = gaps,
                    maxGapMs = maxGapNs / 1_000_000f,
                    motionDegPerSec = if (count > 0) motionSum / count else 0f,
                    peakMotionDegPerSec = motionPeak,
                    batteryTempC = temp,
                    thermal = thermal,
                    refreshHz = refresh,
                    charging = charging,
                    headroom = headroom,
                ),
            )
            count = 0
            gameCount = 0
            deliveries.clear()
            gaps = 0
            maxGapNs = 0L
            motionSum = 0f
            motionPeak = 0f
            if (seconds.size > MAX_SECONDS) seconds.removeAt(0)
        }
    }

    val durationSec: Int get() = synchronized(lock) { seconds.size }

    fun buildReport(): String {
        val rows = synchronized(lock) { seconds.toList() }
        // Drop the first and last second: they're partial buckets.
        val s = if (rows.size > 4) rows.subList(1, rows.size - 1) else rows
        if (s.isEmpty()) return "Session too short to report."

        val medHz = s.map { it.hz }.sorted()[s.size / 2]
        val avgHz = s.map { it.hz }.average().toFloat()
        val minHz = s.minOf { it.hz }
        val avgGameHz = s.map { it.gameHz }.average().toFloat()
        val minGameHz = s.minOf { it.gameHz }
        val allP95 = s.mapNotNull { it.deliveryP95Ms }.sorted()
        val delMedian = s.mapNotNull { it.deliveryMedianMs }.sorted().let { it.getOrNull(it.size / 2) }
        val delP95 = allP95.getOrNull((allP95.size * 95) / 100)
        val delMax = s.mapNotNull { it.deliveryMaxMs }.maxOrNull()
        val totalGaps = s.sumOf { it.gaps }
        val worstGap = s.maxOf { it.maxGapMs }
        val stutter = s.filter { it.hz < medHz * 0.7f || (it.deliveryP95Ms ?: 0f) > 20f || it.maxGapMs > 50f }
        val temps = s.mapNotNull { it.batteryTempC }
        val maxThermal = s.maxOf { it.thermal }
        val firstHot = s.firstOrNull { it.thermal >= THERMAL_MODERATE }
        val refreshCounts = s.groupingBy { Math.round(it.refreshHz) }.eachCount()
        val mainRefresh = refreshCounts.maxByOrNull { it.value }?.key ?: 0
        val active = s.count { it.motionDegPerSec > 30f }
        val endBattery = batteryPercent()
        val start = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(Date(startWallMs))

        fun f(v: Float, d: Int = 1) = String.format(Locale.US, "%.${d}f", v)
        fun mmss(t: Int) = String.format(Locale.US, "%02d:%02d", t / 60, t % 60)

        return buildString {
            appendLine("BGMI Gyro Lab session report (build ${com.bgmigyrolab.app.BuildConfig.VERSION_NAME})")
            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
            appendLine("Sensor: $sensorInfo")
            appendLine("Started: $start   Duration: ${mmss(rows.size)}")
            appendLine()
            appendLine("== Gyro ==")
            appendLine("Rate (boost listener): avg ${f(avgHz, 0)} Hz, median ${f(medHz, 0)}, lowest second ${f(minHz, 0)}")
            appendLine("Rate a game-rate app got: avg ${f(avgGameHz, 0)} Hz, lowest second ${f(minGameHz, 0)}")
            appendLine(
                "Sensor-to-app delay: median ${delMedian?.let { f(it) } ?: "-"} ms, " +
                    "p95 ${delP95?.let { f(it) } ?: "-"} ms, worst ${delMax?.let { f(it) } ?: "-"} ms",
            )
            appendLine("Dropouts: $totalGaps gaps, longest ${f(worstGap)} ms")
            appendLine("Stutter seconds: ${stutter.size} of ${s.size}")
            appendLine()
            appendLine("== Phone ==")
            appendLine("Screen refresh: mostly $mainRefresh Hz ${refreshCounts.entries.sortedBy { it.key }.joinToString { "(${it.key} Hz: ${it.value}s)" }}")
            appendLine(
                "Battery temp: " + if (temps.isEmpty()) "-" else
                    "${f(temps.first())} °C → ${f(temps.last())} °C (max ${f(temps.max())} °C)",
            )
            appendLine("Thermal status: max ${thermalName(maxThermal)}" + (firstHot?.let { " (from ${mmss(it.t)})" } ?: ""))
            val headrooms = s.mapNotNull { it.headroom }
            if (headrooms.isNotEmpty()) {
                appendLine("Thermal headroom: start ${f(headrooms.first(), 2)}, max ${f(headrooms.max(), 2)} (1.00 = throttling)")
            }
            appendLine("Charging: ${s.count { it.charging }}s of ${s.size}s")
            appendLine("Battery: $startBattery% → $endBattery%")
            appendLine("Aiming activity: moving >30°/s for ${active}s of ${s.size}s, peak ${f(s.maxOf { it.peakMotionDegPerSec }, 0)}°/s")
            appendLine()
            appendLine("== Findings ==")
            if (s.any { it.charging }) {
                appendLine("- You played while charging. Charging heats the phone and makes it throttle sooner (lower FPS, stutter).")
            }
            findings(avgHz, medHz, avgGameHz, delMedian, delP95, delMax, s.firstOrNull { (it.deliveryMaxMs ?: 0f) > 200f }?.t, totalGaps, stutter.size, s.size, maxThermal, firstHot?.t, mainRefresh, temps)
                .forEach { appendLine("- $it") }
            appendLine()
            appendLine("== Per minute ==")
            appendLine("min | Hz avg/low | game Hz | delay p95 | gaps | °C   | thermal | refresh | motion")
            s.groupBy { it.t / 60 }.forEach { (m, list) ->
                val p95 = list.mapNotNull { it.deliveryP95Ms }.maxOrNull()
                appendLine(
                    "${m + 1}".padEnd(3) + " | " +
                        "${f(list.map { it.hz }.average().toFloat(), 0)}/${f(list.minOf { it.hz }, 0)}".padEnd(10) + " | " +
                        f(list.map { it.gameHz }.average().toFloat(), 0).padEnd(7) + " | " +
                        (p95?.let { "${f(it)} ms" } ?: "-").padEnd(9) + " | " +
                        "${list.sumOf { it.gaps }}".padEnd(4) + " | " +
                        (list.mapNotNull { it.batteryTempC }.maxOrNull()?.let { f(it) } ?: "-").padEnd(4) + " | " +
                        thermalName(list.maxOf { it.thermal }).padEnd(7) + " | " +
                        "${Math.round(list.map { it.refreshHz }.average())} Hz".padEnd(7) + " | " +
                        "${f(list.map { it.motionDegPerSec }.average().toFloat(), 0)}°/s",
                )
            }
            if (stutter.isNotEmpty()) {
                appendLine()
                appendLine("== Worst seconds ==")
                stutter.sortedByDescending { it.maxGapMs + (it.deliveryP95Ms ?: 0f) }.take(8).forEach {
                    appendLine(
                        "${mmss(it.t)}  ${f(it.hz, 0)} Hz, delay p95 ${it.deliveryP95Ms?.let { d -> f(d) } ?: "-"} ms, " +
                            "longest gap ${f(it.maxGapMs)} ms, ${thermalName(it.thermal)}",
                    )
                }
            }
        }
    }

    private fun findings(
        avgHz: Float,
        medHz: Float,
        gameHz: Float,
        delMedian: Float?,
        delP95: Float?,
        delMax: Float?,
        burstAt: Int?,
        gaps: Int,
        stutterSec: Int,
        totalSec: Int,
        maxThermal: Int,
        hotAt: Int?,
        refresh: Int,
        temps: List<Float>,
    ): List<String> = buildList {
        if (gameHz >= avgHz * 0.8f) {
            add("With boost on, an app asking for normal game rate got ${gameHz.toInt()} Hz (full speed). Run Boost check to see what it gets without boost.")
        } else {
            add("Game-rate apps only got ${gameHz.toInt()} Hz while boost ran at ${avgHz.toInt()} Hz: this phone gives each app its own rate, so boost can't speed up BGMI.")
        }
        if (medHz < 300f) add("Gyro rate dropped to ${medHz.toInt()} Hz during play (normal is ~400). Something throttled the sensor.")
        when {
            delP95 != null && delP95 > 20f -> add("Sensor delay spiked (p95 ${delP95.toInt()} ms). That is lag you can feel; check battery saver / game mode.")
            delMedian != null && delMedian < 5f -> add("Sensor delay stayed low (${String.format(Locale.US, "%.1f", delMedian)} ms median): the gyro chip/driver is NOT the cause of lag.")
        }
        if (delMax != null && delMax > 200f && burstAt != null) {
            add(
                "At ${burstAt / 60}:${String.format(Locale.US, "%02d", burstAt % 60)} readings were held back up to ${delMax.toInt()} ms, then delivered in a burst. " +
                    "Usually the moment you switched apps and the system paused background apps. Set this app's battery to Unrestricted.",
            )
        }
        if (stutterSec > totalSec / 20) add("$stutterSec of $totalSec seconds had gyro stutter. See 'Worst seconds' for when.")
        else if (gaps == 0) add("No dropouts: the gyro stream was perfectly steady.")
        if (maxThermal >= THERMAL_MODERATE) {
            add("Phone reached thermal '${thermalName(maxThermal)}' at ${hotAt?.let { "${it / 60}:${String.format(Locale.US, "%02d", it % 60)}" }}. The system may cut CPU/GPU, dropping FPS, which feels like gyro lag.")
        }
        if (temps.size >= 2 && temps.last() - temps.first() > 6f) add("Battery heated up ${(temps.last() - temps.first()).toInt()} °C during the session.")
        if (refresh in 1..61) add("Screen ran at $refresh Hz. At 60 Hz each frame waits up to 16 ms. Enable 90/120 FPS in BGMI and high refresh in phone settings.")
        else if (refresh > 61) add("Screen ran at $refresh Hz, good for low input lag.")
    }

    /** Forecast of how close the phone is to throttling: 1.0 = severe throttling. API 30+. */
    private fun thermalHeadroom(): Float? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getSystemService(PowerManager::class.java)?.getThermalHeadroom(0)?.takeIf { !it.isNaN() }
        } else {
            null
        }

    private fun batteryPercent(): Int {
        val bm = context.getSystemService(BatteryManager::class.java)
        return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }

    private fun thermalStatus(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: -1
        } else {
            -1
        }

    private fun refreshHz(): Float =
        context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate ?: 0f

    private fun thermalName(status: Int): String = when (status) {
        0 -> "none"
        1 -> "light"
        2 -> "moderate"
        3 -> "severe"
        4 -> "critical"
        5 -> "emergency"
        6 -> "shutdown"
        else -> "n/a"
    }

    private companion object {
        const val THERMAL_MODERATE = 2
        const val MAX_SECONDS = 4 * 60 * 60
    }
}
