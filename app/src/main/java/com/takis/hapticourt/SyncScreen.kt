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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

@Composable
fun SyncScreen(navController: NavController, wifiViewModel: WifiViewModel = viewModel()) {
    val haptic = LocalHapticFeedback.current

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
                .weight(1f) // Menghabiskan 50% layar atas
                .padding(start = 32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.logo_hapticourt),
                    contentDescription = "Logo HaptiCourt",
                    modifier = Modifier
                        .size(48.dp) // Ukuran logo dikecilkan sedikit agar seimbang dengan teks
                        .padding(end = 12.dp) // Jarak antara logo dan teks
                )
                Text(
                    text = "HaptiCourt",
                    fontFamily = SamsungFont,
                    color = Color.Gray,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal
                )
            }
            Text(
                text = "Sync Vest",
                fontFamily = SamsungFont,
                color = Color.White,
                fontSize = 56.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // --- INTERACTION AREA ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f), // Menghabiskan 50% layar bawah
            color = Color(0xFF151515),
            shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                val isConnected = wifiViewModel.connectionState.contains("Connected")
                
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!isConnected) {
                            wifiViewModel.startConnection {
                                navController.navigate(Screen.SportMode.route)
                            }
                        } else {
                            navController.navigate(Screen.SportMode.route)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) Color(0xFF00E676) else Color(0xFF007AFF)
                    ),
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { 
                            role = Role.Button
                            contentDescription = if (isConnected) "Rompi Tersambung. Lanjutkan" else "Tombol Hubungkan Rompi"
                        }
                ) {
                    Text(
                        text = if (isConnected) "Connected" else "Connect", 
                        fontFamily = SamsungFont, 
                        color = Color.White, 
                        fontSize = 40.sp, 
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}