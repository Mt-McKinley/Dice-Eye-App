package com.example.dice_eye_app.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

    // Model configuration - will be auto-detected from model
    private var inputWidth = 224
    private var inputHeight = 224
    private var inputChannels = 3
    private val numClasses = 6

    // IMPROVED: Class mapping - from inspecting the tensorflow-dice-model-main/image_data/die_classification folder structure
    // The model has training data ordered alphabetically as:
    // 0: five (actually face 5)
    // 1: four (actually face 4)
    // 2: one (actually face 1)
    // 3: six (actually face 6)
    // 4: three (actually face 3)
    // 5: two (actually face 2)
    // This maps the model's output index directly to the actual dice face value (1-6)
    private val classToFaceValue = intArrayOf(5, 4, 1, 6, 3, 2)

    private var inputDataType: DataType = DataType.UINT8
    private var outputDataType: DataType = DataType.FLOAT32

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

                // Check output
                val outputTensor = interp.getOutputTensor(0)
                val outputShape = outputTensor.shape()
                outputDataType = outputTensor.dataType()
                Log.d(TAG, "Output: shape=${outputShape.contentToString()}, type=${outputDataType}")
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

            // Use raw UINT8 preprocessing - this matches the model's training configuration
            val inputBuffer = preprocessImageUint8(bitmap)

            // The model outputs UINT8 values
            val output = Array(1) { ByteArray(numClasses) }
            interp.run(inputBuffer, output)

            // Parse the quantized output directly
            val result = parseQuantizedOutputUint8(output[0])
            Log.d(TAG, "Classification: face ${result.classId + 1}, confidence=${result.confidence}")
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

        // Simply copy raw RGB values (0-255)
        for (pixel in pixels) {
            byteBuffer.put(((pixel shr 16) and 0xFF).toByte())  // R
            byteBuffer.put(((pixel shr 8) and 0xFF).toByte())   // G
            byteBuffer.put((pixel and 0xFF).toByte())           // B
        }

        byteBuffer.rewind()
        return byteBuffer
    }

    private fun parseQuantizedOutput(bytes: ByteArray): ClassificationResult {
        var maxVal = -1
        var predictedClass = -1
        val probs = FloatArray(bytes.size)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            probs[i] = v / 255f
            if (v > maxVal) {
                maxVal = v
                predictedClass = i
            }
        }
        val margin = top1Margin(probs)
        Log.d(TAG, "All probabilities (uint8): ${probs.joinToString { "%.3f".format(it) }} | margin=${"%.3f".format(margin)}")
        val confidence = maxVal / 255f

        // FIX: Use the mapping to get the correct face value
        val faceValue = if (predictedClass in classToFaceValue.indices) {
            classToFaceValue[predictedClass]
        } else {
            -1 // Invalid class
        }

        return ClassificationResult(predictedClass, "Face $faceValue", confidence, probs)
    }

    private fun parseQuantizedOutputUint8(bytes: ByteArray): ClassificationResult = parseQuantizedOutput(bytes)

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
