package com.takis.hapticourt

sealed class Screen(val route: String) {
    object Sync : Screen("sync")
    object Calibration : Screen("calibration")
    object SportMode : Screen("sport_mode")
    object LiveTracking : Screen("live_tracking")
}