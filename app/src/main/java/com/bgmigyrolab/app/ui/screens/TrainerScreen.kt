package com.bgmigyrolab.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.bgmigyrolab.app.Drill
import com.bgmigyrolab.app.UiState
import java.util.Locale

@Composable
fun TrainerScreen(
    state: UiState,
    onBack: () -> Unit,
    onDrill: (Drill) -> Unit,
    onSensitivity: (Float) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSize: (Float, Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(onClick = onBack) { Text("Back") }
        Text("Aim trainer", style = MaterialTheme.typography.headlineSmall)
        Text(state.trainerPrompt, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.drill == Drill.HoldStill,
                onClick = { onDrill(Drill.HoldStill) },
                label = { Text("Hold still") },
            )
            FilterChip(
                selected = state.drill == Drill.Track,
                onClick = { onDrill(Drill.Track) },
                label = { Text("Track") },
            )
            FilterChip(
                selected = state.drill == Drill.Snap,
                onClick = { onDrill(Drill.Snap) },
                label = { Text("Snap") },
            )
        }
        Text("Trainer sensitivity ${state.sensitivity.toInt()} (not BGMI’s number — use overshoot to tune in-game ADS)")
        Slider(
            value = state.sensitivity,
            onValueChange = onSensitivity,
            valueRange = 80f..1200f,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .onSizeChanged { onSize(it.width.toFloat(), it.height.toFloat()) },
        ) {
            drawRect(Color(0xFF0E1528))
            val tx = size.width * state.targetX
            val ty = size.height * state.targetY
            val cx = size.width * state.crosshairX
            val cy = size.height * state.crosshairY
            drawCircle(Color(0xFFFFB020), radius = 28f, center = Offset(tx, ty), style = Stroke(3f))
            drawCircle(Color(0xFFFFB020), radius = 4f, center = Offset(tx, ty))
            drawLine(Color(0xFF3DDC97), Offset(cx - 22f, cy), Offset(cx + 22f, cy), strokeWidth = 3f)
            drawLine(Color(0xFF3DDC97), Offset(cx, cy - 22f), Offset(cx, cy + 22f), strokeWidth = 3f)
            drawCircle(Color(0xFF3DDC97), radius = 16f, center = Offset(cx, cy), style = Stroke(2f))
        }
        Button(
            onClick = if (state.trainerRunning) onStop else onStart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.trainerRunning) "Stop" else "Start drill")
        }
        state.lastTimeToTargetMs?.let {
            Text("Time to target: ${it} ms")
        }
        state.lastOvershootPx?.let {
            Text("Overshoot: ${it.toInt()} px")
        }
        state.holdStillScorePx?.let {
            Text("Hold-still drift: ${String.format(Locale.US, "%.1f", it)} px")
        }
        state.trackErrorPx?.let {
            Text("Track error: ${String.format(Locale.US, "%.1f", it)} px")
        }
    }
}
