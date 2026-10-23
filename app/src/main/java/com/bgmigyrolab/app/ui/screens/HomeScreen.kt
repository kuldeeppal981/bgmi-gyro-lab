package com.bgmigyrolab.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import com.bgmigyrolab.app.gyro.DelayGrade

@Composable
fun HomeScreen(
    hasGyro: Boolean,
    lastGrade: DelayGrade?,
    boostOn: Boolean,
    onToggleBoost: () -> Unit,
    onOpenDelay: () -> Unit,
    onOpenCalibrate: () -> Unit,
    onOpenTrainer: () -> Unit,
    onOpenTips: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("BGMI Gyro Lab", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            Text(
                "This app measures your phone gyro and lets you practice. It cannot inject into BGMI or remove chip lag inside the game.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        }
        if (!hasGyro) {
            item {
                Text(
                    "No gyroscope found on this device.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (lastGrade != null) {
            item {
                Text(
                    "Last delay grade: ${lastGrade.name}",
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        if (hasGyro) {
            item {
                ActionCard(
                    title = if (boostOn) "Gyro boost: ON" else "Gyro boost",
                    body = "Holds the gyro at its max rate with batching off, in the background, until you stop it. " +
                        "Start it, then open BGMI. Helps on phones where BGMI asks for a slower rate; " +
                        "can't beat the chip's own limit. Uses a little extra battery.",
                    action = if (boostOn) "Stop boost" else "Start boost",
                    onClick = onToggleBoost,
                )
            }
        }
        item {
            ActionCard(
                title = "Delay test",
                body = "10–15 seconds. Hold still, then flick. See sample rate, jitter, and motion-to-display lag.",
                action = "Run delay test",
                onClick = onOpenDelay,
            )
        }
        item {
            ActionCard(
                title = "Calibrate",
                body = "Capture a still-point, set a deadzone, and check yaw/pitch like BGMI.",
                action = "Open calibrate",
                onClick = onOpenCalibrate,
            )
        }
        item {
            ActionCard(
                title = "Aim trainer",
                body = "Hold still, track, and snap using calibrated gyro.",
                action = "Open trainer",
                onClick = onOpenTrainer,
            )
        }
        item {
            OutlinedButton(onClick = onOpenTips, modifier = Modifier.fillMaxWidth()) {
                Text("BGMI phone tips")
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    body: String,
    action: String,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(action)
            }
        }
    }
}
