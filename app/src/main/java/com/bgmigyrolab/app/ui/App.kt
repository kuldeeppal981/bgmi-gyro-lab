package com.bgmigyrolab.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgmigyrolab.app.LabViewModel
import com.bgmigyrolab.app.Screen
import com.bgmigyrolab.app.boost.GyroBoostService
import com.bgmigyrolab.app.ui.screens.BoostCheckScreen
import com.bgmigyrolab.app.ui.screens.CalibrateScreen
import com.bgmigyrolab.app.ui.screens.DelayTestScreen
import com.bgmigyrolab.app.ui.screens.HomeScreen
import com.bgmigyrolab.app.ui.screens.SessionReportScreen
import com.bgmigyrolab.app.ui.screens.TipsScreen
import com.bgmigyrolab.app.ui.screens.TrainerScreen
import com.bgmigyrolab.app.ui.theme.GyroLabTheme

@Composable
fun GyroLabApp(viewModel: LabViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val boostOn by GyroBoostService.running.collectAsStateWithLifecycle()
    val sessionReport by GyroBoostService.lastReport.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Boost runs even if notifications are denied; the permission only makes its Stop button visible.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        GyroBoostService.start(context)
    }
    GyroLabTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (state.screen) {
                Screen.Home -> HomeScreen(
                    hasGyro = state.hasGyro,
                    lastGrade = state.calibration.lastGrade,
                    boostOn = boostOn,
                    onToggleBoost = {
                        when {
                            boostOn -> GyroBoostService.stop(context)
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> GyroBoostService.start(context)
                        }
                    },
                    onOpenDelay = { viewModel.navigate(Screen.DelayTest) },
                    onOpenCalibrate = { viewModel.navigate(Screen.Calibrate) },
                    onOpenTrainer = { viewModel.navigate(Screen.Trainer) },
                    onOpenTips = { viewModel.navigate(Screen.Tips) },
                    onOpenBoostCheck = { viewModel.navigate(Screen.BoostCheck) },
                    hasSessionReport = sessionReport != null,
                    onOpenSessionReport = { viewModel.navigate(Screen.SessionReport) },
                )
                Screen.DelayTest -> DelayTestScreen(
                    state = state,
                    onBack = { viewModel.navigate(Screen.Home) },
                    onStart = viewModel::startDelayTest,
                    onTips = { viewModel.navigate(Screen.Tips) },
                )
                Screen.Calibrate -> CalibrateScreen(
                    state = state,
                    onBack = { viewModel.navigate(Screen.Home) },
                    onCapture = viewModel::startCalibrationCapture,
                    onDeadzone = viewModel::setDeadzone,
                    onToggleYaw = viewModel::toggleInvertYaw,
                    onTogglePitch = viewModel::toggleInvertPitch,
                    onToggleLandscape = viewModel::toggleLandscape,
                    onTips = { viewModel.navigate(Screen.Tips) },
                )
                Screen.Trainer -> TrainerScreen(
                    state = state,
                    onBack = { viewModel.navigate(Screen.Home) },
                    onDrill = viewModel::setDrill,
                    onSensitivity = viewModel::setSensitivity,
                    onStart = viewModel::startTrainer,
                    onStop = viewModel::stopTrainer,
                    onSize = viewModel::setCanvasSize,
                )
                Screen.BoostCheck -> BoostCheckScreen(
                    state = state,
                    onBack = { viewModel.navigate(Screen.Home) },
                    onStart = viewModel::startBoostCheck,
                )
                Screen.SessionReport -> SessionReportScreen(
                    report = sessionReport,
                    onBack = { viewModel.navigate(Screen.Home) },
                )
                Screen.Tips -> TipsScreen(
                    calibration = state.calibration,
                    onBack = { viewModel.navigate(Screen.Home) },
                )
            }
        }
    }
}
