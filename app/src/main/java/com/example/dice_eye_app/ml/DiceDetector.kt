package com.example.dice_eye_app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.example.dice_eye_app.util.DebugBitmap
import com.example.dice_eye_app.util.DebugConfig
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Detects the location of dice in a given bitmap image using a YOLO-based TFLite model.
 *
 * This class is responsible for Step 1 of the two-step detection process:
 * 1. Pre-processes the input bitmap (resizing, letterboxing).
 * 2. Runs inference using the `die_detection.tflite` model.
 * 3. Post-processes the model's output to generate a list of bounding boxes for potential dice.
 *
 * @param context The application context, used for asset loading and diagnostics.
 */
class DiceDetector(private val context: Context) {

    // Configuration for the detection model and post-processing.
    private object Config {
        const val MODEL_FILENAME = "die_detection.tflite"
        const val NUM_THREADS = 4

        // Detection thresholds, tuned for a balance of precision and recall.
        const val CONFIDENCE_THRESHOLD = 0.25f // Minimum score for a detection to be considered valid.
        const val IOU_THRESHOLD = 0.45f        // Threshold for non-maximum suppression (NMS).

        // Sanity filters for bounding boxes to eliminate obvious false positives.
        // Values are fractions of the original image dimensions.
        const val MIN_AREA_FRACTION = 0.002f   // Filter out tiny specks.
        const val MAX_AREA_FRACTION = 0.20f    // Filter out overly large boxes.
        const val MIN_ASPECT_RATIO = 0.6f      // Lower bound for width/height ratio.
        const val MAX_ASPECT_RATIO = 1.6f      // Upper bound for width/height ratio.

        // The maximum number of dice to return after all filtering and NMS.
        const val MAX_FINAL_DETECTIONS = 12
    }

    private var interpreter: Interpreter? = null
    private var modelLoaded = false

    // Model properties, auto-detected from the TFLite model file upon loading.
    private var modelInputWidth = 0
    private var modelInputHeight = 0
    private var modelInputChannels = 0
    private var modelInputType: DataType = DataType.UINT8

    // Pre-processing values, stored to correctly transform bounding boxes back to original image coordinates.
    private var lastScaleFactor: Float = 1f
    private var lastPadX: Float = 0f
    private var lastPadY: Float = 0f

    init {
        loadModel()
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
            // Step 1: Pre-process the image to match the model's input requirements.
            val inputBuffer = preprocessImage(bitmap)

            // Step 2: Prepare model outputs and run inference.
            val outputs = prepareOutputs()
            interpreter!!.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)

            // Step 3: Post-process the raw model output to get meaningful detections.
            return postprocess(outputs, bitmap.width, bitmap.height)

        } catch (e: Exception) {
            Log.e(TAG, "An error occurred during dice detection.", e)
            return emptyList()
        }
    }

    /**
     * Loads the TFLite model from assets and initializes the interpreter.
     */
    private fun loadModel() {
        try {
            val modelBuffer = loadModelFile(context, Config.MODEL_FILENAME)
            val options = Interpreter.Options().apply {
                numThreads = Config.NUM_THREADS
                // Disabling NNAPI can provide more consistent performance across devices.
                setUseNNAPI(false)
            }
            interpreter = Interpreter(modelBuffer, options)

            // Inspect the loaded model to determine its input/output shapes and types.
            inspectModel()
            modelLoaded = true
            Log.i(TAG, "Dice detection model loaded successfully.")

        } catch (e: Exception) {
            modelLoaded = false
            Log.e(TAG, "Failed to load dice detection model.", e)
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
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        // Calculate scaling factor and padding to create a letterboxed image.
        val scale = minOf(modelInputWidth / bitmap.width.toFloat(), modelInputHeight / bitmap.height.toFloat())
        val newW = (bitmap.width * scale).toInt()
        val newH = (bitmap.height * scale).toInt()
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
            DebugBitmap.save(context, letterboxedBitmap, "detector_input")
        }

        val pixels = IntArray(modelInputWidth * modelInputHeight)
        letterboxedBitmap.getPixels(pixels, 0, modelInputWidth, 0, 0, modelInputWidth, modelInputHeight)

        // Create a ByteBuffer matching the model's expected input type (UINT8).
        val buffer = ByteBuffer.allocateDirect(modelInputWidth * modelInputHeight * modelInputChannels)
        buffer.order(ByteOrder.nativeOrder())
        for (pixelValue in pixels) {
            buffer.put(((pixelValue shr 16) and 0xFF).toByte()) // R
            buffer.put(((pixelValue shr 8) and 0xFF).toByte())  // G
            buffer.put((pixelValue and 0xFF).toByte())          // B
        }
        return buffer.apply { rewind() }
    }

    /**
     * Allocates a map of buffers to receive the model's output.
     * The YOLO model is expected to have 4 outputs: boxes, scores, classes, and number of detections.
     */
    private fun prepareOutputs(): Map<Int, Any> {
        // The model is expected to output a maximum of 25 detections.
        val maxDetections = 25
        return mapOf(
            // Output 0: Bounding boxes [1, 25, 4]
            0 to Array(1) { Array(maxDetections) { FloatArray(4) } },
            // Output 1: Scores [1, 25]
            1 to Array(1) { FloatArray(maxDetections) },
            // Output 2: Classes [1, 25]
            2 to Array(1) { FloatArray(maxDetections) },
            // Output 3: Number of detections [1]
            3 to FloatArray(1)
        )
    }

    /**
     * Processes the raw output from the TFLite model into a clean list of [Detection] objects.
     */
    private fun postprocess(outputs: Map<Int, Any>, originalWidth: Int, originalHeight: Int): List<Detection> {
        // Extract data from the output map.
        val boxes = (outputs[0] as Array<Array<FloatArray>>)[0]
        val scores = (outputs[1] as Array<FloatArray>)[0]
        // Classes are ignored here, as this model only detects a single "die" class.
        val numDetections = (outputs[3] as FloatArray)[0].toInt()

        val detections = mutableListOf<Detection>()
        val imageArea = (originalWidth * originalHeight).toFloat()

        for (i in 0 until numDetections) {
            val score = scores[i]
            if (score < Config.CONFIDENCE_THRESHOLD) continue

            val box = boxes[i]
            val y1 = box[0]
            val x1 = box[1]
            val y2 = box[2]
            val x2 = box[3]

            // Convert normalized, letterboxed coordinates back to original image coordinates.
            val rect = denormalizeAndUnpad(x1, y1, x2, y2, originalWidth, originalHeight)

            // Apply sanity filters to the bounding box.
            if (!isValid(rect, imageArea)) {
                Log.d(TAG, "Detection $i discarded by sanity filters.")
                continue
            }

            detections.add(
                Detection(
                    boundingBox = rect,
                    confidence = score
                )
            )
        }
        Log.d(TAG, "${detections.size} detections passed confidence and sanity checks.")

        // Apply Non-Maximum Suppression (NMS) to remove overlapping boxes for the same object.
        val finalDetections = applyNMS(detections)

        if (DebugConfig.SAVE_DETECTIONS_OVERLAY) {
            val labeled = finalDetections.map { it.boundingBox to "Die (${"%.2f".format(it.confidence)})" }
            val originalBitmap = DebugBitmap.getOriginalBitmap() // Assumes a debug utility to fetch the original bitmap
            originalBitmap?.let {
                val overlay = DebugBitmap.drawOverlay(it, labeled)
                DebugBitmap.save(context, overlay, "detector_final_detections")
            }
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
        val confidence: Float
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
