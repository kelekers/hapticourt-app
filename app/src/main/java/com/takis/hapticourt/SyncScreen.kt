package com.takis.hapticourt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay

@Composable
fun SyncScreen(navController: NavController, wifiViewModel: WifiViewModel = viewModel()) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val calibrationViewModel: CalibrationViewModel = viewModel()
    
    LaunchedEffect(Unit) {
        calibrationViewModel.initTts(context)
        delay(500)
        calibrationViewModel.speakInstruction("HaptiCourt. Tekan tombol Connect di bawah.")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // --- VIEWING AREA ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 64.dp, end = 24.dp)
        ) {
            Text(
                text = "HaptiCourt",
                fontFamily = SamsungFont,
                color = Color.Gray,
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal
            )
            Text(
                text = "Sync Vest",
                fontFamily = SamsungFont,
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // --- INTERACTION AREA ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF151515),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isConnected = wifiViewModel.connectionState.contains("Connected")
                
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!isConnected) {
                            calibrationViewModel.speakInstruction("Menyambungkan rompi.")
                            wifiViewModel.startConnection {
                                calibrationViewModel.speakInstruction("Berhasil. Membuka menu.")
                                navController.navigate(Screen.SportMode.route)
                            }
                        } else {
                            calibrationViewModel.speakInstruction("Membuka menu.")
                            navController.navigate(Screen.SportMode.route)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) Color(0xFF00E676) else Color(0xFF007AFF)
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .semantics { 
                            role = Role.Button
                            contentDescription = if (isConnected) "Rompi Tersambung. Lanjutkan" else "Tombol Hubungkan Rompi"
                        }
                ) {
                    Text(
                        text = if (isConnected) "Connected" else "Connect", 
                        fontFamily = SamsungFont, 
                        color = Color.White, 
                        fontSize = 22.sp, 
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}