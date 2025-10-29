package com.example.dice_eye_app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug utilities for saving and annotating bitmaps during dice detection
 */
object DebugBitmap {
    private const val TAG = "DebugBitmap"
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    /**
     * Save a bitmap to the debug directory with the given base name
     * Saves to public Pictures/DiceEyeDebug folder so it's visible via MTP on Windows
     */
    fun saveBitmap(context: Context, bitmap: Bitmap, baseName: String) {
        if (!DebugConfig.ENABLED) return

        try {
            val timestamp = dateFormat.format(Date())
            val filename = "${timestamp}_${baseName}.jpg"

            // Try public Pictures directory first (visible via MTP)
            var savedSuccessfully = false
            try {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val path = File(picturesDir, "DiceEyeDebug")

                // Make sure directory exists
                if (!path.exists()) {
                    val success = path.mkdirs()
                    Log.d(TAG, "Created directory: ${path.absolutePath}, success=$success")
                }

                val file = File(path, filename)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.flush()
                }

                // Verify file was actually written
                if (file.exists() && file.length() > 0) {
                    Log.e(TAG, "✅ SAVED TO PUBLIC PICTURES: ${file.absolutePath} (${file.length() / 1024}KB)")

                    // Trigger media scan so Windows MTP sees the file immediately
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        arrayOf("image/jpeg"),
                        null
                    )

                    savedSuccessfully = true
                } else {
                    Log.e(TAG, "❌ File not created or empty: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save to public Pictures, trying app-specific storage: ${e.message}")
            }

            // Fallback: Try app-specific storage (always works, no permissions needed)
            if (!savedSuccessfully) {
                try {
                    val appPath = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "DiceEyeDebug")
                    if (!appPath.exists()) {
                        appPath.mkdirs()
                    }

                    val file = File(appPath, filename)
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        out.flush()
                    }

                    if (file.exists() && file.length() > 0) {
                        Log.e(TAG, "✅ SAVED TO APP-SPECIFIC: ${file.absolutePath} (${file.length() / 1024}KB)")
                        Log.e(TAG, "⚠️ NOTE: App-specific files NOT visible via MTP until you use a file manager on the phone")
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "❌ FAILED BOTH LOCATIONS: ${e2.message}", e2)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error saving debug image", e)
        }
    }

    /**
     * Draw bounding boxes with labels on a bitmap
     * @param bitmap The source bitmap
     * @param boxes List of pairs: RectF box and label string
     * @return A new bitmap with boxes and labels drawn on it
     */
    fun drawOverlayLabeled(bitmap: Bitmap, boxes: List<Pair<RectF, String>>): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val boxPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 36f
            style = Paint.Style.FILL
            setShadowLayer(2f, 0f, 0f, Color.BLACK)
        }

        val backgroundPaint = Paint().apply {
            color = Color.argb(160, 0, 0, 0)
            style = Paint.Style.FILL
        }

        for ((box, label) in boxes) {
            // Draw box
            canvas.drawRect(box, boxPaint)

            // Draw label background
            val textWidth = textPaint.measureText(label)
            val textHeight = 40f
            val textLeft = box.left
            val textTop = box.top - textHeight

            if (textTop > 0) {
                canvas.drawRect(
                    textLeft - 2,
                    textTop - 2,
                    textLeft + textWidth + 2,
                    box.top,
                    backgroundPaint
                )
                canvas.drawText(label, textLeft, box.top - 10, textPaint)
            } else {
                canvas.drawRect(
                    textLeft - 2,
                    box.top,
                    textLeft + textWidth + 2,
                    box.top + textHeight,
                    backgroundPaint
                )
                canvas.drawText(label, textLeft, box.top + 30, textPaint)
            }
        }

        return result
    }
}
