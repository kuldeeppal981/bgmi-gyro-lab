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

    private fun openReportIfAsked(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_REPORT, false) == true) {
            viewModel.navigate(Screen.SessionReport)
        }
    }

    companion object {
        const val EXTRA_OPEN_REPORT = "open_session_report"
    }
}
