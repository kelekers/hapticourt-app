package com.takis.hapticourt

// #IMPORTS_SPRINT_3
import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.speech.tts.TextToSpeech
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.Locale
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font

// #TTS_VIEW_MODEL
class CalibrationViewModel : ViewModel(), TextToSpeech.OnInitListener {
    // ... [Kode TTS tetap sama]
    private var tts: TextToSpeech? = null
    var isTtsReady by mutableStateOf(false)
        private set
        
    private var lastSpokenText: String = ""
    private var lastSpokenTime: Long = 0
    
    // State UI Kalibrasi Lapangan
    var isCalibrationDone by mutableStateOf(false)
        private set
        
    var calibrationStatus by mutableStateOf("Tahan HP dengan stabil, arahkan ke seluruh lapangan, lalu tekan Calibrate.")
        private set

    fun updateCalibrationStatus(status: String, done: Boolean = false) {
        calibrationStatus = status
        isCalibrationDone = done
    }

    fun initTts(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("id", "ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            isTtsReady = true
        }
    }

    fun speakInstruction(text: String) {
        if (!isTtsReady) return
        val currentTime = System.currentTimeMillis()
        if (text == lastSpokenText && (currentTime - lastSpokenTime) < 2000) {
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        lastSpokenText = text
        lastSpokenTime = currentTime
    }

    override fun onCleared() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onCleared()
    }
}

// #SCREEN_CALIBRATION_UPDATED
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
        calibrationViewModel.initTts(context)
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
                        calibrationViewModel.speakInstruction("Menganalisa lapangan.")
                        
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
                                            calibrationViewModel.speakInstruction("Kalibrasi sukses. Tekan Start.")
                                        } else {
                                            calibrationViewModel.speakInstruction("Gagal. Harap ulangi.")
                                        }
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    calibrationViewModel.speakInstruction("Gagal Kamera.")
                                }
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // Menghabiskan 50% ruang kotak bawah
                        .semantics { role = Role.Button; contentDescription = "Tombol Kalibrasi Lapangan" }
                ) {
                    Text(
                        text = "Calibrate", 
                        fontFamily = SamsungFont, 
                        color = Color.White, 
                        fontSize = 32.sp, 
                        fontWeight = FontWeight.Bold
                    )
                }

                // Tombol Mulai Tracking
                Button(
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        calibrationViewModel.speakInstruction("Memulai pelacakan.")
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
                        .weight(1f) // Menghabiskan 50% ruang kotak bawah
                        .semantics { role = Role.Button; contentDescription = "Tombol Mulai Pelacakan" }
                ) {
                    Text("Start", fontFamily = SamsungFont, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}