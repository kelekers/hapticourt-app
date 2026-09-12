package com.takis.hapticourt

// #IMPORTS
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// #MAIN_ACTIVITY
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HaptiCourtApp()
        }
    }
}

// #APP_NAVIGATION
@Composable
fun HaptiCourtApp() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Sync.route,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        composable(Screen.Sync.route) { SyncScreen(navController) }
        composable(Screen.Calibration.route) { CalibrationScreen(navController) }
        composable(Screen.SportMode.route) { SportModeScreen(navController) }
        composable(Screen.LiveTracking.route) { LiveTrackingScreen(navController) }
    }
}