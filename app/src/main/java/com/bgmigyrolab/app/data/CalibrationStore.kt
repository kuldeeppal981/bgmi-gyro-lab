package com.bgmigyrolab.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bgmigyrolab.app.gyro.DelayGrade
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "calibration")

class CalibrationStore(private val context: Context) {

    val calibration: Flow<Calibration> = context.dataStore.data.map { prefs ->
        Calibration(
            biasX = prefs[BIAS_X] ?: 0f,
            biasY = prefs[BIAS_Y] ?: 0f,
            biasZ = prefs[BIAS_Z] ?: 0f,
            deadzoneRadPerSec = prefs[DEADZONE] ?: 0.03f,
            invertYaw = prefs[INVERT_YAW] ?: false,
            invertPitch = prefs[INVERT_PITCH] ?: false,
            landscape = prefs[LANDSCAPE] ?: true,
            lastSampleRateHz = prefs[LAST_HZ] ?: 0f,
            lastBiasDegPerSec = prefs[LAST_BIAS] ?: 0f,
            lastJitterDegPerSec = prefs[LAST_JITTER] ?: 0f,
            lastLatencyMs = prefs[LAST_LATENCY]?.takeIf { !it.isNaN() },
            lastDropouts = prefs[LAST_DROPOUTS] ?: 0,
            lastGrade = prefs[LAST_GRADE]?.let { runCatching { DelayGrade.valueOf(it) }.getOrNull() },
        )
    }

    suspend fun save(calibration: Calibration) {
        context.dataStore.edit { prefs ->
            prefs[BIAS_X] = calibration.biasX
            prefs[BIAS_Y] = calibration.biasY
            prefs[BIAS_Z] = calibration.biasZ
            prefs[DEADZONE] = calibration.deadzoneRadPerSec
            prefs[INVERT_YAW] = calibration.invertYaw
            prefs[INVERT_PITCH] = calibration.invertPitch
            prefs[LANDSCAPE] = calibration.landscape
            prefs[LAST_HZ] = calibration.lastSampleRateHz
            prefs[LAST_BIAS] = calibration.lastBiasDegPerSec
            prefs[LAST_JITTER] = calibration.lastJitterDegPerSec
            prefs[LAST_LATENCY] = calibration.lastLatencyMs ?: Float.NaN
            prefs[LAST_DROPOUTS] = calibration.lastDropouts
            if (calibration.lastGrade != null) {
                prefs[LAST_GRADE] = calibration.lastGrade.name
            }
        }
    }

    private companion object {
        val BIAS_X = floatPreferencesKey("bias_x")
        val BIAS_Y = floatPreferencesKey("bias_y")
        val BIAS_Z = floatPreferencesKey("bias_z")
        val DEADZONE = floatPreferencesKey("deadzone")
        val INVERT_YAW = booleanPreferencesKey("invert_yaw")
        val INVERT_PITCH = booleanPreferencesKey("invert_pitch")
        val LANDSCAPE = booleanPreferencesKey("landscape")
        val LAST_HZ = floatPreferencesKey("last_hz")
        val LAST_BIAS = floatPreferencesKey("last_bias")
        val LAST_JITTER = floatPreferencesKey("last_jitter")
        val LAST_LATENCY = floatPreferencesKey("last_latency")
        val LAST_DROPOUTS = intPreferencesKey("last_dropouts")
        val LAST_GRADE = stringPreferencesKey("last_grade")
    }
}
