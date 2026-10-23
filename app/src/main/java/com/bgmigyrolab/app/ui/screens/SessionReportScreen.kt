package com.bgmigyrolab.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SessionReportScreen(report: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(onClick = onBack) { Text("Back") }
        Text("Session report", style = MaterialTheme.typography.headlineSmall)
        if (report == null) {
            Text(
                "No session yet. Tap Start boost on the Home screen, play BGMI for 5+ minutes, " +
                    "then tap Stop (in the app or the notification). The report appears here.",
            )
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                clipboard.setText(AnnotatedString(report))
                Toast.makeText(context, "Report copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy") }
            Button(onClick = {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "BGMI Gyro Lab session report")
                    .putExtra(Intent.EXTRA_TEXT, report)
                context.startActivity(Intent.createChooser(send, "Share report"))
            }) { Text("Share") }
        }
        SelectionContainer {
            Text(
                report,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
            )
        }
    }
}
