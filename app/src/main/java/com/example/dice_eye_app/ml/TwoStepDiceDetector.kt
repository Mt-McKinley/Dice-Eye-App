package com.example.dice_eye_app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import com.example.dice_eye_app.util.DebugBitmap
import com.example.dice_eye_app.util.DebugConfig

/**
 * Two-step dice detection and classification
 * Step 1: Detect dice locations using die_detection.tflite
 * Step 2: Classify each detected die (1-6) using die_classification.tflite
 */
class TwoStepDiceDetector(private val context: Context) {

    private val detector = DiceDetector(context)
    private val classifier = DiceClassifier(context)

    // FINAL, CORRECTED MAPPING: Based on the alphabetical order of the training data folders.
    // The model's labels are ordered alphabetically: five, four, one, six, three, two.
    // This remaps the model's output index to the correct face value index (0-5 for faces 1-6).
    private val classRemap: IntArray = intArrayOf(4, 3, 0, 5, 2, 1)

    // Explanation of the correct mapping:
    // Model Index -> Folder -> Face Value -> Final App Index (0-5)
    // -----------------------------------------------------------
    // 0 -> five   -> 5 -> 4
    // 1 -> four   -> 4 -> 3
    // 2 -> one    -> 1 -> 0
    // 3 -> six    -> 6 -> 5
    // 4 -> three  -> 3 -> 2
    // 5 -> two    -> 2 -> 1

    // Filtering thresholds have been relaxed to avoid incorrectly discarding valid dice.
    private val minClassificationConfidence = 0.25f
    private val minCombinedConfidence = 0.05f
    private val minTop1Margin = 0.02f
    private val finalNmsIou = 0.40f
    private val maxFinalDetections = 10

    /**
     * Detect and classify all dice in the image
     * @param bitmap The input image
     * @return List of detections with classified die faces
     */
    fun detectAndClassify(bitmap: Bitmap): List<ClassifiedDetection> {
        // Step 1: Detect dice locations
        val detections = detector.detectDice(bitmap)
        Log.d(TAG, "Step 1: Found ${detections.size} dice")

        if (detections.isEmpty()) return emptyList()

        val classified = mutableListOf<ClassifiedDetection>()

        // Step 2: Classify each detected die
        detections.forEachIndexed { index, detection ->
            try {
                // Crop the die from the original image
                val croppedDie = cropBitmapSquareWithPadding(bitmap, detection.boundingBox)

                if (croppedDie != null) {
                    if (DebugConfig.ENABLED && DebugConfig.SAVE_CROPS) {
                        DebugBitmap.saveBitmap(context, croppedDie, "crop_${index}")
                    }

                    // Classify the cropped die
                    val classification = classifyWithRotations(croppedDie)

                    if (classification != null) {
                        val margin = top1Margin(classification.allProbabilities)

                        // Enhanced debugging for class remapping
                        if (DebugConfig.ENABLED && DebugConfig.LOG_CLASSIFIER_DETAILS) {
                            val rawProbs = classification.allProbabilities
                            Log.e(TAG, "===== CLASSIFICATION DEBUG for die ${index + 1} =====")
                            Log.e(TAG, "Raw probabilities: ${rawProbs.joinToString(", ") { "%.4f".format(it) }}")
                            Log.e(TAG, "Raw class ID: ${classification.classId}")
                            Log.e(TAG, "Top-1 margin: ${"%.4f".format(margin)}")

                            // Log the remapping table for clarity
                            Log.e(TAG, "Class remapping table:")
                            for (i in 0..5) {
                                Log.e(TAG, "  Model class $i -> Face ${classRemap[i] + 1}")
                            }

                            // Show the actual remapping that will occur
                            val mappedId = classification.classId.let { raw ->
                                if (raw in 0..5) classRemap[raw] else raw
                            }
                            Log.e(TAG, "After remapping: ${classification.classId} -> $mappedId (Face ${mappedId + 1})")
                            Log.e(TAG, "===========================================")
                        }

                        if (margin < minTop1Margin) {
                            Log.d(TAG, "Discarding ambiguous classification (margin=${"%.3f".format(margin)}) for die ${index + 1}")
                        } else {
                            val mappedId = classification.classId.let { raw ->
                                if (raw in 0..5) classRemap[raw] else raw
                            }
                            val combined = detection.confidence * classification.confidence
                            val cd = ClassifiedDetection(
                                boundingBox = detection.boundingBox,
                                detectionConfidence = detection.confidence,
                                classId = mappedId,
                                className = "Face ${mappedId + 1}",
                                classificationConfidence = classification.confidence,
                                combinedConfidence = combined
                            )
                            Log.d(TAG, "Die ${index + 1}: RAW=${classification.classId + 1} -> MAPPED=${mappedId + 1}, Conf=${"%.3f".format(classification.confidence)}, Margin=${"%.3f".format(margin)}, Combined=${"%.3f".format(combined)})")
                            classified.add(cd)
                        }
                    } else {
                        Log.w(TAG, "Die ${index + 1}: Classification failed")
                    }

                    // Clean up cropped bitmap
                    croppedDie.recycle()
                } else {
                    Log.w(TAG, "Die ${index + 1}: Failed to crop")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing die ${index + 1}", e)
            }
        }

        // Stage 3: Filter low-confidence classifications first
        val filtered = classified.filter {
            val keep = it.classificationConfidence >= minClassificationConfidence && it.combinedConfidence >= minCombinedConfidence
            if (!keep) {
                Log.d(TAG, "Filtering out: class=${it.className}, det=${"%.3f".format(it.detectionConfidence)}, cls=${"%.3f".format(it.classificationConfidence)}, comb=${"%.3f".format(it.combinedConfidence)})")
            }
            keep
        }

        if (filtered.isEmpty()) {
            Log.d(TAG, "No detections passed classification thresholds")
            return emptyList()
        }

        // Stage 4: Apply a final NMS on classified boxes to remove overlaps
        val afterNms = applyNmsClassified(filtered, finalNmsIou)

        // Stage 5: Cap the final number of dice to a sensible maximum
        val finalResults = afterNms
            .sortedByDescending { it.combinedConfidence }
            .take(maxFinalDetections)

        if (DebugConfig.ENABLED && DebugConfig.SAVE_FINAL_OVERLAY && finalResults.isNotEmpty()) {
            val labels = finalResults.map { det ->
                val label = "${det.classId + 1} (${"%.2f".format(det.classificationConfidence)})"
                det.boundingBox to label
            }
            val overlay = DebugBitmap.drawOverlayLabeled(bitmap, labels)
            DebugBitmap.saveBitmap(context, overlay, "final_overlay")
            overlay.recycle()
        }

        Log.d(TAG, "Step 2: Classified ${finalResults.size} out of ${detections.size} dice after filtering/NMS")
        return finalResults
    }

    private fun cropBitmapSquareWithPadding(bitmap: Bitmap, rect: RectF): Bitmap? {
        return try {
            val left = rect.left
            val top = rect.top
            val right = rect.right
            val bottom = rect.bottom

            val cx = (left + right) / 2f
            val cy = (top + bottom) / 2f
            val w = (right - left).coerceAtLeast(1f)
            val h = (bottom - top).coerceAtLeast(1f)
            val size = maxOf(w, h)
            val padded = size * 1.15f // 15% padding

            var newLeft = (cx - padded / 2f)
            var newTop = (cy - padded / 2f)
            var newRight = (cx + padded / 2f)
            var newBottom = (cy + padded / 2f)

            // Clamp to image bounds
            if (newLeft < 0) {
                val shift = -newLeft
                newLeft = 0f
                newRight = (newRight + shift).coerceAtMost(bitmap.width.toFloat())
            }
            if (newTop < 0) {
                val shift = -newTop
                newTop = 0f
                newBottom = (newBottom + shift).coerceAtMost(bitmap.height.toFloat())
            }
            if (newRight > bitmap.width) {
                val shift = newRight - bitmap.width
                newRight = bitmap.width.toFloat()
                newLeft = (newLeft - shift).coerceAtLeast(0f)
            }
            if (newBottom > bitmap.height) {
                val shift = newBottom - bitmap.height
                newBottom = bitmap.height.toFloat()
                newTop = (newTop - shift).coerceAtLeast(0f)
            }

            val iLeft = newLeft.toInt().coerceIn(0, bitmap.width - 1)
            val iTop = newTop.toInt().coerceIn(0, bitmap.height - 1)
            val iRight = newRight.toInt().coerceIn(iLeft + 1, bitmap.width)
            val iBottom = newBottom.toInt().coerceIn(iTop + 1, bitmap.height)

            val width = iRight - iLeft
            val height = iBottom - iTop
            val sqSize = minOf(width, height)

            // Center the square within the padded rectangle
            val sqLeft = (iLeft + iRight - sqSize) / 2
            val sqTop = (iTop + iBottom - sqSize) / 2

            if (sqSize > 0) {
                Bitmap.createBitmap(bitmap, sqLeft, sqTop, sqSize, sqSize)
            } else {
                Log.w(TAG, "Invalid square crop dimensions: size=$sqSize")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error square-cropping bitmap", e)
            null
        }
    }

    private fun classifyWithRotations(cropped: Bitmap): DiceClassifier.ClassificationResult? {
        val angles = intArrayOf(0, 90, 180, 270)
        val allProbs = mutableListOf<FloatArray>()
        val bitmaps = mutableListOf<Bitmap>()

        try {
            for (deg in angles) {
                val rotated = if (deg == 0) cropped else rotateBitmap(cropped, deg.toFloat())
                bitmaps.add(rotated)
                if (DebugConfig.ENABLED && DebugConfig.SAVE_ROTATED_VARIANTS) {
                    DebugBitmap.saveBitmap(context, rotated, "crop_rot_${deg}")
                }
                classifier.classify(rotated)?.let { allProbs.add(it.allProbabilities) }
            }

            if (allProbs.isEmpty()) return null

            // Average the probabilities across all rotations
            val avgProbs = FloatArray(allProbs[0].size)
            for (probs in allProbs) {
                for (i in avgProbs.indices) {
                    avgProbs[i] += probs[i]
                }
            }
            for (i in avgProbs.indices) {
                avgProbs[i] /= allProbs.size
            }

            // Find the best class from the averaged probabilities
            var maxProb = -1f
            var predictedClass = -1
            avgProbs.forEachIndexed { index, p ->
                if (p > maxProb) {
                    maxProb = p
                    predictedClass = index
                }
            }

            Log.d(TAG, "Averaged probabilities: ${avgProbs.joinToString { "%.3f".format(it) }}")
            return DiceClassifier.ClassificationResult(
                classId = predictedClass,
                className = "Face ${predictedClass + 1}",
                confidence = maxProb,
                allProbabilities = avgProbs
            )

        } finally {
            // Recycle temp rotated bitmaps
            for (i in 1 until bitmaps.size) {
                try { bitmaps[i].recycle() } catch (_: Exception) {}
            }
        }
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun top1Margin(probs: FloatArray): Float {
        if (probs.isEmpty()) return 0f
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

    private fun applyNmsClassified(detections: List<ClassifiedDetection>, iouThreshold: Float): List<ClassifiedDetection> {
        if (detections.isEmpty()) return emptyList()
        val sorted = detections.sortedByDescending { it.combinedConfidence }
        val picked = mutableListOf<ClassifiedDetection>()
        val used = BooleanArray(sorted.size)
        for (i in sorted.indices) {
            if (used[i]) continue
            val a = sorted[i]
            picked.add(a)
            for (j in i + 1 until sorted.size) {
                if (used[j]) continue
                val b = sorted[j]
                val iou = iou(a.boundingBox, b.boundingBox)
                if (iou > iouThreshold) {
                    used[j] = true
                    Log.d(TAG, "Final NMS: Suppressing overlap (IoU=${"%.3f".format(iou)}) between ${i} and ${j}")
                }
            }
        }
        return picked
    }

    private fun iou(a: RectF, b: RectF): Float {
        val inter = RectF()
        if (!inter.setIntersect(a, b)) return 0f
        val interArea = inter.width() * inter.height()
        val unionArea = a.width() * a.height() + b.width() * b.height() - interArea
        if (unionArea <= 0f) return 0f
        return interArea / unionArea
    }

    fun close() {
        detector.close()
        classifier.close()
        Log.d(TAG, "TwoStepDiceDetector closed")
    }

    data class ClassifiedDetection(
        val boundingBox: RectF,
        val detectionConfidence: Float,
        val classId: Int,
        val className: String,
        val classificationConfidence: Float,
        val combinedConfidence: Float
    )

    companion object {
        private const val TAG = "TwoStepDiceDetector"
    }
}
