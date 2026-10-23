package com.bgmigyrolab.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgmigyrolab.app.LabViewModel
import com.bgmigyrolab.app.Screen
import com.bgmigyrolab.app.ui.screens.CalibrateScreen
import com.bgmigyrolab.app.ui.screens.DelayTestScreen
import com.bgmigyrolab.app.ui.screens.HomeScreen
import com.bgmigyrolab.app.ui.screens.TipsScreen
import com.bgmigyrolab.app.ui.screens.TrainerScreen
import com.bgmigyrolab.app.ui.theme.GyroLabTheme

@Composable
fun GyroLabApp(viewModel: LabViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GyroLabTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (state.screen) {
                Screen.Home -> HomeScreen(
                    hasGyro = state.hasGyro,
                    lastGrade = state.calibration.lastGrade,
                    onOpenDelay = { viewModel.navigate(Screen.DelayTest) },
                    onOpenCalibrate = { viewModel.navigate(Screen.Calibrate) },
                    onOpenTrainer = { viewModel.navigate(Screen.Trainer) },
                    onOpenTips = { viewModel.navigate(Screen.Tips) },
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
                Screen.Tips -> TipsScreen(
                    calibration = state.calibration,
                    onBack = { viewModel.navigate(Screen.Home) },
                )
            }
        }
    }
}
