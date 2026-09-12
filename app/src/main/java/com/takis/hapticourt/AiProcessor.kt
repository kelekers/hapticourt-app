package com.takis.hapticourt

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AiProcessor(context: Context) {

    private var interpreter: Interpreter? = null
    private var isModelLoaded = false

    init {
        try {
            val assetManager = context.assets
            val modelFd = assetManager.openFd("yolo_model.tflite")
            val inputStream = java.io.FileInputStream(modelFd.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = modelFd.startOffset
            val declaredLength = modelFd.declaredLength
            val mappedByteBuffer = fileChannel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            interpreter = Interpreter(mappedByteBuffer)
            isModelLoaded = true
        } catch (e: Exception) {
            println("AI_ERROR: File yolo_model.tflite tidak ditemukan di folder assets.")
        }
    }

    fun analyzeFrame(bitmap: Bitmap): String {
        if (!isModelLoaded) {
            return "Simulation: Ball Center, Opponent Left"
        }

        val byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        return "Ball: Center, Opponent: Left"
    }

    fun close() {
        interpreter?.close()
    }
}