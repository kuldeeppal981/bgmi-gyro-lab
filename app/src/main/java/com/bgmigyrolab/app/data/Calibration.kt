package com.bgmigyrolab.app.data

import com.bgmigyrolab.app.gyro.DelayGrade

data class Calibration(
    val biasX: Float = 0f,
    val biasY: Float = 0f,
    val biasZ: Float = 0f,
    val deadzoneRadPerSec: Float = 0.03f,
    val invertYaw: Boolean = false,
    val invertPitch: Boolean = false,
    val landscape: Boolean = true,
    val lastSampleRateHz: Float = 0f,
    val lastBiasDegPerSec: Float = 0f,
    val lastJitterDegPerSec: Float = 0f,
    val lastLatencyMs: Float? = null,
    val lastDropouts: Int = 0,
    val lastGrade: DelayGrade? = null,
) {
    val hasStillPoint: Boolean
        get() = biasX != 0f || biasY != 0f || biasZ != 0f || lastBiasDegPerSec > 0f
}
