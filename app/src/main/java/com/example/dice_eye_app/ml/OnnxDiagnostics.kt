package com.example.dice_eye_app.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Diagnostic utility to check TensorFlow Lite availability and model health
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
        val detectionModel = "die_classifier.tflite"  // Single model for both detection and classification
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
        val classificationModel = "die_classifier.tflite"
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

    // New: comprehensive model health report
    fun runModelHealth(context: Context): ModelHealthReport {
        val lines = mutableListOf<String>()
        lines += "═══════════════════════════════════════════════════"
        lines += "  MODEL HEALTH REPORT"
        lines += "═══════════════════════════════════════════════════"

        val det = inspectModel(context, "die_classifier.tflite")  // Single model for both
        lines += "[Detection] Input: shape=${det.inputShape.contentToString()}, type=${det.inputType}"
        det.inputQuant?.let { q -> lines += "[Detection] Input quant: scale=${q.scale}, zeroPoint=${q.zeroPoint}" }
        for (i in det.outputs.indices) {
            val t = det.outputs[i]
            lines += "[Detection] Output[$i]: shape=${t.shape.contentToString()}, type=${t.type}"
        }
        // Try to interpret detection outputs
        val boxesIdx = det.outputs.indexOfFirst { it.shape.size == 3 && it.shape.lastOrNull() == 4 }
        val scoresIdx = det.outputs.indexOfFirst { it.shape.size == 2 }
        val countIdx = det.outputs.indexOfFirst { it.shape.size == 1 }
        if (boxesIdx >= 0) lines += "[Detection] Boxes tensor index: $boxesIdx"
        if (scoresIdx >= 0) lines += "[Detection] Scores tensor index: $scoresIdx"
        if (countIdx >= 0) lines += "[Detection] Count tensor index: $countIdx"

        val cls = inspectModel(context, "die_classifier.tflite")
        lines += "[Classification] Input: shape=${cls.inputShape.contentToString()}, type=${cls.inputType}"
        cls.inputQuant?.let { q -> lines += "[Classification] Input quant: scale=${q.scale}, zeroPoint=${q.zeroPoint}" }
        if (cls.outputs.isNotEmpty()) {
            val t0 = cls.outputs[0]
            lines += "[Classification] Output[0]: shape=${t0.shape.contentToString()}, type=${t0.type}"
            cls.outputs.drop(1).forEachIndexed { i, t ->
                lines += "[Classification] Output[${i + 1}]: shape=${t.shape.contentToString()}, type=${t.type}"
            }
        }
        val numClasses = when {
            cls.outputs.isEmpty() -> -1
            cls.outputs[0].shape.size == 2 -> cls.outputs[0].shape[1]
            cls.outputs[0].shape.size == 1 -> cls.outputs[0].shape[0]
            else -> -1
        }
        lines += "[Classification] Inferred numClasses: $numClasses"

        // Labels and mapping
        ClassMapping.initialize(context, expectedClasses = if (numClasses > 0) numClasses else 6)
        lines += "[Mapping] Final class->face mapping: ${ClassMapping.mapping.joinToString(prefix = "[", postfix = "]")}"
        lines += "[Mapping] Mapping valid: ${ClassMapping.isValid()}"

        lines += "═══════════════════════════════════════════════════"
        // Log to Logcat
        for (ln in lines) Log.d(TAG, ln)
        return ModelHealthReport(lines)
    }

    private fun inspectModel(context: Context, assetName: String): ModelIOInfo {
        val buffer = loadModelFile(context, assetName)
        val interp = Interpreter(buffer)
        try {
            val inTensor = interp.getInputTensor(0)
            val inShape = inTensor.shape()
            val inType = inTensor.dataType()
            val inQuant = try {
                val q = inTensor.quantizationParams()
                QuantParams(q.scale, q.zeroPoint)
            } catch (_: Throwable) { null }
            val outs = (0 until interp.outputTensorCount).map { i ->
                val t = interp.getOutputTensor(i)
                val shape = t.shape()
                val type = t.dataType()
                val q = try {
                    val qq = t.quantizationParams()
                    QuantParams(qq.scale, qq.zeroPoint)
                } catch (_: Throwable) { null }
                TensorInfo(shape, type, q)
            }
            return ModelIOInfo(inShape, inType, inQuant, outs)
        } finally {
            try { interp.close() } catch (_: Exception) {}
        }
    }

    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        val fd = context.assets.openFd(filename)
        FileInputStream(fd.fileDescriptor).use { input ->
            val channel = input.channel
            return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }

    data class DiagnosticResult(
        val isAvailable: Boolean,
        val messages: List<String>
    )

    data class ModelHealthReport(val lines: List<String>)

    data class ModelIOInfo(
        val inputShape: IntArray,
        val inputType: DataType,
        val inputQuant: QuantParams?,
        val outputs: List<TensorInfo>
    )

    data class TensorInfo(
        val shape: IntArray,
        val type: DataType,
        val quant: QuantParams?
    )

    data class QuantParams(val scale: Float, val zeroPoint: Int)
}
