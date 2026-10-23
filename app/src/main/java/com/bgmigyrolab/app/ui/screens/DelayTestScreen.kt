package com.bgmigyrolab.app.ui.screens

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.bgmigyrolab.app.DelayPhase
import com.bgmigyrolab.app.UiState
import com.bgmigyrolab.app.boost.GyroBoostService
import com.bgmigyrolab.app.gyro.DelayGrade
import com.bgmigyrolab.app.gyro.DelayReport
import java.util.Locale

@Composable
fun DelayTestScreen(
    state: UiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onTips: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        item {
            Text("Delay test", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Text(state.delayPrompt, style = MaterialTheme.typography.bodyLarge)
        }
        item {
            Text(
                "Live rate ${String.format(Locale.US, "%.1f", state.liveMagnitudeDeg)} °/s",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            Text(
                "Sensor: ${state.sensorInfo}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        if (state.delayPhase == DelayPhase.HoldStill || state.delayPhase == DelayPhase.Flick) {
            item {
                Text("${state.delaySecondsLeft}s", style = MaterialTheme.typography.displaySmall)
            }
        }
        item {
            Button(
                onClick = onStart,
                enabled = state.delayPhase == DelayPhase.Ready || state.delayPhase == DelayPhase.Report,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.delayPhase == DelayPhase.Report) "Run again" else "Start 15s test")
            }
        }
        state.delayReport?.let { report ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            when (report.grade) {
                                DelayGrade.Good -> "Grade: Good"
                                DelayGrade.Playable -> "Grade: Playable"
                                DelayGrade.HighDelay -> "Grade: High delay"
                                DelayGrade.NoGyro -> "Grade: No gyro"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = when (report.grade) {
                                DelayGrade.Good -> MaterialTheme.colorScheme.primary
                                DelayGrade.Playable -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.error
                            },
                        )
                        Text("Sample rate: ${report.sampleRateHz.toInt()} Hz")
                        Text(
                            "Sensor-to-app delay: " + (
                                report.deliveryMedianMs?.let { m ->
                                    "${String.format(Locale.US, "%.1f", m)} ms median, " +
                                        "${String.format(Locale.US, "%.1f", report.deliveryP95Ms ?: m)} ms p95"
                                } ?: "not reported by this phone"
                                ),
                        )
                        Text("Still bias: ${String.format(Locale.US, "%.2f", report.biasDegPerSec)} °/s")
                        Text("Jitter RMS: ${String.format(Locale.US, "%.2f", report.jitterRmsDegPerSec)} °/s")
                        Text(
                            "Motion-to-display: " + (
                                report.latencyMs?.let { "${it.toInt()} ms" } ?: "no flick captured"
                                ),
                        )
                        Text("Dropouts: ${report.dropouts}  •  Samples: ${report.sampleCount}")
                        Text(report.gradeDetail, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(reportText(state, report)))
                            Toast.makeText(context, "Report copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Copy report") }
                    Button(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, "BGMI Gyro Lab report")
                                .putExtra(Intent.EXTRA_TEXT, reportText(state, report))
                            context.startActivity(Intent.createChooser(send, "Share report"))
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Share report") }
                }
            }
            item {
                OutlinedButton(onClick = onTips, modifier = Modifier.fillMaxWidth()) {
                    Text("BGMI tips for this result")
                }
            }
        }
    }
}

private fun reportText(state: UiState, report: DelayReport): String {
    fun f(v: Float, digits: Int = 1) = String.format(Locale.US, "%.${digits}f", v)
    val cal = state.calibration
    return buildString {
        appendLine("BGMI Gyro Lab report (build ${com.bgmigyrolab.app.BuildConfig.VERSION_NAME})")
        appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
        appendLine("Sensor: ${state.sensorInfo}")
        appendLine("Gyro boost: ${if (GyroBoostService.running.value) "on" else "off"}")
        appendLine("Grade: ${report.grade.name}")
        appendLine("Sample rate: ${f(report.sampleRateHz)} Hz")
        appendLine(
            "Sensor-to-app delay: " + (
                report.deliveryMedianMs?.let { "${f(it)} ms median, ${f(report.deliveryP95Ms ?: it)} ms p95" }
                    ?: "not reported"
                ),
        )
        appendLine("Motion-to-display: ${report.latencyMs?.let { "${f(it)} ms" } ?: "no flick captured"}")
        appendLine("Still bias: ${f(report.biasDegPerSec, 2)} °/s")
        appendLine("Jitter RMS: ${f(report.jitterRmsDegPerSec, 2)} °/s")
        appendLine("Dropouts: ${report.dropouts}  Samples: ${report.sampleCount}")
        appendLine(
            "Calibration: bias ${f(cal.biasX, 4)}, ${f(cal.biasY, 4)}, ${f(cal.biasZ, 4)} rad/s, " +
                "deadzone ${f(cal.deadzoneRadPerSec, 3)}, landscape ${cal.landscape}, " +
                "invert yaw ${cal.invertYaw}, invert pitch ${cal.invertPitch}",
        )
        append("Note: ${report.gradeDetail}")
    }
}
