package com.example.dice_eye_app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
     */
    fun saveBitmap(context: Context, bitmap: Bitmap, baseName: String) {
        if (!DebugConfig.ENABLED) return

        try {
            val timestamp = dateFormat.format(Date())
            val filename = "${timestamp}_${baseName}.jpg"

            // Use app-specific directory which doesn't require special permissions
            val path = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), DebugConfig.DEBUG_PATH)

            // Make sure directory exists
            if (!path.exists()) {
                val success = path.mkdirs()
                if (!success) {
                    Log.e(TAG, "Failed to create debug directory at ${path.absolutePath}")
                    return
                }
            }

            val file = File(path, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            Log.d(TAG, "Saved debug image: ${file.absolutePath}")
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
