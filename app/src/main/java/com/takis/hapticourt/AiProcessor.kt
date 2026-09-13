package com.takis.hapticourt

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class AiProcessor(context: Context) {

    private var yoloInterpreter: Interpreter? = null
    private var trackNetInterpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val frameHistory = mutableListOf<Bitmap>()

    init {
        val compatList = CompatibilityList()
        val options = Interpreter.Options()

        if (compatList.isDelegateSupportedOnThisDevice) {
            gpuDelegate = GpuDelegate(compatList.bestOptionsForThisDevice)
            options.addDelegate(gpuDelegate)
        } else {
            options.numThreads = 4
        }

        try {
            yoloInterpreter = Interpreter(loadModelFile(context, "yolo26n_pose_fixed.tflite"), options)
            trackNetInterpreter = Interpreter(loadModelFile(context, "tracknet_fp16.tflite"), options)
        } catch (e: Exception) {
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

    suspend fun analyzeFrame(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        if (yoloInterpreter == null || trackNetInterpreter == null) {
            return@withContext "Error"
        }

        updateFrameHistory(bitmap)

        val yoloTask = async { runYoloInference(bitmap) }
        val trackNetTask = async {
            if (frameHistory.size == 3) runTrackNetInference() else "N,N"
        }

        val yoloResult = yoloTask.await()
        val trackNetResult = trackNetTask.await()

        "B:$trackNetResult|P:$yoloResult"
    }

    private fun updateFrameHistory(bitmap: Bitmap) {
        val resized = Bitmap.createScaledBitmap(bitmap, 512, 288, true)
        frameHistory.add(resized)
        if (frameHistory.size > 3) {
            frameHistory.removeAt(0)
        }
    }

    private fun runYoloInference(bitmap: Bitmap): String {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * 640 * 640 * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(640 * 640)
        resizedBitmap.getPixels(intValues, 0, 640, 0, 0, 640, 640)
        for (pixelValue in intValues) {
            inputBuffer.putFloat(((pixelValue shr 16 and 0xFF) / 255.0f))
            inputBuffer.putFloat(((pixelValue shr 8 and 0xFF) / 255.0f))
            inputBuffer.putFloat(((pixelValue and 0xFF) / 255.0f))
        }

        val outputBuffer = Array(1) { Array(56) { FloatArray(8400) } }
        yoloInterpreter?.run(inputBuffer, outputBuffer)

        var maxConf = 0f
        var bestIdx = -1

        for (i in 0 until 8400) {
            val conf = outputBuffer[0][4][i]
            if (conf > maxConf) {
                maxConf = conf
                bestIdx = i
            }
        }

        if (maxConf > 0.5f && bestIdx != -1) {
            val playerX = outputBuffer[0][0][bestIdx]
            val playerY = outputBuffer[0][1][bestIdx]
            return "${playerX.toInt()},${playerY.toInt()}"
        }

        return "N,N"
    }

    private fun runTrackNetInference(): String {
        val inputBuffer = ByteBuffer.allocateDirect(1 * 9 * 288 * 512 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        for (frame in frameHistory) {
            val intValues = IntArray(512 * 288)
            frame.getPixels(intValues, 0, 512, 0, 0, 512, 288)
            for (pixelValue in intValues) {
                inputBuffer.putFloat(((pixelValue shr 16 and 0xFF) / 255.0f))
                inputBuffer.putFloat(((pixelValue shr 8 and 0xFF) / 255.0f))
                inputBuffer.putFloat(((pixelValue and 0xFF) / 255.0f))
            }
        }

        val outputBuffer = Array(1) { Array(288) { FloatArray(512) } }
        trackNetInterpreter?.run(inputBuffer, outputBuffer)

        var maxVal = 0f
        var ballX = -1
        var ballY = -1

        for (y in 0 until 288) {
            for (x in 0 until 512) {
                val value = outputBuffer[0][y][x]
                if (value > maxVal) {
                    maxVal = value
                    ballX = x
                    ballY = y
                }
            }
        }

        if (maxVal > 0.5f) {
            return "$ballX,$ballY"
        }

        return "N,N"
    }

    fun close() {
        yoloInterpreter?.close()
        trackNetInterpreter?.close()
        gpuDelegate?.close()
    }
}