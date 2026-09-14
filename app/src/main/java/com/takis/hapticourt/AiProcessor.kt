package com.takis.hapticourt

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean

class AiProcessor(context: Context) {

    private val TAG = "HaptiCourt-AI"
    private var yoloInterpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var errorMessage: String = ""
    
    // Mencegah tabrakan frame
    private val isProcessing = AtomicBoolean(false)

    // Alokasi buffer YOLO 416x416 untuk Tipe FLOAT32
    // Karena kita tidak yakin apakah model ini Float32 atau INT8 di bagian Input/Outputnya,
    // kita gunakan buffer ByteBuffer ini (Float)
    private val yoloInputBuffer = ByteBuffer.allocateDirect(1 * 416 * 416 * 3 * 4).apply {
        order(ByteOrder.nativeOrder())
    }

    init {
        val compatList = CompatibilityList()
        val options = Interpreter.Options()

        try {
            if (compatList.isDelegateSupportedOnThisDevice) {
                gpuDelegate = GpuDelegate(compatList.bestOptionsForThisDevice)
                options.addDelegate(gpuDelegate)
                Log.d(TAG, "GPU Delegate berhasil diaktifkan.")
            } else {
                options.numThreads = 4
                Log.d(TAG, "GPU tidak didukung, menggunakan 4 Threads CPU.")
            }

            // Load model YOLO26 Nano murni
            yoloInterpreter = Interpreter(loadModelFile(context, "yolo26n.tflite"), options)
            
            // Cek Tipe Data Input dan Output secara rinci
            val inputTensor = yoloInterpreter!!.getInputTensor(0)
            Log.d(TAG, "==== INFORMASI MODEL ====")
            Log.d(TAG, "INPUT  | Type: ${inputTensor.dataType()}, Shape: ${inputTensor.shape().contentToString()}")
            
            val outputTensorCount = yoloInterpreter!!.outputTensorCount
            Log.d(TAG, "JUMLAH OUTPUT TENSOR: $outputTensorCount")
            for (i in 0 until outputTensorCount) {
                val outTensor = yoloInterpreter!!.getOutputTensor(i)
                Log.d(TAG, "OUTPUT $i | Type: ${outTensor.dataType()}, Shape: ${outTensor.shape().contentToString()}")
                
                // Tambahkan pengecekan skala kuantisasi jika ada
                val params = outTensor.quantizationParams()
                if (params != null && params.scale > 0.0) {
                     Log.d(TAG, "OUTPUT $i QUANTIZATION | Scale: ${params.scale}, ZeroPoint: ${params.zeroPoint}")
                }
            }
            Log.d(TAG, "=========================")

        } catch (e: Exception) {
            errorMessage = e.message ?: "Unknown Error"
            Log.e(TAG, "GAGAL meload model: $errorMessage")
            e.printStackTrace()
        }
    }

    private fun loadModelFile(context: Context, modelName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    suspend fun analyzeFrame(bitmap: Bitmap): String {
        // Skip frame jika masih memproses frame sebelumnya
        if (!isProcessing.compareAndSet(false, true)) {
            return "SKIP" 
        }
        
        return try {
            withContext(Dispatchers.Default) {
                if (yoloInterpreter == null) {
                    return@withContext "ERR: $errorMessage"
                }

                // Cukup jalankan YOLO 1x saja
                runYoloInference(bitmap)
            }
        } finally {
            isProcessing.set(false)
        }
    }

    private fun runYoloInference(bitmap: Bitmap): String {
        // Skala gambar input menjadi 416x416
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 416, 416, true)
        
        // Kosongkan buffer YOLO
        yoloInputBuffer.rewind()

        val intValues = IntArray(416 * 416)
        resizedBitmap.getPixels(intValues, 0, 416, 0, 0, 416, 416)
        
        // Ultralytics TFLite Export default (int8=True) umumnya tetap meminta Input berformat Float32 [0.0 - 1.0]
        // Mereka menyematkan layer "Quantize" di dalam modelnya.
        // TETAPI orientasi gambarnya harus RGB (CameraX memberikan ARGB/RGBA).
        // Mari pastikan urutannya R-G-B
        // Format Input YOLO PyTorch: CHW (Channel, Height, Width) [0.0 - 1.0]
        // KITA HARUS MENGISI: SEMUA RED, lalu SEMUA GREEN, lalu SEMUA BLUE.
        
        // 1. Ekstrak Red
        for (i in 0 until intValues.size) {
            val r = (intValues[i] shr 16 and 0xFF) / 255.0f
            yoloInputBuffer.putFloat(r)
        }
        
        // 2. Ekstrak Green
        for (i in 0 until intValues.size) {
            val g = (intValues[i] shr 8 and 0xFF) / 255.0f
            yoloInputBuffer.putFloat(g)
        }
        
        // 3. Ekstrak Blue
        for (i in 0 until intValues.size) {
            val b = (intValues[i] and 0xFF) / 255.0f
            yoloInputBuffer.putFloat(b)
        }
        
        // Kembalikan ke awal untuk pembacaan TFLite
        yoloInputBuffer.rewind()

        // YOLOv8/11/26 standard COCO memiliki 80 class + 4 koordinat = 84 matrix
        // Karena input 416x416, output tensornya menghasilkan 3549 bounding boxes
        val outputBuffer = Array(1) { Array(84) { FloatArray(3549) } }
        yoloInterpreter?.run(yoloInputBuffer, outputBuffer)

        var bestPlayerConf = 0f
        var bestPlayerIdx = -1

        var bestBallConf = 0f
        var bestBallIdx = -1

        // Loop untuk mengecek semua prediksi
        for (i in 0 until 3549) {
            // Class 0: Person (index = 4 + 0 = 4)
            // Model YOLO versi Float32 umumnya SUDAH MENGAPLIKASIKAN SIGMOID jika tidak pakai NMS
            // Jadi kita ambil nilai aslinya saja dulu
            val playerConf = outputBuffer[0][4][i]
            if (playerConf > bestPlayerConf) {
                bestPlayerConf = playerConf
                bestPlayerIdx = i
            }

            // Class 32: Sports Ball (index = 4 + 32 = 36)
            val ballConf = outputBuffer[0][36][i]
            if (ballConf > bestBallConf) {
                bestBallConf = ballConf
                bestBallIdx = i
            }
        }

        // TAMPILKAN LOG HASIL MENTAH DARI TFLITE
        Log.d(TAG, "RAW SCORE -> Pemain: $bestPlayerConf (Idx: $bestPlayerIdx) | Bola: $bestBallConf (Idx: $bestBallIdx)")

        // Thresholding diturunkan karena Model INT8 Nano memiliki akurasi yang lebih rendah
        // 0.25f (25%) sudah cukup baik untuk mendeteksi manusia di model ringan
        val playerResult = if (bestPlayerConf > 0.25f && bestPlayerIdx != -1) {
            val cx = outputBuffer[0][0][bestPlayerIdx] // Titik tengah X
            val cy = outputBuffer[0][1][bestPlayerIdx] // Titik tengah Y
            "${cx.toInt()},${cy.toInt()}"
        } else {
            "N,N"
        }

        // 0.15f (15%) untuk bola karena objeknya sangat kecil dan sering blur
        val ballResult = if (bestBallConf > 0.15f && bestBallIdx != -1) {
            val cx = outputBuffer[0][0][bestBallIdx] // Titik tengah X
            val cy = outputBuffer[0][1][bestBallIdx] // Titik tengah Y
            "${cx.toInt()},${cy.toInt()}"
        } else {
            "N,N"
        }

        return "B:$ballResult|P:$playerResult"
    }

    fun close() {
        yoloInterpreter?.close()
        gpuDelegate?.close()
    }
}