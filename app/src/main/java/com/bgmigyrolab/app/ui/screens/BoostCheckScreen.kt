package com.bgmigyrolab.app.ui.screens

import android.content.Intent
import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bgmigyrolab.app.UiState
import com.bgmigyrolab.app.gyro.RateResult
import java.util.Locale

@Composable
fun BoostCheckScreen(
    state: UiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { OutlinedButton(onClick = onBack) { Text("Back") } }
        item { Text("Boost check", style = MaterialTheme.typography.headlineSmall) }
        item {
            Text(
                "Reads the gyro the way a game does (\"game\" rate), first without Gyro Boost, then with it. " +
                    "If the rate jumps, Boost gives BGMI a faster gyro on your phone. Put the phone on a table: 8 seconds.",
            )
        }
        item { Text(state.boostCheckMessage, style = MaterialTheme.typography.titleMedium) }
        item {
            Button(
                onClick = onStart,
                enabled = !state.boostCheckRunning,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.boostCheckOff != null) "Run again" else "Start boost check") }
        }
        val off = state.boostCheckOff
        val on = state.boostCheckOn
        if (off != null && on != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Game-rate gyro without Boost: ${line(off)}")
                        Text("Game-rate gyro with Boost: ${line(on)}")
                        Text(state.boostCheckVerdict, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        val text = buildString {
                            appendLine("BGMI Gyro Lab boost check")
                            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
                            appendLine("Sensor: ${state.sensorInfo}")
                            appendLine("Without boost: ${line(off)}")
                            appendLine("With boost: ${line(on)}")
                            append("Verdict: ${state.boostCheckVerdict}")
                        }
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, text)
                        context.startActivity(Intent.createChooser(send, "Share result"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Share result") }
            }
        }
    }
}

private fun line(r: RateResult): String {
    val delivery = r.deliveryMedianMs?.let { String.format(Locale.US, ", %.1f ms delay", it) } ?: ""
    return "${r.hz.toInt()} Hz$delivery (${r.samples} samples)"
}
