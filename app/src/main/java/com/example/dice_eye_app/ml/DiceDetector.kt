package com.example.dice_eye_app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import com.example.dice_eye_app.util.DebugBitmap
import com.example.dice_eye_app.util.DebugConfig
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import java.nio.FloatBuffer

/**
 * Detects the location of dice in a given bitmap image using a YOLO-based TFLite model.
 *
 * This class is responsible for Step 1 of the two-step detection process:
 * 1. Pre-processes the input bitmap (resizing, letterboxing).
 * 2. Runs inference using the YOLO11s model (`die_classifier.tflite`).
 * 3. Post-processes the model's output to generate a list of bounding boxes for potential dice.
 *
 * @param context The application context, used for asset loading and diagnostics.
 */
class DiceDetector(private val context: Context) {

    // Configuration for the detection model and post-processing.
    // Using the same YOLO11s model as the classifier (single-stage approach)
    private object Config {
        const val MODEL_FILENAME = "die_classifier.tflite"  // Same model as classifier
        const val NUM_THREADS = 4

        // Detection thresholds, tuned for a balance of precision and recall.
        const val CONFIDENCE_THRESHOLD = 0.20f // Optimized threshold (was 0.15)
        const val IOU_THRESHOLD = 0.45f        // Threshold for non-maximum suppression (NMS).

        // Sanity filters for bounding boxes to eliminate obvious false positives.
        // Values are fractions of the original image dimensions.
        const val MIN_AREA_FRACTION = 0.002f   // Filter out tiny specks.
        const val MAX_AREA_FRACTION = 0.20f    // Filter out overly large boxes.
        const val MIN_ASPECT_RATIO = 0.6f      // Lower bound for width/height ratio.
        const val MAX_ASPECT_RATIO = 1.6f      // Upper bound for width/height ratio.

        // The maximum number of dice to return after all filtering and NMS.
        const val MAX_FINAL_DETECTIONS = 12
        
        // Hardware acceleration
        const val USE_GPU = false               // Temporarily disabled for debugging
        const val USE_NNAPI = false            // Keep NNAPI disabled for consistency
    }

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var modelLoaded = false

    // Model properties, auto-detected from the TFLite model file upon loading.
    private var modelInputWidth = 0
    private var modelInputHeight = 0
    private var modelInputChannels = 0
    private var modelInputType: DataType = DataType.UINT8
    // Quantization parameters for INT8/UINT8 inputs when available
    private var inputScale: Float = 1.0f
    private var inputZeroPoint: Int = 0

    // Pre-processing values, stored to correctly transform bounding boxes back to original image coordinates.
    private var lastScaleFactor: Float = 1f
    private var lastPadX: Float = 0f
    private var lastPadY: Float = 0f

    init {
        Log.d(TAG, "DiceDetector initializing...")
        loadModel()
        Log.d(TAG, "DiceDetector initialization complete")
    }

    /**
     * Runs inference on the provided bitmap to detect dice.
     *
     * @param bitmap The input image containing dice.
     * @return A list of [Detection] objects, each representing a potential die.
     */
    fun detect(bitmap: Bitmap): List<Detection> {
        if (!modelLoaded || interpreter == null) {
            Log.e(TAG, "Model is not loaded. Cannot run inference.")
            return emptyList()
        }

        try {
            val interp = interpreter!!
            // Step 1: Pre-process the image to match the model's input requirements.
            val inputBuffer = preprocessImage(bitmap)

            // Step 2: Prepare model outputs dynamically and run inference.
            var outputs: MutableMap<Int, Any> = allocateOutputs(interp)
            try {
                interp.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
            } catch (iae: IllegalArgumentException) {
                // Handle shape mismatches by retrying with flat arrays.
                Log.w(TAG, "TFLite output shape mismatch: ${iae.message}. Retrying with flat output buffers...")
                outputs = allocateFlatOutputs(interp)
                interp.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
            }

            // Step 3: Post-process the raw model output to get meaningful detections.
            return postprocess(outputs, bitmap, interp)

        } catch (e: Exception) {
            Log.e(TAG, "An error occurred during dice detection.", e)
            return emptyList()
        }
    }

    /**
     * Loads the TFLite model from assets and initializes the interpreter.
     */
    private fun loadModel() {
        Log.d(TAG, "loadModel() starting...")
        try {
            Log.d(TAG, "Loading model file: ${Config.MODEL_FILENAME}")
            val modelBuffer = loadModelFile(context, Config.MODEL_FILENAME)
            Log.d(TAG, "Model file loaded successfully, creating interpreter options...")
            val options = Interpreter.Options().apply {
                numThreads = Config.NUM_THREADS
                Log.d(TAG, "Set numThreads to ${Config.NUM_THREADS}")
                
                // Try to use GPU acceleration if available and enabled
                if (Config.USE_GPU) {
                    Log.d(TAG, "GPU enabled in config, checking compatibility...")
                    try {
                        val compatList = CompatibilityList()
                        if (compatList.isDelegateSupportedOnThisDevice) {
                            try {
                                gpuDelegate = GpuDelegate()
                                addDelegate(gpuDelegate)
                                Log.i(TAG, "GPU delegate enabled for acceleration")
                            } catch (e: Exception) {
                                Log.w(TAG, "GPU delegate creation failed, falling back to CPU", e)
                                gpuDelegate = null
                            }
                        } else {
                            Log.i(TAG, "GPU delegate not supported on this device")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "GPU compatibility check failed, using CPU", e)
                    }
                } else {
                    Log.d(TAG, "GPU disabled in config")
                }
                
                // NNAPI setting
                setUseNNAPI(Config.USE_NNAPI)
                Log.d(TAG, "Set NNAPI to ${Config.USE_NNAPI}")
            }
            Log.d(TAG, "Creating interpreter...")
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "Interpreter created successfully")

            // Inspect the loaded model to determine its input/output shapes and types.
            inspectModel()
            modelLoaded = true
            Log.i(TAG, "Dice detection model loaded successfully.")

        } catch (e: Exception) {
            modelLoaded = false
            Log.e(TAG, "Failed to load dice detection model.", e)
            throw e  // Re-throw to see the crash
        }
    }

    /**
     * Reads model input and output tensor details and stores them.
     */
    private fun inspectModel() {
        val interp = interpreter ?: return
        Log.d(TAG, "--- Model Inspection ---")

        // Get input tensor details.
        val inputTensor = interp.getInputTensor(0)
        val inputShape = inputTensor.shape()
        modelInputType = inputTensor.dataType()
        Log.d(TAG, "Input: shape=${inputShape.contentToString()}, type=$modelInputType")
        try {
            val q = inputTensor.quantizationParams()
            inputScale = q.scale
            inputZeroPoint = q.zeroPoint
            Log.d(TAG, "Input quantization: scale=${inputScale}, zeroPoint=${inputZeroPoint}")
        } catch (_: Throwable) { /* quantization may be unavailable */ }

        // Assuming NHWC format [Batch, Height, Width, Channels].
        if (inputShape.size == 4) {
            modelInputHeight = inputShape[1]
            modelInputWidth = inputShape[2]
            modelInputChannels = inputShape[3]
            Log.d(TAG, "Auto-detected input size: ${modelInputWidth}x${modelInputHeight}x${modelInputChannels}")
        }

        // Log output tensor details for debugging.
        for (i in 0 until interp.outputTensorCount) {
            val outputTensor = interp.getOutputTensor(i)
            Log.d(TAG, "Output[$i]: name='${outputTensor.name()}', shape=${outputTensor.shape().contentToString()}, type=${outputTensor.dataType()}")
        }
        Log.d(TAG, "----------------------")
    }

    /**
     * Prepares the image for inference: resizes, pads to a square, and converts to a ByteBuffer.
     */
    private fun preprocessImage(bitmap: Bitmap): Any {
        // Calculate scaling factor and padding to create a letterboxed image.
        val scale = minOf(modelInputWidth / bitmap.width.toFloat(), modelInputHeight / bitmap.height.toFloat())
        val newW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val padX = (modelInputWidth - newW) / 2f
        val padY = (modelInputHeight - newH) / 2f

        // Store these values to reverse the transformation during post-processing.
        lastScaleFactor = scale
        lastPadX = padX
        lastPadY = padY

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        val letterboxedBitmap = Bitmap.createBitmap(modelInputWidth, modelInputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(letterboxedBitmap)
        canvas.drawColor(Color.rgb(114, 114, 114)) // Standard YOLO letterbox color.
        canvas.drawBitmap(resizedBitmap, padX, padY, null)

        if (DebugConfig.SAVE_LETTERBOXED) {
            DebugBitmap.saveBitmap(context, letterboxedBitmap, "detector_input")
        }

        val pixels = IntArray(modelInputWidth * modelInputHeight)
        letterboxedBitmap.getPixels(pixels, 0, modelInputWidth, 0, 0, modelInputWidth, modelInputHeight)

        return when (modelInputType) {
            DataType.FLOAT32 -> {
                val floatBuffer = ByteBuffer.allocateDirect(4 * modelInputWidth * modelInputHeight * modelInputChannels)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                // Normalize to 0..1 floats
                for (pixel in pixels) {
                    val r = ((pixel shr 16) and 0xFF) / 255f
                    val g = ((pixel shr 8) and 0xFF) / 255f
                    val b = (pixel and 0xFF) / 255f
                    floatBuffer.put(r); floatBuffer.put(g); floatBuffer.put(b)
                }
                floatBuffer.rewind()
                floatBuffer
            }
            DataType.INT8 -> {
                // Signed int8: quantize normalized [0,1] via scale/zeroPoint
                val byteBuffer = ByteBuffer.allocateDirect(modelInputWidth * modelInputHeight * modelInputChannels)
                byteBuffer.order(ByteOrder.nativeOrder())
                fun q(v: Float): Byte {
                    val quant = Math.round(v / inputScale + inputZeroPoint).coerceIn(-128, 127)
                    return quant.toByte()
                }
                for (pixel in pixels) {
                    val r = ((pixel shr 16) and 0xFF) / 255f
                    val g = ((pixel shr 8) and 0xFF) / 255f
                    val b = (pixel and 0xFF) / 255f
                    byteBuffer.put(q(r)); byteBuffer.put(q(g)); byteBuffer.put(q(b))
                }
                byteBuffer.rewind()
                byteBuffer
            }
            DataType.UINT8 -> {
                // Unsigned bytes 0..255
                val buffer = ByteBuffer.allocateDirect(modelInputWidth * modelInputHeight * modelInputChannels)
                buffer.order(ByteOrder.nativeOrder())
                for (pixelValue in pixels) {
                    buffer.put(((pixelValue shr 16) and 0xFF).toByte()) // R
                    buffer.put(((pixelValue shr 8) and 0xFF).toByte())  // G
                    buffer.put((pixelValue and 0xFF).toByte())          // B
                }
                buffer.rewind()
                buffer
            }
            else -> {
                // Fallback: float32
                val floatBuffer = ByteBuffer.allocateDirect(4 * modelInputWidth * modelInputHeight * modelInputChannels)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                for (pixel in pixels) {
                    val r = ((pixel shr 16) and 0xFF) / 255f
                    val g = ((pixel shr 8) and 0xFF) / 255f
                    val b = (pixel and 0xFF) / 255f
                    floatBuffer.put(r); floatBuffer.put(g); floatBuffer.put(b)
                }
                floatBuffer.rewind()
                floatBuffer
            }
        }
    }

    /**
     * Build an outputs map matching the actual output tensor shapes of the model.
     * Supports common detection heads with separate boxes/scores/classes/num.
     */
    private fun allocateOutputs(interp: Interpreter): MutableMap<Int, Any> {
        val map = mutableMapOf<Int, Any>()
        for (i in 0 until interp.outputTensorCount) {
            val t = interp.getOutputTensor(i)
            val shape = t.shape()
            // Allocate containers according to rank
            when (shape.size) {
                3 -> {
                    // e.g., [1, N, 4] for boxes
                    val n = shape[1]
                    val m = shape[2]
                    map[i] = Array(1) { Array(n) { FloatArray(m) } }
                }
                2 -> {
                    // e.g., [1, N] for scores or classes
                    val n = shape[1]
                    map[i] = Array(1) { FloatArray(n) }
                }
                1 -> {
                    // e.g., [1] for numDetections
                    map[i] = FloatArray(1)
                }
                else -> {
                    // Fallback: allocate a flat float array of total size (rare)
                    val total = shape.fold(1) { acc, v -> acc * v }
                    map[i] = FloatArray(total)
                    Log.w(TAG, "Unknown output rank ${shape.size} for tensor $i, allocating flat array of $total floats")
                }
            }
        }
        return map
    }

    // Allocate flat buffers for every output tensor. Useful as a fallback when shapes are tricky.
    private fun allocateFlatOutputs(interp: Interpreter): MutableMap<Int, Any> {
        val map = mutableMapOf<Int, Any>()
        for (i in 0 until interp.outputTensorCount) {
            val t = interp.getOutputTensor(i)
            val shape = t.shape()
            val total = shape.fold(1) { acc, v -> acc * v }
            map[i] = FloatArray(total)
        }
        return map
    }

    /**
     * Processes the raw outputs into a list of Detection objects. This method is tolerant
     * to different output tensor orders; it will identify boxes/scores/classes by shape.
     *
     * Supports both:
     * - YOLO11 format: Single output tensor [1, num_predictions, 84+] where each prediction is [x, y, w, h, class0_conf, class1_conf, ...]
     * - Legacy format: Separate tensors for boxes, scores, classes
     */
    private fun postprocess(outputs: Map<Int, Any>, originalBitmap: Bitmap, interp: Interpreter): List<Detection> {
        Log.d(TAG, "Postprocessing: parsing ${outputs.size} output tensors")

        // Check if this is YOLO11 single-output format
        if (outputs.size == 1 && outputs.containsKey(0)) {
            val outputTensor = interp.getOutputTensor(0)
            val shape = outputTensor.shape()
            Log.d(TAG, "Single output tensor detected: shape=${shape.contentToString()}")

            // YOLO11 can output in two formats:
            // 1. [1, num_predictions, features] - standard format
            // 2. [1, num_classes, num_predictions] - transposed format

            if (shape.size == 3) {
                // Detect which format based on dimensions
                val dim1 = shape[1]
                val dim2 = shape[2]

                // If dim2 is much larger than dim1, it's likely transposed format
                // e.g., [1, 10, 8400] = [batch, classes, predictions]
                val isTransposed = dim2 > dim1 && dim2 > 100

                if (isTransposed) {
                    Log.d(TAG, "Detected transposed YOLO format: [1, $dim1 classes, $dim2 predictions]")
                    return postprocessYOLO11Transposed(outputs[0]!!, shape, originalBitmap)
                } else if (dim2 >= 5) {
                    Log.d(TAG, "Detected standard YOLO format: [1, $dim1 predictions, $dim2 features]")
                    return postprocessYOLO11Standard(outputs[0]!!, shape, originalBitmap)
                }
            }
        }

        // Fall back to legacy multi-tensor format
        return postprocessLegacyFormat(outputs, originalBitmap, interp)
    }

    /**
     * Parse YOLO11 transposed format: [1, num_outputs, num_predictions]
     * YOLO11 with custom classes outputs [1, 4+num_classes, 8400] where:
     * - First 4 rows: bounding box coordinates (x, y, w, h)
     * - Remaining rows: class confidence scores
     * Format: [batch, outputs, predictions] where outputs = bbox(4) + classes(6) = 10
     */
    private fun postprocessYOLO11Transposed(output: Any, shape: IntArray, originalBitmap: Bitmap): List<Detection> {
        val numOutputs = shape[1]  // Should be 10 (4 bbox + 6 classes)
        val numPredictions = shape[2]  // Should be 8400
        Log.d(TAG, "YOLO11 transposed: $numOutputs outputs (4 bbox + ${numOutputs-4} classes), $numPredictions predictions")

        // Extract data: [1, 10, 8400]
        val data = when (output) {
            is Array<*> -> {
                val arr = output as Array<Array<FloatArray>>
                arr[0]  // [10, 8400]
            }
            else -> {
                Log.e(TAG, "Unsupported output type for transposed format: ${output.javaClass}")
                return emptyList()
            }
        }

        // YOLO11 transposed format structure:
        // data[0] = x_center for all 8400 predictions
        // data[1] = y_center for all 8400 predictions
        // data[2] = width for all 8400 predictions
        // data[3] = height for all 8400 predictions
        // data[4..9] = class confidences (6 dice faces)

        val detections = mutableListOf<Detection>()
        val originalWidth = originalBitmap.width
        val originalHeight = originalBitmap.height
        val imageArea = (originalWidth * originalHeight).toFloat()

        val numClasses = numOutputs - 4  // 10 - 4 = 6 classes

        // Process each of the 8400 predictions
        for (predIdx in 0 until numPredictions) {
            // Extract bounding box coordinates
            val centerX = data[0][predIdx]
            val centerY = data[1][predIdx]
            val width = data[2][predIdx]
            val height = data[3][predIdx]

            // Extract class confidence scores (rows 4-9)
            var maxScore = 0f
            var maxClassIdx = -1
            for (classIdx in 0 until numClasses) {
                val score = data[4 + classIdx][predIdx]
                if (score > maxScore) {
                    maxScore = score
                    maxClassIdx = classIdx
                }
            }

            // Apply confidence threshold
            if (maxScore < Config.CONFIDENCE_THRESHOLD) continue

            // Convert from center format to corner format (normalized 0-1)
            val x1 = (centerX - width / 2f) / modelInputWidth
            val y1 = (centerY - height / 2f) / modelInputHeight
            val x2 = (centerX + width / 2f) / modelInputWidth
            val y2 = (centerY + height / 2f) / modelInputHeight

            // Convert normalized, letterboxed coordinates back to original image coordinates
            val rect = denormalizeAndUnpad(x1, y1, x2, y2, originalWidth, originalHeight)

            // Apply sanity filters
            if (!isValid(rect, imageArea)) {
                if (DebugConfig.LOG_ALL_DETECTIONS) {
                    Log.d(TAG, "Detection $predIdx discarded by sanity filters.")
                }
                continue
            }

            // Include the detected class ID (0-5 for dice faces 1-6)
            detections.add(Detection(boundingBox = rect, confidence = maxScore, classId = maxClassIdx))
        }

        Log.d(TAG, "Processing ${detections.size} raw detections from model output")

        // Log sample detections with class info
        for (i in 0 until minOf(3, detections.size)) {
            val d = detections[i]
            Log.d(TAG, String.format(Locale.US, "Det[%d]: conf=%.3f classId=%d (face %d) box=[%.1f,%.1f,%.1f,%.1f]",
                i, d.confidence, d.classId, d.classId + 1, d.boundingBox.left, d.boundingBox.top, d.boundingBox.right, d.boundingBox.bottom))
        }

        // Apply NMS
        val finalDetections = applyNMS(detections)

        if (DebugConfig.SAVE_DETECTIONS_OVERLAY) {
            val labels = finalDetections.map { it.boundingBox to "Die (${String.format(Locale.US, "%.2f", it.confidence)})" }
            val overlay = DebugBitmap.drawOverlayLabeled(originalBitmap, labels)
            DebugBitmap.saveBitmap(context, overlay, "detector_final_detections")
            overlay.recycle()
        }

        return finalDetections
    }

    /**
     * Parse YOLO11 standard format: [1, num_predictions, features]
     * where features = [x_center, y_center, width, height, class0_conf, class1_conf, ...]
     */
    private fun postprocessYOLO11Standard(output: Any, shape: IntArray, originalBitmap: Bitmap): List<Detection> {
        val numPredictions = shape[1]
        val numFeatures = shape[2]
        Log.d(TAG, "YOLO11 standard format: $numPredictions predictions, $numFeatures features each")

        // Extract data from output
        val data = when (output) {
            is Array<*> -> {
                val arr = output as Array<Array<FloatArray>>
                arr[0]
            }
            is FloatArray -> {
                // Reshape flat array to [num_predictions, num_features]
                Array(numPredictions) { i ->
                    FloatArray(numFeatures) { j ->
                        output[i * numFeatures + j]
                    }
                }
            }
            else -> {
                Log.e(TAG, "Unexpected output type: ${output.javaClass}")
                return emptyList()
            }
        }

        val detections = mutableListOf<Detection>()
        val originalWidth = originalBitmap.width
        val originalHeight = originalBitmap.height
        val imageArea = (originalWidth * originalHeight).toFloat()

        // For single-class detection (die), we only have 1 class score
        val numClasses = numFeatures - 4

        for (i in 0 until numPredictions) {
            val prediction = data[i]

            // Extract bbox (center_x, center_y, width, height)
            val centerX = prediction[0]
            val centerY = prediction[1]
            val width = prediction[2]
            val height = prediction[3]

            // Extract confidence score(s) and class - take the max across all classes
            var maxScore = 0f
            var maxClassIdx = -1
            for (j in 4 until numFeatures) {
                val score = prediction[j]
                if (score > maxScore) {
                    maxScore = score
                    maxClassIdx = j - 4  // Class index (0-5 for dice faces)
                }
            }

            if (maxScore < Config.CONFIDENCE_THRESHOLD) continue

            // Convert from center format to corner format
            val x1 = (centerX - width / 2f) / modelInputWidth
            val y1 = (centerY - height / 2f) / modelInputHeight
            val x2 = (centerX + width / 2f) / modelInputWidth
            val y2 = (centerY + height / 2f) / modelInputHeight

            // Convert normalized, letterboxed coordinates back to original image coordinates
            val rect = denormalizeAndUnpad(x1, y1, x2, y2, originalWidth, originalHeight)

            // Apply sanity filters
            if (!isValid(rect, imageArea)) {
                if (DebugConfig.LOG_ALL_DETECTIONS) {
                    Log.d(TAG, "Detection $i discarded by sanity filters.")
                }
                continue
            }

            detections.add(Detection(boundingBox = rect, confidence = maxScore, classId = maxClassIdx))
        }

        Log.d(TAG, "Processing ${detections.size} raw detections from model output")

        // Log sample detections
        for (i in 0 until minOf(3, detections.size)) {
            val d = detections[i]
            Log.d(TAG, String.format(Locale.US, "Det[%d]: conf=%.3f box=[%.1f,%.1f,%.1f,%.1f]",
                i, d.confidence, d.boundingBox.left, d.boundingBox.top, d.boundingBox.right, d.boundingBox.bottom))
        }

        // Apply NMS
        val finalDetections = applyNMS(detections)

        if (DebugConfig.SAVE_DETECTIONS_OVERLAY) {
            val labels = finalDetections.map { it.boundingBox to "Die (${String.format(Locale.US, "%.2f", it.confidence)})" }
            val overlay = DebugBitmap.drawOverlayLabeled(originalBitmap, labels)
            DebugBitmap.saveBitmap(context, overlay, "detector_final_detections")
            overlay.recycle()
        }

        return finalDetections
    }

    /**
     * Parse legacy multi-tensor output format (separate boxes, scores, classes tensors)
     */
    private fun postprocessLegacyFormat(outputs: Map<Int, Any>, originalBitmap: Bitmap, interp: Interpreter): List<Detection> {
        // Identify candidates
        var boxes: Array<Array<FloatArray>>? = null
        val candidates1xN = mutableListOf<FloatArray>()
        var numDetections: Int? = null

        for ((index, o) in outputs) {
            val shape = interp.getOutputTensor(index).shape()

            // Handle flat outputs by reshaping according to tensor shape
            if (o is FloatArray) {
                when (shape.size) {
                    3 -> {
                        val n = shape[1]
                        val m = shape[2]
                        if (m == 4) {
                            val arr = Array(1) { Array(n) { FloatArray(m) } }
                            var k = 0
                            for (i in 0 until 1) {
                                for (j in 0 until n) {
                                    for (p in 0 until m) {
                                        arr[i][j][p] = o[k++]
                                    }
                                }
                            }
                            boxes = arr
                            continue
                        }
                    }
                    2 -> {
                        val n = shape[1]
                        candidates1xN.add(o.copyOfRange(0, n))
                        continue
                    }
                    1 -> {
                        numDetections = o[0].toInt()
                        continue
                    }
                }
            }

            // Boxes: Array(1){ Array(N){ FloatArray(M) } } where M==4
            val arr3 = o as? Array<*>
            if (arr3 != null && arr3.size == 1) {
                val inner = arr3[0] as? Array<*>
                if (inner != null && inner.isNotEmpty()) {
                    val first = inner[0]
                    if (first is FloatArray && first.size == 4) {
                        @Suppress("UNCHECKED_CAST")
                        boxes = arr3 as Array<Array<FloatArray>>
                        continue
                    }
                }
                // Scores/classes: Array(1){ FloatArray(N) }
                val as1xN = arr3 as? Array<FloatArray>
                if (as1xN != null && as1xN.size == 1) {
                    candidates1xN.add(as1xN[0])
                    continue
                }
            }
            // num detections: FloatArray(1)
            val fa = o as? FloatArray
            if (fa != null && fa.size == 1) {
                numDetections = fa[0].toInt()
            }
        }

        if (boxes == null) {
            Log.e(TAG, "Postprocess: No boxes tensor (1xNx4) found in model outputs.")
            return emptyList()
        }

        val boxArr = boxes!![0]
        val n = boxArr.size

        // Choose scores among 1xN candidates by picking the one that looks like 0..1 probabilities
        var scores: FloatArray? = null
        if (candidates1xN.isNotEmpty()) {
            scores = candidates1xN.maxByOrNull { arr ->
                // Heuristic: prefer arrays whose values are within [0,1] and have higher mean
                val mean = arr.average().toFloat()
                val clampScore = arr.count { it in 0f..1f }
                mean + clampScore / (arr.size + 1f)
            }
        }

        // If numDetections missing, assume all boxes are valid candidates (N)
        val count = numDetections?.coerceIn(0, n) ?: n

        val detections = mutableListOf<Detection>()
        val originalWidth = originalBitmap.width
        val originalHeight = originalBitmap.height
        val imageArea = (originalWidth * originalHeight).toFloat()

        for (i in 0 until count) {
            val box = boxArr[i]
            if (box.size < 4) continue
            val y1 = box[0]
            val x1 = box[1]
            val y2 = box[2]
            val x2 = box[3]

            val score = scores?.getOrNull(i) ?: 1.0f
            if (score < Config.CONFIDENCE_THRESHOLD) continue

            // Convert normalized, letterboxed coordinates back to original image coordinates.
            val rect = denormalizeAndUnpad(x1, y1, x2, y2, originalWidth, originalHeight)

            // Apply sanity filters to the bounding box.
            if (!isValid(rect, imageArea)) {
                if (DebugConfig.LOG_ALL_DETECTIONS) {
                    Log.d(TAG, "Detection $i discarded by sanity filters.")
                }
                continue
            }

            detections.add(Detection(boundingBox = rect, confidence = score))
        }
        Log.d(TAG, "${detections.size} detections passed confidence and sanity checks.")

        // Briefly log up to 3 raw detections for diagnostics
        for (i in 0 until minOf(3, detections.size)) {
            val d = detections[i]
            Log.d(TAG, String.format(Locale.US, "Det[%d]: conf=%.3f box=[%.1f,%.1f,%.1f,%.1f]", i, d.confidence, d.boundingBox.left, d.boundingBox.top, d.boundingBox.right, d.boundingBox.bottom))
        }

        // Apply NMS
        val finalDetections = applyNMS(detections)

        if (DebugConfig.SAVE_DETECTIONS_OVERLAY) {
            val labels = finalDetections.map { it.boundingBox to "Die (${String.format(Locale.US, "%.2f", it.confidence)})" }
            val overlay = DebugBitmap.drawOverlayLabeled(originalBitmap, labels)
            DebugBitmap.saveBitmap(context, overlay, "detector_final_detections")
            overlay.recycle()
        }

        return finalDetections
    }

    /**
     * Converts normalized, letterboxed coordinates back to the original image's coordinate space.
     */
    private fun denormalizeAndUnpad(x1: Float, y1: Float, x2: Float, y2: Float, originalWidth: Int, originalHeight: Int): RectF {
        // Denormalize from [0,1] to model input dimensions.
        val modelX1 = x1 * modelInputWidth
        val modelY1 = y1 * modelInputHeight
        val modelX2 = x2 * modelInputWidth
        val modelY2 = y2 * modelInputHeight

        // Account for letterbox padding.
        val unpaddedX1 = modelX1 - lastPadX
        val unpaddedY1 = modelY1 - lastPadY
        val unpaddedX2 = modelX2 - lastPadX
        val unpaddedY2 = modelY2 - lastPadY

        // Rescale back to original image dimensions.
        val originalX1 = (unpaddedX1 / lastScaleFactor).coerceIn(0f, originalWidth.toFloat())
        val originalY1 = (unpaddedY1 / lastScaleFactor).coerceIn(0f, originalHeight.toFloat())
        val originalX2 = (unpaddedX2 / lastScaleFactor).coerceIn(0f, originalWidth.toFloat())
        val originalY2 = (unpaddedY2 / lastScaleFactor).coerceIn(0f, originalHeight.toFloat())

        return RectF(originalX1, originalY1, originalX2, originalY2)
    }

    /**
     * Checks if a bounding box passes basic sanity checks for area and aspect ratio.
     */
    private fun isValid(rect: RectF, imageArea: Float): Boolean {
        val width = rect.width()
        val height = rect.height()
        if (width <= 0 || height <= 0) return false

        val areaFraction = (width * height) / imageArea
        val aspectRatio = width / height

        return areaFraction in Config.MIN_AREA_FRACTION..Config.MAX_AREA_FRACTION &&
                aspectRatio in Config.MIN_ASPECT_RATIO..Config.MAX_ASPECT_RATIO
    }

    /**
     * Applies Non-Maximum Suppression to filter out overlapping bounding boxes.
     */
    private fun applyNMS(detections: List<Detection>): List<Detection> {
        val sortedDetections = detections.sortedByDescending { it.confidence }
        val selectedDetections = mutableListOf<Detection>()
        val used = BooleanArray(sortedDetections.size)

        for (i in sortedDetections.indices) {
            if (used[i]) continue

            selectedDetections.add(sortedDetections[i])
            used[i] = true

            if (selectedDetections.size >= Config.MAX_FINAL_DETECTIONS) break

            for (j in i + 1 until sortedDetections.size) {
                if (used[j]) continue
                val iou = calculateIoU(sortedDetections[i].boundingBox, sortedDetections[j].boundingBox)
                if (iou > Config.IOU_THRESHOLD) {
                    used[j] = true
                }
            }
        }
        Log.d(TAG, "${selectedDetections.size} detections remaining after NMS.")
        return selectedDetections
    }

    /**
     * Calculates the Intersection over Union (IoU) of two bounding boxes.
     */
    private fun calculateIoU(boxA: RectF, boxB: RectF): Float {
        val intersection = RectF(boxA)
        if (!intersection.intersect(boxB)) return 0f

        val intersectionArea = intersection.width() * intersection.height()
        val unionArea = (boxA.width() * boxA.height()) + (boxB.width() * boxB.height()) - intersectionArea

        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }

    /**
     * Closes the TFLite interpreter to release resources.
     */
    fun close() {
        gpuDelegate?.close()
        gpuDelegate = null
        interpreter?.close()
        interpreter = null
        Log.i(TAG, "DiceDetector closed.")
    }

    /**
     * A data class representing a single detected object.
     * For this detector, it only contains the bounding box and a confidence score.
     */
    data class Detection(
        val boundingBox: RectF,
        val confidence: Float,
        val classId: Int = -1  // Which dice face (0-5 for faces 1-6), -1 if unknown
    )

    companion object {
        private const val TAG = "DiceDetector"

        /**
         * Helper function to load a TFLite model file from the assets folder.
         */
        private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
            val fileDescriptor = context.assets.openFd(filename)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }
    }
}
