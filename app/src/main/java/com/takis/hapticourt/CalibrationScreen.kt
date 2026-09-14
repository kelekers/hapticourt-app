package com.takis.hapticourt

// #IMPORTS_SPRINT_3
import android.Manifest
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.camera.core.CameraSelector
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

// #TTS_VIEW_MODEL
class CalibrationViewModel : ViewModel(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    var isTtsReady by mutableStateOf(false)
        private set
        
    private var lastSpokenText: String = ""
    private var lastSpokenTime: Long = 0

    fun initTts(context: Context) {
        if (tts == null) {
            // SANGAT PENTING: Gunakan applicationContext agar tidak terjadi Memory Leak 
            // saat Activity/Screen di rotasi atau ditutup
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("id", "ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback ke bahasa Inggris jika HP tidak ada bahasa Indonesia
                tts?.setLanguage(Locale.US)
            }
            isTtsReady = true
        }
    }

    fun speakInstruction(text: String) {
        if (!isTtsReady) return
        
        // Anti-Spam: Jangan ucapkan kalimat yang sama berulang-ulang tanpa jeda
        // dan beri jeda minimal 2 detik (2000ms) antar ucapan agar tidak ngelag
        val currentTime = System.currentTimeMillis()
        if (text == lastSpokenText && (currentTime - lastSpokenTime) < 2000) {
            return
        }

        // Gunakan QUEUE_ADD agar kalimat mengantre secara natural, bukan FLUSH yang memutus kalimat secara kasar
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        lastSpokenText = text
        lastSpokenTime = currentTime
    }

    override fun onCleared() {
        tts?.stop()
        tts?.shutdown()
        tts = null // Bebaskan referensi
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
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview
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
                        .semantics { contentDescription = "Layar Kalibrasi Kamera" }
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Text("Membutuhkan izin kamera", color = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    calibrationViewModel.speakInstruction("Arahkan kamera sedikit ke kanan. Posisi lapangan sudah terlihat, kalibrasi selesai.")
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .semantics { contentDescription = "Tombol uji instruksi suara" }
            ) {
                Text("Test Audio Guidance", color = Color.White, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { navController.navigate(Screen.LiveTracking.route) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .semantics { contentDescription = "Selesai kalibrasi, lanjut ke pelacakan langsung" }
            ) {
                Text("Next", color = Color.White, fontSize = 24.sp)
            }
        }
    }
}