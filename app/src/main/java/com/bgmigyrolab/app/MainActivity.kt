package com.bgmigyrolab.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.bgmigyrolab.app.boost.GyroBoostService
import com.bgmigyrolab.app.ui.GyroLabApp

class MainActivity : ComponentActivity() {

    private val viewModel: LabViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestHighestRefreshRate()
        GyroBoostService.loadSavedReport(this)
        openReportIfAsked(intent)
        setContent {
            GyroLabApp(viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openReportIfAsked(intent)
    }

    /**
     * Without this the system may drop this app to 60 Hz, which adds up to ~8 ms of display lag
     * in the Delay test and Trainer versus 120 Hz. Only affects this app, not BGMI.
     */
    private fun requestHighestRefreshRate() {
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = best.modeId
            preferredRefreshRate = best.refreshRate
        }
    }

    private fun openReportIfAsked(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_REPORT, false) == true) {
            viewModel.navigate(Screen.SessionReport)
        }
    }

    companion object {
        const val EXTRA_OPEN_REPORT = "open_session_report"
    }
}
