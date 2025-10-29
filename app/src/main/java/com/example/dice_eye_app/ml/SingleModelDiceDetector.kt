package com.example.dice_eye_app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.example.dice_eye_app.util.DebugBitmap
import com.example.dice_eye_app.util.DebugConfig

/**
 * Single-model YOLO-based dice detector
 * Uses one YOLO11s model for both detection AND classification
 * No separate classification step needed!
 */
class SingleModelDiceDetector(private val context: Context) {
    private val detector = DiceDetector(context)

    data class DiceResult(
        val boundingBox: RectF,
        val faceValue: Int,  // 1-6
        val confidence: Float
    )

    /**
     * Detect and classify all dice in the image
     * @return List of detected dice with their face values
     */
    fun detectAndClassify(bitmap: Bitmap): List<DiceResult> {
        Log.d(TAG, "=== SINGLE-MODEL DETECTION START ===")
        Log.d(TAG, "Input image size: ${bitmap.width}×${bitmap.height}")


        // Run YOLO detection - it returns both bounding boxes AND class IDs
        val detections = detector.detect(bitmap)

        Log.d(TAG, "YOLO detected ${detections.size} dice")

        if (detections.isEmpty()) {
            Log.w(TAG, "No dice detected")
            return emptyList()
        }

        // Convert detections to results
        val results = detections.mapNotNull { detection ->
            // YOLO classId is 0-5, map to dice faces 1-6
            val faceValue = detection.classId + 1

            if (faceValue < 1 || faceValue > 6) {
                Log.w(TAG, "Invalid face value $faceValue from classId ${detection.classId}")
                null
            } else {
                Log.d(TAG, "Die: face=$faceValue, conf=${"%.3f".format(detection.confidence)}, " +
                        "box=[${detection.boundingBox.left.toInt()},${detection.boundingBox.top.toInt()}," +
                        "${detection.boundingBox.right.toInt()},${detection.boundingBox.bottom.toInt()}]")

                DiceResult(
                    boundingBox = detection.boundingBox,
                    faceValue = faceValue,
                    confidence = detection.confidence
                )
            }
        }

        // Save debug overlay
        if (DebugConfig.SAVE_DETECTIONS_OVERLAY) {
            val labels = results.map {
                it.boundingBox to "Face ${it.faceValue} (${"%.2f".format(it.confidence)})"
            }
            val overlay = DebugBitmap.drawOverlayLabeled(bitmap, labels)
            DebugBitmap.saveBitmap(context, overlay, "final_detections")
            overlay.recycle()
        }

        Log.d(TAG, "=== SINGLE-MODEL DETECTION COMPLETE ===")
        Log.d(TAG, "Detected ${results.size} dice: ${results.map { it.faceValue }}")

        return results
    }

    fun close() {
        detector.close()
    }

    companion object {
        private const val TAG = "SingleModelDetector"
    }
}

