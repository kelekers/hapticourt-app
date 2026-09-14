package com.takis.hapticourt

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

// Objek untuk menyimpan Matriks Transformasi Global secara aman
object CourtHomographyManager {
    var perspectiveMatrix: Mat? = null
    var isCalibrated = false
    
    // Titik referensi ukuran asli lapangan tenis dalam Mini-map 2D (misal: 600x1200 pixel)
    // Titik 1: Sudut Kiri Atas, 2: Kanan Atas, 3: Kiri Bawah, 4: Kanan Bawah
    val dstPoints by lazy {
        MatOfPoint2f(
            Point(0.0, 0.0),       // Kiri Atas
            Point(600.0, 0.0),     // Kanan Atas
            Point(0.0, 1200.0),    // Kiri Bawah
            Point(600.0, 1200.0)   // Kanan Bawah
        )
    }
}

class CourtDetector(private val context: Context) {
    private val TAG = "CourtDetector"

    // Resolusi input yang diwajibkan oleh model court_det_float16.tflite
    private val inputWidth = 512
    private val inputHeight = 288
    
    // Model VGG ( court_det_float16.tflite ) mengharapkan NCHW
    private val inputBuffer = ByteBuffer.allocateDirect(1 * 3 * inputHeight * inputWidth * 4).apply {
        order(ByteOrder.nativeOrder())
    }

    suspend fun calibrateCourt(bitmap: Bitmap): Boolean = withContext(Dispatchers.Default) {
        var interpreter: Interpreter? = null
        var gpuDelegate: GpuDelegate? = null
        
        try {
            val options = Interpreter.Options()
            val compatList = CompatibilityList()

            // Gunakan GPU untuk model Float16 karena CPU murni Android sering kesulitan
            if (compatList.isDelegateSupportedOnThisDevice) {
                gpuDelegate = GpuDelegate(compatList.bestOptionsForThisDevice)
                options.addDelegate(gpuDelegate)
                Log.d(TAG, "Menggunakan GPU Delegate untuk Court Detector.")
            } else {
                options.numThreads = 4 
                Log.d(TAG, "GPU tidak didukung. Memaksa CPU (4 Threads).")
            }
            
            // Ganti ke model Float 32 yang lebih stabil dan native di TFLite
            interpreter = Interpreter(loadModelFile(context, "court_det_float32.tflite"), options)
            Log.d(TAG, "Model Lapangan (Float32) berhasil dimuat.")

            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
            inputBuffer.rewind()

            val intValues = IntArray(inputWidth * inputHeight)
            resizedBitmap.getPixels(intValues, 0, inputWidth, 0, 0, inputWidth, inputHeight)

            // Mengisi NCHW secara berurutan (Red, Green, Blue)
            var rOffset = 0
            var gOffset = inputWidth * inputHeight
            var bOffset = inputWidth * inputHeight * 2
            
            // Menurut arsitektur VGG, gambar biasanya dinormalisasi dengan mean/std, tapi kita pakai standar 0-1
            for (pixel in intValues) {
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f
                
                inputBuffer.putFloat(rOffset * 4, r)
                inputBuffer.putFloat(gOffset * 4, g)
                inputBuffer.putFloat(bOffset * 4, b)
                
                rOffset++
                gOffset++
                bOffset++
            }

            // Output dari court_det_float16 adalah [1, 15, 288, 512] (14 keypoint + 1 background)
            val outputBuffer = Array(1) { Array(15) { Array(288) { FloatArray(512) } } }
            
            Log.d(TAG, "Mulai Inference Court...")
            interpreter.run(inputBuffer, outputBuffer)
            Log.d(TAG, "Inference Selesai.")

            // Ekstrak 4 Sudut Terluar Lapangan (Asumsi: Channel 0, 1, 2, dan 3 adalah 4 sudut luar lapangan)
            // *Catatan: Urutan channel ini harus disesuaikan dengan repo asli jika hasilnya terbalik
            val srcPts = mutableListOf<Point>()
            
            // Loop 4 titik sudut
            for (channel in 0..3) {
                var maxVal = -1f
                var bestX = -1
                var bestY = -1
                
                for (y in 0 until inputHeight) {
                    for (x in 0 until inputWidth) {
                        val heat = outputBuffer[0][channel][y][x]
                        if (heat > maxVal) {
                            maxVal = heat
                            bestX = x
                            bestY = y
                        }
                    }
                }
                
                // Kembalikan koordinat ke resolusi asli (640x640) agar sinkron dengan YOLO nanti
                val scaleX = 640f / inputWidth.toFloat()
                val scaleY = 640f / inputHeight.toFloat()
                
                srcPts.add(Point((bestX * scaleX).toDouble(), (bestY * scaleY).toDouble()))
                Log.d(TAG, "Titik Sudut $channel ditemukan di (${bestX}, ${bestY}) | maxHeat: $maxVal")
            }

            // --- PENGURUTAN TITIK OPENCV (MENCEGAH MATRIKS MELINTIR) ---
            // Urutkan titik agar pas dengan dstPoints: TL, TR, BL, BR
            
            // 1. Urutkan berdasarkan titik Y (2 teratas, 2 terbawah)
            val sortedByY = srcPts.sortedBy { it.y }
            
            val topPoints = sortedByY.take(2).sortedBy { it.x } // Top Left, Top Right
            val bottomPoints = sortedByY.takeLast(2).sortedBy { it.x } // Bottom Left, Bottom Right
            
            val topLeft = topPoints[0]
            val topRight = topPoints[1]
            val bottomLeft = bottomPoints[0]
            val bottomRight = bottomPoints[1]

            val orderedSrcPts = listOf(topLeft, topRight, bottomLeft, bottomRight)
            Log.d(TAG, "Titik Urut: TL=$topLeft, TR=$topRight, BL=$bottomLeft, BR=$bottomRight")

            // --- OPENCV HOMOGRAPHY ---
            val srcPointsMat = MatOfPoint2f(*orderedSrcPts.toTypedArray())
            
            // Hitung Matriks Transformasi (Dari perspektif kamera ke peta datar 2D)
            val perspectiveMatrix = Imgproc.getPerspectiveTransform(srcPointsMat, CourtHomographyManager.dstPoints)
            
            CourtHomographyManager.perspectiveMatrix = perspectiveMatrix
            CourtHomographyManager.isCalibrated = true
            
            Log.d(TAG, "Kalibrasi Berhasil! Matriks telah disimpan.")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Gagal kalibrasi: ${e.message}")
            e.printStackTrace()
            return@withContext false
        } finally {
            // SANGAT PENTING: Tutup interpreter untuk mencegah OOM (Out Of Memory)!
            interpreter?.close()
            gpuDelegate?.close()
            Log.d(TAG, "Interpreter Lapangan & GPU Delegate dihancurkan. RAM dibebaskan.")
        }
    }

    private fun loadModelFile(context: Context, modelName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }
}