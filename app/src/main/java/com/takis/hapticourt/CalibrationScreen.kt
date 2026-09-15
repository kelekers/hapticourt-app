package com.takis.hapticourt

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// #VIEW_MODEL_CLEANED
class CalibrationViewModel : ViewModel() {
    // State UI Kalibrasi Lapangan
    var isCalibrationDone by mutableStateOf(false)
        private set
        
    var calibrationStatus by mutableStateOf("Ready to Calibrate")
        private set

    fun updateCalibrationStatus(status: String, done: Boolean = false) {
        calibrationStatus = status
        isCalibrationDone = done
    }
}

val SamsungFont = FontFamily(
    Font(R.font.samsung_one_400, FontWeight.Normal),
    Font(R.font.samsung_one_700, FontWeight.Bold),
    Font(R.font.samsung_sharp_sans_bold, FontWeight.ExtraBold)
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CalibrationScreen(navController: NavController, calibrationViewModel: CalibrationViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val courtDetector = remember { CourtDetector(context) }
    val coroutineScope = remember { CoroutineScope(Dispatchers.Main) }
    
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    // Samsung One UI Background
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // --- VIEWING AREA ---
        Text(
            text = "Calibration",
            fontFamily = SamsungFont,
            color = Color.White,
            fontSize = 42.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .padding(start = 24.dp, top = 64.dp, bottom = 16.dp)
                .semantics { contentDescription = "Layar Kalibrasi Kamera" }
        )

        if (cameraPermissionState.status.isGranted) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.5f)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(32.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            
                            val imgCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            imageCapture = imgCapture
                            
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imgCapture
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.5f)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFF151515)),
                contentAlignment = Alignment.Center
            ) {
                Text("Membutuhkan izin kamera", fontFamily = SamsungFont, color = Color.Gray, fontSize = 18.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- INTERACTION AREA ---
        val haptic = LocalHapticFeedback.current
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            color = Color(0xFF151515),
            shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tombol Kalibrasi
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        calibrationViewModel.updateCalibrationStatus("Menganalisis...", false)
                        
                        imageCapture?.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val rotation = image.imageInfo.rotationDegrees
                                    val bitmap = image.toBitmap()
                                    image.close()

                                    val matrix = Matrix()
                                    matrix.postRotate(rotation.toFloat())
                                    val rotatedBitmap = Bitmap.createBitmap(
                                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                                    )

                                    coroutineScope.launch {
                                        val success = courtDetector.calibrateCourt(rotatedBitmap)
                                        if (success) {
                                            calibrationViewModel.updateCalibrationStatus("Berhasil!", true)
                                        } else {
                                            calibrationViewModel.updateCalibrationStatus("Gagal.", false)
                                        }
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    calibrationViewModel.updateCalibrationStatus("Gagal Kamera.", false)
                                }
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .semantics { role = Role.Button; contentDescription = "Tombol Kalibrasi Lapangan. Status saat ini: ${calibrationViewModel.calibrationStatus}" }
                ) {
                    Text(
                        text = if (calibrationViewModel.calibrationStatus != "Ready to Calibrate") calibrationViewModel.calibrationStatus else "Calibrate", 
                        fontFamily = SamsungFont, 
                        color = Color.White, 
                        fontSize = 28.sp, 
                        fontWeight = FontWeight.Bold
                    )
                }

                // Tombol Mulai Tracking
                Button(
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        navController.navigate(Screen.LiveTracking.route) 
                    },
                    enabled = calibrationViewModel.isCalibrationDone,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (calibrationViewModel.isCalibrationDone) Color(0xFF007AFF) else Color(0xFF333333),
                        disabledContainerColor = Color(0xFF333333)
                    ),
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .semantics { role = Role.Button; contentDescription = "Tombol Mulai Pelacakan" }
                ) {
                    Text("Start", fontFamily = SamsungFont, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}