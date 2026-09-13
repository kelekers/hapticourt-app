import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteHelper(private val context: Context, private val modelName: String) {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    init {
        setupInterpreter()
    }

    private fun setupInterpreter() {
        val options = Interpreter.Options()

        val compatList = CompatibilityList()
        if (compatList.isDelegateSupportedOnThisDevice) {
            val delegateOptions = compatList.bestOptionsForThisDevice
            gpuDelegate = GpuDelegate(delegateOptions)
            options.addDelegate(gpuDelegate)
        } else {
            options.numThreads = 4
        }
        val modelBuffer = loadModelFile(context, modelName)
        interpreter = Interpreter(modelBuffer, options)
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    fun runInference(inputData: Any, outputBuffer: Any) {
        interpreter?.run(inputData, outputBuffer)
    }

    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
    }
}