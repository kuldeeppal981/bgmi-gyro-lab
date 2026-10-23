package com.bgmigyrolab.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bgmigyrolab.app.data.Calibration
import com.bgmigyrolab.app.data.CalibrationStore
import com.bgmigyrolab.app.gyro.AimIntegrator
import com.bgmigyrolab.app.gyro.DelayAnalyzer
import com.bgmigyrolab.app.gyro.DelayGrade
import com.bgmigyrolab.app.gyro.DelayReport
import com.bgmigyrolab.app.gyro.GyroRepository
import com.bgmigyrolab.app.gyro.GyroSample
import com.bgmigyrolab.app.gyro.hypot2
import com.bgmigyrolab.app.gyro.hypot3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Screen {
    Home,
    DelayTest,
    Calibrate,
    Trainer,
    Tips,
}

enum class DelayPhase {
    Ready,
    HoldStill,
    Flick,
    Report,
}

enum class CalibratePhase {
    Ready,
    Capturing,
    AxisCheck,
}

enum class Drill {
    HoldStill,
    Track,
    Snap,
}

data class UiState(
    val screen: Screen = Screen.Home,
    val hasGyro: Boolean = true,
    val sensorInfo: String = "",
    val calibration: Calibration = Calibration(),
    val liveMagnitudeDeg: Float = 0f,
    val delayPhase: DelayPhase = DelayPhase.Ready,
    val delaySecondsLeft: Int = 0,
    val delayPrompt: String = "Hold the phone still, then flick when asked.",
    val delayReport: DelayReport? = null,
    val calPhase: CalibratePhase = CalibratePhase.Ready,
    val calSecondsLeft: Int = 0,
    val calMessage: String = "Hold still for 3 seconds to capture a zero-point.",
    val yawHint: String = "Yaw: —",
    val pitchHint: String = "Pitch: —",
    val drill: Drill = Drill.HoldStill,
    val sensitivity: Float = 420f,
    val crosshairX: Float = 0.5f,
    val crosshairY: Float = 0.5f,
    val targetX: Float = 0.5f,
    val targetY: Float = 0.5f,
    val trainerRunning: Boolean = false,
    val trainerPrompt: String = "Pick a drill and start.",
    val lastTimeToTargetMs: Long? = null,
    val lastOvershootPx: Float? = null,
    val holdStillScorePx: Float? = null,
    val trackErrorPx: Float? = null,
)

class LabViewModel(application: Application) : AndroidViewModel(application) {

    private val gyro = GyroRepository(application)
    private val store = CalibrationStore(application)
    private val integrator = AimIntegrator()

    private val _state = MutableStateFlow(
        UiState(
            hasGyro = gyro.hasGyroscope,
            sensorInfo = "${gyro.sensorName} • max ${gyro.maxRateHz.toInt()} Hz",
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var delayJob: Job? = null
    private var calJob: Job? = null
    private var trainerJob: Job? = null
    private var motionOnsetNanos: Long? = null
    private var firstFrameNanos: Long? = null
    private var stillWindow: List<GyroSample> = emptyList()
    private var flickArmed = false
    private var snapStartMs = 0L
    private var snapHit = false
    private var snapHitAtMs = 0L
    private var maxAfterHit = 0f
    private var holdAccum = 0f
    private var holdFrames = 0
    private var trackAccum = 0f
    private var trackFrames = 0
    private var canvasW = 1f
    private var canvasH = 1f

    init {
        viewModelScope.launch {
            store.calibration.collect { cal ->
                integrator.calibration = cal
                _state.update { it.copy(calibration = cal) }
            }
        }
        viewModelScope.launch {
            gyro.sample.collect { sample ->
                sample ?: return@collect
                val magDeg = Math.toDegrees(sample.magnitude.toDouble()).toFloat()
                _state.update { it.copy(liveMagnitudeDeg = magDeg) }
                handleDelayFlick(sample)
                handleAxisHints(sample)
            }
        }
        viewModelScope.launch {
            gyro.vsyncNanos.collect { vsync ->
                if (flickArmed && motionOnsetNanos != null && firstFrameNanos == null && vsync > 0) {
                    firstFrameNanos = vsync
                }
            }
        }
    }

    fun navigate(screen: Screen) {
        stopTrainer()
        delayJob?.cancel()
        calJob?.cancel()
        flickArmed = false
        when (screen) {
            Screen.Home, Screen.Tips -> gyro.stop()
            else -> {
                gyro.start()
                gyro.clearBuffer()
            }
        }
        _state.update {
            it.copy(
                screen = screen,
                delayPhase = DelayPhase.Ready,
                calPhase = CalibratePhase.Ready,
                delayPrompt = "Hold the phone still, then flick when asked.",
                calMessage = "Hold still for 3 seconds to capture a zero-point.",
            )
        }
    }

    fun startDelayTest() {
        if (!gyro.hasGyroscope) {
            _state.update {
                it.copy(
                    delayPhase = DelayPhase.Report,
                    delayReport = DelayReport(
                        sampleRateHz = 0f,
                        biasDegPerSec = 0f,
                        jitterRmsDegPerSec = 0f,
                        latencyMs = null,
                        dropouts = 0,
                        sampleCount = 0,
                        grade = DelayGrade.NoGyro,
                        gradeDetail = "No gyroscope reported on this device.",
                    ),
                )
            }
            return
        }
        delayJob?.cancel()
        gyro.start()
        gyro.clearBuffer()
        motionOnsetNanos = null
        firstFrameNanos = null
        flickArmed = false
        stillWindow = emptyList()
        _state.update {
            it.copy(
                delayPhase = DelayPhase.HoldStill,
                delaySecondsLeft = STILL_SECONDS,
                delayPrompt = "Hold completely still…",
                delayReport = null,
            )
        }
        delayJob = viewModelScope.launch {
            for (left in STILL_SECONDS downTo 1) {
                _state.update { it.copy(delaySecondsLeft = left) }
                delay(1000)
            }
            stillWindow = gyro.snapshot()
            val noise = stillWindow.takeLast(40).map { it.magnitude }.average().toFloat()
            val threshold = (noise * 8f).coerceIn(0.35f, 1.8f)
            flickArmed = true
            _state.update {
                it.copy(
                    delayPhase = DelayPhase.Flick,
                    delaySecondsLeft = FLICK_SECONDS,
                    delayPrompt = "Flick the phone left or right once, now.",
                )
            }
            val deadline = SystemClock.elapsedRealtime() + FLICK_SECONDS * 1000L
            while (isActive && SystemClock.elapsedRealtime() < deadline && firstFrameNanos == null) {
                val left = ((deadline - SystemClock.elapsedRealtime()) / 1000L).toInt().coerceAtLeast(0)
                _state.update { it.copy(delaySecondsLeft = left) }
                delay(100)
                val latest = gyro.sample.value
                if (latest != null && latest.magnitude > threshold && motionOnsetNanos == null) {
                    motionOnsetNanos = latest.timestampNanos
                }
            }
            flickArmed = false
            val report = DelayAnalyzer.analyze(stillWindow, motionOnsetNanos, firstFrameNanos)
            _state.update {
                it.copy(
                    delayPhase = DelayPhase.Report,
                    delayReport = report,
                    delayPrompt = report.gradeDetail,
                )
            }
            val cal = _state.value.calibration
            store.save(
                cal.copy(
                    lastSampleRateHz = report.sampleRateHz,
                    lastBiasDegPerSec = report.biasDegPerSec,
                    lastJitterDegPerSec = report.jitterRmsDegPerSec,
                    lastLatencyMs = report.latencyMs,
                    lastDropouts = report.dropouts,
                    lastGrade = report.grade,
                ),
            )
        }
    }

    fun startCalibrationCapture() {
        calJob?.cancel()
        gyro.start()
        gyro.clearBuffer()
        _state.update {
            it.copy(
                calPhase = CalibratePhase.Capturing,
                calSecondsLeft = CAL_SECONDS,
                calMessage = "Hold still… capturing bias.",
            )
        }
        calJob = viewModelScope.launch {
            for (left in CAL_SECONDS downTo 1) {
                _state.update { it.copy(calSecondsLeft = left) }
                delay(1000)
            }
            val all = gyro.snapshot()
            val firstTs = all.firstOrNull()?.timestampNanos ?: 0L
            // Drop the first 300 ms: that's the thumb tapping the button.
            val samples = all.filter { it.timestampNanos - firstTs > 300_000_000L }
            if (samples.size < 8) {
                _state.update {
                    it.copy(
                        calPhase = CalibratePhase.Ready,
                        calMessage = "Not enough samples. Keep the phone still and try again.",
                    )
                }
                return@launch
            }
            val biasX = samples.map { it.x }.average().toFloat()
            val biasY = samples.map { it.y }.average().toFloat()
            val biasZ = samples.map { it.z }.average().toFloat()
            val residual = samples.map { s ->
                hypot2(hypot2(s.x - biasX, s.y - biasY), s.z - biasZ)
            }.average().toFloat()
            val peak = samples.maxOf { s -> hypot3(s.x - biasX, s.y - biasY, s.z - biasZ) }
            if (residual > STILL_RESIDUAL_MAX || peak > STILL_PEAK_MAX) {
                _state.update {
                    it.copy(
                        calPhase = CalibratePhase.Ready,
                        calMessage = "Phone moved during capture (noise ${"%.1f".format(Math.toDegrees(residual.toDouble()))} °/s). " +
                            "Rest it on a table or brace your elbows, then capture again. Old calibration kept.",
                    )
                }
                return@launch
            }
            val deadzone = (residual * 2.2f).coerceIn(0.015f, 0.12f)
            val next = _state.value.calibration.copy(
                biasX = biasX,
                biasY = biasY,
                biasZ = biasZ,
                deadzoneRadPerSec = deadzone,
                lastBiasDegPerSec = Math.toDegrees(
                    hypot2(hypot2(biasX, biasY), biasZ).toDouble(),
                ).toFloat(),
            )
            store.save(next)
            _state.update {
                it.copy(
                    calPhase = CalibratePhase.AxisCheck,
                    calMessage = "Rotate left/right then up/down. Invert an axis if the hint is backwards.",
                    yawHint = "Yaw: waiting…",
                    pitchHint = "Pitch: waiting…",
                )
            }
        }
    }

    fun setDeadzone(value: Float) {
        viewModelScope.launch {
            store.save(_state.value.calibration.copy(deadzoneRadPerSec = value.coerceIn(0f, 0.25f)))
        }
    }

    fun toggleInvertYaw() {
        viewModelScope.launch {
            store.save(_state.value.calibration.copy(invertYaw = !_state.value.calibration.invertYaw))
        }
    }

    fun toggleInvertPitch() {
        viewModelScope.launch {
            store.save(_state.value.calibration.copy(invertPitch = !_state.value.calibration.invertPitch))
        }
    }

    fun toggleLandscape() {
        viewModelScope.launch {
            store.save(_state.value.calibration.copy(landscape = !_state.value.calibration.landscape))
        }
    }

    fun setDrill(drill: Drill) {
        stopTrainer()
        _state.update {
            it.copy(
                drill = drill,
                trainerPrompt = when (drill) {
                    Drill.HoldStill -> "Keep the crosshair on the center dot."
                    Drill.Track -> "Follow the moving target."
                    Drill.Snap -> "Flick onto the target as it appears."
                },
            )
        }
    }

    fun setSensitivity(value: Float) {
        _state.update { it.copy(sensitivity = value.coerceIn(80f, 1200f)) }
    }

    fun setCanvasSize(width: Float, height: Float) {
        canvasW = width.coerceAtLeast(1f)
        canvasH = height.coerceAtLeast(1f)
    }

    fun startTrainer() {
        stopTrainer()
        gyro.start()
        integrator.reset()
        gyro.onSample = integrator::onSample
        snapHit = false
        maxAfterHit = 0f
        holdAccum = 0f
        holdFrames = 0
        trackAccum = 0f
        trackFrames = 0
        snapStartMs = SystemClock.elapsedRealtime()
        _state.update {
            it.copy(
                trainerRunning = true,
                crosshairX = 0.5f,
                crosshairY = 0.5f,
                targetX = 0.5f,
                targetY = 0.5f,
                lastTimeToTargetMs = null,
                lastOvershootPx = null,
                holdStillScorePx = null,
                trackErrorPx = null,
                trainerPrompt = when (it.drill) {
                    Drill.HoldStill -> "Hold still on the center. 5 seconds."
                    Drill.Track -> "Track the target."
                    Drill.Snap -> "Snap to the target."
                },
            )
        }
        if (_state.value.drill == Drill.Snap) {
            placeSnapTarget()
        }
        trainerJob = viewModelScope.launch {
            val start = SystemClock.elapsedRealtime()
            while (isActive && _state.value.trainerRunning) {
                val (yawRad, pitchRad) = integrator.angles()
                val sens = _state.value.sensitivity
                var cx = (0.5f + yawRad * sens / canvasW).coerceIn(0.04f, 0.96f)
                var cy = (0.5f + pitchRad * sens / canvasH).coerceIn(0.04f, 0.96f)
                var tx = _state.value.targetX
                var ty = _state.value.targetY
                val elapsed = SystemClock.elapsedRealtime() - start
                when (_state.value.drill) {
                    Drill.HoldStill -> {
                        tx = 0.5f
                        ty = 0.5f
                        val dx = (cx - 0.5f) * canvasW
                        val dy = (cy - 0.5f) * canvasH
                        holdAccum += hypot2(dx, dy)
                        holdFrames++
                        val left = ((5000 - elapsed) / 1000L).toInt().coerceAtLeast(0)
                        if (elapsed >= 5000) {
                            val avg = if (holdFrames == 0) 0f else holdAccum / holdFrames
                            _state.update {
                                it.copy(
                                    trainerRunning = false,
                                    holdStillScorePx = avg,
                                    trainerPrompt = "Hold-still drift ${avg.toInt()} px average. Lower deadzone if this crawls.",
                                )
                            }
                            return@launch
                        }
                        _state.update {
                            it.copy(
                                crosshairX = cx,
                                crosshairY = cy,
                                targetX = tx,
                                targetY = ty,
                                trainerPrompt = "Hold still… ${left}s",
                            )
                        }
                    }
                    Drill.Track -> {
                        val t = elapsed / 1000f
                        tx = (0.5f + 0.28f * sin(t * 1.4f)).coerceIn(0.12f, 0.88f)
                        ty = (0.5f + 0.18f * cos(t * 1.1f)).coerceIn(0.18f, 0.82f)
                        val dx = (cx - tx) * canvasW
                        val dy = (cy - ty) * canvasH
                        trackAccum += hypot2(dx, dy)
                        trackFrames++
                        if (elapsed >= 8000) {
                            val avg = if (trackFrames == 0) 0f else trackAccum / trackFrames
                            _state.update {
                                it.copy(
                                    trainerRunning = false,
                                    trackErrorPx = avg,
                                    trainerPrompt = "Average tracking error ${avg.toInt()} px. Raise sensitivity if you lag behind.",
                                )
                            }
                            return@launch
                        }
                        _state.update {
                            it.copy(
                                crosshairX = cx,
                                crosshairY = cy,
                                targetX = tx,
                                targetY = ty,
                            )
                        }
                    }
                    Drill.Snap -> {
                        val dx = (cx - tx) * canvasW
                        val dy = (cy - ty) * canvasH
                        val dist = hypot2(dx, dy)
                        val radius = 36f
                        if (!snapHit && dist <= radius) {
                            snapHit = true
                            snapHitAtMs = SystemClock.elapsedRealtime()
                            maxAfterHit = 0f
                            val tt = snapHitAtMs - snapStartMs
                            _state.update { it.copy(lastTimeToTargetMs = tt) }
                        }
                        if (snapHit) {
                            maxAfterHit = maxOf(maxAfterHit, dist)
                            if (SystemClock.elapsedRealtime() - snapHitAtMs >= 900) {
                                _state.update {
                                    it.copy(
                                        trainerRunning = false,
                                        lastOvershootPx = maxAfterHit,
                                        trainerPrompt = "Time ${it.lastTimeToTargetMs} ms, overshoot ${maxAfterHit.toInt()} px. High delay? Drop ADS gyro.",
                                    )
                                }
                                return@launch
                            }
                        }
                        _state.update {
                            it.copy(crosshairX = cx, crosshairY = cy, targetX = tx, targetY = ty)
                        }
                    }
                }
                // One update per display frame: no extra polling delay, no wasted work.
                awaitFrame()
            }
        }
    }

    fun stopTrainer() {
        gyro.onSample = null
        trainerJob?.cancel()
        trainerJob = null
        _state.update { it.copy(trainerRunning = false) }
    }

    private fun placeSnapTarget() {
        val x = 0.18f + Random.nextFloat() * 0.64f
        val y = 0.22f + Random.nextFloat() * 0.56f
        snapStartMs = SystemClock.elapsedRealtime()
        snapHit = false
        _state.update { it.copy(targetX = x, targetY = y) }
    }

    private fun handleDelayFlick(sample: GyroSample) {
        if (!flickArmed || motionOnsetNanos != null) return
        val noise = stillWindow.takeLast(30).map { it.magnitude }.average().toFloat().coerceAtLeast(0.02f)
        if (sample.magnitude > (noise * 8f).coerceIn(0.35f, 1.8f)) {
            motionOnsetNanos = sample.timestampNanos
        }
    }

    private fun handleAxisHints(sample: GyroSample) {
        if (_state.value.screen != Screen.Calibrate) return
        if (_state.value.calPhase != CalibratePhase.AxisCheck) return
        val cal = _state.value.calibration
        val wx = sample.x - cal.biasX
        val wy = sample.y - cal.biasY
        val yawRate = if (cal.landscape) -wx else -wy
        val pitchRate = if (cal.landscape) -wy else -wx
        val yaw = if (cal.invertYaw) -yawRate else yawRate
        val pitch = if (cal.invertPitch) -pitchRate else pitchRate
        val yawText = when {
            abs(yaw) < 0.25f -> "Yaw: still"
            yaw > 0 -> "Yaw: looking right"
            else -> "Yaw: looking left"
        }
        val pitchText = when {
            abs(pitch) < 0.25f -> "Pitch: still"
            pitch > 0 -> "Pitch: looking down"
            else -> "Pitch: looking up"
        }
        _state.update { it.copy(yawHint = yawText, pitchHint = pitchText) }
    }

    override fun onCleared() {
        gyro.release()
        super.onCleared()
    }

    private companion object {
        const val STILL_SECONDS = 5
        const val FLICK_SECONDS = 8
        const val CAL_SECONDS = 3
        // ~2 °/s average noise and ~9 °/s peak: above this the hand moved, so the bias would be wrong.
        const val STILL_RESIDUAL_MAX = 0.035f
        const val STILL_PEAK_MAX = 0.16f
    }
}
