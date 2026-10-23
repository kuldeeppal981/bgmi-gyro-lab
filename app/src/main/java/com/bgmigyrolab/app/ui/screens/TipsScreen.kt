package com.bgmigyrolab.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bgmigyrolab.app.data.Calibration
import com.bgmigyrolab.app.gyro.DelayGrade

@Composable
fun TipsScreen(
    calibration: Calibration,
    onBack: () -> Unit,
) {
    val grade = calibration.lastGrade
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { OutlinedButton(onClick = onBack) { Text("Back") } }
        item { Text("BGMI tips", style = MaterialTheme.typography.headlineSmall) }
        item {
            Text(
                "These are phone and in-game settings. This app never touches BGMI.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        }
        item {
            Text(
                when (grade) {
                    DelayGrade.Good ->
                        "Your last test was Good. Keep ADS gyro where it already feels honest; do not add extra smoothing."
                    DelayGrade.Playable ->
                        "Your last test was Playable. Drop ADS / 3x / 6x gyro a little so delayed shots do not fly past the head."
                    DelayGrade.HighDelay ->
                        "Your last test was High delay. Lower ADS gyro, avoid heavy gyro + high FOV, and expect the same lag in BGMI — the chip is not faster in-game."
                    DelayGrade.NoGyro ->
                        "No gyro on this phone. BGMI gyroscope aim will not work here."
                    null ->
                        "Run a delay test first for a grade. Until then, keep ADS gyro moderate."
                },
            )
        }
        item {
            Text("Phone", style = MaterialTheme.typography.titleLarge)
        }
        item { Text("• Use 90 Hz or 120 Hz if the panel supports it. 60 Hz adds a frame of aim lag.") }
        item { Text("• In Android settings, disable battery optimization for BGMI so the gyro is not throttled.") }
        item { Text("• Close extra apps, keep the phone cool, and avoid power-saving modes while you play.") }
        item { Text("• Game booster “touch optimization” is fine; anything that claims to fake or boost gyro from another app is not what this lab does.") }
        item {
            Text("In BGMI", style = MaterialTheme.typography.titleLarge)
        }
        item { Text("• Turn gyroscope on. Prefer 3rd person / ADS values you can stop on a dummy, not the highest number.") }
        item { Text("• If the game offers extra gyro smoothing, leave it off. Smoothing feels like more delay.") }
        item { Text("• High delay phones: start ADS around 80–120% of what you use on a snappy phone, then raise only if you undershoot in this trainer.") }
        item { Text("• If the crosshair crawls while you hold still, raise this app’s deadzone a little, then match by not over-flicking in BGMI.") }
        if (calibration.lastLatencyMs != null) {
            item {
                Text(
                    "Last motion-to-display: ${calibration.lastLatencyMs.toInt()} ms at ${calibration.lastSampleRateHz.toInt()} Hz, ${calibration.lastDropouts} dropouts.",
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
