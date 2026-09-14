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
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CalibrationScreen(navController: NavController, calibrationViewModel: CalibrationViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val courtDetector = remember { CourtDetector(context) }
    val coroutineScope = remember { CoroutineScope(Dispatchers.Main) }
    
    // Simpan referensi ImageCapture untuk mengambil foto lapangan
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    LaunchedEffect(Unit) {
        calibrationViewModel.initTts(context)
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (cameraPermissionState.status.isGranted) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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

                Text(
                    text = "CALIBRATION",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f))
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Color.DarkGray), contentAlignment = Alignment.Center) {
                Text("Membutuhkan izin kamera", color = Color.White)
            }
        }

        // Area Status & Tombol
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = calibrationViewModel.calibrationStatus,
                color = if (calibrationViewModel.isCalibrationDone) Color.Green else Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(
                onClick = {
                    calibrationViewModel.updateCalibrationStatus("Memotret dan menganalisa lapangan...")
                    
                    imageCapture?.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val rotation = image.imageInfo.rotationDegrees
                                val bitmap = image.toBitmap()
                                image.close() // Segera tutup frame

                                // Putar agar tegak
                                val matrix = Matrix()
                                matrix.postRotate(rotation.toFloat())
                                val rotatedBitmap = Bitmap.createBitmap(
                                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                                )

                                // Lempar ke AI Court Detector
                                coroutineScope.launch {
                                    val success = courtDetector.calibrateCourt(rotatedBitmap)
                                    if (success) {
                                        calibrationViewModel.updateCalibrationStatus("Kalibrasi Berhasil! Matriks 2D disimpan.", true)
                                        calibrationViewModel.speakInstruction("Kalibrasi lapangan berhasil. Anda bisa melanjutkan.")
                                    } else {
                                        calibrationViewModel.updateCalibrationStatus("Gagal menemukan sudut lapangan. Coba geser sedikit.", false)
                                        calibrationViewModel.speakInstruction("Gagal mendeteksi sudut. Harap ulangi.")
                                    }
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                calibrationViewModel.updateCalibrationStatus("Gagal mengambil gambar kamera.", false)
                            }
                        }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Text("Calibrate Court", color = Color.White, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { navController.navigate(Screen.LiveTracking.route) },
                enabled = calibrationViewModel.isCalibrationDone,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (calibrationViewModel.isCalibrationDone) Color.Blue else Color.Gray
                ),
                modifier = Modifier.fillMaxWidth().height(80.dp)
            ) {
                Text("Start Tracking", color = Color.White, fontSize = 24.sp)
            }
        }
    }
}