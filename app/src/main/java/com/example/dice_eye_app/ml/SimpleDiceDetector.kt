package com.example.dice_eye_app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/**
 * Dice detector that tries to load the ONNX model
 * Falls back to mock detection if model loading fails
 */
class SimpleDiceDetector(private val context: Context) {

    private var modelLoaded = false
    private var modelFile: File? = null

    init {
        tryLoadModel()
    }

    private fun tryLoadModel() {
        try {
            Log.d(TAG, "=== ATTEMPTING TO LOAD ONNX MODEL ===")

            // Copy model from assets to cache directory
            val inputStream = context.assets.open("my_model.onnx")
            val modelBytes = inputStream.readBytes()
            inputStream.close()

            Log.d(TAG, "Model file found in assets, size: ${modelBytes.size} bytes")

            // Save to cache for potential future use
            modelFile = File(context.cacheDir, "my_model.onnx")
            FileOutputStream(modelFile).use { it.write(modelBytes) }

            Log.d(TAG, "Model copied to cache: ${modelFile?.absolutePath}")

            // For now, we can't load ONNX without the runtime library
            // But at least we've verified the model file exists
            modelLoaded = false
            Log.w(TAG, "Model file exists but ONNX Runtime is not available")
            Log.w(TAG, "Using mock detection - you need to convert model to TensorFlow Lite")

        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
            modelLoaded = false
        }
    }

    /**
     * Detect dice in the given bitmap image
     * Currently returns mock detections since ONNX Runtime won't load
     */
    fun detectDice(bitmap: Bitmap): List<Detection> {
        Log.d(TAG, "SimpleDiceDetector: Analyzing bitmap ${bitmap.width}x${bitmap.height}")

        if (!modelLoaded) {
            Log.w(TAG, "⚠️ USING MOCK DETECTION - Model not loaded")
            Log.w(TAG, "To fix: Convert your ONNX model to TensorFlow Lite (.tflite)")
            Log.w(TAG, "Or manually sync Gradle in Android Studio: File > Sync Project with Gradle Files")
        }

        // Analyze bitmap brightness to vary number of dice detected
        // This makes it seem more realistic than pure random
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var brightness = 0
        for (i in pixels.indices step 1000) { // Sample every 1000th pixel for speed
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            brightness += (r + g + b) / 3
        }
        val avgBrightness = brightness / (pixels.size / 1000)

        // Use brightness to vary number of dice (1-4)
        val numDice = ((avgBrightness / 64) % 4) + 1

        Log.d(TAG, "Image brightness: $avgBrightness, generating $numDice mock dice")

        val detections = mutableListOf<Detection>()

        for (i in 0 until numDice) {
            val diceValue = Random.nextInt(0, 6) // 0-5 for classId
            val confidence = Random.nextFloat() * 0.4f + 0.6f // 0.6 to 1.0

            // Random position within image bounds
            val centerX = Random.nextFloat() * bitmap.width
            val centerY = Random.nextFloat() * bitmap.height
            val size = Random.nextFloat() * 100 + 50 // 50-150 pixels

            detections.add(
                Detection(
                    boundingBox = RectF(
                        centerX - size/2,
                        centerY - size/2,
                        centerX + size/2,
                        centerY + size/2
                    ),
                    confidence = confidence,
                    classId = diceValue,
                    className = "Dice ${diceValue + 1}"
                )
            )
        }

        Log.d(TAG, "Generated ${detections.size} mock detections: ${detections.map { it.classId + 1 }}")
        return detections
    }

    fun close() {
        Log.d(TAG, "SimpleDiceDetector closed")
        modelFile?.delete()
    }

    data class Detection(
        val boundingBox: RectF,
        val confidence: Float,
        val classId: Int,
        val className: String
    )

    companion object {
        private const val TAG = "SimpleDiceDetector"
    }
}
