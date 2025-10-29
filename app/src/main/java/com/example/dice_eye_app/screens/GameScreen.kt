package com.example.dice_eye_app.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.graphics.Matrix
import android.content.Context
import com.example.dice_eye_app.util.DebugBitmap
import com.example.dice_eye_app.util.DebugConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.example.dice_eye_app.camera.CameraPreview
import com.example.dice_eye_app.ml.SingleModelDiceDetector
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Game screen - The main screen for dice roll monitoring functionality
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun GameScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var detectedDice by remember { mutableStateOf<List<Int>>(emptyList()) }
    var rollHistory by remember { mutableStateOf(listOf<List<Int>>()) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Ready to capture") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraPermissionState = rememberPermissionState(permission = android.Manifest.permission.CAMERA)
    val storagePermissionState = rememberPermissionState(permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

    // Request storage permission on startup if not granted
    DisposableEffect(Unit) {
        if (!storagePermissionState.status.isGranted) {
            storagePermissionState.launchPermissionRequest()
        }
        onDispose { }
    }

    // Initialize SingleModelDiceDetector (one YOLO model for both detection and classification)
    val diceDetector = remember { SingleModelDiceDetector(context) }

    // Clean up detector when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            diceDetector.close()
        }
    }

    // Function to capture and analyze dice
    fun captureAndAnalyze() {
        android.util.Log.d("GameScreen", "Capture button pressed")

        if (!cameraPermissionState.status.isGranted) {
            statusMessage = "Camera permission required"
            cameraPermissionState.launchPermissionRequest()
            return
        }

        val capture = imageCapture
        if (capture == null) {
            statusMessage = "Camera not ready, please wait..."
            android.util.Log.e("GameScreen", "ImageCapture is null")
            return
        }

        isProcessing = true
        statusMessage = "Capturing..."

        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    android.util.Log.d("GameScreen", "Image captured successfully")

                    scope.launch {
                        try {
                            statusMessage = "Processing image..."

                            // Convert ImageProxy to Bitmap
                            val bitmap = withContext(Dispatchers.Default) {
                                imageProxyToBitmap(context, image)
                            }

                            if (bitmap == null) {
                                statusMessage = "Failed to process image"
                                android.util.Log.e("GameScreen", "Failed to convert image to bitmap")
                                return@launch
                            }

                            statusMessage = "Analyzing dice..."

                            // Run single-model detection + classification on background thread
                            val results = withContext(Dispatchers.Default) {
                                diceDetector.detectAndClassify(bitmap)
                            }

                            android.util.Log.d("GameScreen", "Detections (with classification): ${results.size}")

                            // Extract dice face values (already 1-6)
                            val detectedValues = results
                                .map { it.faceValue }
                                .sorted()

                            detectedDice = detectedValues

                            if (detectedValues.isEmpty()) {
                                statusMessage = "No dice detected. Try again with better lighting or angle."
                            } else {
                                val total = detectedValues.sum()
                                statusMessage = "Detected ${detectedValues.size} dice - Total: $total"
                            }

                            android.util.Log.d("GameScreen", "Detected dice values: $detectedValues")

                        } catch (e: Exception) {
                            android.util.Log.e("GameScreen", "Error analyzing image", e)
                            statusMessage = "Error: ${e.message}"
                        } finally {
                            isProcessing = false
                            image.close()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    android.util.Log.e("GameScreen", "Image capture failed", exception)
                    statusMessage = "Capture failed: ${exception.message}"
                    isProcessing = false
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dice Monitor") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!isProcessing) {
                        captureAndAnalyze()
                    }
                },
                containerColor = if (isProcessing)
                    MaterialTheme.colorScheme.secondary
                else
                    MaterialTheme.colorScheme.primary
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = "📷",
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status message
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = statusMessage,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            // Camera preview with permission handling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(Color.LightGray, RoundedCornerShape(8.dp))
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (cameraPermissionState.status.isGranted) {
                    // Camera permission is granted, show the camera preview
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
                        onImageCaptureCreated = { captureInstance ->
                            imageCapture = captureInstance
                            statusMessage = "Camera ready - tap to capture"
                            android.util.Log.d("GameScreen", "ImageCapture initialized")
                        }
                    )
                } else {
                    // Camera permission is not granted, show request button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Camera permission is required",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                            Text("Request Permission")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dice recognition results
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Last Detected Roll",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (detectedDice.isNotEmpty()) detectedDice.joinToString(", ") else "No dice detected yet",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (detectedDice.isNotEmpty())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = {
                                if (detectedDice.isNotEmpty()) {
                                    rollHistory = rollHistory + listOf(detectedDice)
                                    statusMessage = "Rolls ${detectedDice.joinToString(", ")} added to history"
                                    detectedDice = emptyList()
                                }
                            },
                            enabled = detectedDice.isNotEmpty()
                        ) {
                            Text("Save Roll")
                        }

                        Button(
                            onClick = {
                                detectedDice = emptyList()
                                statusMessage = "Ready to capture"
                            },
                            enabled = detectedDice.isNotEmpty()
                        ) {
                            Text("Reset")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Roll history
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Roll History (${rollHistory.size} rolls)",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (rollHistory.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No rolls recorded yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // This would be a LazyColumn in a real implementation
                        Column {
                            rollHistory.reversed().forEachIndexed { index, roll ->
                                if (index > 0) {
                                    HorizontalDivider()
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Roll #${rollHistory.size - index}")
                                    Text(roll.joinToString(", "), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun imageProxyToBitmap(appContext: Context, image: ImageProxy): Bitmap? {
    return try {
        val decoded = when (image.format) {
            ImageFormat.JPEG -> {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            ImageFormat.YUV_420_888 -> {
                val nv21 = yuv420888ToNv21(image)
                val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
                val out = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
                val jpegBytes = out.toByteArray()
                BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            }
            else -> {
                android.util.Log.w("GameScreen", "Unsupported ImageProxy format: ${image.format}. Attempting to decode first plane as JPEG")
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }

        if (decoded == null) return null

        val rotation = image.imageInfo.rotationDegrees
        val finalBmp = if (rotation != 0) {
            val m = Matrix()
            m.postRotate(rotation.toFloat())
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
            if (rotated != decoded) decoded.recycle()
            rotated
        } else {
            decoded
        }

        if (DebugConfig.ENABLED && DebugConfig.SAVE_ORIGINAL) {
            DebugBitmap.saveBitmap(appContext, finalBmp, "original_rotated")
        }

        finalBmp
    } catch (e: Exception) {
        android.util.Log.e("GameScreen", "Failed to convert ImageProxy to Bitmap", e)
        null
    }
}

private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
    val width = image.width
    val height = image.height

    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]

    val yRowStride = yPlane.rowStride
    val yPixelStride = yPlane.pixelStride

    val uRowStride = uPlane.rowStride
    val uPixelStride = uPlane.pixelStride

    val vRowStride = vPlane.rowStride
    val vPixelStride = vPlane.pixelStride

    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer

    val ySize = width * height
    val uvSize = width * height / 2

    val out = ByteArray(ySize + uvSize)

    // Copy Y plane: row by row to skip row padding
    var outIndex = 0
    for (row in 0 until height) {
        val yRowStart = row * yRowStride
        if (yPixelStride == 1) {
            // Fast path: contiguous row
            for (col in 0 until width) {
                out[outIndex++] = yBuffer.get(yRowStart + col)
            }
        } else {
            // Uncommon path
            for (col in 0 until width) {
                out[outIndex++] = yBuffer.get(yRowStart + col * yPixelStride)
            }
        }
    }

    // Copy UV planes and interleave to NV21 (V then U)
    val chromaHeight = height / 2
    var uvOutIndex = ySize
    for (row in 0 until chromaHeight) {
        val uRowStart = row * uRowStride
        val vRowStart = row * vRowStride
        for (col in 0 until width / 2) {
            val u = uBuffer.get(uRowStart + col * uPixelStride)
            val v = vBuffer.get(vRowStart + col * vPixelStride)
            out[uvOutIndex++] = v
            out[uvOutIndex++] = u
        }
    }

    return out
}
