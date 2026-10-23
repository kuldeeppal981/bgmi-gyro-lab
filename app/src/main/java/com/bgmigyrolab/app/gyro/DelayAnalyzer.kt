package com.bgmigyrolab.app.gyro

import kotlin.math.sqrt

enum class DelayGrade {
    Good,
    Playable,
    HighDelay,
    NoGyro,
}

data class DelayReport(
    val sampleRateHz: Float,
    val biasDegPerSec: Float,
    val jitterRmsDegPerSec: Float,
    val latencyMs: Float?,
    val dropouts: Int,
    val sampleCount: Int,
    val grade: DelayGrade,
    val gradeDetail: String,
    /** Median sensor-to-app delay; this is the part of gyro lag the phone's sensor stack adds. */
    val deliveryMedianMs: Float? = null,
    val deliveryP95Ms: Float? = null,
)

object DelayAnalyzer {

    fun analyze(
        stillSamples: List<GyroSample>,
        motionOnsetNanos: Long?,
        firstFrameAfterMotionNanos: Long?,
    ): DelayReport {
        if (stillSamples.size < 8) {
            return DelayReport(
                sampleRateHz = 0f,
                biasDegPerSec = 0f,
                jitterRmsDegPerSec = 0f,
                latencyMs = null,
                dropouts = 0,
                sampleCount = stillSamples.size,
                grade = DelayGrade.HighDelay,
                gradeDetail = "Not enough still samples. Hold the phone still and try again.",
            )
        }

        val dtSeconds = stillSamples.zipWithNext { a, b ->
            (b.timestampNanos - a.timestampNanos) / 1_000_000_000.0
        }.filter { it in 0.0002..0.05 }
        val meanDt = dtSeconds.average().toFloat().coerceAtLeast(0.0005f)
        val hz = 1f / meanDt

        val expectedDtNs = (meanDt * 1_000_000_000.0).toLong()
        val dropoutLimit = (expectedDtNs * 2.5).toLong().coerceAtLeast(8_000_000L)
        val dropouts = stillSamples.zipWithNext { a, b ->
            b.timestampNanos - a.timestampNanos
        }.count { it > dropoutLimit }

        val meanX = stillSamples.map { it.x }.average().toFloat()
        val meanY = stillSamples.map { it.y }.average().toFloat()
        val meanZ = stillSamples.map { it.z }.average().toFloat()
        val biasRad = sqrt(meanX * meanX + meanY * meanY + meanZ * meanZ)
        val biasDeg = Math.toDegrees(biasRad.toDouble()).toFloat()

        val jitterRad = sqrt(
            stillSamples.map { s ->
                val dx = s.x - meanX
                val dy = s.y - meanY
                val dz = s.z - meanZ
                dx * dx + dy * dy + dz * dz
            }.average().toFloat().coerceAtLeast(0f),
        )
        val jitterDeg = Math.toDegrees(jitterRad.toDouble()).toFloat()

        val latencyMs = if (motionOnsetNanos != null && firstFrameAfterMotionNanos != null &&
            firstFrameAfterMotionNanos >= motionOnsetNanos
        ) {
            (firstFrameAfterMotionNanos - motionOnsetNanos) / 1_000_000f
        } else {
            null
        }

        val delivery = stillSamples.mapNotNull { it.deliveryMs }.sorted()
        val deliveryMedian = delivery.takeIf { it.size >= 8 }?.let { it[it.size / 2] }
        val deliveryP95 = delivery.takeIf { it.size >= 8 }?.let { it[(it.size * 95) / 100] }

        val grade = gradeFor(hz, latencyMs, jitterDeg, deliveryMedian)
        return DelayReport(
            sampleRateHz = hz,
            biasDegPerSec = biasDeg,
            jitterRmsDegPerSec = jitterDeg,
            latencyMs = latencyMs,
            dropouts = dropouts,
            sampleCount = stillSamples.size,
            grade = grade,
            gradeDetail = detailFor(grade, latencyMs) + batchingHint(deliveryMedian, deliveryP95),
            deliveryMedianMs = deliveryMedian,
            deliveryP95Ms = deliveryP95,
        )
    }

    private fun gradeFor(hz: Float, latencyMs: Float?, jitterDeg: Float, deliveryMedian: Float?): DelayGrade {
        // No flick captured: estimate from sensor delay plus one 60 Hz frame instead of assuming the worst.
        val latency = latencyMs ?: deliveryMedian?.plus(16f) ?: 80f
        return when {
            hz >= 200f && latency < 25f && jitterDeg < 1.2f -> DelayGrade.Good
            hz >= 100f && latency < 45f -> DelayGrade.Playable
            else -> DelayGrade.HighDelay
        }
    }

    private fun batchingHint(median: Float?, p95: Float?): String = when {
        p95 != null && p95 > 20f ->
            " Sensor delivery spikes to ${p95.toInt()} ms: something is batching or throttling the gyro. " +
                "Turn off battery saver / adaptive battery for BGMI and test again."
        median != null && median > 8f ->
            " Sensor-to-app delay alone is ${median.toInt()} ms — that's the chip/driver, not your settings."
        else -> ""
    }

    private fun detailFor(grade: DelayGrade, latencyMs: Float?): String {
        val lag = latencyMs?.let { "${it.toInt()} ms motion-to-display" }
            ?: "no flick captured, graded from sensor delay"
        return when (grade) {
            DelayGrade.Good ->
                "Gyro is snappy ($lag). BGMI ADS should feel close to your hand."
            DelayGrade.Playable ->
                "Usable but not instant ($lag). Lower ADS gyro a bit so you do not overshoot."
            DelayGrade.HighDelay ->
                "High delay or low sample rate ($lag). This app cannot speed up the chip; BGMI will feel the same lag."
            DelayGrade.NoGyro ->
                "No gyroscope reported on this device."
        }
    }
}
