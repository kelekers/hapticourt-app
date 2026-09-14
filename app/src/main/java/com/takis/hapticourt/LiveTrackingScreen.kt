// #PACKAGE
package com.takis.hapticourt

// #IMPORTS
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import java.util.concurrent.Executors
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opencv.core.Core
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point

// #SCREEN_LIVE_TRACKING
@Composable
fun LiveTrackingScreen(navController: NavController, wifiViewModel: WifiViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var inferenceResult by remember { mutableStateOf("Menunggu Frame...") }
    var fps by remember { mutableStateOf(0) }
    
    // Variabel untuk menyimpan titik 2D setelah Homography (Mini-map) sekarang berbasis List
    var minimapPlayers by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var minimapBalls by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val aiProcessor = remember { AiProcessor(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val coroutineScope = remember { CoroutineScope(Dispatchers.Default) }
    
    // Variabel untuk menghitung FPS
    var frameCount by remember { mutableStateOf(0) }
    var lastFpsTime by remember { mutableStateOf(System.currentTimeMillis()) }

    DisposableEffect(Unit) {
        onDispose {
            aiProcessor.close()
            executor.shutdown()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

                        // #CAMERA_RESOLUTION_SETUP
                        val resolutionSelector = ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(Size(640, 640), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER))
                            .build()

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setResolutionSelector(resolutionSelector)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .build()

                        // #FRAME_ANALYZER
                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                            // 1. Dapatkan rotasi aktual dari kamera (biasanya 90 derajat di Portrait)
                            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                            val rawBitmap = imageProxy.toBitmap()

                            // 2. Putar Bitmap agar objek (manusia) berdiri tegak
                            val matrix = Matrix()
                            matrix.postRotate(rotationDegrees.toFloat())
                            val rotatedBitmap = Bitmap.createBitmap(
                                rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
                            )

                            coroutineScope.launch {
                                try {
                                    val result = aiProcessor.analyzeFrame(rotatedBitmap)
                                    
                                    if (result != "SKIP") {
                                        inferenceResult = result
                                        
                                        // Update FPS
                                        frameCount++
                                        val currentTime = System.currentTimeMillis()
                                        if (currentTime - lastFpsTime >= 1000) {
                                            fps = frameCount
                                            frameCount = 0
                                            lastFpsTime = currentTime
                                        }

                                        // Parsing & Hitung Transformasi Homography (Sekarang List)
                                        val parts = result.split("|")
                                        val pPart = parts.find { it.startsWith("P:") }?.substringAfter("P:")
                                        val bPart = parts.find { it.startsWith("B:") }?.substringAfter("B:")

                                        val transformedPlayers = mutableListOf<Offset>()
                                        val transformedBalls = mutableListOf<Offset>()

                                        if (pPart != null && pPart != "N,N") {
                                            val coordsList = pPart.split(";")
                                            for (coord in coordsList) {
                                                val xy = coord.split(",")
                                                if (xy.size == 2) {
                                                    transformedPlayers.add(applyHomography(xy[0].toDouble(), xy[1].toDouble()))
                                                }
                                            }
                                        }
                                        
                                        if (bPart != null && bPart != "N,N") {
                                            val coordsList = bPart.split(";")
                                            for (coord in coordsList) {
                                                val xy = coord.split(",")
                                                if (xy.size == 2) {
                                                    transformedBalls.add(applyHomography(xy[0].toDouble(), xy[1].toDouble()))
                                                }
                                            }
                                        }

                                        // Update state Mini-map (di UI thread)
                                        minimapPlayers = transformedPlayers
                                        minimapBalls = transformedBalls

                                        // NOTE: Kirim koordinat TRANSFORMASI ke HapticMapper (Bukan koordinat kamera)
                                        // Tapi untuk tes UI, kita gunakan string lama dulu
                                        val hapticCommand = HapticMapper.mapAiToHaptic(result)
                                        wifiViewModel.sendHapticCommand(hapticCommand)
                                    }
                                } finally {
                                    imageProxy.close()
                                }
                            }
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // #UI_OVERLAY_CANVAS
            // Menambahkan canvas untuk menggambar titik AI, pastikan Z-Index di atas kamera
            Canvas(modifier = Modifier.fillMaxSize().zIndex(10f)) {
                val canvasW = size.width
                val canvasH = size.height

                try {
                    val parts = inferenceResult.split("|")
                    // Menggambar Bola (Warna Biru)
                    val ballPart = parts.find { it.startsWith("B:") }?.substringAfter("B:")
                    if (ballPart != null && ballPart != "N,N") {
                        val coords = ballPart.split(",")
                        if (coords.size == 2) {
                            // Resolusi sekarang mengikuti YOLO (416x416)
                            val rx = coords[0].toFloat() / 416f
                            val ry = coords[1].toFloat() / 416f
                            
                            // Gunakan titik pusat layar jika resolusi berantakan (clamp)
                            val finalX = (rx * canvasW).coerceIn(0f, canvasW)
                            val finalY = (ry * canvasH).coerceIn(0f, canvasH)
                            
                            drawCircle(
                                color = Color.Blue,
                                radius = 25f,
                                center = Offset(finalX, finalY)
                            )
                        }
                    }

                    // Menggambar Pemain (Warna Merah)
                    val playerPart = parts.find { it.startsWith("P:") }?.substringAfter("P:")
                    if (playerPart != null && playerPart != "N,N") {
                        val coords = playerPart.split(",")
                        if (coords.size == 2) {
                            // Resolusi input YOLO adalah 416x416
                            val rx = coords[0].toFloat() / 416f
                            val ry = coords[1].toFloat() / 416f
                            
                            val finalX = (rx * canvasW).coerceIn(0f, canvasW)
                            val finalY = (ry * canvasH).coerceIn(0f, canvasH)
                            
                            drawCircle(
                                color = Color.Red,
                                radius = 35f,
                                center = Offset(finalX, finalY)
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HaptiCourt-UI", "Error saat parsing koordinat di Canvas: ${e.message}")
                }
            }

            // AREA MINI-MAP & INDIKATOR FPS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // FPS Counter
                Text(
                    text = "FPS: $fps",
                    color = Color.Yellow,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f)).padding(8.dp)
                )
                
                // Peta Mini (Court Minimap)
                Box(
                    modifier = Modifier
                        .size(120.dp, 240.dp) // Rasio 1:2 (misal untuk merepresentasikan 600x1200)
                        .background(Color(0xFF2E7D32)) // Warna Hijau Lapangan
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Gambar garis tengah lapangan
                        drawLine(
                            color = Color.White,
                            start = Offset(0f, size.height / 2),
                            end = Offset(size.width, size.height / 2),
                            strokeWidth = 2f
                        )
                        
                        // Gambar Pemain (Merah) - Bisa Banyak
                        for (pos in minimapPlayers) {
                            if (pos.x >= 0f) {
                                val mapX = (pos.x / 600f) * size.width
                                val mapY = (pos.y / 1200f) * size.height
                                
                                val clampX = mapX.coerceIn(0f, size.width)
                                val clampY = mapY.coerceIn(0f, size.height)
                                
                                drawCircle(color = Color.Red, radius = 10f, center = Offset(clampX, clampY))
                            }
                        }
                        
                        // Gambar Bola (Biru) - Bisa Banyak
                        for (pos in minimapBalls) {
                            if (pos.x >= 0f) {
                                val mapX = (pos.x / 600f) * size.width
                                val mapY = (pos.y / 1200f) * size.height
                                
                                val clampX = mapX.coerceIn(0f, size.width)
                                val clampY = mapY.coerceIn(0f, size.height)
                                
                                drawCircle(color = Color.Blue, radius = 6f, center = Offset(clampX, clampY))
                            }
                        }
                    }
                }
            }

            Text(
                text = "LIVE TRACKING",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .semantics { contentDescription = "Layar Pelacakan Langsung" }
            )

            Text(
                text = inferenceResult,
                color = Color.Green,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .semantics { contentDescription = "Hasil AI: $inferenceResult" }
            )
        }

        Button(
            onClick = { navController.popBackStack("sync", inclusive = false) },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .height(80.dp)
                .semantics { contentDescription = "Tombol Hentikan Pelacakan" }
        ) {
            Text("Stop Tracking", color = Color.White, fontSize = 24.sp)
        }
    }
}

// FUNGSI UNTUK MENERJEMAHKAN 1 TITIK (Point) MENGGUNAKAN MATRIKS KALIBRASI
fun applyHomography(x: Double, y: Double): Offset {
    val matrix = CourtHomographyManager.perspectiveMatrix
    if (matrix == null || matrix.empty()) {
        // Jika belum kalibrasi, kembalikan nilai asal (tidak ditransformasi)
        return Offset(x.toFloat(), y.toFloat())
    }

    // OpenCV butuh input array (MatOfPoint2f) meski hanya 1 titik
    val srcPoint = MatOfPoint2f(Point(x, y))
    val dstPoint = MatOfPoint2f()

    Core.perspectiveTransform(srcPoint, dstPoint, matrix)

    val transformedArr = dstPoint.toArray()
    if (transformedArr.isNotEmpty()) {
        val newX = transformedArr[0].x.toFloat()
        val newY = transformedArr[0].y.toFloat()
        return Offset(newX, newY)
    }

    return Offset(x.toFloat(), y.toFloat())
}