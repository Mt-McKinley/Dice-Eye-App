package com.example.dice_eye_app.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.dice_eye_app.util.DebugConfig
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

/**
 * Dice classifier using TensorFlow Lite for image classification
 * Step 2 of the two-step detection process: classifies each detected die (1-6)
 */
class DiceClassifier(private val context: Context) {
    private var interpreter: Interpreter? = null
    private var modelLoaded = false

    private val modelFileName = "die_classification.tflite"

    // Model configuration - auto-detected from model
    private var inputWidth = 224
    private var inputHeight = 224
    private var inputChannels = 3
    private val numClasses = 6

    private var inputDataType: DataType = DataType.UINT8
    private var outputDataType: DataType = DataType.FLOAT32

    // Quantization parameters (if available)
    private var inputScale: Float = 1.0f
    private var inputZeroPoint: Int = 0
    private var outputScale: Float = 1.0f
    private var outputZeroPoint: Int = 0

    // Optional normalization toggles for FLOAT32 input models
    private val normalizeZeroOne = true       // default
    private val normalizeMinusOneToOne = false
    private val useImagenetMeanStd = false    // mean=[0.485,0.456,0.406], std=[0.229,0.224,0.225]

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            Log.d(TAG, "=== LOADING CLASSIFICATION MODEL ===")

            val modelBuffer = loadModelFile()
            Log.d(TAG, "Classification model file found, size: ${modelBuffer.capacity()} bytes")

            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseNNAPI(false)
            }

            interpreter = Interpreter(modelBuffer, options)
            modelLoaded = true

            Log.d(TAG, "✓✓✓ Classification model loaded successfully! ✓✓✓")

            interpreter?.let { interp ->
                Log.d(TAG, "=== CLASSIFICATION MODEL INSPECTION ===")

                // Check input
                val inputTensor = interp.getInputTensor(0)
                val inputShape = inputTensor.shape()
                inputDataType = inputTensor.dataType()
                Log.d(TAG, "Input: shape=${inputShape.contentToString()}, type=${inputDataType}")
                if (inputShape.size == 4) {
                    inputHeight = inputShape[1]
                    inputWidth = inputShape[2]
                    inputChannels = inputShape[3]
                    Log.d(TAG, ">>> Auto-detected input: ${inputWidth}x${inputHeight}x${inputChannels}")
                }
                try {
                    val q = inputTensor.quantizationParams()
                    if (q != null) {
                        inputScale = q.scale
                        inputZeroPoint = q.zeroPoint
                        Log.d(TAG, "Input quantization: scale=$inputScale, zeroPoint=$inputZeroPoint")
                    }
                } catch (_: Throwable) { /* quantization params may be unavailable */ }

                // Check output
                val outputTensor = interp.getOutputTensor(0)
                val outputShape = outputTensor.shape()
                outputDataType = outputTensor.dataType()
                Log.d(TAG, "Output: shape=${outputShape.contentToString()}, type=${outputDataType}")
                try {
                    val q = outputTensor.quantizationParams()
                    if (q != null) {
                        outputScale = q.scale
                        outputZeroPoint = q.zeroPoint
                        Log.d(TAG, "Output quantization: scale=$outputScale, zeroPoint=$outputZeroPoint")
                    }
                } catch (_: Throwable) { /* ignore */ }
                Log.d(TAG, "=====================================")
            }

        } catch (e: Exception) {
            Log.e(TAG, "✗ ERROR loading classification model", e)
            interpreter = null
            modelLoaded = false
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Classifies a cropped die image into faces 1-6
     * @param bitmap The cropped die image
     * @return ClassificationResult with predicted class (0-5 for faces 1-6) and confidence
     */
    fun classify(bitmap: Bitmap): ClassificationResult? {
        if (!modelLoaded || interpreter == null) {
            Log.e(TAG, "Classification model not loaded")
            return null
        }

        try {
            val interp = interpreter!!

            // Build input buffer based on input tensor type
            val inputBuffer: Any = when (inputDataType) {
                DataType.FLOAT32 -> preprocessImageFloat32(bitmap)
                DataType.UINT8 -> preprocessImageUint8(bitmap)
                DataType.INT8 -> preprocessImageInt8(bitmap)
                else -> {
                    Log.w(TAG, "Unsupported input type: $inputDataType, defaulting to FLOAT32 path")
                    preprocessImageFloat32(bitmap)
                }
            }

            // Prepare output container based on output tensor type
            val result = when (outputDataType) {
                DataType.FLOAT32 -> {
                    val output = Array(1) { FloatArray(numClasses) }
                    interp.run(inputBuffer, output)
                    parseFloatOutput(output[0])
                }
                DataType.UINT8, DataType.INT8 -> {
                    val output = Array(1) { ByteArray(numClasses) }
                    interp.run(inputBuffer, output)
                    parseQuantizedOutput(output[0])
                }
                else -> {
                    Log.w(TAG, "Unsupported output type: $outputDataType, attempting FLOAT32 parse")
                    val output = Array(1) { FloatArray(numClasses) }
                    interp.run(inputBuffer, output)
                    parseFloatOutput(output[0])
                }
            }

            Log.d(TAG, "Classification RAW: class=${result.classId}, conf=${"%.3f".format(result.confidence)}")
            return result

        } catch (e: Exception) {
            Log.e(TAG, "Error during classification", e)
            return null
        }
    }

    private fun preprocessImageUint8(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val byteBuffer = ByteBuffer.allocateDirect(inputWidth * inputHeight * inputChannels)
        byteBuffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputWidth * inputHeight)
        resized.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF).toByte()
            val g = ((pixel shr 8) and 0xFF).toByte()
            val b = (pixel and 0xFF).toByte()
            if (DebugConfig.SWAP_RB_CHANNELS) {
                byteBuffer.put(b); byteBuffer.put(g); byteBuffer.put(r)
            } else {
                byteBuffer.put(r); byteBuffer.put(g); byteBuffer.put(b)
            }
        }
        byteBuffer.rewind()
        return byteBuffer
    }

    private fun preprocessImageInt8(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val byteBuffer = ByteBuffer.allocateDirect(inputWidth * inputHeight * inputChannels)
        byteBuffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputWidth * inputHeight)
        resized.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        // Quantize using inputScale/zeroPoint when available
        fun q(valNorm: Float): Byte {
            val quant = Math.round(valNorm / inputScale + inputZeroPoint).coerceIn(-128, 127)
            return quant.toByte()
        }
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            if (DebugConfig.SWAP_RB_CHANNELS) {
                byteBuffer.put(q(b)); byteBuffer.put(q(g)); byteBuffer.put(q(r))
            } else {
                byteBuffer.put(q(r)); byteBuffer.put(q(g)); byteBuffer.put(q(b))
            }
        }
        byteBuffer.rewind()
        return byteBuffer
    }

    private fun preprocessImageFloat32(bitmap: Bitmap): FloatBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val floatBuffer = ByteBuffer.allocateDirect(4 * inputWidth * inputHeight * inputChannels)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        val pixels = IntArray(inputWidth * inputHeight)
        resized.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        val mean = if (useImagenetMeanStd) floatArrayOf(0.485f, 0.456f, 0.406f) else floatArrayOf(0f, 0f, 0f)
        val std = if (useImagenetMeanStd) floatArrayOf(0.229f, 0.224f, 0.225f) else floatArrayOf(1f, 1f, 1f)

        for (pixel in pixels) {
            var r = ((pixel shr 16) and 0xFF) / 255f
            var g = ((pixel shr 8) and 0xFF) / 255f
            var b = (pixel and 0xFF) / 255f

            if (normalizeMinusOneToOne) {
                r = r * 2f - 1f
                g = g * 2f - 1f
                b = b * 2f - 1f
            } // else normalizeZeroOne keeps 0..1

            // Optional Imagenet normalization
            r = (r - mean[0]) / std[0]
            g = (g - mean[1]) / std[1]
            b = (b - mean[2]) / std[2]

            if (DebugConfig.SWAP_RB_CHANNELS) {
                floatBuffer.put(b); floatBuffer.put(g); floatBuffer.put(r)
            } else {
                floatBuffer.put(r); floatBuffer.put(g); floatBuffer.put(b)
            }
        }
        floatBuffer.rewind()
        return floatBuffer
    }

    private fun parseFloatOutput(probs: FloatArray): ClassificationResult {
        // Treat as logits unless clearly normalized; apply softmax for stability
        val sm = softmax(probs)
        var maxProb = -Float.MAX_VALUE
        var predictedClass = -1
        sm.forEachIndexed { index, p ->
            if (p > maxProb) {
                maxProb = p
                predictedClass = index
            }
        }
        val margin = top1Margin(sm)
        Log.d(TAG, "All probabilities (float): ${sm.joinToString { "%.3f".format(it) }} | margin=${"%.3f".format(margin)}")
        return ClassificationResult(predictedClass, "Face ${predictedClass + 1}", sm[predictedClass], sm)
    }

    private fun parseQuantizedOutput(bytes: ByteArray): ClassificationResult {
        // Dequantize to real values (logits), then apply softmax
        val logits = FloatArray(bytes.size)
        for (i in bytes.indices) {
            val signed = bytes[i].toInt()
            logits[i] = if (outputDataType == DataType.UINT8) {
                val uq = signed and 0xFF
                outputScale * (uq - outputZeroPoint)
            } else {
                outputScale * (signed - outputZeroPoint)
            }
        }
        val sm = softmax(logits)
        var predictedClass = -1
        var maxProb = -Float.MAX_VALUE
        for (i in sm.indices) {
            if (sm[i] > maxProb) {
                maxProb = sm[i]
                predictedClass = i
            }
        }
        val margin = top1Margin(sm)
        Log.d(TAG, "All probabilities (quant): ${sm.joinToString { "%.3f".format(it) }} | margin=${"%.3f".format(margin)}")
        return ClassificationResult(predictedClass, "Face ${predictedClass + 1}", sm[predictedClass], sm)
    }

    private fun softmax(logits: FloatArray): FloatArray {
        if (logits.isEmpty()) return logits
        val maxLogit = logits.maxOrNull() ?: 0f
        var sumExp = 0.0
        val exps = DoubleArray(logits.size)
        for (i in logits.indices) {
            val e = exp((logits[i] - maxLogit).toDouble())
            exps[i] = e
            sumExp += e
        }
        if (sumExp <= 0.0) return FloatArray(logits.size) { 1f / logits.size }
        return FloatArray(logits.size) { (exps[it] / sumExp).toFloat() }
    }

    private fun top1Margin(probs: FloatArray): Float {
        var top1 = -1f
        var top2 = -1f
        for (p in probs) {
            if (p > top1) {
                top2 = top1
                top1 = p
            } else if (p > top2) {
                top2 = p
            }
        }
        if (top1 < 0f || top2 < 0f) return 0f
        return top1 - top2
    }

    fun close() {
        try {
            interpreter?.close()
            interpreter = null
            Log.d(TAG, "DiceClassifier closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing classifier", e)
        }
    }

    data class ClassificationResult(
        val classId: Int,
        val className: String,
        val confidence: Float,
        val allProbabilities: FloatArray
    )

    companion object {
        private const val TAG = "DiceClassifier"
    }
}
