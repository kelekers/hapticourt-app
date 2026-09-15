package com.takis.hapticourt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@Composable
fun SportModeScreen(navController: NavController) {
    val haptic = LocalHapticFeedback.current
    val calibrationViewModel: CalibrationViewModel = viewModel()
    
    LaunchedEffect(Unit) {
        calibrationViewModel.speakInstruction("Pilih olahraga.")
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
                text = "Sport Mode",
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
                Button(
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        calibrationViewModel.speakInstruction("Tenis dipilih.")
                        navController.navigate(Screen.Calibration.route) 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .semantics { role = Role.Button; contentDescription = "Pilih Mode Tenis" }
                ) {
                    Text("Tennis", fontFamily = SamsungFont, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}