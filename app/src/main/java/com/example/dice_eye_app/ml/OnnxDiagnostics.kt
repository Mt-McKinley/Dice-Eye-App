package com.example.dice_eye_app.ml

import android.content.Context
import android.util.Log

/**
 * Diagnostic utility to check TensorFlow Lite availability
 */
object TFLiteDiagnostics {
    private const val TAG = "TFLiteDiagnostics"

    fun runDiagnostics(context: Context): DiagnosticResult {
        val results = mutableListOf<String>()
        var isTFLiteAvailable = false

        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "  TENSORFLOW LITE DIAGNOSTIC TEST")
        Log.d(TAG, "═══════════════════════════════════════════════════")

        // Test 1: Check if TFLite classes can be loaded
        results.add("Test 1: Checking TensorFlow Lite classes...")
        try {
            Class.forName("org.tensorflow.lite.Interpreter")
            results.add("  ✓ Interpreter class found")
            Log.d(TAG, "✓ Interpreter class found")

            Class.forName("org.tensorflow.lite.Tensor")
            results.add("  ✓ Tensor class found")
            Log.d(TAG, "✓ Tensor class found")

            isTFLiteAvailable = true
            results.add("  ✓ TensorFlow Lite is available!")
            Log.d(TAG, "✓ TensorFlow Lite is available!")

        } catch (e: ClassNotFoundException) {
            results.add("  ✗ TensorFlow Lite classes NOT FOUND")
            results.add("    Missing class: ${e.message}")
            Log.e(TAG, "✗ TensorFlow Lite NOT FOUND", e)
        }

        // Test 2: Check model files
        results.add("\nTest 2: Checking for model files...")

        // Check the detection model (Step 1)
        val detectionModel = "die_detection.tflite"
        try {
            val modelStream = context.assets.open(detectionModel)
            val modelSize = modelStream.available()
            modelStream.close()
            results.add("  ✓ Detection model found: $detectionModel (${modelSize / 1024} KB)")
            Log.d(TAG, "✓ Detection model found: $detectionModel (${modelSize / 1024} KB)")
        } catch (e: Exception) {
            results.add("  ✗ Detection model NOT FOUND: $detectionModel")
            results.add("    Error: ${e.message}")
            Log.e(TAG, "✗ Detection model error: $detectionModel", e)
        }

        // Check the classification model (Step 2)
        val classificationModel = "die_classification.tflite"
        try {
            val modelStream = context.assets.open(classificationModel)
            val modelSize = modelStream.available()
            modelStream.close()
            results.add("  ✓ Classification model found: $classificationModel (${modelSize / 1024} KB)")
            Log.d(TAG, "✓ Classification model found: $classificationModel (${modelSize / 1024} KB)")
        } catch (e: Exception) {
            results.add("  ✗ Classification model NOT FOUND: $classificationModel")
            results.add("    Error: ${e.message}")
            Log.e(TAG, "✗ Classification model error: $classificationModel", e)
        }

        Log.d(TAG, "═══════════════════════════════════════════════════")

        return DiagnosticResult(
            isAvailable = isTFLiteAvailable,
            messages = results
        )
    }

    data class DiagnosticResult(
        val isAvailable: Boolean,
        val messages: List<String>
    )
}
