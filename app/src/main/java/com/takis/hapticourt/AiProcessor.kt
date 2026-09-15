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

    // Alokasi buffer YOLO 640x640 untuk Tipe FLOAT32
    // Karena kita tidak yakin apakah model ini Float32 atau INT8 di bagian Input/Outputnya,
    // kita gunakan buffer ByteBuffer ini (Float)
    private val yoloInputBuffer = ByteBuffer.allocateDirect(1 * 640 * 640 * 3 * 4).apply {
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

            // Load model kustom yang sudah dilatih khusus bola tenis dan pemain (INT8)
            yoloInterpreter = Interpreter(loadModelFile(context, "best_int8.tflite"), options)
            
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

    // Fungsi untuk mengubah Index YOLO menjadi koordinat Layar (X,Y)
    private fun getCoordinatesFromIndex(index: Int): Pair<Int, Int> {
        // YOLO 640x640 memiliki 3 tingkatan Grid (Feature Maps):
        // 1. Grid 80x80 (6400 kotak) -> Index 0 s/d 6399 (Untuk Objek Kecil, Kotak ukuran 8x8)
        // 2. Grid 40x40 (1600 kotak) -> Index 6400 s/d 7999 (Untuk Objek Sedang, Kotak ukuran 16x16)
        // 3. Grid 20x20 (400 kotak) -> Index 8000 s/d 8399 (Untuk Objek Besar, Kotak ukuran 32x32)
        
        return when {
            index < 6400 -> { // Grid 80x80 (Ukuran per sel: 640/80 = 8 pixel)
                val gridX = index % 80
                val gridY = index / 80
                Pair(gridX * 8 + 4, gridY * 8 + 4) // +4 untuk titik tengah sel
            }
            index < 8000 -> { // Grid 40x40 (Ukuran per sel: 640/40 = 16 pixel)
                val relIndex = index - 6400
                val gridX = relIndex % 40
                val gridY = relIndex / 40
                Pair(gridX * 16 + 8, gridY * 16 + 8) // +8 untuk titik tengah
            }
            else -> { // Grid 20x20 (Ukuran per sel: 640/20 = 32 pixel)
                val relIndex = index - 8000
                val gridX = relIndex % 20
                val gridY = relIndex / 20
                Pair(gridX * 32 + 16, gridY * 32 + 16) // +16 untuk titik tengah
            }
        }
    }

    private fun runYoloInference(bitmap: Bitmap): String {
        // Skala gambar input menjadi 640x640
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
        
        val intValues = IntArray(640 * 640)
        resizedBitmap.getPixels(intValues, 0, 640, 0, 0, 640, 640)
        
        yoloInputBuffer.rewind()
        
        val floatArray = FloatArray(640 * 640 * 3)

        var rIdx = 0
        var gIdx = 640 * 640
        var bIdx = 640 * 640 * 2
        
        for (pixel in intValues) {
            floatArray[rIdx++] = ((pixel shr 16) and 0xFF) / 255.0f
            floatArray[gIdx++] = ((pixel shr 8) and 0xFF) / 255.0f
            floatArray[bIdx++] = (pixel and 0xFF) / 255.0f
        }
        
        yoloInputBuffer.asFloatBuffer().put(floatArray)
        yoloInputBuffer.rewind()

        // Model kustom best_int8.tflite memiliki 1 Kelas (Bola Tenis) + 4 BBox = 5 matrix
        // Karena input 640x640, output tensornya menghasilkan 8400 bounding boxes
        val outputBuffer = Array(1) { Array(5) { FloatArray(8400) } }
        yoloInterpreter?.run(yoloInputBuffer, outputBuffer)

        // List untuk menampung semua objek yang terdeteksi
        // Gunakan list berisi Pair(Confidence, Coordinate) untuk melakukan sorting
        val ballCandidates = mutableListOf<Pair<Float, Pair<Int, Int>>>()

        var maxBallConf = 0f
        
        for (i in 0 until 8400) {
            // Karena hanya ada 1 kelas di model ini (Bola), index kelasnya adalah 4 (0,1,2,3 adalah BBox)
            val ballConf = outputBuffer[0][4][i] 
            
            if (ballConf > maxBallConf) maxBallConf = ballConf
            if (ballConf > 0.15f) {
                ballCandidates.add(Pair(ballConf, getCoordinatesFromIndex(i)))
            }
        }

        // Batasi maksimum 1 bola (karena hanya ada 1 bola dalam permainan tenis)
        val ballList = ballCandidates
            .sortedByDescending { it.first }
            .take(1) // Ambil 1 bola terbaik
            .map { it.second }

        val ballStr = if (ballList.isNotEmpty()) {
            ballList.joinToString(";") { "${it.first},${it.second}" }
        } else {
            "N,N"
        }

        // Karena model ini TIDAK BISA mendeteksi pemain, kita kirimkan "N,N" untuk pemain.
        // Jika kamu ingin mendeteksi keduanya, kamu harus men-training ulang model YOLO-mu dengan 2 kelas (Bola dan Pemain).
        return "B:$ballStr|P:N,N"
    }

    fun close() {
        yoloInterpreter?.close()
        gpuDelegate?.close()
    }
}