package com.bgmigyrolab.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bgmigyrolab.app.CalibratePhase
import com.bgmigyrolab.app.UiState
import java.util.Locale

@Composable
fun CalibrateScreen(
    state: UiState,
    onBack: () -> Unit,
    onCapture: () -> Unit,
    onDeadzone: (Float) -> Unit,
    onToggleYaw: () -> Unit,
    onTogglePitch: () -> Unit,
    onToggleLandscape: () -> Unit,
    onTips: () -> Unit,
) {
    val cal = state.calibration
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { OutlinedButton(onClick = onBack) { Text("Back") } }
        item { Text("Calibrate", style = MaterialTheme.typography.headlineSmall) }
        item { Text(state.calMessage) }
        if (state.calPhase == CalibratePhase.Capturing) {
            item { Text("${state.calSecondsLeft}s", style = MaterialTheme.typography.displaySmall) }
        }
        item {
            Button(
                onClick = onCapture,
                enabled = state.calPhase != CalibratePhase.Capturing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Capture 3s still-point") }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Deadzone ${String.format(Locale.US, "%.3f", cal.deadzoneRadPerSec)} rad/s — stops idle crawl.",
                )
                Slider(
                    value = cal.deadzoneRadPerSec,
                    onValueChange = onDeadzone,
                    valueRange = 0f..0.25f,
                )
            }
        }
        item {
            FilterChip(
                selected = cal.landscape,
                onClick = onToggleLandscape,
                label = { Text(if (cal.landscape) "Hold like BGMI (landscape)" else "Portrait mapping") },
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.yawHint, style = MaterialTheme.typography.titleMedium)
                FilterChip(
                    selected = cal.invertYaw,
                    onClick = onToggleYaw,
                    label = { Text(if (cal.invertYaw) "Yaw inverted" else "Invert yaw") },
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.pitchHint, style = MaterialTheme.typography.titleMedium)
                FilterChip(
                    selected = cal.invertPitch,
                    onClick = onTogglePitch,
                    label = { Text(if (cal.invertPitch) "Pitch inverted" else "Invert pitch") },
                )
            }
        }
        item {
            Text(
                if (cal.hasStillPoint) {
                    "Saved. Trainer will subtract this bias."
                } else {
                    "No still-point saved yet."
                },
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        item {
            OutlinedButton(onClick = onTips, modifier = Modifier.fillMaxWidth()) {
                Text("Suggested BGMI sensitivity tips")
            }
        }
    }
}
