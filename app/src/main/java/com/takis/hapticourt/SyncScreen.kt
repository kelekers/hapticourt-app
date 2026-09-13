package com.takis.hapticourt

// #IMPORTS_BLE
import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

// #SCREEN_SYNC_UPDATED
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SyncScreen(navController: NavController, wifiViewModel: WifiViewModel = viewModel()) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    val permissionState = rememberMultiplePermissionsState(permissions = blePermissions)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SYNC",
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { contentDescription = "Halaman Sinkronisasi Rompi" }
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Status: ${wifiViewModel.connectionState}",
            color = if (wifiViewModel.connectionState == "Connected") Color.Green else Color.White,
            fontSize = 20.sp,
            modifier = Modifier.semantics { contentDescription = "Status Koneksi: ${wifiViewModel.connectionState}" }
        )

        if (wifiViewModel.connectionState == "Connected") {
            Text(
                text = "Vest battery: ${wifiViewModel.batteryLevel}%",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 8.dp).semantics { contentDescription = "Baterai rompi ${wifiViewModel.batteryLevel} persen" }
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                if (permissionState.allPermissionsGranted) {
                    wifiViewModel.startScanningAndConnect(context) {
                        navController.navigate(Screen.SportMode.route)
                    }
                } else {
                    permissionState.launchMultiplePermissionRequest()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .semantics { contentDescription = "Tombol Hubungkan Rompi" }
        ) {
            Text("Connection", color = Color.White, fontSize = 24.sp)
        }
    }
}